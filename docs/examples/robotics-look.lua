-- Look round the robot: one scan of the six spaces round it, in its own frame, a line each: the
-- face, what kind of thing is there, and its name (a container says how many slots it has).
local sys = require("sys")

local FACES = {"front", "back", "left", "right", "up", "down"}

local robot = assert(sys.open("/dev/robotics0"))
local around = assert(sys.ioctl(robot, "scan"))

for _, face in ipairs(FACES) do
  local space = around[face]
  local line = face .. ": " .. space.kind
  if space.name then
    line = line .. " " .. space.name
  end
  if space.container then
    line = line .. " (" .. space.container .. " slots)"
  end
  print(line)
end
