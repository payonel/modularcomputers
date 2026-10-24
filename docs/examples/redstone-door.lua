-- A door that opens when a button on the west face is pressed, and closes by itself once nothing
-- has changed for a few seconds (a pressure plate or a button held keeps it open).
--
-- The door (an iron door, a piston) is on the north face. It uses the card's methods, through its
-- file's ioctls: get to read the button, set to power the door. sys.poll waits for the card to
-- change (PRI), and with a time limit for the door to stay open. A redstone file's PRI stays on
-- until the file is sought back to its start, so the program seeks after each wake.
local sys = require("sys")

local BUTTON = "west"
local DOOR = "north"
local HOLD = 3 -- seconds the door stays open after the last change

local card = assert(sys.open("/dev/redstone0"))
local watch = {[card] = {PRI = true}}

-- Waits until the card changes, or 'seconds' pass: whether it changed.
local function changed(seconds)
  local ready = sys.poll(watch, seconds)
  sys.lseek(card, 0, "set") -- (PRI again only for a newer change)
  return ready[card] ~= nil
end

local function pressed()
  return sys.ioctl(card, "get", {BUTTON})[BUTTON].level > 0
end

local function door(strength)
  sys.ioctl(card, "set", {[DOOR] = {powered = strength}})
end

door(0)
while true do
  if pressed() then
    door(15)
    while changed(HOLD) do -- (each change, the button let go too, starts the wait again)
    end
    door(0)
  end
  changed() -- wait for the next press
end
