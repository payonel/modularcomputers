-- machine.lua: the Lua CPU's firmware, between the host (the Java runner) and the EEPROM's code.
--
-- The host runs this as the firmware coroutine (firmware_co), on the computer's own thread, with the
-- standard library (debug included), and resumes it whenever it has an answer. This file builds the sandbox,
-- loads the EEPROM's text into it as the user coroutine (user_co), budgets user_co and freezes it
-- when its slice is spent, and serves its syscalls. The EEPROM's code is user code: it never sees
-- this file's locals, the real globals or 'debug'.
--
-- What it asks the host, by yielding firmware_co:
--   "slice"                       the slice is spent: a point where the host may save the machine;
--                                 resumed at once
--   "call", name, args...         a board call (list, status, type, methods, doc, info, uptime,
--                                 time, power, hardware, invoke), carried out on the server thread, where
--                                 devices live; answered with its values
--   "baseline"                    the sandbox is built: the host measures the memory held as the
--                                 baseline the program's RAM goes on top of; asked once
--   "clock"                       the machine's clock (uptime), in ticks; answered at once
--   "wait", deadline, held, sources...
--                                 wait for an interrupt (sources: any when none; deadline: on the
--                                 machine's clock, -1 for none, so a wait saved partway ends when
--                                 it would have; held: what the inbox holds, by address); answered with
--                                 the pending sources, as a sequence, and the device data taken
--                                 for the inbox: {address, method, value} each
--   "halt"                        the CPU halts; never resumed
--   "poweroff", "reboot"          the machine turns off, or off and on again; never resumed
--
-- Rings (user design, 2026-10-14): a program may give this file a ring for a source
-- (board.ring(address, size)): a table it reads, {head, tail, size, [1..size] = packets}, which
-- this file fills with what the device sends, without being asked, as a device writes into a ring
-- its driver gave it by DMA. The host takes a device's data as it arrives, up to the room each
-- ring has (its credit: this file reports the free slots at every resume), into a mailbox; at every
-- resume of user code, this file moves the mailbox into the rings. The program reads packets
-- between head and tail and moves head on; this file only ever writes slots and tail. A source
-- with a ring isn't cached in the inbox.
--
-- Stops (user design, 2026-10-15; LungoLua 1.1's thaw with stops): a program may name coroutines
-- (board.addstop) whose resumer should hear of new input at once, as a scheduler names its
-- children. When a ring gets data the program had nothing left of (it had taken all before), this
-- file thaws user code with those stops: the resume of a named coroutine returns as if it had just
-- frozen there (debug.isfrozen is then true; the program thaws it to go on, with the sandbox's
-- debug.thaw), so the scheduler runs, sees the data in its ring, and decides. Otherwise a thaw is
-- plain. Nothing in user code has to ask for input.
--
-- Traps and the timer (2026-10-15): board.trap() is the CPU's syscall instruction: it freezes user
-- code where it is and thaws it with the stops, so the resume of the nearest named coroutine returns
-- (its scheduler, a kernel, serves the request the coroutine left and thaws it); with no stop in the
-- chain it returns at once. It's answered here, with no host call, and the slice goes on with what
-- it had left. A spent slice is the timer interrupt: its thaw stops too, so a kernel gets control
-- back from a busy process at least once a slice, as a timer tick preempts a real one.
--
-- Device data (user design, 2026-10-13): this file caches what devices have (keys, sensor events)
-- in its inbox, a queue per device. As a wait is answered the host takes, from each device with
-- data, as much as that device's queue has room for (its size comes from the RAM: none with only
-- tier 1); a device keeps the rest, so nothing is dropped, and a full queue never crowds out
-- another device's. A read of a device's stream (a terminal's read, a sensor's pull) comes from the
-- inbox with no syscall, and a wait the inbox can answer needs none either.
-- and what it ends with: "returned" (the EEPROM's code returned: a panic), "abort", why (the
-- EEPROM's text didn't load), or an error (the EEPROM's code crashed).
--
-- Syscalls freeze instead of yielding: every board call, a wait and a halt records its request and cuts user_co's budget to one step; the budget
-- callback then freezes user_co's whole chain, wherever it is (inside coroutines, a sort's
-- comparison, a gsub callback), and the freeze lands here. The host answers, user_co thaws, and the
-- syscall returns the answer. No user coroutine sees a yield, and pcall can't catch a freeze.
--
-- This file never calls methods on strings ("s:sub()"): the string metatable is shared with user
-- code.

