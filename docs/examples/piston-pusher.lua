-- Push whatever is in front of the piston every few seconds, as a farm's harvester or a block
-- conveyor does: lua piston-pusher.lua [side] &
--
-- push is a pulse: the arm goes out and comes back by itself. When nothing will move (the line
-- of blocks is too long, or something immovable is in the way) its end says so (blocked, the block
-- that won't move, or - when no one block is to blame, and the face: "blocked - east"):
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

-- The end of the piston's last 'method' command (its event): ok, or not, why, and for blocked the
-- block that won't move (nil: no one block) and the face.
local function ended(fd, method)
  while true do
    local got = sys.ioctl(fd, "pull")
    if got and got.event == method then
      return got
    elseif not got then
      sys.poll({[fd] = {PRI = true}})
    end
  end
end

-- What a failed end says: its why, and a blocked one's block (- for none) and face.
local function said(got)
  if got.why == "blocked" then
    return "blocked " .. (got.block or "-") .. " " .. got.face
  end
  return tostring(got.why)
end

local blocked = false
while true do
  local done, why = sys.ioctl(piston, "push", side)
  local got = done and ended(piston, "push")
  if done and got.ok then
    if blocked then
      log("moving again")
    end
    blocked = false
    sys.sleep(PERIOD)
  else
    if not blocked then
      log(got and said(got) or tostring(why))
    end
    blocked = true
    sys.sleep(BLOCKED)
  end
end
