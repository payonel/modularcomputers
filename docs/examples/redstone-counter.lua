-- Count the pulses that come in on the west face (a button, a hopper clock, a daylight sensor) and
-- log how many came each minute, a line a minute: "Day 3 08:12:00 4".
--
-- It uses the card's methods, through its file's ioctls: get for a face's level now, and pull for
-- each change the card kept since the last look. The card keeps its changes (32 at most) while the
-- program sleeps, so sys.sleep is the whole wait and no pulse between looks is missed. A change it
-- had to drop shows as "lost" on the next one.
local sys = require("sys")

local FACE = "west"            -- where the pulses come in
local PERIOD = 60              -- seconds between counts
local LOG = "/home/pulses.log" -- a line a count, appended

local card = assert(sys.open("/dev/redstone0"))

-- Every change the card kept, oldest first (pull answers nil when there are none left).
local function changes()
  local list = {}
  local event = sys.ioctl(card, "pull")
  while event do
    list[#list + 1] = event
    event = sys.ioctl(card, "pull")
  end
  return list
end

changes() -- (what changed before the program started is not counted)
local was = sys.ioctl(card, "get", {FACE})[FACE].level

while true do
  sys.sleep(PERIOD)
  local pulses, lost = 0, 0
  for _, event in ipairs(changes()) do
    lost = lost + (event.lost or 0)
    if event.side == FACE then
      if event.level > 0 and was == 0 then
        pulses = pulses + 1 -- off to on: a pulse
      end
      was = event.level
    end
  end
  local log = assert(io.open(LOG, "a"))
  log:write(os.date("%c"), " ", pulses, lost > 0 and " (some changes lost)" or "", "\n")
  log:close() -- (a disk's file shows what's written when it's closed)
end
