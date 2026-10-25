-- visitors: a service that keeps a log of every creature the motion sensor sees, with the world's
-- date and time, a line each, in /home/visitors.log. It runs in the background as a service (rc
-- visitors start), and ends cleanly when stopped (rc sends SIGTERM).
local sys = require("sys")

local LOG = "/home/visitors.log"

local sensor = assert(io.open("/dev/motion0"))

-- rc's stop: say so in the log, and end.
sys.signal("SIGTERM", function()
  local log = io.open(LOG, "a")
  log:write(os.date("%c"), " stopped\n")
  log:close()
  os.exit(0)
end)

-- Each event, a line: "minecraft:pig 1.5 0.0 -3.0" (what, and where from the sensor).
for line in sensor:lines() do
  local mob = line:match("^(%S+)")
  -- Opened and closed each time: a file on a disk shows what's written only once it's closed.
  local log = assert(io.open(LOG, "a"))
  log:write(os.date("%c"), " ", mob, "\n")
  log:close()
end
