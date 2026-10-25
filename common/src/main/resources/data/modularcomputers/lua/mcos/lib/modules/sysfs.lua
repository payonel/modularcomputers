-- sysfs: /sys, what each device on the bus can do, so a player can learn a device without the
-- manual (the user's). A directory a device, by the kernel's name for it (as /dev's: robotics0,
-- crafting0), and in it a file a method (its /dev file's ioctls), read only: its arguments and what
-- it does, from the device's own descriptions (cat /sys/robotics0/walk; ls /sys/robotics0), as
-- Linux's sysfs has a directory a device and a file an attribute. A program lists and reads them as
-- any files.
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

-- A method's text: its signature (how a doc begins: "walk(forward[, right]): ..."), then what it
-- does, wrapped and indented.
local function about(device, name)
  local doc = board.doc(device.id, name) or (name .. "()")
  local signature, says = doc:match("^([%w_]+%b())[^:]*:%s*(.*)$")
  return (signature or doc) .. "\n" .. (signature and wrap(says) .. "\n" or "")
end

-- What a path names: a device (its directory), and one of its methods (a file), or nothing.
local function find(path)
  local name, method = path:match("^/([^/]+)/?([^/]*)$")
  local device = k.devices.byname[name or ""]
  if not device or method == "" then
    return device
  end
  for _, each in ipairs(board.methods(device.id) or {}) do
    if each == method then
      return device, method
    end
  end
end

function fs:open(path, mode)
  local device, method = find(path)
  if not method then
    return fail(device and "EISDIR" or "ENOENT")
  elseif mode:find("[wa+]") then
    return fail("EACCES") -- (read only)
  end
  local text, at = about(device, method), 0
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
  end
  local device, method = find(path)
  if method then
    return {type = "file", size = #about(device, method), modified = 0}
  elseif device then
    return {type = "directory", size = 0, modified = 0}
  end
  return fail("ENOENT")
end

-- / lists the devices (directories), a device its methods (files), in name order.
function fs:list(path)
  local entries = {}
  if path == "/" or path == "" then
    for i, device in ipairs(k.devices.list) do
      entries[i] = {name = device.name, type = "directory"}
    end
    return entries
  end
  local device, method = find(path)
  if not device or method then
    return fail(method and "ENOTDIR" or "ENOENT")
  end
  local methods = board.methods(device.id) or {}
  table.sort(methods)
  for i, name in ipairs(methods) do
    entries[i] = {name = name, type = "file"}
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
