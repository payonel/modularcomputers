-- A beacon: say this computer's name over the air every few seconds, to every computer near
-- enough, as a Wi-Fi access point does. Run it as a service, or in the background with &.
--
-- The card's signal is set first (its setStrength method, an ioctl on its file), so the beacon is
-- heard only as far as it should be, and costs no more than that. Each beacon is a UDP datagram
-- broadcast to PORT, its data the computer's name. sys.sleep blocks between beacons.
--
-- usage: lua wlan-beacon.lua [blocks]
local sys = require("sys")

local CARD = "/dev/network0"
local PORT = 4001 -- where listeners take beacons
local INTERVAL = 5 -- seconds between beacons
local STRENGTH = tonumber(arg[1]) or 16 -- how far it's heard, in blocks

local card = assert(sys.open(CARD))
local strength = assert(sys.ioctl(card, "setStrength", STRENGTH))
sys.close(card)
io.stderr:write("beacon: heard up to ", strength, " blocks away\n")

local sock = assert(sys.socket())
while true do
  sys.sendto(sock, sys.gethostname() .. "\n", "broadcast", PORT) -- (best effort: nobody near is fine)
  sys.sleep(INTERVAL)
end
