-- A robot's moves in one thread, other work in another: the walker takes the robot round a square,
-- LAPS times, each move waiting for the one before (the robot does one at a time) and for its end,
-- while the main thread goes on, saying each second how far the walk has got. Only the walker's
-- thread waits on the robot.
--   lua threads-robot.lua [laps]        (2 when not given)
local devices = require("devices")
local thread = require("thread")
local sys = require("sys")

local LAPS = tonumber(arg[1] or "2")
local SIDE = 3 -- blocks a side

local robot = assert(devices.open("robotics0"))

local walked = 0 -- sides walked so far (the walker counts, the main thread reads)

local walker = thread.create(function()
  for side = 1, LAPS * 4 do
    robot.walk(SIDE)
    local ok, why = devices.ended(robot, "walk") -- (this thread waits; the main one goes on)
    if not ok then
      return false, why
    end
    robot.turn("right")
    devices.ended(robot, "turn")
    walked = side
  end
  return true
end)

local seconds = 0
while walker:status() ~= "dead" do
  sys.sleep(1)
  seconds = seconds + 1
  print(seconds .. "s: " .. walked .. " sides walked")
end
local _, back, why = walker:join()
print(back and "back where it began" or "stopped: " .. tostring(why))
