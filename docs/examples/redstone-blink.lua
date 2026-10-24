-- Blink whatever is on the redstone card's north face: on half a second, off half a second, forever.
--
-- There is no event loop and nothing to yield: this is a plain loop. sys.sleep waits (the
-- computer does other work meanwhile) and the loop goes on until you stop it with Ctrl+C.
local sys = require("sys")

local FACE = "north"  -- any face: north, south, east, west, up, down (or front, back, left, right)
local HALF = 0.5      -- seconds on, and seconds off

-- A line written to the card's file is "face strength" and takes effect at once.
local card = io.open("/dev/redstone0", "w")

local function set(strength)
  card:write(FACE, " ", strength, "\n")
  card:flush()
end

while true do
  set(15)
  sys.sleep(HALF)
  set(0)
  sys.sleep(HALF)
end
