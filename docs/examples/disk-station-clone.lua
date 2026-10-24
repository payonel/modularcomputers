-- Clone the source disk onto the target, through the station's methods (its file's ioctls), and
-- report what was copied.
--
-- usage: lua disk-station-clone.lua [station]     (default diskstation0)
--
-- "get" gives the slots as a table ({target = disk, source = disk}, each {tier, mode, used,
-- capacity, label}, an empty slot left out); "clone" makes the target a copy of the source, its
-- format and its files (their times kept), or refuses with nothing changed. The source is only read:
-- the station is its write blocker.
local sys = require("sys")

local station = "/dev/" .. (... or "diskstation0")
local file = assert(io.open(station))
local fd = sys.fileno(file)

local function describe(disk)
  return string.format("tier %d %s, %d of %d bytes used%s", disk.tier, disk.mode, disk.used, disk.capacity,
    disk.label and (", labeled " .. disk.label) or "")
end

local slots = assert(sys.ioctl(fd, "get"))
if not (slots.source and slots.target) then
  io.stderr:write("disk-station-clone: put a disk in both slots (", slots.source and "target" or "source", " is empty)\n")
  os.exit(1)
end
print("source: " .. describe(slots.source))
print("target: " .. describe(slots.target) .. " (everything on it will be gone)")

local ok, why = sys.ioctl(fd, "clone")
if not ok then
  io.stderr:write("disk-station-clone: ", why, "\n") -- (doesn't fit, no power...)
  os.exit(1)
end
slots = assert(sys.ioctl(fd, "get"))
print("cloned: the target is " .. describe(slots.target))
file:close()
