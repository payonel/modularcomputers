-- devices: the bus's devices, for programs (OpenComputers' component library's place, done mcos's
-- way): what's on the bus, a device as a table of its methods, and waiting for one to come. All in
-- user space, on what any program can reach: /dev (a device's file, whose ioctls are its methods),
-- /sys/class (what each device is, its methods' descriptions) and inotify (/dev's names coming and
-- going). The kernel has no devices library: this is one program's convenience, as libudev is.
--
--   local devices = require("devices")
--   for _, device in ipairs(devices.list("robotics")) do print(device.name) end
--   local robot = assert(devices.open("robotics0"))
--   robot.walk(3)                                   -- an ioctl: the device's walk
--   for name, method in pairs(robot) do print(name, method) end   -- each its description
--   local name = devices.wait("charger", 10)        -- the first charger, waiting up to 10 s for one
--   local event = devices.event(robot)              -- its next event, waiting for one
--   local ok, why = devices.ended(robot, "walk")    -- a buffered command's end: true, or false and why
local sys = require("sys")
local fs = require("fs")

local devices = {}

local CLASS = "/sys/class"
local WRITE_ONLY = {sound = true} -- (a sound card's file plays; it doesn't record: opened to write)

-- A small file's text, or nil.
local function slurp(path)
  local file = io.open(path)
  if not file then
    return nil
  end
  local text = file:read("a")
  file:close()
  return text
end

-- devices.list([type]): the devices on the bus (of that type), {name, type} each, by name.
function devices.list(kind)
  local found = {}
  for _, class in ipairs(fs.list(CLASS) or {}) do
    if not kind or class.name == kind then
      for _, device in ipairs(fs.list(CLASS .. "/" .. class.name) or {}) do
        found[#found + 1] = {name = device.name, type = class.name}
      end
    end
  end
  table.sort(found, function(a, b)
    return a.name < b.name
  end)
  return found
end

-- The type of the device named 'name', or nil when there's none.
local function typeof(name)
  for _, device in ipairs(devices.list()) do
    if device.name == name then
      return device.type
    end
  end
end

-- A device's method: calling it calls the device's (an ioctl on its file: its answer, or nil and
-- why); tostring gives its description, from /sys (OpenComputers' methods printed theirs).
local Method = {}
Method.__index = Method

function Method.__call(self, ...)
  return sys.ioctl(self.fd, self.name, ...)
end

function Method.__tostring(self)
  return self.doc
end

-- What each opened device is: its name, type and file, kept beside it (its table holds only methods).
local about = setmetatable({}, {__mode = "k"})

-- devices.open(name): the device (robotics0, or /dev/robotics0) as a table of its methods, each
-- called as a function (robot.walk(3)), and pairs over it lists them. Nil and why when there's no
-- such device. Its file stays open until devices.close.
function devices.open(name)
  name = name:match("([^/]+)$") or name
  local kind = typeof(name)
  if not kind then
    return nil, "no such device: " .. name
  end
  local fd, why = sys.open("/dev/" .. name, WRITE_ONLY[kind] and "w" or "r")
  if not fd then
    return nil, why
  end
  local base = CLASS .. "/" .. kind .. "/" .. name
  local device = {}
  for _, method in ipairs(fs.list(base .. "/methods") or {}) do
    local doc = (slurp(base .. "/methods/" .. method.name) or method.name .. "()"):gsub("\n$", "")
    device[method.name] = setmetatable({fd = fd, name = method.name, doc = doc}, Method)
  end
  about[device] = {name = name, type = kind, fd = fd, id = (slurp(base .. "/id") or ""):gsub("\n$", "")}
  return device
end

-- devices.info(device): what a device is: an opened one, or one by its name (operator0, or
-- /dev/operator0): {name, type, id, path, label, methods} (path: its slot, nil when it's cabled;
-- methods: their names), and an opened one's fd. Nil and why when there's no such device.
function devices.info(device)
  local opened = type(device) == "table" and about[device]
  local name = opened and opened.name or type(device) == "string" and (device:match("([^/]+)$") or device)
  local kind = name and typeof(name)
  if not kind then
    return nil, "no such device: " .. tostring(name or device)
  end
  local base = CLASS .. "/" .. kind .. "/" .. name
  local function attribute(key)
    local value = (slurp(base .. "/" .. key) or "-"):gsub("\n$", "")
    return value ~= "-" and value or nil
  end
  local info = {name = name, type = kind, id = attribute("id"), path = attribute("path"), label = attribute("label"), methods = {},
    fd = opened and opened.fd or nil}
  for i, method in ipairs(fs.list(base .. "/methods") or {}) do
    info.methods[i] = method.name
  end
  return info
end

-- devices.close(device): closes its file.
function devices.close(device)
  local info = about[device]
  if info then
    sys.close(info.fd)
    about[device] = nil
  end
  return true
end

-- devices.wait(what[, seconds]): the name of the first device that is, or comes onto the bus, of
-- type 'what' or named 'what' (as OpenComputers' component_added, but a device already there
-- counts: no race between looking and waiting). It watches /dev with inotify and polls it; nil and
-- "timed out" when 'seconds' pass first.
function devices.wait(what, seconds)
  local watch = sys.inotify()
  sys.watch(watch, "/dev") -- (watching before looking: one coming meanwhile isn't missed)
  local function found()
    for _, device in ipairs(devices.list()) do
      if device.type == what or device.name == what then
        return device.name
      end
    end
  end
  local deadline = seconds and sys.uptime() + seconds
  local name = found()
  while not name do
    local left = deadline and deadline - sys.uptime()
    if left and left <= 0 then
      sys.close(watch)
      return nil, "timed out"
    end
    local ready = sys.poll({[watch] = {IN = true}}, left)
    if ready[watch] then
      sys.read(watch, 4096) -- (what came: whatever it was, look again)
      name = found()
    end
  end
  sys.close(watch)
  return name
end

-- devices.event(device[, seconds]): an opened device's next event (its pull method's), waiting for
-- one if none waits (poll's PRI on its file); with seconds, nil and "timed out" after that long.
-- The loop every program waiting on a device writes, once.
function devices.event(device, seconds)
  local opened = about[device]
  if not opened or not device.pull then
    return nil, "not an opened device with events"
  end
  local deadline = seconds and sys.uptime() + seconds
  while true do
    local got, why = device.pull()
    if got then
      return got
    elseif why then
      return nil, why
    end
    local left = deadline and deadline - sys.uptime()
    if left and left <= 0 then
      return nil, "timed out"
    end
    sys.poll({[opened.fd] = {PRI = true}}, left)
  end
end

-- A buffered device's command's end (.agents/BUFFERED.md): waits for 'method''s next completion
-- event on 'device' (taking the events before it: news a program wanting it reads with event
-- instead), and answers true, or false and why it failed (or nil and why the wait did).
function devices.ended(device, method, seconds)
  while true do
    local got, why = devices.event(device, seconds)
    if not got then
      return nil, why
    elseif got.event == method and got.ok ~= nil then
      return got.ok, got.why
    end
  end
end

return devices
