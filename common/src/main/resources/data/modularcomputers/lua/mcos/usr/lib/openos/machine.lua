-- openos's machine: an OpenComputers computer for one program, made of mcos's (as wine makes a
-- Windows of Linux). The native part, over mcos's calls: component (the bus's devices through
-- adapters, openos/device/<type>.lua, and the terminal's gpu, screen and keyboard), computer (its
-- signals), unicode, filesystem, shell. On it, OpenOS's own libraries, ported (openos/lib: event,
-- keyboard, term, tty, text, serialization, sides, colors, buffer, uuid, robot), in the program's
-- environment: its own globals, as an OpenComputers program had (component, computer, unicode,
-- checkArg, os.sleep), its own require (OpenOS's library names; a library OpenOS had and openos
-- doesn't is an error saying so), its io the tty's.
local sys = require("sys")
local devices = require("devices")
local unicode = require("openos.unicode")
local filesystem = require("openos.filesystem")
local makeShell = require("openos.shell")
local terminal = require("openos.terminal")
local native = {computer = require("openos.computer"), component = require("openos.component")}

local machine = {}

local LIB = "/usr/lib/openos/lib/"
local PATH = LIB .. "?.lua;/home/lib/?.lua;./?.lua;" .. LIB .. "?/init.lua;/home/lib/?/init.lua;./?/init.lua"
local MACHINE_ID = "/etc/machine-id" -- this computer's address, made once (systemd's machine-id)
local OSVERSION = "OpenOS 1.8.x (openos, on mcos)"
-- mcos's device types with an adapter, and OpenComputers' component each is.
local ADAPTERS = {eeprom = "eeprom", redstone = "redstone", network = "modem", robotics = "robot"}
-- OpenOS's libraries openos doesn't have: requiring one says so.
local UNSUPPORTED = {process = true, pipe = true, devfs = true, rc = true, transforms = true, note = true,
  internet = true, bit32 = true, thread = true, sh = true}

-- OpenComputers' checkArg: argument n is one of the types, or an error.
local function checkArg(n, have, ...)
  have = type(have)
  for i = 1, select("#", ...) do
    if have == select(i, ...) then
      return
    end
  end
  error(("bad argument #%d (%s expected, got %s)"):format(n, table.concat({...}, " or "), have), 3)
end
_G.checkArg = checkArg -- (the native libraries check their arguments with it too)

local function uuid()
  local bytes = {}
  for i = 1, 16 do
    bytes[i] = math.random(0, 255)
  end
  bytes[7] = bytes[7] & 0x0F | 0x40
  bytes[9] = bytes[9] & 0x3F | 0x80
  return ("%02x%02x%02x%02x-%02x%02x-%02x%02x-%02x%02x-%02x%02x%02x%02x%02x%02x"):format(table.unpack(bytes))
end

-- This computer's address: /etc/machine-id's, made the first time (as the first boot does).
local function machineId()
  local file = io.open(MACHINE_ID)
  local id = file and file:read("l")
  if file then
    file:close()
  end
  if id and id:match("^%x+%-%x+%-%x+%-%x+%-%x+$") then
    return id
  end
  id = uuid()
  file = io.open(MACHINE_ID, "w")
  if file then
    file:write(id, "\n")
    file:close()
  end
  return id
end

-- ---- the components -----------------------------------------------------------------------------

local adapted = {} -- the bus's devices with adapters, by mcos's name: {address, type, close}

local function adapt(name)
  local info = devices.info(name)
  local kind = info and ADAPTERS[info.type]
  if not kind or adapted[name] then
    return
  end
  local address = (info.id or name):gsub("^id:", "")
  local ctx = {push = native.computer.pushSignal, source = native.computer.machine.source,
    settle = native.computer.machine.settle}
  local ok, methods, docs, close = pcall(require("openos.device." .. info.type), name, info, ctx, address)
  if not ok then
    return -- (a device that went as it was found)
  end
  native.component.machine.add(address, kind, methods, docs)
  adapted[name] = {address = address, type = kind, close = close}
  if kind == "robot" then
    native.computer.machine.robot = true
  end
  return address, kind
