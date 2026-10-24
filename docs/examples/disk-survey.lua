-- Survey every disk on the bus: its format, how full it is, and what's at its root, asked of each
-- disk directly through ioctls on its /dev file, mounted or not.
--
-- usage: lua disk-survey.lua
--
-- A device file's ioctls are its device's methods, by name: getMode, getCapacity and getUsed work on
-- any disk; list and stat on an hfs one (a raw disk refuses them: it has no files). Paths given to
-- them are the disk's own, from its root, whatever mcos mounts it as.
local sys = require("sys")

local KIB = 1024

-- One disk's lines.
local function survey(device)
  local disk, why = io.open("/dev/" .. device.name)
  if not disk then
    return device.name .. ": " .. why .. "\n"
  end
  local fd = sys.fileno(disk)
  local mode = assert(sys.ioctl(fd, "getMode"))
  local capacity = assert(sys.ioctl(fd, "getCapacity"))
  local used = assert(sys.ioctl(fd, "getUsed"))
  local lines = {string.format("%s %s %s %d/%d KiB used%s", device.name, device.path or device.id, mode,
    used // KIB, capacity // KIB, device.label and (" \"" .. device.label .. "\"") or "")}
  if mode == "hfs" then
    for _, entry in ipairs(assert(sys.ioctl(fd, "list", "/", true))) do
      local stat = sys.ioctl(fd, "stat", "/" .. entry.name, true) -- (true: a link itself)
      if entry.type == "directory" then
        lines[#lines + 1] = "  " .. entry.name .. "/"
      elseif entry.type == "link" then
        lines[#lines + 1] = "  " .. entry.name .. " -> " .. stat.link
      else
        lines[#lines + 1] = string.format("  %s %d", entry.name, stat.size)
      end
    end
  end
  disk:close()
  return table.concat(lines, "\n") .. "\n"
end

for _, device in ipairs(sys.devices()) do
  if device.type == "storage" then
    io.write(survey(device))
  end
end
