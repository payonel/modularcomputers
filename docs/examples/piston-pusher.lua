-- Push whatever is in front of the piston every few seconds, as a farm's harvester or a block
-- conveyor does: lua piston-pusher.lua [side] &
--
-- push is a pulse: the arm goes out and comes back by itself. When nothing will move (the line
-- of blocks is too long, or something immovable is in the way) the push fails with "cannot push":
-- the program says so once, waits longer, and tries again, so it costs little while blocked.
-- Every push costs power, moved or not.
local sys = require("sys")

local PERIOD = 2      -- seconds between pushes
local BLOCKED = 10    -- seconds to wait after a push that could not move anything
local LOG = "/home/pusher.log"

local side = arg[1] or "front"
local piston = assert(sys.open("/dev/actuator0"))

local function log(text)
  local file = assert(io.open(LOG, "a"))
  file:write(os.date("%c"), " ", text, "\n")
  file:close()
end

local blocked = false
while true do
  local done, why = sys.ioctl(piston, "push", side)
  if done then
    if blocked then
      log("moving again")
    end
    blocked = false
    sys.sleep(PERIOD)
  else
    if not blocked then
      log("blocked: " .. tostring(why))
    end
    blocked = true
    sys.sleep(BLOCKED)
  end
end
