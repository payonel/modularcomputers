-- Keep a diary of the weather: each change the weather sensor reports is appended to a log with the
-- world's time ("Day 4 11:20:00 rain"), so `cat /home/weather.log` shows a world's history.
--
-- It reads the sensor's file, a line a change: read blocks until the weather changes, which may be
-- a long while. A first line notes the weather when the program started (the sensor's getWeather,
-- through its file's ioctl), since that is not a change.
local sys = require("sys")

local LOG = "/home/weather.log"

local sensor = assert(io.open("/dev/weather0"))

local function note(text)
  local log = assert(io.open(LOG, "a"))
  log:write(os.date("%c"), " ", text, "\n")
  log:close() -- (on the disk at once)
end

note(sys.ioctl(sys.fileno(sensor), "getWeather") .. " (started)")
for line in sensor:lines() do
  note(line) -- "rain", or "lost n" when changes were dropped
end
