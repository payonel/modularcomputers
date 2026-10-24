-- A disk duplicator: each disk put in the station's target slot is made ready on its own, a copy of
-- the source disk when there is one, else a fresh mcos install, and the work is logged.
--
-- usage: lua disk-station-duplicator.lua [station [log]] &
--   station  the station's name in /dev (default diskstation0)
--   log      where to log each disk done (default /home/duplicator.log)
--
-- The station raises no interrupt when a disk goes in, so the program looks at its slots every POLL
-- seconds ("get", its method), sleeping in between (sys.sleep blocks: it uses no time). A disk is
-- done once, when it appears in the target slot; take it out and put in the next.
local sys = require("sys")

local POLL = 1 -- seconds between looks at the slots

local station, logname = ...
station = "/dev/" .. (station or "diskstation0")
logname = logname or "/home/duplicator.log"

local file = assert(io.open(station))
local fd = sys.fileno(file)

local function log(line)
  local out = assert(io.open(logname, "a"))
  out:write(os.date("%T "), line, "\n")
  out:close()
end

local waiting = true -- the target slot was empty at the last look (a disk in at the start is done)
while true do
  local slots = sys.ioctl(fd, "get")
  if type(slots) == "table" and slots.target and waiting then
    local action = slots.source and "clone" or "install"
    local ok, why = sys.ioctl(fd, action)
    if ok then
      local disk = assert(sys.ioctl(fd, "get")).target
      log(string.format("%s: done, tier %d %s, %d bytes used", action, disk.tier, disk.mode, disk.used))
    else
      log(action .. ": failed: " .. tostring(why))
    end
  end
  waiting = not (type(slots) == "table" and slots.target)
  sys.sleep(POLL)
end
