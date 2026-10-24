-- Keep the internet card's access log in a file: every request any program makes through it
-- (wget, curl, your own), as it ends. Run it in the background with &.
--
-- The card's file, /dev/internet0, is its access log, a line a request as a web server's:
-- "GET https://example.com/ 200 143ms 1256" (the method, the URL, the status or "-" and why it
-- failed, how long it took, the bytes it brought). The card keeps only its last 8, so a program
-- that wants them all reads as they come. sys.poll waits (IN) until a line is there, so the
-- program uses no time between requests.
--
-- usage: lua internet-log.lua [log]
local sys = require("sys")

local CARD = "/dev/internet0"
local LOG = arg[1] or "/home/internet.log"
local READ_SIZE = 4096 -- the most one read takes

local card = assert(sys.open(CARD))
local watch = {[card] = {IN = true}} -- IN: a line of the log is there to read
local pending = "" -- what has been read of a line not yet ended
while true do
  sys.poll(watch) -- blocks until a request ends
  pending = pending .. assert(sys.read(card, READ_SIZE))
  for line in pending:gmatch("([^\n]*)\n") do
    local log = assert(io.open(LOG, "a"))
    log:write(line, "\n")
    log:close() -- (on the disk at once)
  end
  pending = pending:match("[^\n]*$")
end
