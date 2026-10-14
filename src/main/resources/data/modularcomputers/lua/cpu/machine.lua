-- machine.lua: the Lua CPU's firmware, between the host (the Java runner) and the EEPROM's code.
--
-- The host runs this as the kernel coroutine (kernel_co), on the computer's own thread, with the
-- standard library (debug included), and resumes it whenever it has an answer. This file builds the sandbox,
-- loads the EEPROM's text into it as the user coroutine (user_co), budgets user_co and freezes it
-- when its slice is spent, and serves its syscalls. The EEPROM's code is user code: it never sees
-- this file's locals, the real globals or 'debug'.
--
-- What it asks the host, by yielding kernel_co:
--   "slice"                       the slice is spent: a point where the host may save the machine;
--                                 resumed at once
--   "call", name, args...         a board call (list, status, type, methods, doc, info, uptime,
--                                 power, hardware, invoke), carried out on the server thread, where
--                                 devices live; answered with its values
--   "clock"                       the machine's clock (uptime), in ticks; answered at once
--   "wait", deadline, held, sources...
--                                 wait for an interrupt (sources: any when none; deadline: on the
--                                 machine's clock, -1 for none, so a wait saved partway ends when
--                                 it would have; held: what the inbox holds, by address); answered with
--                                 the pending sources, as a sequence, and the device data taken
--                                 for the inbox: {address, method, value} each
--   "halt"                        the CPU halts; never resumed
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

local image = ...

local coroutine_create, coroutine_resume, coroutine_status, coroutine_yield =
  coroutine.create, coroutine.resume, coroutine.status, coroutine.yield
local setbudget, freeze, thaw, isfrozen = debug.setbudget, debug.freeze, debug.thaw, debug.isfrozen
-- The host's switch: while user code runs it may use the RAM less the firmware's reserve, so a
-- program that fills its memory gets Lua's error and this file can still answer the host.
local usermode = debug.getregistry()["modularcomputers.usermode"]
-- The board's interrupt status, as the server thread published it the moment a line rose: the
-- sources with an interrupt raised (the bus, devices with data they still hold). Read here, free.
local pending = debug.getregistry()["modularcomputers.pending"]
local load, pairs, ipairs, select, type, error, tostring = load, pairs, ipairs, select, type, error, tostring
local table_pack, table_unpack, table_remove = table.pack, table.unpack, table.remove
local setmetatable = setmetatable
local math_huge, math_floor = math.huge, math.floor

-- Steps user_co runs before its budget freezes it: how often the host gets a safe point.
local SLICE = 100000

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

-- Asks the host (yields kernel_co).
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
  power = function() return call("call", "power") end,
  hardware = function() return call("call", "hardware") end,
  -- true and the method's values, or false and why it failed (a device never stops the machine);
  -- a read of a device's stream comes from the inbox when it has some
  invoke = function(target, method, ...)
    local queue = inbox[target]
    if queue and queue.method == method and queue[1] ~= nil and select("#", ...) == 0 then
      return true, table_remove(queue, 1)
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
      local queue = inbox[address]
      if (not queue or queue[1] == nil) and want(address) then
        news = true
        break
      end
    end
    if not news then
      for address, queue in pairs(inbox) do
        if queue[1] ~= nil and want(address) then
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
    local reply = syscall("wait", ticks, held, ...)
    -- Still in firmware mode: the data's out of the devices now, so it must reach the inbox.
    for _, item in ipairs(reply[2]) do
      local queue = inbox[item.address]
      if not queue or queue.method ~= item.method then
        queue = {method = item.method}
        inbox[item.address] = queue
      end
      queue[#queue + 1] = item.value
    end
    usermode(true)
    return reply[1]
  end,
  -- the CPU stops; power stays on. It never returns.
  halt = function()
    syscall("halt")
  end,
}

local main, why = load(image, "=eeprom", "t", sandbox)
if not main then
  return "abort", "EEPROM: " .. why
end
user_co = coroutine_create(main)
setbudget(user_co, SLICE, spent)

-- Runs user code (a resume or a thaw) in user mode; its results are packed back in the firmware's.
local function user(run)
  usermode(true)
  local a, b, c = run(user_co)
  usermode(false)
  return a, b, c
end

local result = table_pack(user(coroutine_resume))
while true do
  if isfrozen(user_co) then
    if #queue > 0 then
      -- Every syscall waiting, answered in order (a finalizer's queues behind the call it ran inside).
      while #queue > 0 do
        local request = table_remove(queue, 1)
        if request[1] == "wait" and request[2] >= 0 then
          request[2] = ask("clock") + request[2] -- its length becomes a deadline
        end
        request.reply = table_pack(ask(table_unpack(request, 1, request.n)))
      end
    else
      ask("slice") -- the slice is spent
    end
    setbudget(user_co, SLICE, spent) -- a new slice
    result = table_pack(user(thaw))
  elseif coroutine_status(user_co) == "dead" then
    if result[1] then
      return "returned"
    end
    error(result[2], 0)
  else
    ask("slice") -- the EEPROM's code yielded at its top: it gives up its turn
    setbudget(user_co, SLICE, spent)
    result = table_pack(user(coroutine_resume))
  end
end
