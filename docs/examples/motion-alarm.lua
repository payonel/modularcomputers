-- Sound an alarm (a redstone signal on the north face) whenever the motion sensor sees something move.
--
-- Reading a sensor's file blocks until it has an event, so the loop below is a plain while loop:
-- the program sleeps in read() until something moves, with no event.pull and no yield.
local sys = require("sys")

local ALARM = "north"
local PULSE = 1  -- seconds the alarm stays on

local motion = io.open("/dev/motion0")
local alarm = io.open("/dev/redstone0", "w")

local function set(strength)
  alarm:write(ALARM, " ", strength, "\n")
  alarm:flush()
end

while true do
  local line = motion:read("l")  -- blocks until the sensor has something to say
  local mob, x, y, z = line:match("^(%S+) (%S+) (%S+) (%S+)$")
  if mob then  -- (a line "lost n" says n events were dropped: nothing to sound for)
    print(mob .. " at " .. x .. " " .. y .. " " .. z)
    set(15)
    sys.sleep(PULSE)
    set(0)
  end
end
