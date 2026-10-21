-- devfs: /dev, devices as files, as Linux's devtmpfs: only data. The kernel's own: console (its
-- console tty), tty (the opening process's own terminal), null and zero; and every device on the
-- bus that has data to read or write (a get or a set), by the kernel's name for it (eeprom). Reading
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

-- null and zero: writes are taken and dropped; reads give 'read's answer.
local function discard(self, data)
  return #data
end

local function sink(read)
  return function()
    return {type = "device", read = read, write = discard}
  end
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
  null = sink(function()
    return ""
  end),
  zero = sink(function(self, n)
    return ("\0"):rep(n)
  end),
}

-- What a path in /dev names: one of the kernel's devices (its maker), or a bus device with data.
local function find(path)
  local name = path:match("^/([^/]+)$") or ""
  local device = k.devices.byname[name]
  return devices[name], device and (device.get or device.set) and device
end

-- A device call's failure as an errno: a device that's gone is ENODEV (and the kernel's view of the
-- bus is read again: it was out of date); anything else, EIO.
local function failed(why)
  if (tostring(why)):find("^no such device") then
    k.refreshdevices()
    return fail("ENODEV")
  end
  return fail("EIO")
end

-- Drivers, by device type: a device whose data is a table, not text, has one on the boot disk
-- (k.driver), which makes the table text to read (get) and the text written back a table (set). It's
-- looked for the first time a file of that type is opened, and remembered (false: none), so a type
-- costs nothing until it's used, and a type without one costs one look.
local drivers = {}

-- An open device file: reads from what get gave (taken at the first read, through its driver if it
-- has one), writes kept for one set at close. (Which of them the open's mode allows is the VFS's to
-- enforce.)
local function devicefile(device, mode)
  local plus = mode:find("+", 1, true)
  local reads, writes = mode:sub(1, 1) == "r" or plus, mode:sub(1, 1) ~= "r" or plus
  if (reads and not device.get) or (writes and not device.set) then
    return fail("EACCES")
  end
  local id, kind = device.id, device.type or ""
  local driver = drivers[kind]
  if driver == nil then
    driver = k.driver(kind) or false
    drivers[kind] = driver
  end
  local data, at = nil, 0
  local written = nil
  return {
    type = "device",
    read = function(self, n)
      if data == nil then
        local ok, got = board.invoke(id, "get")
        if not ok then
          return failed(got)
        end
        data = driver and driver.get(got) or tostring(got or "")
      end
      local chunk = data:sub(at + 1, at + n)
      at = at + #chunk
      return chunk
    end,
    write = function(self, bytes)
      written = written or {}
      written[#written + 1] = bytes
      return #bytes
    end,
    close = function(self)
      if written then
        local value = table.concat(written)
        written = nil
        if driver then
          value = driver.set(value)
          if value == nil then
            return fail("EINVAL")
          end
        end
        local ok, why = board.invoke(id, "set", value)
        if not ok then
          return failed(why)
        end
      end
      return true
    end,
  }
end

function fs:open(path, mode, process)
  local make, device = find(path)
  if make then
    return make(process)
  elseif not device then
    return fail("ENOENT")
  end
  return devicefile(device, mode)
end

function fs:stat(path)
  local make, device = find(path)
  if path == "/" then
    return {type = "directory", size = 0, modified = 0}
  elseif make or device then
    return {type = "device", size = 0, modified = 0}
  end
  return fail("ENOENT")
end

-- (only / is a directory here, so it's the only one listed)
function fs:list()
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
fs.remove, fs.rename = fs.mkdir, fs.mkdir

function fs:statfs()
  return {type = "devfs", size = 0, used = 0}
end

return fs
