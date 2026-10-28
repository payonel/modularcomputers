-- Turn logs from the chest in front of the robot into planks: take a stack of logs into the
-- crafting grid's first slot, craft once a log (four planks each, into the card's storage), and
-- put everything in its storage back into the chest. Each craft takes a tick, the robot busy
-- meanwhile; the card's event, craft, says when they're all done (ok, and what it made), or why it
-- stopped (no room, no recipe). The store waits for the craft (the robot does one thing at a time).
local sys = require("sys")

local GRID_FIRST = 1 -- the grid's top left slot

local card = assert(sys.open("/dev/crafting0"))

-- Waits for the card's next craft's end, and gives it.
local function crafted()
  while true do
    local got = sys.ioctl(card, "pull")
    if got and got.event == "craft" then
      return got
    elseif not got then
      sys.poll({[card] = {PRI = true}})
    end
  end
end

local logs = assert(sys.ioctl(card, "take", "front", nil, 64, GRID_FIRST))
if logs == 0 then
  print("no logs in the chest")
  os.exit(1)
end

assert(sys.ioctl(card, "craft", logs))
local done = crafted()
if not done.ok then
  print("stopped after " .. done.result.count .. ": " .. done.why)
  os.exit(1)
end

local stored, left = sys.ioctl(card, "store", "front")
if stored then
  print("made " .. done.result.count .. " " .. done.result.name .. ", all in the chest")
else
  print("made " .. done.result.count .. " " .. done.result.name .. ", " .. left .. " left: the chest is full")
end
