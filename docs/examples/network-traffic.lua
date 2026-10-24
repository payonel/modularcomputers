-- Keep a log of everything a network card sends and takes, each frame timed, in a file.
--
-- The card's file, /dev/network0, is its traffic: a line for each frame in or out, as
-- "in 5f0c2a9e-... 3.0m 17 taken" (the direction, the other card or "broadcast", how far, the
-- frame's bytes, and how it went). A read waits until a frame comes or goes, so the program uses
-- no time between frames. Each line is added to the log, and the log closed again, so what's
-- logged is on the disk at once (a file's writes reach the disk when it's closed).
--
-- usage: lua network-traffic.lua [log [card]]
local sys = require("sys")

local LOG = arg[1] or "/home/traffic.log"
local CARD = arg[2] or "/dev/network0"
local READ_SIZE = 512 -- the most one read takes (a line is far shorter)

local traffic = assert(sys.open(CARD))
local pending = "" -- what has been read of a line not yet ended

while true do
  local data = assert(sys.read(traffic, READ_SIZE)) -- waits for a frame
  pending = pending .. data
  for line in pending:gmatch("([^\n]*)\n") do
    local log = assert(io.open(LOG, "a"))
    log:write(string.format("%10.2f %s\n", sys.time(), line))
    log:close()
  end
  pending = pending:match("[^\n]*$")
end
