-- Remember a Bluetooth link and put it back later: "save" keeps the board the card links to in a
-- file, "restore" links the card to it again (after someone relinked the card by hand, say), and
-- "clear" unlinks it.
--
--   lua bluetooth-remember.lua save | restore | clear
--
-- It works only through the card's file: a read gives "link <id>" and "board <id>" lines, and a
-- line written ("link <id>", or "link -" for none) relinks the card as soon as it is written.
local sys = require("sys")

local CARD = "/dev/bluetooth0"
local SAVED = "/home/bluetooth.link"

local function relink(id)
  local card = assert(io.open(CARD, "w"))
  card:write("link ", id, "\n")
  local ok, why = card:flush() -- (the line goes to the card here: a bad one fails)
  card:close()
  if not ok then
    io.stderr:write("bluetooth-remember: ", why, "\n")
    sys.exit(1)
  end
end

local action = ...
if action == "save" then
  local card = assert(io.open(CARD))
  local link = card:read("a"):match("link (%S+)")
  card:close()
  local out = assert(io.open(SAVED, "w"))
  out:write(link, "\n") -- ("-" when it links to none)
  out:close()
elseif action == "restore" then
  local saved = assert(io.open(SAVED))
  relink(saved:read("l"))
  saved:close()
elseif action == "clear" then
  relink("-")
else
  io.stderr:write("usage: lua bluetooth-remember.lua save|restore|clear\n")
  sys.exit(2)
end
