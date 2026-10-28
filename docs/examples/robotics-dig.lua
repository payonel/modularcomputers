-- Dig a tunnel: break the block in front with what the robot holds (a pickaxe), walk into the
-- space, and again, LENGTH times. Breaking takes a player's time with that tool: swing answers
-- "breaking" at once, and its event, swing, says when it's done, or why it couldn't. Each command
-- waits for the one before it (the device does one at a time), so the program only checks how
-- each ended. It stops at what it can't break or walk into, and says how far it got.
local sys = require("sys")

local LENGTH = tonumber(arg[1] or "8")

local robot = assert(sys.open("/dev/robotics0"))

-- Waits for the next end of one of the device's 'method' commands: ok, or false and why.
local function ended(method)
  while true do
    local got = sys.ioctl(robot, "pull")
    if got and got.event == method then
      return got.ok, got.why
    elseif not got then
      sys.poll({[robot] = {PRI = true}})
    end
  end
end

local held = sys.ioctl(robot, "held")
print("digging with " .. (held and held.name or "a bare hand"))

for dug = 0, LENGTH - 1 do
  local ahead = assert(sys.ioctl(robot, "scan", "front"))
  if ahead.kind == "block" then
    local swung, why = sys.ioctl(robot, "swing", "front")
    if swung == "breaking" then
      swung, why = ended("swing")
    end
    if not swung then
      print("stopped after " .. dug .. ": " .. tostring(why))
      os.exit(1)
    end
  end
  assert(sys.ioctl(robot, "walk", 1))
  local walked, why = ended("walk")
  if not walked then
    print("stopped after " .. dug .. ": " .. tostring(why))
    os.exit(1)
  end
end
print("dug " .. LENGTH)
