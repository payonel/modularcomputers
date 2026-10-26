-- Layer 2, mcos's sys: when the lever on the computer's west side flips, the lamp on its east side
-- follows. In mcos a program never calls the board: the kernel owns the card, and the program
-- reaches it through its file, /dev/redstone0. sys.poll sleeps until the file has news (PRI: the
-- card's interrupt, as the kernel sees it), and the file's ioctls are the card's own methods:
-- "pull" takes a change, "set" sets its output. (The file's text is the card too: cat it for a line
-- a face, or echo "east 15" into it.)
local sys = require("sys")

local LEVER = "west"
local LAMP = "east"
local ON = 15

local fd = assert(sys.open("/dev/redstone0"))
print("watching the lever")
io.stdout:flush()
while true do
  sys.poll({[fd] = {PRI = true}}) -- sleeps until the card has news
  local change = sys.ioctl(fd, "pull") -- its oldest change, or nil
  while change do
    if change.side == LEVER then
      local on = change.level > 0
      sys.ioctl(fd, "set", {[LAMP] = {powered = on and ON or 0}})
      print("lever " .. (on and "on" or "off"))
      io.stdout:flush()
    end
    change = sys.ioctl(fd, "pull")
  end
end
