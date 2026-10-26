-- Visitors at two doors, counted in one tally: a motion sensor card in an expansion hub at each of a
-- house's two doors, both hubs cabled back to this computer (motion0 and motion1 in /dev), a thread
-- watching each, the tally written to a file after each sighting. Each update is several steps: read the count, do something slow with it (here
-- a moment's sleep stands for it: asking another computer, moving a robot), write the count back. The
-- other thread goes on running meanwhile, so without the mutex it would do its own update between
-- this one's read and write: both read 4, both write 5, and a sighting is lost. With the mutex, it
-- waits at lock:run until this one's update is done.
local sys = require("sys")
local thread = require("thread")
local devices = require("devices")

local FILE = "/home/tally.txt"
local SLOW = 0.1 -- seconds the slow step takes

local tally = {} -- creature -> times seen, shared by both threads
local lock = thread.mutex()

local function record(name)
  lock:run(function() -- one thread at a time in here
    local seen = tally[name] or 0 -- read
    sys.sleep(SLOW) -- (the slow step: the other thread runs on, but waits at lock:run)
    tally[name] = seen + 1 -- write
    local file = assert(io.open(FILE, "w"))
    for creature, n in pairs(tally) do
      file:write(creature, " ", n, "\n")
    end
    file:close()
  end)
end

local function watch(sensor)
  local device = assert(devices.open(sensor))
  while true do
    record(devices.event(device).mob)
  end
end

local front = thread.create(watch, "motion0") -- (the front door's hub)
local back = thread.create(watch, "motion1") -- (the back door's)
print("tallying both doors' visitors in " .. FILE)
io.stdout:flush()
front:join()
back:join()
