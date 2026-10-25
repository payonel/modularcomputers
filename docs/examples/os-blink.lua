-- blink.lua: a program for the tiny OS (run /blink.lua 3): blinks the redstone card's north
-- face on and off as many times as asked, then returns to the shell. It uses the board as the
-- OS does, and the OS's print.
local FACE = "north"
local HALF = 0.5 -- seconds on, and seconds off

local times = tonumber(arg[1]) or 1

-- The first redstone card on the bus.
local card
for _, device in ipairs(board.list()) do
  if device.type == "redstone" then
    card = device.address
    break
  end
end
if not card then
  print("no redstone card")
  return
end

for i = 1, times do
  board.invoke(card, "set", {[FACE] = {powered = 15}})
  board.waitForInterrupt(HALF, "nothing") -- a sleep: no device is called "nothing"
  board.invoke(card, "set", {[FACE] = {powered = 0}})
  board.waitForInterrupt(HALF, "nothing")
  print("blink " .. i)
end
