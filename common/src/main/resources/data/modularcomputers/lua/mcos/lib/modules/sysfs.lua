-- sysfs: /sys, what each device on the bus is and can do, so a player can learn a device without
-- the manual (the user's). As Linux's /sys/class: a directory a kind of device (its type), in it a
-- directory a device, by the kernel's name for it (as /dev's), and in that its attributes, a small
-- read-only file each, and its methods (its /dev file's ioctls), a file each, saying its arguments
-- and what it does, from the device's own descriptions:
--   /sys/class/robotics/robotics0/type, id, path, label     (path: its slot, or - when it's cabled)
--   /sys/class/robotics/robotics0/methods/walk
-- Linux has a /sys/devices tree too, by where each device is; mcos's bus is flat, so there's only
-- the class tree. Programs list and read it as any files.
local k, vfs = ...

local fail = vfs.fail

local fs = {type = "sysfs"}

local CLASS = "class"
local METHODS = "methods"
local ATTRIBUTES = {"type", "id", "path", "label"} -- a device's, from the kernel's view of the bus
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

local function methods(device)
  local list = board.methods(device.id) or {}
  table.sort(list)
  return list
end

local function kind(device)
  return tostring(device.type or "device")
end

-- What a path names: {dir = entries()} or {file = text()}, or nil.
local function find(path)
  local parts = {}
  for part in path:gmatch("[^/]+") do
    parts[#parts + 1] = part
  end
  if #parts == 0 then
    return {dir = function() return {{name = CLASS, type = "directory"}} end}
  elseif parts[1] ~= CLASS then
    return nil
  elseif #parts == 1 then
    return {dir = function()
      local seen, entries = {}, {}
      for _, device in ipairs(k.devices.list) do
        if not seen[kind(device)] then
          seen[kind(device)] = true
          entries[#entries + 1] = {name = kind(device), type = "directory"}
        end
      end
      return entries
    end}
  end
  if #parts == 2 then
    local entries = {}
    for _, device in ipairs(k.devices.list) do
      if kind(device) == parts[2] then
        entries[#entries + 1] = {name = device.name, type = "directory"}
      end
    end
    return #entries > 0 and {dir = function() return entries end} or nil
  end
  local device = k.devices.byname[parts[3]]
  if not device or kind(device) ~= parts[2] then
    return nil
  elseif #parts == 3 then
    return {dir = function()
      local entries = {}
      for i, attribute in ipairs(ATTRIBUTES) do
        entries[i] = {name = attribute, type = "file"}
      end
      entries[#entries + 1] = {name = METHODS, type = "directory"}
      return entries
    end}
  elseif #parts == 4 and parts[4] == METHODS then
    return {dir = function()
      local entries = {}
      for i, name in ipairs(methods(device)) do
        entries[i] = {name = name, type = "file"}
      end
      return entries
    end}
  elseif #parts == 4 then
    for _, attribute in ipairs(ATTRIBUTES) do
      if attribute == parts[4] then
        return {file = function() return tostring(device[attribute] or "-") .. "\n" end}
      end
    end
  elseif #parts == 5 and parts[4] == METHODS then
    for _, name in ipairs(methods(device)) do
      if name == parts[5] then
        return {file = function() return about(device, name) end}
      end
    end
  end
  return nil
end

function fs:open(path, mode)
  local node = find(path)
  if not node then
    return fail("ENOENT")
  elseif node.dir then
    return fail("EISDIR")
  elseif mode:find("[wa+]") then
    return fail("EACCES") -- (read only)
  end
  local text, at = node.file(), 0
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
  local node = find(path)
  if not node then
    return fail("ENOENT")
  elseif node.dir then
    return {type = "directory", size = 0, modified = 0}
  end
  return {type = "file", size = #node.file(), modified = 0}
end

function fs:list(path)
  local node = find(path)
  if not node then
    return fail("ENOENT")
  elseif not node.dir then
    return fail("ENOTDIR")
  end
  return node.dir()
end

function fs:mkdir()
  return fail("EPERM")
end
fs.remove, fs.rename = fs.mkdir, fs.mkdir

function fs:statfs()
  return {type = "sysfs", size = 0, used = 0}
end

return fs
