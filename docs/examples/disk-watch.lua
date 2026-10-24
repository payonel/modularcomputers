-- Watch a disk's directories for changes, whoever makes them (this computer, another one on the
-- same disk, a Disk Station), and log each one with the world's time.
--
-- usage: lua disk-watch.lua [log [directory...]] &
--   log        where the lines go (default /home/disk-watch.log)
--   directory  what to watch (default /mnt/sata/1, the disk in SATA slot 1)
--
-- A disk reports every change made on it, and mcos turns its reports into inotify notices, in the
-- paths it's mounted at: "CREATE /mnt/sata/1/photos", "MODIFY ...", "DELETE ...", a rename as
-- "MOVED_FROM" and "MOVED_TO", and "OVERFLOW" when notices were lost or the disk was formatted.
-- Watching a directory reports its entries too. The read blocks until a notice comes: no time is
-- used while nothing happens.
local sys = require("sys")

local args = {...}
local logname = args[1] or "/home/disk-watch.log"
local watched = {table.unpack(args, 2)}
if #watched == 0 then
  watched = {"/mnt/sata/1"}
end

local fd = assert(sys.inotify())
for _, path in ipairs(watched) do
  assert(sys.watch(fd, path))
end

local pending = "" -- a notice read only in part, waiting for its end
while true do
  local data = assert(sys.read(fd)) -- blocks until there are notices
  pending = pending .. data
  local lines = {}
  for notice in pending:gmatch("([^\n]*)\n") do
    lines[#lines + 1] = os.date("%T ") .. notice .. "\n"
  end
  pending = pending:match("[^\n]*$")
  if #lines > 0 then
    local log = assert(io.open(logname, "a")) -- (opened and closed each time: a disk's file shows
    log:write(table.concat(lines)) --          its writes at close)
    log:close()
  end
end
