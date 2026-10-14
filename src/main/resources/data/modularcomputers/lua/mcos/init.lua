-- mcos (Lua): the kernel. The bootloader hands it its boot disk's address, then its command line
-- (words: console=<address> names the input device its tty reads, the board's console by default).
local root, cmdline = ...

local VERSION = "0.1"
local CONSOLE = "console" -- the device the kernel's tty is on, unless the command line says (console=)
local SHELL = "/sh.lua" -- what runs on the tty, by its path (no PATH); the kernel starts it again when it ends
-- Starts within the window before the kernel gives up on it, as sysvinit's "respawning too fast".
local RESPAWN_LIMIT = 10
local RESPAWN_WINDOW_TICKS = 100 -- 5 seconds
local READ_SIZE = 4096 -- what one read of a file asks for (a disk's most)

local options = {}
for word in string.gmatch(cmdline or "", "%S+") do
  local key, value = string.match(word, "^([^=]+)=(.*)$")
  options[key or word] = value or true
end

-- Files on the boot disk.
local function readfile(path)
  local opened, file = board.invoke(root, "open", path)
  if not opened then
    return nil, file
  end
  local parts = {}
  while true do
    local ok, chunk = board.invoke(file, "read", READ_SIZE)
    if not ok or chunk == nil then
      break
    end
    parts[#parts + 1] = chunk
  end
  board.invoke(file, "close")
  return table.concat(parts)
end

local function loadfile(path, env)
  local code, why = readfile(path)
  if not code then
    return nil, path .. ": " .. tostring(why)
  end
  return load(code, "=" .. path, "t", env)
end

local tty = assert(loadfile("/lib/tty.lua"))()
local newio = assert(loadfile("/lib/io.lua"))()

-- Processes: each a coroutine with its own globals, and the tty it inherited from its parent. A
-- process waiting for input yields BLOCKED; the kernel resumes it when its tty has some.
local BLOCKED = {}
local processes = {}

local function block()
  coroutine.yield(BLOCKED)
end

local function spawn(path, parent)
  local env = setmetatable({}, {__index = _G})
  env._G = env
  local process = {path = path, tty = parent.tty}
  env.io = newio(process.tty, block)
  local main, why = loadfile(path, env)
  if not main then
    return nil, why
  end
  process.co = coroutine.create(main)
  processes[#processes + 1] = process
  return process
end

-- A signal goes to the tty's foreground process; with no handlers yet, it ends the process.
local function signal(t, name)
  if t.foreground then
    t.foreground.signal = name
  end
end

local console = tty.new(options.console or CONSOLE, signal)
local kernel = {tty = console} -- the parent of what the kernel starts

local starts = {}
local function shell()
  local now = board.uptime()
  while starts[1] and now - starts[1] > RESPAWN_WINDOW_TICKS do
    table.remove(starts, 1)
  end
  if #starts >= RESPAWN_LIMIT then
    console:write("mcos: " .. SHELL .. " respawning too fast: halted\n")
    board.halt()
  end
  starts[#starts + 1] = now
  local process, why = spawn(SHELL, kernel)
  if not process then
    console:write("mcos: " .. tostring(why) .. "\n")
    board.halt()
  end
  process.respawn = true
  console.foreground = process
end

local function ended(process, killed)
  if killed then
    process.tty:write("\n") -- after the ^C, as a shell does when its job dies of a signal
  end
  if process.respawn then
    shell()
  end
end

-- Runs every process that can run, until each waits or ends; whether any ran.
local function run()
  local ran = false
  for i = #processes, 1, -1 do
    local process = processes[i]
    if process.signal then
      coroutine.close(process.co)
      table.remove(processes, i)
      ended(process, true)
      ran = true
    elseif not process.waiting or process.tty:readable() then
      local ok, why = coroutine.resume(process.co)
      if not ok then
        process.tty:write(process.path .. ": " .. tostring(why) .. "\n")
      end
      if coroutine.status(process.co) == "dead" then
        table.remove(processes, i)
        ended(process, false)
      else
        process.waiting = true
      end
      ran = true
    end
  end
  return ran
end

console:write("\27[0m\27[2J\27[H" .. "mcos " .. VERSION .. "\n")
shell()
while true do
  if not run() then
    -- Nothing can run: the input service waits for the tty's device and hands the tty what it sent
    -- (a read takes every byte waiting).
    board.waitForInterrupt(nil, console.device)
    while true do
      local ok, bytes = board.invoke(console.device, "read")
      if not ok or bytes == nil then
        break
      end
      console:receive(bytes)
    end
  end
end
