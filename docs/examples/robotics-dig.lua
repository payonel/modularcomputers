-- Dig a tunnel: break the block in front with what the robot holds (a pickaxe), walk into the
-- space, and again, LENGTH times. Breaking takes a player's time with that tool; its event says
-- when it's done (broke), or why it couldn't (failed). It stops at what it can't break or walk
-- into, and says how far it got.
local sys = require("sys")

local LENGTH = tonumber(arg[1] or "8")

local robot = assert(sys.open("/dev/robotics0"))

-- Waits for the device's next event, and gives it.
local function event()
  while true do
    local got = sys.ioctl(robot, "pull")
    if got then
      return got
    end
    sys.poll({[robot] = {PRI = true}})
  end
end

local held = sys.ioctl(robot, "held")
print("digging with " .. (held and held.name or "a bare hand"))

for dug = 0, LENGTH - 1 do
  local ahead = assert(sys.ioctl(robot, "scan", "front"))
  if ahead.kind == "block" then
    local ok, why = sys.ioctl(robot, "swing", "front")
    if not ok then
      print("stopped after " .. dug .. ": " .. why)
      os.exit(1)
    end
    local swung = event()
    if swung.event ~= "broke" then
      print("stopped after " .. dug .. ": " .. tostring(swung.why))
      os.exit(1)
    end
  end
  assert(sys.ioctl(robot, "walk", 1))
  local walked = event()
  if walked.event ~= "arrived" then
    print("stopped after " .. dug .. ": " .. tostring(walked.why))
    os.exit(1)
  end
end
print("dug " .. LENGTH)