end

-- /dev's names coming and going: component_added and component_removed.
local function hotplug(watch)
  local notices = sys.read(watch, 4096) or ""
  for what, name in notices:gmatch("(%u+) /dev/([^\n]+)") do
    if what == "CREATE" then
      local address, kind = adapt(name)
      if address then
        native.computer.pushSignal("component_added", address, kind)
      end
    elseif what == "DELETE" and adapted[name] then
      local gone = adapted[name]
      adapted[name] = nil
      pcall(gone.close)
      native.component.machine.remove(gone.address)
      native.computer.pushSignal("component_removed", gone.address, gone.type)
    end
  end
end

local function deviceInfo()
  local info = {}
  for address, entry in pairs(native.component.machine.entries()) do
    info[address] = {class = entry.type, description = entry.type, product = entry.type}
  end
  return info
end

-- ---- the program's environment ------------------------------------------------------------------

local BASE = {"assert", "error", "getmetatable", "ipairs", "next", "pairs", "pcall", "rawequal", "rawget", "rawlen",
  "rawset", "select", "setmetatable", "tonumber", "tostring", "type", "xpcall", "_VERSION"}
local OS = {"clock", "date", "difftime", "remove", "rename", "time", "tmpname"}
local EXIT = {} -- what os.exit throws, to end the program where it is

local function copy(t)
  local c = {}
  for k, v in pairs(t) do
    c[k] = v
  end
  return c
end

local function environment(environ, execute)
  local env = {}
  for _, name in ipairs(BASE) do
    env[name] = _G[name]
  end
  env.string, env.table, env.math, env.coroutine, env.utf8, env.debug = string, table, math, coroutine, utf8, debug
  env._G, env._OSVERSION = env, OSVERSION
  env.checkArg, env.unicode = checkArg, unicode
  env.computer = copy(native.computer)
  env.computer.machine = nil
  env.component = copy(native.component)
  env.component.machine = nil
  env.load = function(chunk, name, mode, e)
    return load(chunk, name, mode, e or env)
  end
  env.loadfile = function(file, mode, e)
    return loadfile(file, mode, e or env)
  end
  env.dofile = function(file)
    local program, reason = env.loadfile(file)
    if not program then
      return error(reason .. ":" .. file, 0)
    end
    return program()
  end
  env.os = {}
  for _, name in ipairs(OS) do
    env.os[name] = os[name]
  end
  function env.os.exit(code)
    error({[EXIT] = true, code = code}, 0)
  end
  function env.os.execute(command)
    if command == nil then
      return true
    end
    return execute(command)
  end
  env.io = {popen = io.popen}

  local package = {path = PATH, preload = {}, config = "/\n;\n?\n!\n-\n"}
  package.loaded = {_G = env, string = string, table = table, math = math, coroutine = coroutine, utf8 = utf8,
    os = env.os, io = env.io, package = package, unicode = unicode, component = env.component,
    computer = env.computer, filesystem = filesystem, shell = makeShell(environ, execute)}
  env.package = package
  function env.require(name)
    checkArg(1, name, "string")
    local loaded = package.loaded[name]
    if loaded ~= nil then
      return loaded
    end
    local loader = package.preload[name]
    if not loader then
      if UNSUPPORTED[name] then
        error("openos: OpenOS's '" .. name .. "' library is not supported", 2)
      end
      local file, tried = _G.package.searchpath(name, package.path)
      if not file then
        error("module '" .. name .. "' not found:" .. tried, 2)
      end
      local why
      loader, why = loadfile(file, "t", env)
      if not loader then
        error("error loading module '" .. name .. "' from file '" .. file .. "':\n\t" .. why, 2)
      end
    end
    local value = loader(name)
    if value == nil then
      value = package.loaded[name] == nil and true or package.loaded[name]
    end
    package.loaded[name] = value
    return value
  end
  return env
end

-- ---- running a program --------------------------------------------------------------------------

