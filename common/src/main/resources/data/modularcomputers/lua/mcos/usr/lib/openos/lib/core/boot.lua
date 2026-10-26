-- core/boot: what OpenOS's boot scripts set up for a program (ported from OpenOS, MIT: see
-- /usr/share/openos/LICENSE), run in the program's environment before it: print (00_base), os
-- (02_os), io over the tty and the program's files (03_io), the primary components and
-- component.gpu's kind of access (04_component), the keyboard's state (92_keyboard), the gpu bound
-- to the screen and the tty to it (91_gpu, 93_term). Given the machine's streams: whether each of
-- stdin, stdout and stderr is the terminal, and the files it is when not.
local std = ...
local computer = require("computer")
local component = require("component")
local event = require("event")
local keyboard = require("keyboard")
local buffer = require("buffer")
local filesystem = require("filesystem")

-- 00_base: print
function print(...)
  local args = table.pack(...)
  local stdout = io.stdout
  local pre = ""
  for i = 1, args.n do
    stdout:write(pre, (assert(tostring(args[i]), "'tostring' must return a string to 'print'")))
    pre = "\t"
  end
  stdout:write("\n")
  stdout:flush()
end

-- 02_os: the environment as OpenOS's (getenv with no name: all of it), and sleep as a pull
local environ = std.environ
function os.getenv(varname)
  if not varname then
    return environ
  elseif varname == "#" then
    return #environ
  end
  return environ[varname]
end

function os.setenv(varname, value)
  checkArg(1, varname, "string", "number")
  if value ~= nil then
    value = tostring(value)
  end
  environ[varname] = value
  return value
end

function os.sleep(timeout)
  checkArg(1, timeout, "number", "nil")
  local deadline = computer.uptime() + (timeout or 0)
  repeat
    event.pull(deadline - computer.uptime())
  until computer.uptime() >= deadline
end

-- 04_component: primaries, component.<type>
local adding = {}
local primaries = {}

setmetatable(component, {
  __index = function(_, key)
    return component.getPrimary(key)
  end,
  __pairs = function(self)
    local parent = false
    return function(_, key)
      if parent then
        return next(primaries, key)
      else
        local k, v = next(self, key)
        if not k then
          parent = true
          return next(primaries)
        else
          return k, v
        end
      end
    end
  end
})

function component.get(address, componentType)
  checkArg(1, address, "string")
  checkArg(2, componentType, "string", "nil")
  for c in component.list(componentType, true) do
    if c:sub(1, address:len()) == address then
      return c
    end
  end
  return nil, "no such component"
end

function component.isAvailable(componentType)
  checkArg(1, componentType, "string")
  if not primaries[componentType] and not adding[componentType] then
    component.setPrimary(componentType, component.list(componentType, true)())
  end
  return primaries[componentType] ~= nil
end

function component.isPrimary(address)
  local componentType = component.type(address)
  if componentType then
    if component.isAvailable(componentType) then
      return primaries[componentType].address == address
    end
  end
  return false
end

function component.getPrimary(componentType)
  checkArg(1, componentType, "string")
  assert(component.isAvailable(componentType),
    "no primary '" .. componentType .. "' available")
  return primaries[componentType]
end

function component.setPrimary(componentType, address)
  checkArg(1, componentType, "string")
  checkArg(2, address, "string", "nil")
  if address ~= nil then
    address = component.get(address, componentType)
    assert(address, "no such component")
  end

  local wasAvailable = primaries[componentType]
  if wasAvailable and address == wasAvailable.address then
    return
  end
  local wasAdding = adding[componentType]
  if wasAdding and address == wasAdding.address then
    return
  end
  if wasAdding then
    event.cancel(wasAdding.timer)
  end
  primaries[componentType] = nil
  adding[componentType] = nil

  local primary = address and component.proxy(address) or nil
  if wasAvailable then
    computer.pushSignal("component_unavailable", componentType)
  end
  if primary then
    if wasAvailable or wasAdding then
      adding[componentType] = {
        address = address,
        proxy = primary,
        timer = event.timer(0.1, function()
          adding[componentType] = nil
          primaries[componentType] = primary
          computer.pushSignal("component_available", componentType)
        end)
      }
    else
      primaries[componentType] = primary
      computer.pushSignal("component_available", componentType)
    end
  end
end

local function onComponentAdded(_, address, componentType)
  local prev = primaries[componentType] or (adding[componentType] and adding[componentType].proxy)
  if not prev then
    component.setPrimary(componentType, address)
  end
end

local function onComponentRemoved(_, address, componentType)
  if primaries[componentType] and primaries[componentType].address == address or
     adding[componentType] and adding[componentType].address == address
  then
    component.setPrimary(componentType, component.list(componentType, true)())
  end
end

event.listen("component_added", onComponentAdded)
event.listen("component_removed", onComponentRemoved)

