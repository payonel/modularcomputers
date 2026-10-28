-- Walk the robot to a point relative to where it stands, and say how it went.
--
--   lua robotics-goto.lua forward [right [up]]
--
-- A line written to the robotics device's file is an action ("walk 3 -1"); how it went comes as an
-- event, walk, ok or not and why. sys.poll waits (it blocks, it does not yield) until the device
-- has news, and the pull ioctl takes the event. Exit status 0: it got there; 1: it didn't, and why
-- is printed ("blocked: minecraft:stone front").
local sys = require("sys")

local DEVICE = "/dev/robotics0"

local forward, right, up = tonumber(arg[1]), tonumber(arg[2] or "0"), tonumber(arg[3] or "0")
if not forward or not right or not up then
  io.stderr:write("usage: robotics-goto forward [right [up]]\n")
  os.exit(2)
end

local robot = assert(sys.open(DEVICE, "w"))
sys.write(robot, ("walk %s %s %s\n"):format(forward, right, up)) -- (a line takes effect as it's written)

while true do
  local event = sys.ioctl(robot, "pull")
  if event and event.event == "walk" then
    print(event.ok and "arrived" or tostring(event.why))
    os.exit(event.ok and 0 or 1)
  end
  sys.poll({[robot] = {PRI = true}}) -- wait for its news
end
