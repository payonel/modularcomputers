-- A light that grows brighter the closer something moves: four times a second, the redstone card's
-- up face is set from the nearest motion the sensor saw since the last look (15 right at the block,
-- fading to 0 at the sensor's range), and goes dark when nothing moved.
--
-- It uses the sensor's method, pull, through its file's ioctls: each event is a table, its forward,
-- right and up numbers, with no text to parse. The sensor keeps its events (32 at most) while the program
-- sleeps, so a look takes all that came since the last.
local sys = require("sys")

local LAMP = "up"
local RANGE = 8      -- the sensor's range, in blocks
local LOOK = 0.25    -- seconds between looks

local sensor = assert(sys.open("/dev/motion0"))
local card = assert(sys.open("/dev/redstone0"))

-- The distance of the nearest event waiting, or nil if there are none.
local function nearest()
  local best
  local event = sys.ioctl(sensor, "pull")
  while event do
    local d = math.sqrt(event.forward ^ 2 + event.right ^ 2 + event.up ^ 2)
    if not best or d < best then
      best = d
    end
    event = sys.ioctl(sensor, "pull")
  end
  return best
end

local lit = -1
while true do
  local d = nearest()
  local strength = d and math.max(0, math.floor(15 * (1 - d / RANGE) + 0.5)) or 0
  if strength ~= lit then
    sys.ioctl(card, "set", {[LAMP] = {powered = strength}})
    lit = strength
  end
  sys.sleep(LOOK)
end
