-- devfs: /dev, devices as files, as Linux's devtmpfs. The kernel's own: console (its console tty),
-- tty (the opening process's own terminal), null and zero; and every device on the bus, by the
-- kernel's name for it (eeprom, storage0, internet0). What a device's file reads and writes is its
-- data: a disk's bytes (as a block device's: read, written and sought by byte offset; a disk with a
-- filesystem, hfs, refuses them: EMEDIUMTYPE), a sound card's samples (OSS's dsp, its ioctls the
-- format), a sensor's events (a line each), or what its get returns (taken at the first read) and
-- what's written going to its set (a line at a time through its driver, else all at close); a
-- device with none of those reads as what it is. Every device file's ioctls are its device's
-- methods, by name (its own API, as a Linux device's ioctls are), and it's ready for poll (PRI)
-- while its device has news. An open file holds the device's id: it reaches that device or fails
-- (ENODEV, and the kernel reads the bus again), whatever happens to the names meanwhile. The names
-- come from the kernel's view of the bus (k.devices), which may be a moment behind: no lookup here
-- makes a board call.
local k, vfs = ...

local fail = vfs.fail

local fs = {type = "devfs"}

local TENTH = 2 -- a tenth of a second, in ticks (VTIME's unit; 20 ticks a second)

-- A raw read's wait, POSIX's MIN and TIME: VMIN 0 and VTIME 0, what's there now; VMIN 0, the first
-- byte, or VTIME tenths of a second pass ("": none); VTIME 0, VMIN bytes (or all of n); else the
-- first byte, then VMIN bytes, or VTIME after the last came. Its answer, or k.BLOCK ('request'
-- keeps its timer; the process wakes at its deadline).
local function rawread(t, n, p, request)
  local min, time = t.flags.vmin, t.flags.vtime
  local have = t:pending()
  local want = math.min(min, n)
  if have > 0 and have >= want or min == 0 and time == 0 then
    return t:read(n) or ""
  elseif time == 0 or have == 0 and min > 0 then
    return k.BLOCK
  end
  local now = k.now()
  if min > 0 and have ~= request.seen then
    request.seen, request.deadline = have, now + time * TENTH -- (the timer starts again at each byte)
  end
  request.deadline = request.deadline or now + time * TENTH
  if now >= request.deadline then
    return t:read(n) or ""
  end
  p.wakeat = request.deadline
  return k.BLOCK
end

-- A tty as a file: reads block until a line (or what's typed, in raw mode, as VMIN and VTIME say)
-- is ready. (A hung-up tty, a pty whose master closed, reads as its end and fails writes, as Linux's.)
function k.ttyfile(t)
  if t.opened then
    t:opened() -- (a pty counts its terminal end's open files)
  end
  return {
    type = "tty",
    tty = t,
    events = function(self)
      return {IN = #t.ready > 0 or t.hungup, OUT = not t.hungup, HUP = t.hungup}
    end,
    read = function(self, n, p, request)
      if t.hungup then
        return ""
      elseif not t.flags.icanon and p then
        return rawread(t, n, p, request)
      end
      local data = t:read(n)
      if data == nil then
        return k.BLOCK
      end
      return data
    end,
    write = function(self, data)
      if t.hungup then
        return fail("EIO")
      elseif t:write(data) == k.BLOCK then
        return k.BLOCK -- (a pty's master is full)
      end
      return #data
    end,
    close = t.closed and function()
      t:closed()
      return true
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

local DISK = "storage" -- a disk's device type: its file is a block device's
local SOUND = "sound" -- a sound card's: its file is OSS's /dev/dsp

-- What a path in /dev names: one of the kernel's devices (its maker), or a bus device (every one
-- has a file).
local function find(path)
  local name = path:match("^/([^/]+)$") or ""
  return devices[name], k.devices.byname[name]
end

-- A device call's failure as an errno: a device that's gone is ENODEV (and the kernel's view of the
-- bus is read again: it was out of date); anything else, 'errno' (EIO unless said).
local function failed(why, errno)
  if (tostring(why)):find("^no such device") then
    k.refreshdevices()
    return fail("ENODEV")
  end
  return fail(errno or "EIO")
end

-- Drivers, by device type: a device whose data is a table, not text, has one on the boot disk
-- (k.driver), which makes it text, as Linux's interfaces are (lines, words), never Lua's syntax:
-- get(value) is what a read gives (an event's line, or the state); line(text), if it has one, is
-- what to set for a line written (nil: not a line it takes), each taking effect as it's written, as
-- a sysfs attribute's write does. It's looked for the first time a file of that type is opened, and
-- remembered (false: none), so a type costs nothing until it's used, and a type without one costs
-- one look.
local drivers = {}

-- A device's driver (false: none), looked for once a type.
local function driverof(device)
  local kind = device.type or ""
  local driver = drivers[kind]
  if driver == nil then
    driver = k.driver(kind) or false
    drivers[kind] = driver
  end
  return driver
end

-- A device's events, for every file open on it (the user's: each reader sees them all, a script and
-- a cat alike): whoever asks takes what the device has (k.take, which listens when it has nothing),
-- and every file open on it gets its own copy, in order. A file's own events are bounded: past
-- FILE_EVENTS the oldest go (a file nobody reads can't grow without end), the next one counting them
-- (lost), as a device's own queue does.
local FILE_EVENTS = 32
local readers = {} -- by device id: {[a file's key] = {its events, oldest first}}

local function deepcopy(value)
  if type(value) ~= "table" then
    return value
  end
  local c = {}
  for key, v in pairs(value) do
    c[key] = deepcopy(v)
  end
  return c
end

-- Takes what device 'id' has waiting into every file open on it: the device's last answer (true,
-- nil once it has nothing; or false and why, its failure).
local function deliver(id)
  while true do
    local ok, event = k.take(id, "pull")
    if not ok or event == nil then
      return ok, event
    end
    for _, queue in pairs(readers[id] or {}) do
      if #queue >= FILE_EVENTS then
        table.remove(queue, 1)
        queue.lost = (queue.lost or 0) + 1
      end
      local own = deepcopy(event)
      if queue.lost and type(own) == "table" then
        own.lost, queue.lost = (own.lost or 0) + queue.lost, nil
      end
      queue[#queue + 1] = own
    end
  end
end

-- A file ('key', its own) takes device 'id''s events from now on, or no more.
local function enlist(id, key)
  readers[id] = readers[id] or {}
  readers[id][key] = readers[id][key] or {}
end

local function delist(id, key)
  if readers[id] then
    readers[id][key] = nil
    if next(readers[id]) == nil then
      readers[id] = nil
    end
  end
end

-- A file's next event of device 'id': true and it (nil: none now, and the kernel listens for one), or
-- false and why (the device's failure, once what it took is given).
local function nextevent(id, key)
  enlist(id, key)
  local ok, why = deliver(id)
  local queue = readers[id][key]
  if queue[1] ~= nil then
    return true, table.remove(queue, 1)
  elseif not ok then
    return false, why
  end
  return true, nil
end

-- A buffered device's command (one at a time) from a process: while the device is busy (or a
-- robot held), the calling thread blocks, only it, and is asked again (its events taken into its
-- files first, so its interrupt is down and the next is the running command's end). A stop of the
-- device meanwhile ends the wait: what the threads were feeding it was stopped (ECANCELED).
-- Without a syscall to block (a file closing as its process ends), busy is EBUSY.
local stopped = {} -- by device id: how many stops it's had

local function command(id, method, ...)
  local request = k.request
  if request and request.stops ~= nil and request.stops ~= (stopped[id] or 0) then
    return nil, "stopped", "ECANCELED"
  end
  local answer = table.pack(board.invoke(id, method, ...))
  if not answer[1] and (answer[2] == "busy" or answer[2] == "held") then
    if not request then
      return nil, answer[2], "EBUSY"
    end
    request.stops = request.stops or (stopped[id] or 0)
    deliver(id)
    k.listen(id)
    return k.BLOCK
  end
  if answer[1] and method == "stop" then
    stopped[id] = (stopped[id] or 0) + 1
  end
  return table.unpack(answer, 1, answer.n)
end

-- An event device's file (a sensor's, with events to pull and no get): read only; each event is a
-- line (its driver's), and a read with none waiting waits for one, as a keyboard's does. Ready to
-- poll (IN) while one waits. An event a poll took and nothing read yet is the file's to give a
-- pull ioctl first (held), so polling never loses one.
local function eventfile(id, driver)
  local line, at = "", 0
  local event -- (the one in 'line', while none of it is read)
  local file = {type = "device"}
  file.eventkey = file -- (its own copy of the device's events: the pull ioctl takes from it too)
  -- An event to read: true (one is in 'line'), false (none waiting: the kernel listens for one), or
  -- the device's failure.
  local function take()
    if at < #line then
      return true
    end
    local ok, got = nextevent(id, file)
    if not ok then
      return failed(got)
    elseif got == nil then
      return false -- (k.take listens for the next)
    end
    line, at, event = driver and driver.get(got) or tostring(got) .. "\n", 0, got
    return true
  end
  function file.events(self)
    local got = take() ~= false
    return {IN = got, PRI = got or nil} -- (an event in its own copy is news to it, whether or not the kernel saw the line rise)
  end
  function file.held(self)
    local got = event
    if got ~= nil then
      line, at, event = "", 0, nil
    end
    return got
  end
  function file.read(self, n)
    local got, why, errno = take()
    if got == nil then
      return nil, why, errno
    elseif not got then
      return k.BLOCK
    end
    local chunk = line:sub(at + 1, at + n)
    at, event = at + #chunk, nil
    return chunk
  end
  function file.close(self)
    delist(id, file)
    return true
  end
  return file
end

-- A disk's file: its bytes from offset 0 to its capacity, as a block device's, read and written
-- where the file is (the disk's raw read and write, so an hfs disk refuses: EMEDIUMTYPE; a read-only
-- one refuses writes: EROFS). Past its end, a read gives nothing and a write ENOSPC.
local function diskfile(device)
  local id, at = device.id, 0
  local function refused(why)
    why = tostring(why)
    if why:find("^hfs disk") then
      return fail("EMEDIUMTYPE")
    elseif why:find("^read%-only") then
      return fail("EROFS")
    end
    return failed(why)
  end
  return {
    type = "device",
    events = function(self)
      return {IN = true, OUT = true}
    end,
    read = function(self, n)
      local ok, data = board.invoke(id, "read", at, n)
      if not ok then
        return refused(data)
      end
      data = data or ""
      at = at + #data
      return data
    end,
    write = function(self, data)
      local ok, count = board.invoke(id, "write", at, data)
      if not ok then
        return refused(count)
      elseif count == 0 and #data > 0 then
        return fail("ENOSPC")
      end
      at = at + count
      return count
    end,
    seek = function(self, whence, offset)
      local base = at
      if whence == "set" then
        base = 0
      elseif whence == "end" then
        local ok, capacity = board.invoke(id, "getCapacity")
        if not ok then
          return failed(capacity)
        end
        base = capacity
      end
      if base + offset < 0 then
        return fail("EINVAL")
      end
      at = base + offset
      return at
    end,
  }
end

-- OSS's sample formats, by their names, and the card's for each.
local AFMT = {AFMT_U8 = "u8", AFMT_S16_LE = "s16le", AFMT_IMA_ADPCM = "ima_adpcm"}
local AFMT_NAME = {}
for name, encoding in pairs(AFMT) do
  AFMT_NAME[encoding] = name
end

-- A sound card's file, OSS's /dev/dsp: what's written plays (a write waits while the card's
-- buffer is full, as a pipe's writer does); an open starts at OSS's defaults (8000 a second,
-- unsigned 8-bit, mono), so a raw file plays as written, and what another left queued in the same
-- format plays on first. Its ioctls, by OSS's names: SNDCTL_DSP_SPEED (a rate), SNDCTL_DSP_SETFMT
-- (AFMT_U8, AFMT_S16_LE, AFMT_IMA_ADPCM), SNDCTL_DSP_CHANNELS (1 or 2), each answering what it's
-- set to; SNDCTL_DSP_GETODELAY (bytes queued), SNDCTL_DSP_RESET (they're dropped), SNDCTL_DSP_SYNC
-- (waits until they've played). The card's own settings beyond OSS's (an ADPCM block's bytes) are its
-- methods, as any device file's ioctls (setFormat).
local function dspfile(device)
  local id = device.id
  local ok, why = board.invoke(id, "setFormat", {rate = 8000, encoding = "u8", channels = 1})
  if not ok then
    return failed(why)
  end
  local function set(field, value, answer)
    local done, err = board.invoke(id, "setFormat", {[field] = value})
    if not done then
      return failed(err, "EINVAL")
    end
    local got, format = board.invoke(id, "getFormat")
    if not got then
      return failed(format)
    end
    return answer(format)
  end
  local ops = {
    SNDCTL_DSP_SPEED = function(rate)
      return set("rate", rate, function(format) return format.rate end)
    end,
    SNDCTL_DSP_SETFMT = function(name)
      if not AFMT[name] then
        return fail("EINVAL")
      end
      return set("encoding", AFMT[name], function(format) return AFMT_NAME[format.encoding] end)
    end,
    SNDCTL_DSP_CHANNELS = function(channels)
      return set("channels", channels, function(format) return format.channels end)
    end,
    SNDCTL_DSP_GETODELAY = function()
      local got, delay = board.invoke(id, "getDelay")
      if not got then
        return failed(delay)
      end
      return delay
    end,
    SNDCTL_DSP_RESET = function()
      local done, err = board.invoke(id, "reset")
      if not done then
        return failed(err)
      end
      return true
    end,
    SNDCTL_DSP_SYNC = function()
      local done, drained = board.invoke(id, "sync")
      if not done then
        return failed(drained)
      elseif not drained then
        k.listen(id) -- (its interrupt says when it's played out)
        return k.BLOCK
      end
      return true
    end,
  }
  return {
    type = "device",
    events = function(self)
      return {OUT = true}
    end,
    write = function(self, data)
      local done, count = board.invoke(id, "write", data)
      if not done then
        return failed(count)
      elseif count == 0 and #data > 0 then
        k.listen(id) -- full: its interrupt says when there's room
        return k.BLOCK
      end
      return count
    end,
    ioctl = function(self, op, arg)
      local f = ops[op]
      if not f then
        return fail("ENOTTY") -- (not OSS's: the card's own methods, by the device file)
      end
      return f(arg)
    end,
  }
end

-- An open device file: reads from what get gave (taken at the first read, through its driver if it
-- has one); writes, a line at a time as they come with a line driver, otherwise kept for one set at
-- close (the EEPROM's image: all of it or none, as a firmware load's commit); or an event device's.
-- (Which of them the open's mode allows is the VFS's to enforce.)
-- A device with nothing to read or write (no get, set or events): its file says what it is, its
-- type, id, slot, label and methods, a line each (its methods are its ioctls).
local function aboutfile(device)
  local methods = board.methods(device.id) or {}
  table.sort(methods)
  local text = table.concat({"type " .. tostring(device.type), "id " .. tostring(device.id), "path " .. (device.path or "-"),
    "label " .. (device.label or "-"), "methods " .. table.concat(methods, " ")}, "\n") .. "\n"
  local at = 0
  return {
    type = "device",
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

local function devicefile(device, mode)
  local plus = mode:find("+", 1, true)
  local reads, writes = mode:sub(1, 1) == "r" or plus, mode:sub(1, 1) ~= "r" or plus
  local events = device.pull and not device.get
  if not (device.get or device.set or events) then
    if writes then
      return fail("EACCES")
    end
    return aboutfile(device)
  end
  if (reads and not (device.get or events)) or (writes and not device.set) then
    return fail("EACCES")
  end
  local id = device.id
  local driver = driverof(device)
  if events then
    return eventfile(id, driver)
  end
  local lines = driver and driver.line
  local data, at = nil, 0
  local written = nil -- what's been written: all of it (kept for close), or a line not yet ended
  local changed = false -- a change came in since the file was last read from its start (poll's PRI)
  local kept = {} -- the events drain took, for a pull ioctl (held), oldest first
  local key = {} -- (its own copy of the device's events)
  -- Each event the device has waiting (a redstone card's changes), taken and kept for a pull
  -- ioctl: true if there was one. (A failure counts: the next read reports it.)
  local function drain()
    local any = false
    while true do
      local ok, event = nextevent(id, key)
      if not ok or event == nil then
        return any or not ok
      end
      kept[#kept + 1] = event
      any = true
    end
  end
  -- A line written, set at once (its driver's).
  local function set(text)
    local value = lines(text)
    if value == nil then
      return fail("EINVAL")
    end
    local ok, why, errno = command(id, "set", value)
    if ok == k.BLOCK then
      return k.BLOCK
    elseif not ok then
      return failed(why, errno or "EINVAL")
    end
    return true
  end
  return {
    type = "device",
    eventkey = key,
    -- As a sysfs attribute, for poll: always readable (IN, a read gives the state at once); PRI once
    -- the state changed since it was read from its start (as a GPIO's value file).
    events = function(self)
      if not changed and device.pull then
        changed = drain() -- (none: k.take listens for the next)
      end
      -- (a device without events leaves PRI to its news, withmethods': nil; one with them says,
      -- false too, never "and changed or nil", which makes a false nil and its news ready for ever)
      local pri = nil
      if device.pull then
        pri = changed
      end
      return {IN = true, PRI = pri, OUT = device.set ~= nil}
    end,
    -- A change a poll took, for a pull ioctl; the last one pulled is the change looked at (PRI off).
    held = function(self)
      local event = table.remove(kept, 1)
      if event ~= nil and #kept == 0 then
        changed = false
      end
      return event
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
        kept = {} -- (read anew: what changed is in the state)
      end
      return 0
    end,
    read = function(self, n)
      if data == nil then
        local ok, got, errno = command(id, "get")
        if ok == k.BLOCK then
          return k.BLOCK -- (a buffered device's get waits its turn)
        elseif not ok then
          return failed(got, errno)
        end
        data = driver and driver.get(got) or tostring(got or "")
      end
      local chunk = data:sub(at + 1, at + n)
      at = at + #chunk
      return chunk
    end,
    write = function(self, bytes)
      if not lines then
        written = written or {}
        written[#written + 1] = bytes
        return #bytes
      end
      local request = k.request
      if not (request and request.absorbed) then
        written = (written or "") .. bytes
        if request then
          request.absorbed = true -- (asked again after a block: these bytes are in already)
        end
      end
      while true do
        local line, rest = written:match("^([^\n]*)\n(.*)$")
        if not line then
          break
        end
        local ok, why, errno = set(line)
        if ok == k.BLOCK then
          return k.BLOCK -- (a buffered device busy: this line and the rest wait their turn)
        elseif not ok then
          written = nil
          return nil, why, errno
        end
        written = rest
      end
      return #bytes
    end,
    close = function(self)
      delist(id, key)
      local value = written
      written = nil
      if lines then
        return value == nil or value == "" or set(value) -- (a last line with no end)
      elseif value then
        local ok, why = board.invoke(id, "set", table.concat(value))
        if not ok then
          return failed(why)
        end
      end
      return true
    end,
  }
end

-- Every device file, over what it reads and writes: its device's methods as its ioctls, by name,
-- with their arguments as given (a device's own operations, as Linux's ioctls are; its own ioctls
-- come first: OSS's on a sound card's), setLabel (the kernel's: the label on its item), and ready
-- for poll (PRI) while its device has news (its interrupt came since the file last read or asked;
-- a file with its own PRI, a GPIO-like one's, keeps its own). An ioctl's answer is the method's
-- (true for a method that answers nothing, as a Linux ioctl's 0); a failure is EINVAL with why
-- (ENODEV: the device is gone).
local function withmethods(d, device)
  local id, own, events, read = device.id, d.ioctl, d.events, d.read
  -- What this file's calls opened and nothing else ends (a driver's 'opens': an internet request, a
  -- connection, by the method that closes it), closed with the file, as a socket's connection is.
  local driver = driverof(device)
  local opens, open = driver and driver.opens, {}
  local closes = {}
  for _, method in pairs(opens or {}) do
    closes[method] = true
  end
  local close = d.close
  d.close = function(self)
    local result, why, errno = true
    if close then
      result, why, errno = close(self)
    end
    for handle, method in pairs(open) do
      board.invoke(id, method, handle) -- (gone already, or the device is: nothing to do)
    end
    open = {}
    return result, why, errno
  end
  if read then
    d.read = function(self, ...)
      local got = table.pack(read(self, ...))
      if got[1] ~= k.BLOCK then
        k.news[id] = nil -- (read: its news is looked at; a read that waits looked at nothing)
      end
      return table.unpack(got, 1, got.n)
    end
  end
  d.ioctl = function(self, op, ...)
    if own then
      local answer = table.pack(own(self, op, ...))
      if not (answer[1] == nil and answer[3] == "ENOTTY") then
        return table.unpack(answer, 1, answer.n)
      end
    end
    if type(op) ~= "string" then
      return fail("ENOTTY")
    end
    k.news[id] = nil -- (asked: its news is looked at)
    if op == "pull" and d.held then
      local event = d.held(d)
      if event ~= nil then
        return event -- (one a poll took: the file's, first)
      end
    end
    if op == "pull" and d.eventkey and select("#", ...) == 0 then
      local ok, event = nextevent(id, d.eventkey) -- (its own copy: every file sees every event)
      if not ok then
        return failed(event)
      end
      return event
    end
    if op == "setLabel" then
      return k.setlabel(device, ...)
    end
    local answer = table.pack(command(id, op, ...))
    if answer[1] == k.BLOCK then
      return k.BLOCK -- (a buffered device busy: this thread waits, only it)
    elseif answer[1] == nil and answer[3] then
      return nil, answer[2], answer[3] -- (stopped while it waited)
    end
    if answer[1] and opens and opens[op] and answer[2] ~= nil then
      open[answer[2]] = opens[op] -- (its handle: open until its close, or the file's)
    elseif answer[1] and closes[op] then
      open[(...)] = nil
    end
    if not answer[1] then
      local why = tostring(answer[2])
      if why:find("^no such device") then
        return failed(why)
      end
      return nil, why, "EINVAL"
    end
    if answer.n == 1 then
      return true -- (a method that answers nothing: done)
    end
    for i = 2, answer.n do
      if type(answer[i]) == "userdata" then
        return nil, "a handle stays in the kernel: use the filesystem", "EINVAL" -- (a disk's open)
      end
    end
    return table.unpack(answer, 2, answer.n)
  end
  if device.pull and d.eventkey then
    enlist(id, d.eventkey) -- (every event from its open on: none taken before it's read is lost to it)
  end
  d.events = function(self)
    local ready = events and events(self) or {}
    if ready.PRI == nil then
      local queue = d.eventkey and readers[id] and readers[id][d.eventkey]
      if k.news[id] or queue and queue[1] ~= nil then
        ready.PRI = true
      else
        k.listen(id) -- (its interrupt wakes a poll waiting on it)
      end
    end
    return ready
  end
  return d
end

function fs:open(path, mode, process)
  local make, device = find(path)
  if make then
    return make(process)
  elseif not device then
    return fail("ENOENT")
  end
  local d, why, errno
  if device.type == DISK then
    d = diskfile(device)
  elseif device.type == SOUND then
    if mode:sub(1, 1) == "r" and not mode:find("+", 1, true) then
      return fail("EACCES") -- (it plays; it doesn't record)
    end
    d, why, errno = dspfile(device)
  else
    d, why, errno = devicefile(device, mode)
  end
  if not d then
    return nil, why, errno
  end
  return withmethods(d, device)
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
    entries[#entries + 1] = {name = device.name, type = "device"}
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
