-- devfs: /dev, devices as files, as Linux's devtmpfs. The kernel's own: console (its console tty),
-- tty (the opening process's own terminal), null and zero; and every device on the bus, by the
-- kernel's name for it (eeprom, storage0, internet0). What a device's file reads and writes is its
-- data: a disk's bytes (as a block device's: read, written and sought by byte offset; a disk with a
-- filesystem, hfs, refuses them: EMEDIUMTYPE), a sound card's samples (OSS's dsp, its ioctls the
-- format), a sensor's events (a line each), or what its get returns (taken at the first read; a
-- driver may name other methods) and what's written (a line at a time through its driver, each
-- calling the method it names, else all of it going to its set at close); a
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

-- A device call's failure as an errno: a device that's gone (not_found, a device: k.invoke has read
-- the bus again, the kernel's view was out of date) is ENODEV; anything else, 'errno' (EIO unless said).
local function failed(why, errno, failure)
  if type(failure) == "table" and failure.why == "not_found" and failure.kind == "device" then
    return fail("ENODEV")
  end
  return fail(errno or "EIO")
end

-- Drivers, by device type: a device whose data is a table, not text, has one on the boot disk
-- (k.driver), which makes it text, as Linux's interfaces are (lines, words), never Lua's syntax:
-- get(...) is what a read gives (an event's line, or the state), from the answers of the methods
-- its reads names (get alone when it names none); line(text), if it has one, is the device's
-- method a line written calls and its arguments (nil: not a line it takes), each taking effect as
-- it's written, as a sysfs attribute's write does, the device's own method and no other door (the
-- user's); failed(answer), if it has one, is why a call that answered meant it failed (nil: it
-- didn't). It's looked for the first time a file of that type is opened, and
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
-- FILE_EVENTS devfs takes no more of the device's events (the user's, 2026-11-11: devfs drops nothing
-- and knows no kind of event; what's full is its own): they wait in the device, whose own policy says
-- what it keeps (its news window, its ends a handshake), as a pipe's writer waits for its reader. A
-- reader that stops reading holds its device's other readers too, till it reads or closes (its read
-- or close is the kernel's progress: the others' waits are asked again, and take).
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
-- nil once it has nothing; or false and why, its failure), how many it took, and its failure ({why, ...}).
local function deliver(id)
  local taken = 0
  while true do
    for _, queue in pairs(readers[id] or {}) do
      if #queue >= FILE_EVENTS then
        return true, nil, taken -- (a file full: the rest wait in the device, nothing dropped; its read wakes the others)
      end
    end
    local ok, event, failure = k.take(id, "pull")
    if not ok or event == nil then
      return ok, event, taken, failure
    end
    taken = taken + 1
    k.delivered = true -- (into other files' queues too: their waiters have news, a pass of their own)
    for _, queue in pairs(readers[id] or {}) do
      queue[#queue + 1] = deepcopy(event)
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
  local ok, why, _, failure = deliver(id)
  local queue = readers[id][key]
  if queue[1] ~= nil then
    return true, table.remove(queue, 1)
  elseif not ok then
    return false, why, failure
  end
  return true, nil
end

-- A buffered device's command (one at a time) from a process: while the device is busy (or a
-- robot held), the calling thread blocks, only it, and is asked again (its events taken into its
-- files first, so its interrupt is down and the next is the running command's end). Events taken
-- then may hold that end already (it came between the refusal and the look): then it's asked again
-- at once, as an end already taken raises nothing to wake it. When the board names another device
-- whose news ends the wait (a crafting card's robot, a robot's crafting card), it hears both. It's
-- asked at most once a tick unless one of them raised its interrupt (the board's state changes a
-- tick at a time). A stop of the device, or of the one it waits on, ends the wait: what the threads
-- were feeding it was stopped (ECANCELED); a line "stop" written is one. Without a syscall to block
-- (a file closing as its process ends), busy is EBUSY.
local stopped = {} -- by device id: how many stops it's had
local COMMAND_TRIES = 4 -- times a command is asked again at once, news having come with each refusal

-- A device's sync from a process (every buffered device's, the sound card's): the device answers
-- whether its queue is empty (no command of its own under way); while it isn't, the calling thread
-- blocks, only it, until the device's interrupt. A sync takes no events: those it moves out of the
-- device go into its files, for their reads (how its commands went), and may hold the end already
-- (it came between the answer and the look), so it's asked again at once then. Without a syscall to
-- block (a file closing as its process ends), not empty is EBUSY.
local function sync(id)
  local tries = 0
  while true do
    local ok, empty, failure = k.invoke(id, "sync")
    if not ok then
      return nil, empty, nil, failure -- (its failure: why, no errno, the failure itself)
    elseif empty then
      return ok, empty
    elseif not k.request then
      return nil, "busy", "EBUSY"
    end
    local _, _, taken = deliver(id)
    tries = tries + 1
    if taken == 0 or tries >= COMMAND_TRIES then
      k.listen(id) -- (its command's end raises its interrupt)
      return k.BLOCK
    end
  end
end

-- Whether a stop of the device, or of the one it waits on, came since the request began waiting.
local function cancelled(request)
  for device, count in pairs(request.stops) do
    if (stopped[device] or 0) ~= count then
      return true
    end
  end
  return false
end

-- Whether a call is a stop (a line "stop" written calls it too).
local function isstop(method)
  return method == "stop"
end

local function command(id, method, ...)
  local request = k.request
  if request and request.stops and cancelled(request) then
    return nil, "stopped", "ECANCELED"
  end
  if request and request.waitedat == board.uptime() and not k.news[id] and not (request.blocker and k.news[request.blocker]) then
    k.listen(id) -- (asked this tick already, and nothing's been raised since: no call for nothing; the idle
    if request.blocker then -- wait hears it, as it forgets what it listened for each time)
      k.listen(request.blocker)
    end
    return k.BLOCK
  end
  local answer = table.pack(k.invoke(id, method, ...))
  local tries = 0
  while not answer[1] and (answer[2] == "busy" or answer[2] == "held") do
    if not request then
      return nil, answer[2], "EBUSY", answer[3]
    end
    -- The board names the device whose news ends the wait (its own, or a card's robot): hear both.
    local blocker = answer[3].id
    request.blocker = blocker
    if not request.stops then
      request.stops = {[id] = stopped[id] or 0}
    end
    if blocker and request.stops[blocker] == nil then
      request.stops[blocker] = stopped[blocker] or 0
    end
    local _, _, taken = deliver(id)
    if blocker then
      local _, _, also = deliver(blocker)
      taken = taken + (also or 0)
    end
    tries = tries + 1
    if taken == 0 or tries >= COMMAND_TRIES then
      -- Nothing came: what ends the wait will raise the device's interrupt. (Or news keeps coming: the
      -- pass counts it, k.delivered, so it's asked again at the next, never forgotten.)
      k.listen(id)
      if blocker then
        k.listen(blocker)
      end
      request.waitedat = taken == 0 and board.uptime() or nil
      return k.BLOCK
    end
    answer = table.pack(k.invoke(id, method, ...)) -- (what came may be the end it waited for)
  end
  if answer[1] and isstop(method) then
    stopped[id] = (stopped[id] or 0) + 1
  elseif not answer[1] then
    return nil, answer[2], nil, answer[3] -- (its failure: why, no errno of its own, the failure itself)
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
    local ok, got, failure = nextevent(id, file)
    if not ok then
      return failed(got, nil, failure)
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
  local function refused(why, failure)
    local reason = type(failure) == "table" and failure.why == "denied" and failure.reason
    if reason == "hfs_disk" then
      return fail("EMEDIUMTYPE")
    elseif reason == "read_only" then
      return fail("EROFS")
    end
    return failed(why, nil, failure)
  end
  return {
    type = "device",
    events = function(self)
      return {IN = true, OUT = true}
    end,
    read = function(self, n)
      local ok, data, failure = k.invoke(id, "read", at, n)
      if not ok then
        return refused(data, failure)
      end
      data = data or ""
      at = at + #data
      return data
    end,
    write = function(self, data)
      local ok, count, failure = k.invoke(id, "write", at, data)
      if not ok then
        return refused(count, failure)
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
        local ok, capacity, failure = k.invoke(id, "getCapacity")
        if not ok then
          return failed(capacity, nil, failure)
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
  local ok, why, failure = k.invoke(id, "setFormat", {rate = 8000, encoding = "u8", channels = 1})
  if not ok then
    return failed(why, nil, failure)
  end
  local function set(field, value, answer)
    local done, err, failure = k.invoke(id, "setFormat", {[field] = value})
    if not done then
      return failed(err, "EINVAL", failure)
    end
    local got, format, failure = k.invoke(id, "getFormat")
    if not got then
      return failed(format, nil, failure)
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
      local got, delay, failure = k.invoke(id, "getDelay")
      if not got then
        return failed(delay, nil, failure)
      end
      return delay
    end,
    SNDCTL_DSP_RESET = function()
      local done, err, failure = k.invoke(id, "reset")
      if not done then
        return failed(err, nil, failure)
      end
      return true
    end,
    SNDCTL_DSP_SYNC = function()
      local done, drained, failure = k.invoke(id, "sync")
      if not done then
        return failed(drained, nil, failure)
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
      local done, count, failure = k.invoke(id, "write", data)
      if not done then
        return failed(count, nil, failure)
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
  local driver = driverof(device)
  local reads = driver and driver.reads or {"get"} -- (the methods a read gives the answers of: its driver knows its type's)
  if driver and driver.stream and not writes then
    return eventfile(device.id, driver) -- (its read is its events, a line each, from its open: its driver says so)
  end
  local readable = (driver and driver.reads or device.get) ~= nil
  local lines = driver and driver.line
  local writable = (lines or device.set) ~= nil -- (lines is false with no driver: never "~= nil" alone)
  local events = device.pull and not readable
  if not (readable or writable or events) then
    if writes then
      return fail("EACCES")
    end
    return aboutfile(device)
  end
  if (reads and not (readable or events)) or (writes and not writable) then
    return fail("EACCES")
  end
  local id = device.id
  if events then
    return eventfile(id, driver)
  end
  local data, at = nil, 0
  local written = nil -- what's been written: all of it (kept for close), or a line not yet ended
  local absorbing, before = nil, nil -- the write whose bytes are in 'written', while it waits; what was there before it
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
  -- A line written: the method it calls, at once (its driver's).
  local function set(text)
    local call = table.pack(lines(text))
    if call[1] == nil then
      return fail("EINVAL")
    end
    local ok, why, errno, failure = command(id, table.unpack(call, 1, call.n))
    if ok == k.BLOCK then
      return k.BLOCK
    elseif not ok then
      return failed(why, errno or "EINVAL", failure)
    end
    if driver.failed and driver.failed(why) then
      return fail("EINVAL") -- (its answer meant it failed: an operator's command that didn't succeed)
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
      return {IN = true, PRI = pri, OUT = writable}
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
        local ends = {} -- (read anew: what changed is in the state; a command's end isn't, it stays)
        for _, event in ipairs(kept) do
          if type(event) == "table" and event.ok ~= nil then
            ends[#ends + 1] = event
          end
        end
        kept = ends
      end
      return 0
    end,
    read = function(self, n)
      if data == nil then
        local answers = {}
        for i, method in ipairs(reads) do
          local ok, got, errno, failure = command(id, method)
          if ok == k.BLOCK then
            return k.BLOCK -- (a buffered device's get waits its turn)
          elseif not ok then
            return failed(got, errno, failure)
          end
          answers[i] = got
        end
        data = driver and driver.get(table.unpack(answers, 1, #reads)) or tostring(answers[1] or "")
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
      if absorbing and absorbing ~= request then
        written = before -- (a write that waited and never came back, EINTR or a cancel: its bytes go)
        absorbing = nil
      end
      if not (request and request.absorbed) then
        before = written
        written = (written or "") .. bytes
        if request then
          request.absorbed = true -- (asked again after a block: these bytes are in already)
          absorbing = request
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
          written, absorbing = nil, nil
          return nil, why, errno
        end
        written = rest
      end
      absorbing = nil
      return #bytes
    end,
    -- Before its last close: a last line with no end is sent (a busy device's waits, the closing
    -- thread only, its descriptor still open), so its close sends nothing.
    flush = function(self)
      if lines and written ~= nil and written ~= "" then
        local ok, why, errno = set(written)
        if ok == k.BLOCK then
          return k.BLOCK
        end
        written = nil
        if not ok then
          return nil, why, errno
        end
      end
      return true
    end,
    close = function(self)
      delist(id, key)
      local value = written
      written = nil
      if lines then
        return value == nil or value == "" or set(value) -- (a last line with no end)
      elseif value then
        local ok, why, failure = k.invoke(id, "set", table.concat(value))
        if not ok then
          return failed(why, nil, failure)
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
-- (true for a method that answers nothing, as a Linux ioctl's 0); a failure is EINVAL with why (its
-- word), then the failure itself, {why, ...} (its fields: the device's own shape for that why)
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
      k.invoke(id, method, handle) -- (gone already, or the device is: nothing to do)
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
      local ok, event, failure = nextevent(id, d.eventkey) -- (its own copy: every file sees every event)
      if not ok then
        return failed(event, nil, failure)
      end
      return event
    end
    if op == "setLabel" then
      return k.setlabel(device, ...)
    end
    local answer
    if op == "sync" and select("#", ...) == 0 then
      answer = table.pack(sync(id))
    else
      answer = table.pack(command(id, op, ...))
    end
    if answer[1] == k.BLOCK then
      return k.BLOCK -- (a buffered device busy: this thread waits, only it)
    elseif answer[1] == nil and answer[3] then
      return nil, answer[2], answer[3], answer[4] -- (stopped while it waited; busy with no thread to block)
    end
    if answer[1] and opens and opens[op] and answer[2] ~= nil then
      open[answer[2]] = opens[op] -- (its handle: open until its close, or the file's)
    elseif answer[1] and closes[op] then
      open[(...)] = nil
    end
    if not answer[1] then
      local why, failure = tostring(answer[2]), answer[4]
      if type(failure) == "table" and failure.why == "not_found" and failure.kind == "device" then
        return failed(why, nil, failure)
      end
      if type(failure) == "table" and failure.why == "limit" and failure.of == "events" then
        return nil, why, "EAGAIN", failure -- (its unread ends fill it: read them, then give it more; Linux's flow control's word)
      end
      return nil, why, "EINVAL", failure -- (its why, then the failure itself: {why, ...}, its fields)
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
    -- Every event from its open on, and none from before (the user's, 2026-11-10: evdev's way, a
    -- sitting cat or a tail -f shows what happens from then): what waits in the device now goes to
    -- the files already open on it (none: no file was reading, and it's gone), then this file takes
    -- what comes after it opened.
    deliver(id)
    enlist(id, d.eventkey)
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
