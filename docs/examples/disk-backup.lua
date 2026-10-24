-- Back up a directory onto another disk, keeping the last few copies, as a nightly backup job does.
--
-- usage: lua disk-backup.lua [directory [disk]]
--   directory  what to back up (default /home)
--   disk       the backup disk's name in /dev (default storage1): an hfs disk, which mcos mounts
--              by itself when it joins the bus (a disk in SATA slot 1 at /mnt/sata/1)
--
-- Each run copies the directory to backup/1, backup/2... on the disk, and then removes the oldest
-- copies beyond KEEP. Before it copies anything, it asks the disk itself, through ioctls on its /dev
-- file (getCapacity, getUsed), whether the copy fits, so a backup that can't fit is refused whole.
local sys = require("sys")
local fs = require("fs")

local KEEP = 3 -- copies kept on the disk
local ENTRY = 512 -- what a disk counts for each file and directory, on top of its bytes
local FOLDER = "backup" -- where the copies go, on the disk

local from, diskname = ...
from = from or "/home"
diskname = diskname or "storage1"

local function die(...)
  io.stderr:write("disk-backup: ", ...)
  io.stderr:write("\n")
  os.exit(1)
end

-- The disk, as the kernel knows it, and where it's mounted.
local device
for _, d in ipairs(sys.devices()) do
  if d.name == diskname then
    device = d
  end
end
if not device or device.type ~= "storage" then
  die(diskname, ": not a disk")
end
local mountpoint
for _, mount in ipairs(sys.mounts()) do
  if mount.source == device.path or mount.source == device.id then
    mountpoint = mount.path
  end
end
if not mountpoint then
  die(diskname, ": not mounted (a raw disk has no files: format it hfs)")
end

-- What a tree takes on a disk: its files' bytes, and an entry for each file and directory.
local function size(path)
  local stat = assert(sys.stat(path))
  if stat.type ~= "directory" then
    return ENTRY + stat.size
  end
  local total = ENTRY
  for _, entry in ipairs(assert(fs.list(path))) do
    total = total + size(fs.join(path, entry.name))
  end
  return total
end

-- Ask the disk how much room it has.
local disk = assert(io.open("/dev/" .. diskname))
local fd = sys.fileno(disk)
local capacity = assert(sys.ioctl(fd, "getCapacity"))
local used = assert(sys.ioctl(fd, "getUsed"))
disk:close()

local needed = size(from)
if needed > capacity - used then
  die(from, " needs ", needed, " bytes; ", diskname, " has ", capacity - used, " free")
end

-- The copies there now, oldest first.
local folder = fs.join(mountpoint, FOLDER)
assert(fs.mkdir(folder, true))
local copies = {}
for _, entry in ipairs(assert(fs.list(folder))) do
  local n = tonumber(entry.name)
  if n and entry.type == "directory" then
    copies[#copies + 1] = n
  end
end
table.sort(copies)

local number = (copies[#copies] or 0) + 1
local to = fs.join(folder, tostring(number))
assert(fs.copy(from, to, true))
copies[#copies + 1] = number
print("backed up " .. from .. " to " .. to .. " (" .. needed .. " bytes)")

while #copies > KEEP do
  local oldest = fs.join(folder, tostring(table.remove(copies, 1)))
  assert(fs.remove(oldest, true))
  print("removed " .. oldest)
end
