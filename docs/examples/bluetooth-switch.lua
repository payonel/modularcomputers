-- Switch a computer's Bluetooth link between boards in turn: run it with the boards' ids, and each
-- run links the card to the one after the board it links to now (the first, if it links to none
-- of them). A desk with one keyboard and screen reaching one machine room after another.
--
--   lua bluetooth-switch.lua 5f0c...-... 9a21...-...
--
-- It uses the card's methods, getLink and setLink, through its file's ioctls; the boards need not be
-- there when it runs. It exits 1 if an id is not a board's.
local sys = require("sys")

local boards = {...}
if #boards == 0 then
  io.stderr:write("usage: lua bluetooth-switch.lua board-id...\n")
  sys.exit(2)
end

local card = assert(sys.open("/dev/bluetooth0"))
local now = sys.ioctl(card, "getLink") -- nil: no link

local target = boards[1]
for i, id in ipairs(boards) do
  if id == now then
    target = boards[i % #boards + 1]
  end
end

local ok, why = sys.ioctl(card, "setLink", target)
if not ok then
  io.stderr:write("bluetooth-switch: ", why, "\n")
  sys.exit(1)
end
print("linked to " .. target)
