-- Turn logs from the chest in front of the robot into planks: take a stack of logs into the
-- crafting grid's first slot, craft once a log (four planks each, into the card's storage), and
-- put everything in its storage back into the chest. Each craft takes half a second; the card's
-- event says when they're all done (crafted), or why it stopped (failed: no room, no recipe).
local sys = require("sys")

local GRID_FIRST = 1 -- the grid's top left slot

local card = assert(sys.open("/dev/crafting0"))

-- Waits for the card's next event, and gives it.
local function event()
  while true do
    local got = sys.ioctl(card, "pull")
    if got then
      return got
    end
    sys.poll({[card] = {PRI = true}})
  end
end

local logs = assert(sys.ioctl(card, "take", "front", nil, 64, GRID_FIRST))
if logs == 0 then
  print("no logs in the chest")
  os.exit(1)
end

assert(sys.ioctl(card, "craft", logs))
local done = event()
if done.event ~= "crafted" then
  print("stopped after " .. done.count .. ": " .. done.why)
  os.exit(1)
end

local stored, left = sys.ioctl(card, "store", "front")
if stored then
  print("made " .. done.count .. " " .. done.name .. ", all in the chest")
else
  print("made " .. done.count .. " " .. done.name .. ", " .. left .. " left: the chest is full")
end
