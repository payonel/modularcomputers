-- sysfs: /sys, what each device on the bus can do, so a player can learn a device without the
-- manual (the user's). A file a device, by the kernel's name for it (as /dev's: robotics0,
-- crafting0), read only: each of its methods (its /dev file's ioctls), its arguments and what it
-- does, from the device's own descriptions. Linux's sysfs is a tree of attributes; this is flat, a
-- file a device, its methods' text.
local k, vfs = ...

local fail = vfs.fail

local fs = {type = "sysfs"}

local WIDTH = 78 -- a description's lines, at most, indented
local INDENT = "    "

-- Text wrapped to WIDTH, each line indented.
local function wrap(text)
  local lines, line = {}, INDENT
  for word in text:gmatch("%S+") do
    if #line > #INDENT and #line + 1 + #word > WIDTH then
      lines[#lines + 1] = line
      line = INDENT .. word
    else
      line = line .. (#line > #INDENT and " " or "") .. word
    end
  end
  lines[#lines + 1] = line
  return table.concat(lines, "\n")
end

-- A device's methods as text: each its signature (how a doc begins: "walk(forward[, right]): ..."),
-- then what it does, a blank line between, in name order.
local function about(device)
  local methods = board.methods(device.id) or {}
  table.sort(methods)
  local text = {}
  for i, name in ipairs(methods) do
    local doc = board.doc(device.id, name) or (name .. "()")
    local signature, says = doc:match("^([%w_]+%b())[^:]*:%s*(.*)$")
    text[i] = (signature or doc) .. "\n" .. (signature and wrap(says) .. "\n" or "")
  end
  return device.name .. ": " .. tostring(device.type) .. "\n\n" .. table.concat(text, "\n")
end

local function find(path)
  return k.devices.byname[path:match("^/([^/]+)$") or ""]
end

function fs:open(path, mode)
  local device = find(path)
  if not device then
    return fail("ENOENT")
  elseif mode:find("[wa+]") then
    return fail("EACCES") -- (read only)
  end
  local text, at = about(device), 0
  return {
    type = "file",
    events = function(self)
      return {IN = true}
    end,
    read = function(self, n)
      local chunk = text:sub(at + 1, at + n)
      at = at + #chunk
      return chunk
    end,
    seek = function(self, whence, offset)
      if whence ~= "set" or offset ~= 0 then
        return fail("ESPIPE")
      end
      at = 0
      return 0
    end,
  }
end

function fs:stat(path)
  if path == "/" then
    return {type = "directory", size = 0, modified = 0}
  elseif find(path) then
    return {type = "file", size = 0, modified = 0}
  end
  return fail("ENOENT")
end

-- (only / is a directory here, so it's the only one listed)
function fs:list()
  local entries = {}
  for i, device in ipairs(k.devices.list) do
    entries[i] = {name = device.name, type = "file"}
  end
  return entries
end

function fs:mkdir()
  return fail("EPERM")
end
fs.remove, fs.rename = fs.mkdir, fs.mkdir

function fs:statfs()
  return {type = "sysfs", size = 0, used = 0}
end

return fs
