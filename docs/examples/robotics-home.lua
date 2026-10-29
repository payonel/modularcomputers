-- Walk out, say where it is, and walk home: its position (where it is from its origin, the way it faced
-- when it started) is exact, so walkTo(0, 0, 0) brings it back onto the very block it started on.
--   lua robotics-home.lua [forward [right]]     (3 and 2 when not given)
local devices = require("devices")

local FORWARD = tonumber(arg[1] or "3")
local RIGHT = tonumber(arg[2] or "2")

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

-- Gives the robot a move, waits for its end, and stops with why if it didn't get there.
local function go(method, ...)
  local ok, why = robot[method](...)
  if ok then
    ok, why = ended(robot, method)
  end
  if not ok then
    io.stderr:write("robotics-home: ", tostring(why), "\n")
    os.exit(1)
  end
end

local function where()
  local at = robot.position() -- (a command too: it waits for the move before it)
  print(string.format("at %s forward, %s right, %s up, facing %s", at.forward, at.right, at.up, at.heading))
end

go("walk", FORWARD, RIGHT)
where()
go("walkTo", 0, 0, 0)
where()