-- machine.run(path, args): runs an OpenOS program with its arguments; its status (what os.exit
-- gave, or 0; 1 when it failed, its error on stderr).
function machine.run(path, args)
  local environ = sys.environ()
  environ.PWD = sys.getcwd()
  local tty -- (the program's tty library, once booted: its cursor is where the terminal's goes)
  local function cursor()
    if tty and tty.window then
      return tty.getCursor()
    end
  end
  local execute = function(command)
    local x, y = cursor()
    terminal.suspend(x, y)
    local results = table.pack(os.execute(command))
    local nx, ny = terminal.resume()
    if tty and nx then
      tty.setCursor(nx, ny)
    end
    return table.unpack(results, 1, results.n)
  end
  local env = environment(environ, execute)

  local computerMachine = native.computer.machine
  computerMachine.address = machineId()
  computerMachine.tmp = uuid()
  computerMachine.deviceInfo = deviceInfo
  native.component.machine.add(computerMachine.address, "computer", {
    isRunning = function() return true end,
    start = function() return false end,
    stop = function() native.computer.shutdown() end,
    beep = native.computer.beep,
    getDeviceInfo = deviceInfo,
  })

  local addresses = {screen = uuid(), gpu = uuid(), keyboard = uuid()}
  local x, y = terminal.open(native.computer.pushSignal, addresses)
  if x then
    native.component.machine.add(addresses.gpu, "gpu", terminal.gpuMethods)
    native.component.machine.add(addresses.screen, "screen", terminal.screenMethods)
    native.component.machine.add(addresses.keyboard, "keyboard", terminal.keyboardMethods)
    computerMachine.source(terminal.fd(), {IN = true}, terminal.read)
    computerMachine.beforeWait(terminal.flush)
  end
  computerMachine.bell = function()
    if terminal.fd() then
      terminal.flush()
      sys.write(terminal.fd(), "\a")
    end
  end
  for _, device in ipairs(devices.list()) do
    adapt(device.name)
  end
  local watch = sys.inotify()
  sys.watch(watch, "/dev")
  computerMachine.source(watch, {IN = true}, function()
    hotplug(watch)
  end)

  local function finish()
    local cx, cy = cursor()
    terminal.close(cx, cy)
    for name, entry in pairs(adapted) do
      pcall(entry.close)
      adapted[name] = nil
    end
    sys.close(watch)
  end
  computerMachine.exit = finish

  local status = 0
  local ok, why = xpcall(function()
    local boot = assert(loadfile(LIB .. "core/boot.lua", "t", env))
    boot({environ = environ, tty = {[0] = x and sys.isatty(0), [1] = x and sys.isatty(1), [2] = x and sys.isatty(2)},
      queued = computerMachine.queued, cursor = x and {x, y}})
    tty = env.require("tty")
    local main, reason = loadfile(path, "t", env)
    if not main then
      error(reason, 0)
    end
    local results = table.pack(main(table.unpack(args, 1, args.n)))
    if math.type(results[1]) == "integer" then
      status = results[1] -- (as OpenOS's sh took a program's status)
    elseif results[1] == false or results[1] == nil and results[2] ~= nil then
      status = 1
      if results[2] ~= nil then
        env.io.stderr:write(tostring(results[2]), "\n")
      end
    end
  end, function(e)
    if type(e) == "table" and e[EXIT] then
      return e
    end
    return tostring(e) .. "\n" .. debug.traceback("", 2):gsub("^\n", "")
  end)
  if not ok then
    if type(why) == "table" and why[EXIT] then
      local code = why.code
      status = (code == nil or code == true) and 0 or code == false and 1 or math.tointeger(code) or 1
    else
      status = 1
      local stderr = env.io and env.io.stderr
      if stderr and getmetatable(stderr) == "file" then
        pcall(stderr.write, stderr, tostring(why) .. "\n")
      else
        io.stderr:write(tostring(why), "\n")
      end
    end
  end
  if env.io.stderr then
    pcall(env.io.stderr.flush, env.io.stderr)
  end
  pcall(function()
    env.io.stdout:flush()
  end)
  finish()
  return status
end

return machine
