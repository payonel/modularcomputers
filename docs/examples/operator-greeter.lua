-- Greet whoever steps on the pressure plate at the door, and keep a visitors' log.
-- lua operator-greeter.lua &
--
-- The plate's signal comes in on a redstone card (/dev/redstone0, a line a face: "face level
-- powered"); sys.poll waits for its change (PRI). On each step the program runs a server command
-- through the operator card's set method (as a line written to its file would: a command that
-- fails fails the call, with what the server said), then its get method gives what the command
-- said (who was shown the greeting), which goes in the log with the world's date and time.
local sys = require("sys")

local PLATE = "south"                  -- the face the plate's signal comes in on
-- The command run for each visitor: a title on the screens of the players within 5 blocks of the
-- card (a command runs at its block). The server answers "Showing new title for <player>".
local GREETING = 'title @a[distance=..5] title "Welcome home!"'
local LOG = "/home/visitors.log"

local redstone = assert(io.open("/dev/redstone0"))
local card = assert(sys.open("/dev/operator0"))
local watch = {[sys.fileno(redstone)] = {PRI = true}}

-- Whether the plate is pressed now: the file read again from its start.
local function pressed()
  redstone:seek("set", 0)
  return (tonumber(redstone:read("a"):match(PLATE .. " (%d+)")) or 0) > 0
end

local was = pressed()
while true do
  sys.poll(watch)  -- block until the redstone card changes
  local now = pressed()
  if now and not was then
    local done, why = sys.ioctl(card, "set", {command = GREETING})
    local log = assert(io.open(LOG, "a"))
    if done then
      log:write(os.date("%c"), " visitor: ", (sys.ioctl(card, "get"):gsub("\n$", "")), "\n")
    else
      log:write(os.date("%c"), " greeting failed: ", tostring(why), "\n")
    end
    log:close()
  end
  was = now
end
