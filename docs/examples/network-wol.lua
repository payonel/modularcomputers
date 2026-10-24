-- Wake-on-LAN: get this computer ready to be turned on over the network, or turn another one on.
--
--   lua network-wol.lua enable MESSAGE    from now on, a frame holding MESSAGE turns this one on
--   lua network-wol.lua disable           it doesn't any more
--   lua network-wol.lua status            the message set, if any
--   lua network-wol.lua ADDRESS MESSAGE   turn on the computer whose card is at ADDRESS
--
-- The wake message is the card's own setting, set and read with its methods setWake and getWake,
-- which are ioctls on its file. A card with one listens while its computer is off, and a frame
-- holding the message anywhere in its data presses the power button, so a UDP datagram carrying it
-- does. The sender hears whether the card took it (EHOSTUNREACH: no card there, or it isn't
-- listening).
local sys = require("sys")

local CARD = "/dev/network0"
local PORT = 9 -- discard's port, where real wake-on-LAN packets go: no program answers there

local command, message = arg[1], arg[2]

local function fail(...)
  io.stderr:write("wol: ", ...)
  io.stderr:write("\n")
  os.exit(1)
end

if command == "enable" or command == "disable" or command == "status" then
  local card = assert(sys.open(CARD))
  if command == "status" then
    print(sys.ioctl(card, "getWake") or "(none)")
  else
    if command == "enable" and not message then
      fail("enable needs a message")
    end
    local old, why = sys.ioctl(card, "setWake", command == "enable" and message or nil)
    if old == nil and why then
      fail(why) -- (a message over 256 bytes)
    end
    print("was: " .. tostring(old or "(none)"))
  end
  sys.close(card)
elseif command and message then
  local sock = assert(sys.socket())
  local sent, why = sys.sendto(sock, message, command, PORT)
  if not sent then
    fail(command, ": ", why)
  end
  print("sent to " .. command)
else
  fail("usage: wol enable MESSAGE | disable | status | ADDRESS MESSAGE")
end
