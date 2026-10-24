-- Turn a WLAN card's signal down to the weakest that still reaches another computer, to save
-- power: a send costs more the farther the signal reaches.
--
-- It works through the card's methods, ioctls on its file: setStrength sets how far the signal
-- reaches (clamped to the card's tier, so asking for a huge one gives the tier's most), and send
-- puts a frame to one card and says whether it took it. A card that answers "busy" was reached
-- too: it just had no room. The strength is found by halving the range each try, so a tier 2
-- card's 400 blocks take nine.
--
-- usage: lua wlan-tune.lua ADDRESS
local sys = require("sys")

local CARD = "/dev/network0"
local PROBE = "tune" -- a probe frame's data: no protocol a kernel takes, so the other computer drops it
local HUGE = 1000000 -- more than any tier reaches: setStrength gives the tier's most

local address = arg[1] or error("usage: wlan-tune ADDRESS")
local card = assert(sys.open(CARD))

-- Whether a frame reaches the other card at this strength.
local function reaches(blocks)
  assert(sys.ioctl(card, "setStrength", blocks))
  local taken, why = sys.ioctl(card, "send", address, PROBE)
  if taken == nil then
    error(why) -- not a WLAN card, or it's gone
  end
  return taken or why == "busy"
end

local most = assert(sys.ioctl(card, "setStrength", HUGE))
if not reaches(most) then
  io.stderr:write("wlan-tune: ", address, " is out of reach even at ", most, " blocks\n")
  os.exit(1)
end

local low, high = 0, most -- 'high' reaches; below 'low' nothing has been tried yet
while low < high do
  local middle = (low + high) // 2
  if reaches(middle) then
    high = middle
  else
    low = middle + 1
  end
end
assert(sys.ioctl(card, "setStrength", high))
print(string.format("%s: %d blocks (of %d)", address, sys.ioctl(card, "getStrength"), most))
