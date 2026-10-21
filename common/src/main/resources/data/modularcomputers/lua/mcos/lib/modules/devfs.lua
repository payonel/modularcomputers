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
    ready = function(self)
      return #t.ready > 0
    end,
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

-- A bus device with data: a get, a set, or events to pull.
local function hasdata(device)
  return device.get or device.set or device.pull
end

-- What a path in /dev names: one of the kernel's devices (its maker), or a bus device with data.
local function find(path)
  local name = path:match("^/([^/]+)$") or ""
  local device = k.devices.byname[name]
  return devices[name], device and hasdata(device) and device
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

-- An event device's file (a sensor's, with events to pull and no get): read only; each event is a
-- line (through its driver), and a read with none waiting waits for one, as a keyboard's does.
local function eventfile(id, driver)
  local line, at = "", 0
  -- An event to read: true (one is in 'line'), false (none waiting: the kernel listens for one), or
  -- the device's failure.
  local function take()
    if at < #line then
      return true
    end
    local ok, event = board.invoke(id, "pull")
    if not ok then
      return failed(event)
    elseif event == nil then
      k.listen(id)
      return false
    end
    line, at = driver and driver.get(event) or tostring(event) .. "\n", 0
    return true
  end
  return {
    type = "device",
    ready = take,
    read = function(self, n)
      local got, why, errno = take()
      if got == nil then
        return nil, why, errno
      elseif not got then
        return k.BLOCK
      end
      local chunk = line:sub(at + 1, at + n)
      at = at + #chunk
      return chunk
    end,
  }
end

-- An open device file: reads from what get gave (taken at the first read, through its driver if it
-- has one), writes kept for one set at close; or an event device's. (Which of them the open's mode
-- allows is the VFS's to enforce.)
local function devicefile(device, mode)
  local plus = mode:find("+", 1, true)
  local reads, writes = mode:sub(1, 1) == "r" or plus, mode:sub(1, 1) ~= "r" or plus
  local events = device.pull and not device.get
  if (reads and not (device.get or events)) or (writes and not device.set) then
    return fail("EACCES")
  end
  local id, kind = device.id, device.type or ""
  local driver = drivers[kind]
  if driver == nil then
    driver = k.driver(kind) or false
    drivers[kind] = driver
  end
  if events then
    return eventfile(id, driver)
  end
  local data, at = nil, 0
  local written = nil
  local changed = false -- a change came in since the file was last read from its start (poll)
  -- Each event the device has waiting (a redstone card's changes coming in), taken and dropped: true
  -- if there was one. (A failure counts: the next read reports it.)
  local function drain()
    local any = false
    while true do
      local ok, event = board.invoke(id, "pull")
      if not ok or event == nil then
        return any or not ok
      end
      any = true
    end
  end
  return {
    type = "device",
    -- Ready for poll once something changed since it was read from its start (as a GPIO's value
    -- file, in sysfs); a device without events always is.
    ready = function(self)
      if not changed and device.pull then
        changed = drain()
        if not changed then
          k.listen(id)
        end
      end
      return changed or not device.pull
    end,
    -- Back to the start (the only place it seeks to): the next read takes get anew, and what's
    -- changed so far is in it.
    seek = function(self, whence, offset)
      if whence ~= "set" or offset ~= 0 then
        return fail("ESPIPE")
      end
      data, at, changed = nil, 0, false
      if device.pull then
        drain()
      end
      return 0
    end,
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
    if hasdata(device) then
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
