-- Do one Disk Station action through its /dev file, checking first that the disks it needs are in.
--
-- usage: lua disk-station-do.lua action [station]
--   action   format, wipe, clone, install or update
--   station  the station's name in /dev (default diskstation0)
--
-- Reading the station's file gives a line a slot: "target 2 hfs 40960 4194304 backups" (the slot,
-- then its disk's tier, format, bytes used, capacity and label), or "source -" for an empty slot. A
-- line written is an action, done at once; a write that fails (the action refused) fails with
-- EINVAL. (The station's methods, as ioctls, say why: see disk-station-clone.lua.)
local ACTIONS = {format = true, wipe = true, clone = true, install = true, update = true}

local action, station = ...
station = "/dev/" .. (station or "diskstation0")
if not ACTIONS[action] then
  io.stderr:write("usage: disk-station-do.lua format|wipe|clone|install|update [station]\n")
  os.exit(2)
end

local function die(...)
  io.stderr:write("disk-station-do: ", ...)
  io.stderr:write("\n")
  os.exit(1)
end

-- The slots, as the file says them: {tier, mode, used, capacity, label} for a disk, false for none.
local function slots()
  local file = assert(io.open(station))
  local found = {}
  for line in file:lines() do
    local slot, rest = line:match("^(%S+) (.*)$")
    if rest == "-" then
      found[slot] = false
    else
      local tier, mode, used, capacity, label = rest:match("^(%d+) (%S+) (%d+) (%d+) ?(.*)$")
      found[slot] = {tier = tonumber(tier), mode = mode, used = tonumber(used),
        capacity = tonumber(capacity), label = label ~= "" and label or nil}
    end
  end
  file:close()
  return found
end

local before = slots()
if not before.target then
  die("no disk in the target slot")
elseif action == "clone" then
  local source = before.source
  if not source then
    die("clone: no disk in the source slot")
  end
  local needed = source.mode == "raw" and source.capacity or source.used
  if needed > before.target.capacity then
    die("clone: the source needs ", needed, " bytes; the target holds ", before.target.capacity)
  end
elseif action == "update" and before.target.mode ~= "hfs" then
  die("update: the target is ", before.target.mode, ", not hfs: install instead")
end

local file = assert(io.open(station, "w"))
file:write(action, "\n")
local ok, why = file:close() -- (the line is written at the latest when the file closes)
if not ok then
  die(action, ": ", why)
end

local after = slots().target
print(string.format("%s: done; the target is now %s, %d of %d bytes used", action, after.mode, after.used, after.capacity))
