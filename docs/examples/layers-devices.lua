-- Layer 3, the devices library: when the lever on the computer's west side flips, the lamp on its
-- east side follows. The same card, through the same file, but as a table of its methods: open it
-- by its name (waiting for one to come, if none's on the bus yet), call its methods as functions,
-- and take its events with devices.event, which waits for the next one. Less to write, nothing
-- hidden: each call is the file's ioctl, each wait its poll.
local devices = require("devices")

local LEVER = "west"
local LAMP = "east"
local ON = 15

local card = assert(devices.open(devices.wait("redstone"))) -- the first redstone card, when there is one
print("watching the lever")
io.stdout:flush()
while true do
  local change = devices.event(card) -- its next change, waiting for one
  if change.side == LEVER then
    local on = change.level > 0
    card.set({[LAMP] = {powered = on and ON or 0}})
    print("lever " .. (on and "on" or "off"))
    io.stdout:flush()
  end
end
