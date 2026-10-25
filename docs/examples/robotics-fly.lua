-- Fly up on the thrusters, look round from up there, and land back on the legs: the golem's legs
-- are left standing where it takes off (its thrusters fire from their sockets), and land brings it
-- down onto them.
--   lua robotics-fly.lua [height]          (5 blocks when not given)
local devices = require("devices")
local sys = require("sys")

local HEIGHT = tonumber(arg[1] or "5")

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

-- Does an action that ends in an event, and stops with why if it isn't the one hoped for.
local function go(want, ok, why)
  if not ok then
    io.stderr:write("robotics-fly: ", tostring(why), "\n")
    os.exit(1)
  end
  local got = event()
  if got.event ~= want then
    io.stderr:write("robotics-fly: ", got.event, " (", tostring(got.why), ")\n")
    os.exit(1)
  end
end

go("arrived", robot.fly(0, 0, HEIGHT))
for face, space in pairs(robot.scan()) do
  print(face, space.kind, space.name or "")
end
go("landed", robot.land())
print("back on its legs")