-- The EEPROM's text, taken from the registry, where the host put it: dropped once it's compiled, as
-- is the bootloader's function once user code has it (a chunk's arguments, or this chunk's locals,
-- would keep both for as long as the machine runs).
local image = debug.getregistry()["modularcomputers.image"]
debug.getregistry()["modularcomputers.image"] = nil

local coroutine_create, coroutine_resume, coroutine_status, coroutine_yield =
  coroutine.create, coroutine.resume, coroutine.status, coroutine.yield
local setbudget, getbudget, freeze, thaw, isfrozen = debug.setbudget, debug.getbudget, debug.freeze, debug.thaw, debug.isfrozen
-- The host's switch: while user code runs it may use the RAM less the firmware's reserve, so a
-- program that fills its memory gets Lua's error and this file can still answer the host.
local usermode = debug.getregistry()["modularcomputers.usermode"]
-- The board's interrupt status, as the server thread published it the moment a line rose: the
-- sources with an interrupt raised (the bus, devices with data they still hold). Read here, free.
local pending = debug.getregistry()["modularcomputers.pending"]
-- The host's mailbox of pushed device data (address, value, address, value...), nil when empty;
-- and its ear for how much room each ring has.
local drain = debug.getregistry()["modularcomputers.drain"]
local credit = debug.getregistry()["modularcomputers.credit"]
local load, pairs, ipairs, select, type, error, tostring = load, pairs, ipairs, select, type, error, tostring
local table_pack, table_unpack, table_remove = table.pack, table.unpack, table.remove
local setmetatable, rawget, rawset = setmetatable, rawget, rawset
local math_huge, math_floor, math_type = math.huge, math.floor, math.type
local table_create = table.create

-- Steps user_co runs before its budget freezes it: how often the host gets a safe point.
local SLICE = 100000
-- The most packets a ring may hold.
local RING_MAX = 1024

local user_co
-- Syscalls waiting for the host, oldest first; each request (packed) gets its own reply (.reply).
-- One slot shared by all wasn't enough (found by the LungoLua agent, 2026-10-13): a finalizer
-- (__gc) can make a board call inside another syscall that's waiting, which then took its reply
-- or spun forever; and a syscall left by an error after it was recorded was carried out anyway.
local queue = {}
-- A request left by an error before it was answered withdraws itself.
local REQUEST = {__close = function(request, err)
  if err ~= nil and request.reply == nil then
    for i = 1, #queue do
      if queue[i] == request then
        table_remove(queue, i)
        break
      end
    end
  end
end}

-- The inbox: a few of each device's data, by address: {method = ..., [1] = oldest, ...}.
local inbox = {}

