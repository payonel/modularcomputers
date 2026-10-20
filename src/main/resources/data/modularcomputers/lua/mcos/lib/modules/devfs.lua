-- devfs: /dev, devices as files, as Linux's devtmpfs: only data. The kernel's own: console (its
-- console tty), tty (the opening process's own terminal), null and zero; and every device on the
-- bus that has data to read or write (a get or a set), by the kernel's name for it (eeprom0). Reading
-- a device's file gives what its get returns, taken at the first read; what's written to it goes to
-- its set, all of it in one call, when the file is closed; the device decides what that means
-- (replace, append...). An open file holds the device's id: it reaches that device or fails
-- (ENODEV, and the kernel reads the bus again), whatever happens to the names meanwhile. The names
-- come from the kernel's view of the bus (k.devices), which may be a moment behind: no lookup here
-- makes a board call.
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

-- A bus device with data, by its /dev name; nil if there's none (or it has no get or set).
local function datadevice(path)
  local device = k.devices.byname[name(path) or ""]
  if device and (device.get or device.set) then
    return device
  end
end

-- A device call's failure as an errno: a device that's gone is ENODEV (and the kernel's view of the
-- bus is read again: it was out of date); anything else, EIO.
local function failed(why)
  if string.find(tostring(why), "^no such device") then
    k.refreshdevices()
    return fail("ENODEV")
  end
  return fail("EIO")
end

-- An open device file: reads from what get gave (taken at the first read), writes kept for one set
-- at close.
local function devicefile(device, mode)
  local first = string.sub(mode, 1, 1)
  local readable = first == "r" or string.find(mode, "+", 1, true) ~= nil
  local writable = first ~= "r" or string.find(mode, "+", 1, true) ~= nil
  if (readable and not device.get) or (writable and not device.set) then
    return fail("EACCES")
  end
  local id = device.id
  local data, at = nil, 0
  local written = nil
  return {
    type = "device",
    read = function(self, n)
      if not readable then
        return fail("EBADF")
      end
      if data == nil then
        local ok, got = board.invoke(id, "get")
        if not ok then
          return failed(got)
        end
        data = tostring(got or "")
      end
      local chunk = string.sub(data, at + 1, at + n)
      at = at + #chunk
      return chunk
    end,
    write = function(self, bytes)
      if not writable then
        return fail("EBADF")
      end
      written = written or {}
      written[#written + 1] = bytes
      return #bytes
    end,
    close = function(self)
      if written then
        local ok, why = board.invoke(id, "set", table.concat(written))
        written = nil
        if not ok then
          return failed(why)
        end
      end
      return true
    end,
  }
end

function fs:open(path, mode, process)
  local make = devices[name(path) or ""]
  if make then
    return make(process)
  end
  local device = datadevice(path)
  if not device then
    return fail(path == "/" and "EISDIR" or "ENOENT")
  end
  return devicefile(device, mode)
end

function fs:stat(path)
  if path == "/" then
    return {type = "directory", size = 0, modified = 0}
  end
  if devices[name(path) or ""] or datadevice(path) then
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
  for _, device in ipairs(k.devices.list) do
    if device.get or device.set then
      entries[#entries + 1] = {name = device.name, type = "device"}
    end
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
