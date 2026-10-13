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
-- inbox with no syscall.
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
local load, pairs, ipairs, select, type, error, tostring = load, pairs, ipairs, select, type, error, tostring
local table_pack, table_unpack, table_remove = table.pack, table.unpack, table.remove
local math_huge, math_floor = math.huge, math.floor

-- Steps user_co runs before its budget freezes it: how often the host gets a safe point.
local SLICE = 100000

local user_co
local request -- the syscall waiting for the host, packed
local answer  -- the host's answer to it, packed

-- The inbox: a few of each device's data, by address: {method = ..., [1] = oldest, ...}.
local inbox = {}

-- Asks the host (yields kernel_co).
local ask = coroutine_yield

-- The budget's callback always freezes: where a freeze can't happen, the engine ends user_co's
-- chain with an error instead, and the CPU shows it. Nothing runs on unfrozen past its slice.
local function spent(co)
  freeze(user_co)
end

local function syscall(...)
  request = table_pack(...)
  setbudget(user_co, 1, spent) -- the next step freezes the chain, in the loop below
  while answer == nil do
  end
  local reply = answer
  answer = nil
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
  list = function() return syscall("call", "list") end,
  status = function() return syscall("call", "status") end,
  type = function(address) return syscall("call", "type", address) end,
  methods = function(address) return syscall("call", "methods", address) end,
  doc = function(address, method) return syscall("call", "doc", address, method) end,
  info = function(address) return syscall("call", "info", address) end,
  uptime = function() return syscall("call", "uptime") end,
  power = function() return syscall("call", "power") end,
  hardware = function() return syscall("call", "hardware") end,
  -- true and the method's values, or false and why it failed (a device never stops the machine);
  -- a read of a device's stream comes from the inbox when it has some
  invoke = function(target, method, ...)
    local queue = inbox[target]
    if queue and queue.method == method and queue[1] ~= nil and select("#", ...) == 0 then
      return true, table_remove(queue, 1)
    end
    return syscall("call", "invoke", target, method, ...)
  end,
  -- waits until one of the sources (any, when none) has an interrupt pending, or 'seconds' pass
  waitForInterrupt = function(seconds, ...)
    if seconds ~= nil and type(seconds) ~= "number" then
      error("bad argument #1 to 'waitForInterrupt' (number expected, got " .. type(seconds) .. ")", 2)
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
    local ready, taken = syscall("wait", ticks, held, ...)
    for _, item in ipairs(taken) do
      local queue = inbox[item.address]
      if not queue or queue.method ~= item.method then
        queue = {method = item.method}
        inbox[item.address] = queue
      end
      queue[#queue + 1] = item.value
    end
    return ready
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

local result = table_pack(coroutine_resume(user_co))
while true do
  if isfrozen(user_co) then
    if request then
      local asked = request
      request = nil
      if asked[1] == "wait" and asked[2] >= 0 then
        asked[2] = ask("clock") + asked[2] -- its length becomes a deadline
      end
      answer = table_pack(ask(table_unpack(asked, 1, asked.n)))
    else
      ask("slice") -- the slice is spent
    end
    setbudget(user_co, SLICE, spent) -- a new slice
    result = table_pack(thaw(user_co))
  elseif coroutine_status(user_co) == "dead" then
    if result[1] then
      return "returned"
    end
    error(result[2], 0)
  else
    ask("slice") -- the EEPROM's code yielded at its top: it gives up its turn
    setbudget(user_co, SLICE, spent)
    result = table_pack(coroutine_resume(user_co))
  end
end