-- The rings the program gave, by source: {ring = its table, size, tail (this file's own count; the
-- ring's tail field is a copy for the program), overflow = what came and had no slot yet, armed =
-- the program had taken everything: new data is news, and stops the thaw}.
local rings = {}

-- The coroutines whose resumer hears of new input (board.addstop): weak, so a stop that's
-- dropped by the program goes with it.
local stops = setmetatable({}, {__mode = "k"})

-- A ring's head as the program left it, trusted only within [tail - size, tail]: anything else
-- reads as full, so this file never writes past the slots it made.
local function head_of(r)
  local head = rawget(r.ring, "head")
  if math_type(head) ~= "integer" or head > r.tail or head < r.tail - r.size then
    return r.tail - r.size
  end
  return head
end

-- Puts a packet in a ring's next slot, if it's free: no allocation (the slot and the fields exist).
local function store(r, value)
  if r.tail - head_of(r) >= r.size then
    return false
  end
  rawset(r.ring, r.tail % r.size + 1, value)
  r.tail = r.tail + 1
  rawset(r.ring, "tail", r.tail)
  return true
end

-- Packets waiting for the program in a source's ring, or in its inbox queue.
local function unread(address)
  local r = rings[address]
  if r then
    return r.tail - head_of(r) + #r.overflow
  end
  local queue = inbox[address]
  return queue and #queue or 0
end

-- Before user code resumes (firmware mode): what the host pushed goes into the rings, and the host
-- hears how much room each has now. Whether a ring got news: data where the program had taken
-- everything (so a stop isn't repeated until it has taken what came before).
local function deliver()
  local got = drain()
  if got then
    for i = 1, #got, 2 do
      local r = rings[got[i]]
      if r then
        r.overflow[#r.overflow + 1] = got[i + 1]
      end
    end
  end
  local news = false
  for address, r in pairs(rings) do
    if head_of(r) == r.tail and r.overflow[1] == nil then
      r.armed = true -- all taken: the next data is news
    end
    local before = r.tail
    while r.overflow[1] ~= nil and store(r, r.overflow[1]) do
      table_remove(r.overflow, 1)
    end
    if r.tail > before and r.armed then
      r.armed = false
      news = true
    end
    credit(address, r.size - (r.tail - head_of(r)) - #r.overflow)
  end
  return news
end

-- A thaw that stops at the program's stops (the resume of a named coroutine returns).
local function thaw_stopping(co)
  return thaw(co, stops)
end

-- Asks the host (yields firmware_co).
local ask = coroutine_yield

-- The budget's callback always freezes: where a freeze can't happen, the engine ends user_co's
-- chain with an error instead, and the CPU shows it. Nothing runs on unfrozen past its slice.
local function spent(co)
  usermode(false) -- the firmware's now: the freeze may need its reserve (user mode comes back with the thaw)
  freeze(user_co)
end

-- A syscall's bookkeeping runs on the firmware's reserve (usermode(false)), though it's in
-- user_co: a program that's filled its memory (or left garbage a dead frame still holds) gets its
-- error in its own code, never in this file's, and device data taken for the inbox is never lost.
-- Returns the reply packed, still in firmware mode; the caller goes back to user mode.
local function syscall(...)
  usermode(false)
  local request <close> = setmetatable(table_pack(...), REQUEST)
  if request[1] == "trap" then
    local _, _, left = getbudget(user_co)
    request.left = left -- what the slice had left: a trap doesn't start a new one
  end
  queue[#queue + 1] = request
  while request.reply == nil do
    -- The next step freezes the chain; armed again when a nested syscall (a finalizer's) took that freeze.
    setbudget(user_co, 1, spent)
  end
  usermode(false) -- (the thaw went back to user mode)
  return request.reply
end

-- A syscall answered as values, back in user mode.
local function call(...)
  local reply = syscall(...)
  usermode(true)
  return table_unpack(reply, 1, reply.n)
end

local function copy(t)
  local c = {}
  for k, v in pairs(t) do
    c[k] = v
  end
  return c
end

-- The sandbox: what the EEPROM's code sees as its globals.
local sandbox = {
  assert = assert, error = error, getmetatable = getmetatable, ipairs = ipairs, next = next,
  pairs = pairs, pcall = pcall, rawequal = rawequal, rawget = rawget, rawlen = rawlen,
  rawset = rawset, select = select, setmetatable = setmetatable, tonumber = tonumber,
  tostring = tostring, type = type, xpcall = xpcall, _VERSION = _VERSION,
  coroutine = copy(coroutine), string = copy(string), table = copy(table), math = copy(math),
  utf8 = copy(utf8),
}
sandbox._G = sandbox

-- Text only (crafted bytecode is the classic way out of a VM), and the sandbox by default.
sandbox.load = function(chunk, name, mode, env)
  return load(chunk, name, "t", env or sandbox)
end

-- The collector is the VM's: a program may measure it and nudge it, not stop it.
local collect = collectgarbage
-- A program's own frozen coroutines (a stop left one so): whether one is, and going on with it; and
-- a traceback, for reporting errors (LungoLua's reads the stack raw: no metamethod of the program's runs).
sandbox.debug = {
  traceback = debug.traceback,
  isfrozen = function(co)
    return isfrozen(co)
  end,
  thaw = function(co)
    return thaw(co)
  end,
}

sandbox.collectgarbage = function(what, ...)
  what = what or "collect"
  if what == "count" or what == "step" or what == "collect" or what == "isrunning" then
    return collect(what, ...)
  end
  error("collectgarbage: '" .. tostring(what) .. "' isn't allowed", 2)
end

sandbox.board = {
  list = function() return call("call", "list") end,
  status = function() return call("call", "status") end,
  type = function(address) return call("call", "type", address) end,
  methods = function(address) return call("call", "methods", address) end,
  doc = function(address, method) return call("call", "doc", address, method) end,
  info = function(address) return call("call", "info", address) end,
  uptime = function() return call("call", "uptime") end,
  time = function() return call("call", "time") end,
  power = function() return call("call", "power") end,
  hardware = function() return call("call", "hardware") end,
  -- true and the method's values, or false and why it failed (a device never stops the machine);
  -- a read of a device's stream comes from the inbox when it has some. An empty queue whose device
  -- has no interrupt raised answers nil, free: interrupts are level-triggered, so the device has
  -- nothing either (a key arriving meanwhile raises it, and the next wait or read sees it).
  invoke = function(target, method, ...)
    local queue = inbox[target]
    if queue and queue.method == method and select("#", ...) == 0 then
      if queue[1] ~= nil then
        return true, table_remove(queue, 1)
      end
      usermode(false)
      local raised = pending()
      usermode(true)
      local up = false
      for i = 1, #raised do
        if raised[i] == target then
          up = true
          break
        end
      end
      if not up then
        return true, nil
      end
    end
    return call("call", "invoke", target, method, ...)
  end,
  -- waits until one of the sources (any, when none) has an interrupt pending, or 'seconds' pass
  waitForInterrupt = function(seconds, ...)
    if seconds ~= nil and type(seconds) ~= "number" then
      error("bad argument #1 to 'waitForInterrupt' (number expected, got " .. type(seconds) .. ")", 2)
    end
    -- A warm inbox answers at once, on this thread: free. But news goes cold: a source waited on
    -- with an interrupt raised and nothing of it in the inbox (a key just typed, a bus change) goes
    -- to the server thread, which answers with everything pending and tops every device's queue up.
    local wanted, ready = table_pack(...), {}
    local function want(address)
      if wanted.n == 0 then
        return true
      end
      for i = 1, wanted.n do
        if wanted[i] == address then
          return true
        end
      end
      return false
    end
    usermode(false)
    local raised = pending()
    usermode(true)
    local news = false
    for _, address in ipairs(raised) do
      if unread(address) == 0 and want(address) then
        news = true
        break
      end
    end
    if not news then
      for address in pairs(inbox) do
        if not rings[address] and unread(address) > 0 and want(address) then
          ready[#ready + 1] = address
        end
      end
      for address in pairs(rings) do
        if unread(address) > 0 and want(address) then
          ready[#ready + 1] = address
        end
      end
      if #ready > 0 then
        return ready
      end
    end
    local ticks = (seconds == nil or seconds == math_huge) and -1 or math_floor(seconds * 20 + 0.5)
    if ticks < -1 then
      ticks = 0
    end
    -- The host counts data in the inbox as pending, and tops each device's queue up as it answers.
    local held = {}
    for address, queue in pairs(inbox) do
      held[address] = #queue
    end
    for address in pairs(rings) do
      held[address] = unread(address)
    end
    local reply = syscall("wait", ticks, held, ...)
    -- Still in firmware mode: the data's out of the devices now, so it must reach the program:
    -- a source's ring when it has one (the host may not know it does: after a restore it learns
    -- the rings again only at the next delivery, and a wait saved partway is answered first), its
    -- inbox queue otherwise.
    for _, item in ipairs(reply[2]) do
      local r = rings[item.address]
      if r then
        r.overflow[#r.overflow + 1] = item.value -- (into the ring at the next delivery, before user code runs)
      else
        local queue = inbox[item.address]
        if not queue or queue.method ~= item.method then
          queue = {method = item.method}
          inbox[item.address] = queue
        end
        queue[#queue + 1] = item.value
      end
    end
    usermode(true)
    return reply[1]
  end,
  -- gives this file a ring for a source's data, 'size' packets: it fills the ring as the device
  -- sends, with no call (see "Rings" above); returns the ring, {head, tail, size, [1..size]}
  ring = function(source, size)
    if type(source) ~= "string" then
      error("bad argument #1 to 'ring' (string expected, got " .. type(source) .. ")", 2)
    end
    if math_type(size) ~= "integer" or size < 1 or size > RING_MAX then
      error("bad argument #2 to 'ring' (an integer from 1 to " .. RING_MAX .. " expected)", 2)
    end
    local ring = table_create(size, 3) -- the program's memory (user mode)
    for i = 1, size do
      ring[i] = false
    end
    ring.head, ring.tail, ring.size = 0, 0, size
    usermode(false)
    -- what the inbox holds of the source already goes to the ring first: the program reads it there now
    local queue = inbox[source]
    inbox[source] = nil
    local overflow = {}
    if queue then
      for i = 1, #queue do
        overflow[i] = queue[i]
      end
    end
    rings[source] = {ring = ring, size = size, tail = 0, overflow = overflow, armed = true}
    credit(source, size - #overflow) -- (it goes in at the next delivery)
    usermode(true)
    return ring
  end,
  -- names a coroutine whose resumer hears of new input at once (see "Stops" above); removestop
  -- takes it back
  addstop = function(co)
    if type(co) ~= "thread" then
      error("bad argument #1 to 'addstop' (coroutine expected, got " .. type(co) .. ")", 2)
    end
    usermode(false)
    stops[co] = true
    usermode(true)
  end,
  removestop = function(co)
    stops[co] = nil
  end,
  -- the syscall instruction: stops at the nearest stop, whose resumer serves what it was left
  -- (see "Traps and the timer" above); returns when thawed, or at once with no stop in the chain
  trap = function()
    syscall("trap")
    usermode(true)
  end,
  -- the CPU stops; power stays on. It never returns.
  halt = function()
    syscall("halt")
  end,
  -- the machine turns off (ACPI soft-off), as its power button does. It never returns.
  poweroff = function()
    syscall("poweroff")
  end,
  -- the machine turns off and on again: the EEPROM boots afresh. It never returns.
  reboot = function()
    syscall("reboot")
  end,
}

-- The sandbox is built: the host measures what's held now as the baseline the program's RAM goes
-- on top of (so the RAM is all the program's, whatever this file and the libraries take).
ask("baseline")

local main, why = load(image, "=eeprom", "t", sandbox)
image = nil
if not main then
  return "abort", "EEPROM: " .. why
end
user_co = coroutine_create(main)
main = nil
setbudget(user_co, SLICE, spent)

-- Runs user code (a resume or a thaw) in user mode; its results are packed back in the firmware's.
local function user(run)
  usermode(true)
  local a, b, c = run(user_co)
  usermode(false)
  return a, b, c
end

-- A trap's reply: nothing.
local TRAPPED = {n = 0}

local result = table_pack(user(coroutine_resume))
while true do
  if isfrozen(user_co) then
    local stop = false -- a trap or the timer: the thaw stops
    local budget = SLICE
    if #queue > 0 then
      -- Every syscall waiting, answered in order (a finalizer's queues behind the call it ran inside).
      local asked = false
      while #queue > 0 do
        local request = table_remove(queue, 1)
        if request[1] == "trap" then
          request.reply = TRAPPED
          stop = true
          if not asked and request.left and request.left > 0 then
            budget = request.left
          end
        else
          asked = true
          budget = SLICE
          if request[1] == "wait" and request[2] >= 0 then
            request[2] = ask("clock") + request[2] -- its length becomes a deadline
          end
          request.reply = table_pack(ask(table_unpack(request, 1, request.n)))
        end
      end
    else
      ask("slice") -- the slice is spent: the timer
      stop = true
    end
    setbudget(user_co, budget, spent) -- a new slice, or what a trap's had left
    if deliver() or stop then
      result = table_pack(user(thaw_stopping)) -- new input: the program's scheduler hears at once
    else
      result = table_pack(user(thaw))
    end
  elseif coroutine_status(user_co) == "dead" then
    if result[1] then
      return "returned"
    end
    error(result[2], 0)
  else
    ask("slice") -- the EEPROM's code yielded at its top: it gives up its turn
    setbudget(user_co, SLICE, spent)
    deliver()
    result = table_pack(user(coroutine_resume))
  end
end
