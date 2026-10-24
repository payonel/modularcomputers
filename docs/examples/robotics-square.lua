-- Walk the robot round a square, SIDE blocks a side, coming back where it started, facing as it
-- did. Each move is a method of the robotics device (an ioctl on its file): walk, then turn right,
-- four times, each waited for: sys.poll blocks until the device has news, and pull takes the
-- event. If a walk is blocked, it stops there and says why (exit status 1).
local sys = require("sys")

local SIDE = tonumber(arg[1] or "4")

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

for side = 1, 4 do
  assert(sys.ioctl(robot, "walk", SIDE))
  local walked = event()
  if walked.event ~= "arrived" then
    print("side " .. side .. ": " .. walked.event .. " (" .. tostring(walked.why) .. ")")
    os.exit(1)
  end
  assert(sys.ioctl(robot, "turn", "right"))
  event() -- turned
end
print("back where it started")
