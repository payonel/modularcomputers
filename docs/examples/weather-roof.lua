-- Close the roof while it rains: a sticky piston on the redstone card's up face pushes a roof
-- block over a crop field, or a skylight, while the weather sensor says rain, snow or thunder,
-- and pulls it back when it clears.
--
-- It asks the sensor for the weather now with its method, getWeather (an ioctl on its file), then
-- reads the file for each change: read blocks until the weather changes.
local sys = require("sys")

local ROOF = "up"
local WET = {rain = true, snow = true, thunder = true}

local sensor = assert(io.open("/dev/weather0"))
local card = assert(sys.open("/dev/redstone0"))

local function roof(weather)
  sys.ioctl(card, "set", {[ROOF] = {powered = WET[weather] and 15 or 0}})
end

roof(sys.ioctl(sys.fileno(sensor), "getWeather"))
for weather in sensor:lines() do
  if not weather:find("^lost ") then -- (a "lost n" line: the change after it says the weather)
    roof(weather)
  end
end
