-- Walk out, say where it is, and walk home: its pose (where it is from its origin, the way it faced
-- when it started) is exact, so walkTo(0, 0, 0) brings it back onto the very block it started on.
--   lua robotics-home.lua [forward [right]]     (3 and 2 when not given)
local devices = require("devices")
local sys = require("sys")

local FORWARD = tonumber(arg[1] or "3")
local RIGHT = tonumber(arg[2] or "2")

local robot = assert(devices.open("robotics0"))

-- Waits for its next event, and gives it.
local function event()
  local fd = devices.info(robot).fd
  while true do
    local got = robot.pull()
    if got then
      return got
    end
    sys.poll({[fd] = {PRI = true}})
  end
end

-- Starts a move, and stops with why if it doesn't arrive.
local function go(ok, why)
  if not ok then
    io.stderr:write("robotics-home: ", tostring(why), "\n")
    os.exit(1)
  end
  local done = event()
  if done.event ~= "arrived" then
    io.stderr:write("robotics-home: ", done.event, " (", tostring(done.why), ")\n")
    os.exit(1)
  end
end

local function where()
  local pose = robot.pose()
  print(string.format("at %s forward, %s right, %s up, facing %s", pose.forward, pose.right, pose.up, pose.heading))
end

go(robot.walk(FORWARD, RIGHT))
where()
go(robot.walkTo(0, 0, 0))
where()
