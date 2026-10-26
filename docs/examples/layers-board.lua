-- /init.lua: layer 1, the board. An operating system of its own (no mcos): when the lever on the
-- computer's west side flips, the lamp on its east side follows, and the screen says so. It talks
-- to the redstone card as any OS's driver does: board.list to find it, board.waitForInterrupt to
-- sleep until it has news, and board.invoke to call its methods (pull its changes, set its output).
-- Device calls never wait: pull answers at once, nil when there's nothing, so the OS waits on the
-- interrupt first, then takes everything the card has.
local LEVER = "west" -- where the lever is (a face, as the card names them)
local LAMP = "east"  -- where the lamp is
local ON = 15        -- the lamp's signal while the lever's on

local function write(text)
  board.invoke("console", "write", (text:gsub("\n", "\r\n")))
end

-- The redstone card: the first on the bus.
local card
for _, device in ipairs(board.list()) do
  if device.type == "redstone" then
    card = device.address
    break
  end
end
if not card then
  write("no redstone card\n")
  board.halt()
end

write("watching the lever\n")
while true do
  board.waitForInterrupt(nil, card) -- sleeps until the card has news (its interrupt)
  while true do
    local ok, change = board.invoke(card, "pull") -- its oldest change: {side, level, powered}, or nil
    if not ok or not change then
      break -- (all taken: its interrupt is down, and the next wait sleeps)
    end
    if change.side == LEVER then -- (the lamp's own change comes too: it's ignored)
      local on = change.level > 0
      board.invoke(card, "set", {[LAMP] = {powered = on and ON or 0}})
      write("lever " .. (on and "on" or "off") .. "\n")
    end
  end
end
