-- /init.lua: a tiny operating system with a command shell. It reads a command, runs it, and
-- reads the next, forever. Its commands talk to the board and its devices: list them, read their
-- methods, call any method, wait for interrupts, read and write files on the boot disk, run a Lua
-- program from it, and turn the computer off. Type help for the list.
local disk = ...  -- the boot disk's address (sata:0, say): what the bootloader passes

local READ_SIZE = 4096 -- bytes a file read asks for at a time
local TICKS_PER_SECOND = 20 -- the board counts time in game ticks

-- ---- the screen and the keyboard ---------------------------------------------------------------

-- Writes text to every screen ("console"). A terminal goes back to the left only on \r.
local function write(text)
  board.invoke("console", "write", (text:gsub("\n", "\r\n")))
end

-- A value as text: tables as {key = value, ...}, so a method's answers can be read.
local function show(value)
  if type(value) == "string" then
    return string.format("%q", value)
  elseif type(value) ~= "table" then
    return tostring(value)
  end
  local parts = {}
  for k, v in pairs(value) do
    parts[#parts + 1] = (type(k) == "string" and k or "[" .. show(k) .. "]") .. " = " .. show(v)
  end
  table.sort(parts)
  return "{" .. table.concat(parts, ", ") .. "}"
end

-- Writes its arguments, separated by spaces, and a new line.
local function print(...)
  local parts = table.pack(...)
  for i = 1, parts.n do
    parts[i] = tostring(parts[i])
  end
  write(table.concat(parts, " ") .. "\n")
end

-- Reads a line: shows what's typed, takes Backspace, ends at Enter.
local function readline(prompt)
  write(prompt)
  local line = ""
  local escape = false -- in the middle of a key's escape sequence (an arrow key sends ESC [ A)
  while true do
    board.waitForInterrupt(nil, "console") -- sleeps until a key is pressed
    local ok, keys = board.invoke("console", "read")
    if ok and keys then
      for i = 1, #keys do
        local key = keys:sub(i, i)
        local byte = key:byte()
        if escape then
          escape = not key:match("[%a~]")
        elseif byte == 27 then
          escape = true
        elseif key == "\r" then
          write("\n")
          return line
        elseif byte == 127 or byte == 8 then
          if #line > 0 then
            line = line:sub(1, -2)
            write("\b \b")
          end
        elseif byte >= 32 then
          line = line .. key
          write(key)
        end
      end
    end
  end
end

-- ---- devices and files -------------------------------------------------------------------------

-- A device's address from what's typed: a type (redstone, the first one on the bus) or an address
-- as it is (sata:0, exp:1, id:<uuid>, console).
local function find(name)
  for _, device in ipairs(board.list()) do
    if device.type == name then
      return device.address
    end
  end
  return name
end

-- The boot disk's file at path, all of it; or nil and why.
local function readfile(path)
  local ok, file = board.invoke(disk, "open", path)
  if not ok then
    return nil, file
  end
  local parts = {}
  while true do
    local read, chunk = board.invoke(file, "read", READ_SIZE)
    if not read or chunk == nil then
      break
    end
    parts[#parts + 1] = chunk
  end
  board.invoke(file, "close")
  return table.concat(parts)
end

-- Writes text to a file on the boot disk (made, or replaced); true, or nil and why.
local function writefile(path, text)
  local ok, file = board.invoke(disk, "open", path, "w")
  if not ok then
    return nil, file
  end
  board.invoke(file, "write", text)
  local closed, why = board.invoke(file, "close") -- (a disk keeps what's written at close)
  if not closed then
    return nil, why
  end
  return true
end

-- ---- the commands ------------------------------------------------------------------------------

-- Each command: a function given the words after its name, and the whole line as typed.
local commands = {}

commands.help = function()
  print("devices                    the devices on the bus")
  print("methods <device>           a device's methods, and what each does")
  print("call <device> <method> ... calls a method; its arguments are Lua values: 1 \"text\" {a = 1}")
  print("wait [seconds]             waits for an interrupt from any device but the keyboard")
  print("ls [dir]                   the files in a directory on the boot disk")
  print("cat <file>                 shows a file")
  print("save <file> <text>         writes the text to a file")
  print("run <file> [words]         runs a Lua program from the boot disk")
  print("uptime                     seconds since the computer started")
  print("reboot, off                restarts, or turns off, the computer")
end

commands.devices = function()
  for _, device in ipairs(board.list()) do
    print(device.address, device.type, device.id)
  end
end

commands.methods = function(words)
  local address = find(words[1] or "")
  local names = board.methods(address)
  if not names then
    print("no device " .. (words[1] or ""))
    return
  end
  for _, method in ipairs(names) do
    print(board.doc(address, method))
  end
end

commands.call = function(words, rest)
  local address, method = find(words[1] or ""), words[2]
  if not method then
    print("usage: call <device> <method> [arguments]")
    return
  end
  -- The arguments: whatever follows the method's name on the line (after "call", the device and
  -- the method), read as Lua values (with nothing else visible to them: no globals).
  local argtext = rest:match("^%s*%S+%s+%S+%s+%S+%s*(.*)$") or ""
  local chunk, why = load("return " .. argtext, "=arguments", "t", {})
  if not chunk then
    print("bad arguments: " .. why)
    return
  end
  local results = table.pack(board.invoke(address, method, chunk()))
  if not results[1] then
    print("failed: " .. tostring(results[2]))
    return
  end
  for i = 2, results.n do
    print(show(results[i]))
  end
end

commands.wait = function(words)
  -- Every device but the terminals (their keys are the shell's), and "bus": the bus's own
  -- interrupt, raised when a device joins or leaves.
  local sources = {"bus"}
  for _, device in ipairs(board.list()) do
    if device.type ~= "terminal" then
      sources[#sources + 1] = device.address
    end
  end
  local raised = board.waitForInterrupt(tonumber(words[1]), table.unpack(sources))
  if #raised == 0 then
    print("nothing")
  end
  for _, source in ipairs(raised) do
    print(source) -- still raised until its data is taken: call it (pull, read, changes...)
  end
end

commands.ls = function(words)
  local ok, names = board.invoke(disk, "list", words[1] or "/")
  if not ok then
    print(names)
    return
  end
  for _, name in ipairs(names or {}) do
    print(name)
  end
end

commands.cat = function(words)
  local text, why = readfile(words[1] or "")
  write(text or (tostring(why) .. "\n"))
end

commands.save = function(words, rest)
  local text = rest:match("^%s*%S+%s+%S+%s(.*)$") or "" -- (the line after "save" and the file, as typed)
  local ok, why = writefile(words[1] or "", text .. "\n")
  if not ok then
    print(why)
  end
end

commands.run = function(words)
  local path = table.remove(words, 1) or ""
  local text, why = readfile(path)
  if not text then
    print(why)
    return
  end
  -- The program sees the OS's print, write and readline, its words as arg, and every global.
  local env = setmetatable({print = print, write = write, readline = readline, disk = disk, arg = words}, {__index = _G})
  local program, err = load(text, "=" .. path, "t", env)
  if not program then
    print(err)
    return
  end
  program(table.unpack(words))
end

commands.uptime = function()
  print(board.uptime() / TICKS_PER_SECOND) -- (the board's uptime is in ticks)
end

commands.reboot = function()
  board.reboot()
end

commands.off = function()
  board.poweroff()
end

-- ---- the shell ---------------------------------------------------------------------------------

print("tiny OS, booted from " .. disk .. ". Type help.")
while true do
  local line = readline("# ")
  local words = {}
  for word in line:gmatch("%S+") do
    words[#words + 1] = word
  end
  local name = table.remove(words, 1)
  if name then
    local command = commands[name]
    if command then
      -- A command that fails says why; the shell goes on (an error here would stop the CPU).
      local ok, err = pcall(command, words, line)
      if not ok then
        print("error: " .. tostring(err))
      end
    else
      print(name .. ": no such command")
    end
  end
end
