-- A log kept on a raw disk, with no filesystem: lines are added at the end, and read back in order.
--
-- usage: lua disk-raw-log.lua add [disk]     adds each line of standard input
--        lua disk-raw-log.lua show [disk]    writes every line kept, oldest first
--        lua disk-raw-log.lua clear [disk]   empties it
-- disk is the raw disk's name in /dev (default storage1).
--
-- A raw disk's /dev file is its bytes, from offset 0 to its capacity, as a Linux block device's:
-- read, written and sought anywhere. This log keeps a 16-byte header at offset 0 saying how many
-- bytes of lines follow it ("rawlog " and eight digits), and the lines right after. A disk never
-- written is all zeros, which reads as an empty log.
local sys = require("sys")

local HEADER = 16 -- bytes of the header, at offset 0
local MAGIC = "rawlog " -- how a header starts
local CHUNK = 4096 -- the most a disk's read gives at once

local command, diskname = ...
diskname = diskname or "storage1"

local function die(...)
  io.stderr:write("disk-raw-log: ", ...)
  io.stderr:write("\n")
  os.exit(1)
end

local fd, why = sys.open("/dev/" .. diskname, "r+")
if not fd then
  die(diskname, ": ", why)
end
local mode = sys.ioctl(fd, "getMode")
if mode ~= "raw" then
  die(diskname, " is ", tostring(mode), ": wipe it raw first (a Disk Station's wipe)")
end
local capacity = assert(sys.ioctl(fd, "getCapacity"))

-- Bytes from an offset, as many as asked (a read gives at most CHUNK).
local function readat(offset, n)
  assert(sys.lseek(fd, offset, "set"))
  local parts = {}
  while n > 0 do
    local data = assert(sys.read(fd, math.min(n, CHUNK)))
    if data == "" then
      break -- the disk's end
    end
    parts[#parts + 1] = data
    n = n - #data
  end
  return table.concat(parts)
end

local function writeat(offset, data)
  assert(sys.lseek(fd, offset, "set"))
  assert(sys.write(fd, data))
end

-- How many bytes of lines there are (0 for a disk that holds no log yet).
local function length()
  local header = readat(0, HEADER)
  return tonumber(header:match("^" .. MAGIC .. "(%d+)\n")) or 0
end

local function setlength(n)
  writeat(0, string.format("%s%08d\n", MAGIC, n))
end

if command == "add" then
  local at = length()
  for line in io.lines() do
    local record = line .. "\n"
    if HEADER + at + #record > capacity then
      die(diskname, " is full")
    end
    writeat(HEADER + at, record)
    at = at + #record
    setlength(at) -- (after each line: a log cut short still holds every line it says it has)
  end
elseif command == "show" then
  local left, offset = length(), HEADER
  while left > 0 do
    local data = readat(offset, math.min(left, CHUNK))
    io.write(data)
    offset, left = offset + #data, left - #data
  end
elseif command == "clear" then
  setlength(0)
else
  die("usage: disk-raw-log.lua add|show|clear [disk]")
end
sys.close(fd)