-- 92_keyboard: which keys are down
local function onKeyChange(ev, _, char, code)
  keyboard.pressedChars[char] = ev == "key_down" or nil
  keyboard.pressedCodes[code] = ev == "key_down" or nil
end

event.listen("key_down", onKeyChange)
event.listen("key_up", onKeyChange)

-- 91_gpu: the gpu bound to the screen, the tty to the gpu
local tty = require("tty")
local function onComponentAvailable(_, componentType)
  if (componentType == "screen" and component.isAvailable("gpu")) or
     (componentType == "gpu" and component.isAvailable("screen"))
  then
    local gpu, screen = component.gpu, component.screen
    local screen_address = screen.address
    if gpu.getScreen() ~= screen_address then
      gpu.bind(screen_address)
    end
    local depth = math.floor(2^(gpu.getDepth()))
    os.setenv("TERM", "term-"..depth.."color")
    event.push("gpu_bound", gpu.address, screen_address)
    if tty.gpu() ~= gpu then
      tty.bind(gpu)
      event.push("term_available")
    end
  end
end

event.listen("component_available", onComponentAvailable)

-- 93_term: the tty's gpu and keyboard as components come and go
local function components_changed(ename, address, type)
  local window = tty.window
  if not window then
    return
  end
  if ename == "component_available" or ename == "component_unavailable" then
    type = address
  end
  if ename == "component_removed" or ename == "component_unavailable" then
    if type == "gpu" and window.gpu and window.gpu.address == address then
      window.gpu = nil
      window.keyboard = nil
    elseif type == "keyboard" then
      window.keyboard = nil
    end
    if (type == "screen" or type == "gpu") and not tty.isAvailable() then
      event.push("term_unavailable")
    end
  elseif (ename == "component_added" or ename == "component_available") and type == "keyboard" then
    window.keyboard = nil
  end
end

event.listen("component_removed", components_changed)
event.listen("component_added", components_changed)
event.listen("component_available", components_changed)
event.listen("component_unavailable", components_changed)

-- 03_io: the standard streams, the tty's (stderr red) or the program's files
local streams = {}

function io.stream(fd, file, mode)
  checkArg(1, fd, "number")
  checkArg(2, file, "table", "string", "nil")
  if file then
    if type(file) == "string" then
      file = assert(io.open(file, mode))
    end
    streams[fd] = file
  end
  return streams[fd]
end

function io.input(file)
  return io.stream(0, file, "r")
end

function io.output(file)
  return io.stream(1, file, "w")
end

function io.error(file)
  return io.stream(2, file, "w")
end

function io.open(path, mode)
  local stream, result = filesystem.open(path, mode)
  if stream then
    return buffer.new(mode, stream)
  end
  return nil, result
end

function io.close(file)
  return (file or io.output()):close()
end

function io.flush()
  return io.output():flush()
end

function io.read(...)
  return io.input():read(...)
end

function io.write(...)
  return io.output():write(...)
end

function io.lines(filename, ...)
  if filename then
    local file, reason = io.open(filename)
    if not file then
      error(reason, 2)
    end
    local args = table.pack(...)
    return function()
      local result = table.pack(file:read(table.unpack(args, 1, args.n)))
      if not result[1] then
        if result[2] then
          error(result[2], 2)
        else
          file:close()
          return nil
        end
      end
      return table.unpack(result, 1, result.n)
    end
  end
  return io.input():lines()
end

function io.tmpfile()
  local name = os.tmpname()
  if name then
    return io.open(name, "a")
  end
end

function io.type(object)
  if type(object) == "table" and getmetatable(object) == "file" then
    return object.closed and "closed file" or "file"
  end
  return nil
end

local tty_stream = tty.stream
local function standard(fd, mode, writer)
  if std.tty[fd] then
    local file = buffer.new(mode, writer or tty_stream)
    file:setvbuf("no")
    file.tty = true
    file.close = tty_stream.close
    return file
  end
  return buffer.new(mode, filesystem.stream(fd))
end

io.input(standard(0, "r"))
io.output(standard(1, "w"))
io.error(standard(2, "w", setmetatable({
  write = function(_, str)
    return tty_stream:write("\27[31m" .. str .. "\27[37m")
  end
}, {__index = tty_stream})))

setmetatable(io, {__index = function(_, k)
  return k == "stdin" and io.input() or k == "stdout" and io.output() or k == "stderr" and io.error() or nil
end})

-- The components as they're there at the start, each added as at OpenOS's boot; the tty at the
-- terminal's cursor.
for address, componentType in component.list() do
  onComponentAdded("component_added", address, componentType)
end
while std.queued() > 0 do
  computer.pullSignal(0) -- (event.pull(0) gives up when its deadline's passed, and a moment always has)
end
if std.cursor then
  tty.setCursor(std.cursor[1], std.cursor[2])
end
