-- Copy the signal coming in on the west face out of the east face, and keep doing it.
--
-- The card's file says "face level powered" for each face: the level of the signal there, and how
-- much of it is this card's own. sys.poll waits (it blocks, it does not yield) until the card
-- reports a change, so the program uses no time while nothing happens.
local sys = require("sys")

local FROM = "west"
local TO = "east"

local reader = io.open("/dev/redstone0")
local writer = io.open("/dev/redstone0", "w")
local watch = {[sys.fileno(reader)] = {PRI = true}}  -- PRI: the device's state changed

-- Read the whole file from its start, and pick out one face's level.
local function level(face)
  reader:seek("set", 0)
  return tonumber(reader:read("a"):match(face .. " (%d+)")) or 0
end

local last = -1
while true do
  local now = level(FROM)
  if now ~= last then
    writer:write(TO, " ", now, "\n")
    writer:flush()
    last = now
  end
  sys.poll(watch)  -- block until the card changes
end
