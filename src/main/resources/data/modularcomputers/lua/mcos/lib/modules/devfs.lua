-- devfs: /dev, the kernel's devices as files, as Linux's devtmpfs: console (the kernel's console
-- tty), tty (the opening process's own terminal), null and zero; and every device on the bus, as
-- the board lists it, named by its type and its place among those of its type in bus order
-- (terminal0, storage1: as Linux's sda, ttyS0). Reading one gives what the board says of it: both
-- addresses board.invoke takes (its path, the slot it's in, if it has one: sata:0; and its id,
-- id:<uuid>, however it's connected), its type and label, and what its kind adds (a tier, a size).
local k, vfs = ...

local fail = vfs.fail

local fs = {type = "devfs"}

-- A tty as a file: reads block until a line (or what's typed, in raw mode) is ready.
function k.ttyfile(t)
  return {
    type = "tty",
    tty = t,
    read = function(self, n)
      local data = t:read(n)
      if data == nil then
        return k.BLOCK
      end
      return data
    end,
    write = function(self, data)
      t:write(data)
      return #data
    end,
  }
end

local devices = {
  console = function()
    return k.ttyfile(k.console)
  end,
  tty = function(process)
    if not (process and process.tty) then
      return fail("ENODEV")
    end
    return k.ttyfile(process.tty)
  end,
  null = function()
    return {
      type = "device",
      read = function()
        return ""
      end,
      write = function(self, data)
        return #data
      end,
    }
  end,
  zero = function()
    return {
      type = "device",
      read = function(self, n)
        return string.rep("\0", n)
      end,
      write = function(self, data)
        return #data
      end,
    }
  end,
}

local function name(path)
  return string.match(path, "^/([^/]+)$")
end

-- The bus's devices as /dev names them: {name = {address, id, type}}, and the names in bus order.
local function busdevices()
  local byname, names, counts = {}, {}, {}
  for _, device in ipairs(board.list()) do
    local kind = string.gsub(device.type or "device", "[^%w]", "")
    local n = counts[kind] or 0
    counts[kind] = n + 1
    local entry = kind .. n
    byname[entry] = device
    names[#names + 1] = entry
  end
  return byname, names
end

-- What a bus device's file says: "key: value" lines, its addresses first.
local INFO_FIRST = {"address", "id", "type", "label"}

local function describe(device)
  local info = board.info(device.address) or {}
  local lines = {}
  local path = device.address ~= device.id and device.address or nil
  lines[#lines + 1] = "path: " .. (path or "(none: not in a slot)")
  lines[#lines + 1] = "id: " .. tostring(device.id or "(none)")
  lines[#lines + 1] = "type: " .. tostring(device.type)
  if info.label then
    lines[#lines + 1] = "label: " .. tostring(info.label)
  end
  local keys = {}
  for key in pairs(info) do
    local first = false
    for _, known in ipairs(INFO_FIRST) do
      if key == known then
        first = true
      end
    end
    if not first then
      keys[#keys + 1] = key
    end
  end
  table.sort(keys)
  for _, key in ipairs(keys) do
    lines[#lines + 1] = key .. ": " .. tostring(info[key])
  end
  return table.concat(lines, "\n") .. "\n"
end

-- A file whose contents are fixed when it's opened.
local function textfile(text)
  local at = 0
  return {
    type = "device",
    read = function(self, n)
      local data = string.sub(text, at + 1, at + n)
      at = at + #data
      return data
    end,
  }
end

function fs:open(path, mode, process)
  local make = devices[name(path) or ""]
  if make then
    return make(process)
  end
  local device = busdevices()[name(path) or ""]
  if not device then
    return fail(path == "/" and "EISDIR" or "ENOENT")
  elseif mode ~= "r" then
    return fail("EACCES")
  end
  return textfile(describe(device))
end

function fs:stat(path)
  if path == "/" then
    return {type = "directory", size = 0, modified = 0}
  end
  if devices[name(path) or ""] or busdevices()[name(path) or ""] then
    return {type = "device", size = 0, modified = 0}
  end
  return fail("ENOENT")
end

function fs:list(path)
  if path ~= "/" then
    return fail("ENOTDIR")
  end
  local entries = {}
  for device in pairs(devices) do
    entries[#entries + 1] = {name = device, type = "device"}
  end
  local _, names = busdevices()
  for _, device in ipairs(names) do
    entries[#entries + 1] = {name = device, type = "device"}
  end
  return entries
end

function fs:mkdir()
  return fail("EPERM")
end

function fs:remove()
  return fail("EPERM")
end

function fs:rename()
  return fail("EPERM")
end

function fs:statfs()
  return {type = "devfs", size = 0, used = 0}
end

return fs
