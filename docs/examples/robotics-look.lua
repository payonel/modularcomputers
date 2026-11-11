-- Look round the robot: one scan of the six spaces round it, in its own frame, a line each: the
-- face, then every thing there, its kind and its name (a space's creatures and items, then its block,
-- air too; a container says how many slots it has).
local sys = require("sys")

local FACES = {"front", "back", "left", "right", "up", "down"}

local robot = assert(sys.open("/dev/robotics0"))
local around = assert(sys.ioctl(robot, "scan"))

for _, face in ipairs(FACES) do
  local line = face .. ":"
  for _, thing in ipairs(around[face]) do
    line = line .. " " .. thing.kind
    if thing.kind ~= "air" then
      line = line .. " " .. thing.name
    end
    if thing.kind == "block" and thing.container > 0 then -- (a block's: 0, not a container)
      line = line .. " (" .. thing.container .. " slots)"
    end
  end
  print(line)
end
