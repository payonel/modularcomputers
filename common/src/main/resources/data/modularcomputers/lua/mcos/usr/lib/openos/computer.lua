-- OpenComputers' computer library, for openos's programs (a machine's own there; here over mcos's
-- calls). Its signals are a queue: what's pushed, and what the machine's sources make of what's
-- ready (the terminal's keys, a device's events, a socket's datagrams), each source a file that
-- pullSignal waits on with sys.poll, all at once. The machine's own calls (sources, the queue's
-- size) are in computer.machine, not the program's table.
local sys = require("sys")

local computer = {}
local machine = {}

local QUEUE_SIZE = 256 -- signals kept unpulled (OpenComputers' limit): one more is dropped
local UNITS_PER_MICROWATT_HOUR = 72000 / 1000000 -- stored power units in a µWh (72000 a watt-hour)
local SUPPLY = "/sys/class/power_supply/case/"

local queue = {}
local sources = {} -- what pullSignal waits on, by fd: {events, ready}
local before = {} -- what's run before any wait (the terminal's output, written)

-- machine.source(fd, events, ready): pullSignal waits on fd for events (poll's: IN, PRI), and
-- calls ready(what) when they come; ready(nil) ends it.
function machine.source(fd, events, ready)
  sources[fd] = events and {events = events, ready = ready} or nil
end

function machine.beforeWait(f)
  before[#before + 1] = f
end

-- How many signals wait.
function machine.queued()
  return #queue
end

local function settle()
  for _, f in ipairs(before) do
    f()
  end
end
machine.settle = settle

function computer.pushSignal(name, ...)
  if type(name) ~= "string" then
    error(("bad argument #1 (string expected, got %s)"):format(type(name)), 2)
  end
  if #queue >= QUEUE_SIZE then
    return false
  end
  queue[#queue + 1] = table.pack(name, ...)
  return true
end

-- The oldest signal, waiting for one up to 'timeout' seconds (none: as long as it takes); nothing
-- when the time runs out.
function computer.pullSignal(timeout)
  if timeout ~= nil and type(timeout) ~= "number" then
    error(("bad argument #1 (number expected, got %s)"):format(type(timeout)), 2)
  end
  local deadline = timeout and timeout ~= math.huge and sys.uptime() + timeout or nil
  while not queue[1] do
    settle()
    local fds = {}
    for fd, source in pairs(sources) do
      fds[fd] = source.events
    end
    local left = deadline and math.max(0, deadline - sys.uptime())
    local ready = sys.poll(fds, left)
    for fd, what in pairs(ready) do
      if sources[fd] then
        sources[fd].ready(what)
      end
    end
    if not queue[1] and deadline and sys.uptime() >= deadline then
      return
    end
  end
  local signal = table.remove(queue, 1)
  return table.unpack(signal, 1, signal.n)
end

function computer.uptime()
  return sys.uptime()
end

function computer.address()
  return machine.address
end

function computer.tmpAddress()
  return machine.tmp
end

function computer.freeMemory()
  return sys.sysinfo().freeram
end

function computer.totalMemory()
  return sys.sysinfo().totalram
end

local function supply(name)
  local file = io.open(SUPPLY .. name)
  if not file then
    return nil
  end
  local value = tonumber(file:read("l"))
  file:close()
  return value
end

-- The case's store, in the game's power units (its file's µWh); an infinite one is endless.
function computer.energy()
  local now = supply("energy_now")
  return now and now * UNITS_PER_MICROWATT_HOUR or math.huge
end

function computer.maxEnergy()
  local full = supply("energy_full")
  return full and full * UNITS_PER_MICROWATT_HOUR or math.huge
end

function computer.shutdown(reboot)
  machine.exit()
  sys.reboot(reboot and "restart" or "poweroff")
end

-- The terminal's bell, and the computer waits as long as the beep (OpenComputers' did).
function computer.beep(frequency, duration)
  local beeps = {}
  if type(frequency) == "string" then
    for symbol in frequency:gmatch("[%.%-]") do
      beeps[#beeps + 1] = symbol == "." and 0.1 or 0.3
    end
  else
    beeps[1] = tonumber(duration) or 0.1
  end
  for _, seconds in ipairs(beeps) do
    machine.bell()
    sys.sleep(seconds)
  end
end

function computer.users()
end

function computer.addUser()
  return nil, "openos: users are not supported"
end

function computer.removeUser()
  return false
end

function computer.isRobot()
  return machine.robot == true
end

function computer.getArchitecture()
  return _VERSION
end

function computer.getArchitectures()
  return {_VERSION}
end

function computer.setArchitecture()
  error("openos: the architecture is this CPU's: " .. _VERSION, 2)
end

-- The boot disk's address: the disk mounted at /.
function computer.getBootAddress()
  for _, mount in ipairs(sys.mounts()) do
    if mount.path == "/" then
      return (tostring(mount.source):gsub("^id:", ""))
    end
  end
end

function computer.setBootAddress()
  error("openos: the boot disk is the EEPROM's boot order: efibootmgr sets it", 2)
end

function computer.getDeviceInfo()
  return machine.deviceInfo()
end

function computer.getProgramLocations()
  return {}
end

computer.machine = machine
return computer
