-- Keep a log of the robots that come to a charging pad and leave it, and how full its buffer was.
--
--   lua charger-log.lua [log]   (run it with & to keep it going)
--
-- The pad's file says what it's doing ("charging <robot> 26400/26400"); its events (docked,
-- undocked) come as poll's PRI and the pull ioctl. sys.poll blocks until there's news, so the
-- program uses no time while nothing comes or goes.
local sys = require("sys")

local LOG = arg[1] or "/home/charger.log"

local pad = assert(sys.open("/dev/charger0"))

-- The pad's state now: its file read from its start.
local function state()
  sys.lseek(pad, 0, "set")
  return (sys.read(pad, 256) or ""):gsub("\n$", "")
end

while true do
  local event = sys.ioctl(pad, "pull")
  if event then
    local log = assert(io.open(LOG, "a"))
    log:write(event.event, " ", event.robot, " (", state(), ")\n")
    log:close()
  else
    sys.poll({[pad] = {PRI = true}})
  end
end
