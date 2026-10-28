-- Walk the robot round a square, SIDE blocks a side, coming back where it started, facing as it
-- did. Each move is a method of the robotics device (an ioctl on its file), and the device does one
-- at a time: a move given while one runs waits for it (this thread only), so the program gives them
-- in order. A walk's end is an event, walk, saying whether it got there (ok) or why not: sys.poll
-- waits until the device has news, and pull takes it. If a walk is blocked, it stops there and says
-- why (exit status 1).
local sys = require("sys")

local SIDE = tonumber(arg[1] or "4")

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

for side = 1, 4 do
  assert(sys.ioctl(robot, "walk", SIDE))
  local ok, why = ended("walk")
  if not ok then
    print("side " .. side .. ": " .. tostring(why))
    os.exit(1)
  end
  assert(sys.ioctl(robot, "turn", "right"))
end
ended("turn") -- (the last turn's end: facing as it did)
print("back where it started")
