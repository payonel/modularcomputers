-- ticker: a service that wakes every few seconds, forever, and writes the world's time to
-- /home/ticker.log; when rc stops it (SIGTERM), it finishes the line it's on, says so, and ends.
-- Its argument is how many seconds it sleeps between lines (5 when not given).
local sys = require("sys")

local LOG = "/home/ticker.log"
local DEFAULT_SECONDS = 5 -- how long it sleeps between lines, unless its argument says

local seconds = tonumber(arg[1]) or DEFAULT_SECONDS

-- Appends a line to the log. Opened and closed each time: a file on a disk shows what's written
-- only once it's closed, so you can read the log while it runs.
local function log(text)
  local file = assert(io.open(LOG, "a"))
  file:write(os.date("%c"), " ", text, "\n")
  file:close()
end

-- rc's stop sends SIGTERM. The handler only notes it; the loop sees the note and ends. A sleep
-- the signal came in during returns early (EINTR), so it ends at once, not a sleep later.
local stopping = false
sys.signal("SIGTERM", function()
  stopping = true
end)

log("started, a line every " .. seconds .. " seconds")
local beats = 0
while not stopping do -- forever, until stopped
  beats = beats + 1
  log("tick " .. beats)
  sys.sleep(seconds) -- waits without using the CPU: the computer draws its waiting power
end
log("stopped after " .. beats .. " ticks")
