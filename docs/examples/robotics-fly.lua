-- Fly up on the thrusters, look round from up there, and land back on the legs: the golem's legs
-- are left standing where it takes off (its thrusters fire from their sockets), and land brings it
-- down onto them.
--   lua robotics-fly.lua [height]          (5 blocks when not given)
local devices = require("devices")

local HEIGHT = tonumber(arg[1] or "5")

local robot = assert(devices.open("robotics0"))

-- How its last command went, once it's done: sync waits until the device's queue is empty, and the
-- command's end is among its events (ok, or false and why).
local function ended(device, method)
  devices.sync(device)
  local ok, why = true, nil
  for event in function() return devices.event(device, 0) end do
    if event.event == method and event.ok ~= nil then
      ok, why = event.ok, event.why
    end
  end
  return ok, why
end

-- Gives the robot a command that runs over time, waits for its end, and stops with why if it failed.
local function go(method, ...)
  local ok, why = robot[method](...)
  if ok then
    ok, why = ended(robot, method)
  end
  if not ok then
    io.stderr:write("robotics-fly: ", tostring(why), "\n")
    os.exit(1)
  end
end

go("fly", 0, 0, HEIGHT)
for face, space in pairs(robot.scan()) do
  print(face, space.kind, space.name or "")
end
go("land")
print("back on its legs")
