-- mcos (Lua): the kernel. The bootloader hands it its boot disk's address, then its command line
-- (words: console=<address> names the input device its console tty reads, the board's console by
-- default; init=<path> the program it starts on it, /bin/sh by default; rc=<path> the shell script
-- it runs first, to its end, /etc/rc by default).
--
-- Kernel and user space, as Linux keeps them: the kernel owns every resource (open files, pipes,
-- ttys, mounts, processes) and runs only on its own coroutine; a process runs its own code and
-- reaches the kernel only through syscalls (the 'sys' table it's given), which trap: board.trap()
-- stops the process where it is and returns to the kernel's resume of it (every process is one of
-- the firmware's stops), and the kernel serves the request on its own stack and thaws the process
-- with the answer. A syscall that can't finish yet (a read with nothing typed, a full pipe, a wait
-- for a child) leaves the process blocked until it can. A process is also stopped when input
-- arrives (the kernel runs its interrupt handlers at once: a ^C ends the foreground job) and when
-- its slice is spent (the timer), so processes are preempted: a busy one never keeps the others
-- (or the kernel) from running. Since a process is only ever stopped in its own code, the kernel
-- can end it at any time by dropping it (SIGKILL), and closes what it held.
local root, cmdline = ...

local VERSION = "0.2"
local CONSOLE = "console" -- the device the console tty is on, unless the command line says (console=)
local INIT = "/bin/sh" -- what runs on the console, unless the command line says (init=); started again when it ends
local SHELL = "/bin/sh" -- what runs the boot's script
local RC = "/etc/rc" -- the boot's script, unless the command line says (rc=): run once, before init
local LOADER = "/lib/ld.lua" -- where every process starts (an ELF program's PT_INTERP): every program is Lua, native to this CPU
local MODULES = "/lib/modules/" -- the kernel's own parts
-- When init can't be started (its file missing or broken), how long before the kernel tries again.
local RESPAWN_RETRY_TICKS = 100 -- 5 seconds
local READ_SIZE = 4096 -- the most one read(2) of a file takes (a disk's most)
local MAX_FDS = 64 -- file descriptors a process may hold (RLIMIT_NOFILE)
local TICKS_PER_SECOND = 20
local MOUNT_BASE = "/mnt" -- where disks found on the bus are mounted (sata:1 at /mnt/sata/1, id:x at /mnt/id/x)

local options = {}
for word in (cmdline or ""):gmatch("%S+") do
  local key, value = word:match("^([^=]+)=(.*)$")
  options[key or word] = value or true
end

local isfrozen, thaw = debug.isfrozen, debug.thaw
local trap = board.trap

-- What the kernel's parts share.
local k = {
  READ_SIZE = READ_SIZE,
  BLOCK = setmetatable({}, {__name = "BLOCK"}), -- a description's answer: try again when things change
}

-- A file of the kernel's, read off the boot disk itself (the VFS isn't up yet), run with its arguments.
local function kload(path, ...)
  local opened, file = board.invoke(root, "open", path)
  if not opened then
    error(path .. ": " .. tostring(file), 0)
  end
  local parts, ok, chunk = {}, nil, nil
  repeat
    ok, chunk = board.invoke(file, "read", READ_SIZE)
    parts[#parts + 1] = ok and chunk or nil
  until not (ok and chunk)
  board.invoke(file, "close")
  local at = 0
  return assert(load(function() -- (straight from the pieces read: never joined into one string)
    at = at + 1
    local piece = parts[at]
    parts[at] = nil
    return piece
  end, "=" .. path))(...)
end

-- A kernel module (lib/modules/<name>.lua).
local function module(name, ...)
  return kload(MODULES .. name .. ".lua", ...)
end

-- A device type's driver (lib/modules/drivers/<type>.lua), loaded when it's first needed, as Linux
-- finds a device's module on disk: nil when the boot disk has none.
function k.driver(kind)
  if not kind:match("^%w+$") then
    return nil
  end
  local path = MODULES .. "drivers/" .. kind .. ".lua"
  local ok, found = board.invoke(root, "stat", path)
  return ok and found and kload(path, k) or nil
end

local vfs = module("vfs", k)
local fail = vfs.fail
local tty = module("tty")
local hfs = module("hfs", k, vfs)
local devfs = module("devfs", k, vfs)
local newpipe = module("pipe", k, vfs)
local tmpfs = module("tmpfs", k, vfs)

-- The bus as the kernel last read it: each device's name (its type and its place among those of its
-- type in bus order: storage0, terminal1, as Linux's sda, ttyS0; a device the board has one slot
-- for, and nowhere else to be, is just its type: eeprom, as Linux's /dev/mem), id (the address it's used by, whatever
-- slot or cable it's on), slot path (nil when it isn't in one), type, label, and whether it has a
-- get and a set (data /dev can read and write). It may be out of date, and is correct eventually
-- (as Linux's /dev, which udev fills in after the fact): it's read again when the bus's hot-plug
-- interrupt comes and when a call to a device fails because the device is gone, never on a /dev
-- lookup. Nothing depends on it being fresh: an open file holds a device's id, so it reaches that
-- device or fails (ENODEV), never another one.
local devices = {list = {}, byname = {}}
k.devices = devices
local described = {} -- by id: {label, get, set}, asked once a device (an id never changes what it is)
local SINGLE = {eeprom = "eeprom"} -- the slots of devices there's only ever one of: named without a number

function k.refreshdevices()
  local list, byname, counts = {}, {}, {}
  for _, device in ipairs(board.list()) do
    local kind = (device.type or "device"):gsub("[^%w]", "")
    local n = counts[kind] or 0
    counts[kind] = n + 1
    local id = device.id or device.address
    local about = described[id]
    if not about then
      local info = board.info(device.address) or {}
      about = {label = info.label}
      for _, method in ipairs(board.methods(device.address) or {}) do
        if method == "get" or method == "set" or method == "pull" then
          about[method] = true
        end
      end
      described[id] = about
    end
    local slot = device.address ~= device.id and not device.address:find("^id:") and device.address or nil
    local entry = {name = SINGLE[slot] or kind .. n, id = id, path = slot,
      type = device.type, label = about.label, get = about.get, set = about.set, pull = about.pull}
    list[#list + 1], byname[entry.name] = entry, entry
  end
  devices.list, devices.byname = list, byname
  return list
end

-- printk: the kernel's messages go to the console.
local console
local function printk(text)
  console:write("mcos: " .. text .. "\n")
end

-- ---- interrupts ---------------------------------------------------------------------------------

-- The interrupt handler, run whenever the kernel gets control (a process stopped for input or by the
-- timer, a syscall, waking from idle): the console tty's line discipline, over its device's ring.
local function interrupt()
  console:pump()
end

-- ---- processes ----------------------------------------------------------------------------------

-- Processes by pid; each: {pid, ppid, pgid, path, argv, co, fds, cwd, environ, tty, handlers (by
-- signal: "ignore", or a function, run in the process), caught (signals for it to handle), request
-- (a syscall it trapped with), reply, blocked, zombie, status}.
local processes = {}
local order = {} -- pids in the order they run (round robin)
local nextpid = 1
local kernel = {pid = 0, cwd = "/", fds = {}, environ = {PATH = "/bin", HOME = "/"}, handlers = {}}

local SIGNALS = {SIGHUP = 1, SIGINT = 2, SIGKILL = 9, SIGPIPE = 13, SIGTERM = 15, SIGCHLD = 17}
-- What a signal does when a process hasn't said: end it, or nothing.
local IGNORED_BY_DEFAULT = {SIGCHLD = true}

local function children(parent)
  local list = {}
  for _, pid in ipairs(order) do
    local p = processes[pid]
    if p.ppid == parent.pid then
      list[#list + 1] = p
    end
  end
  return list
end

-- File descriptors point at open file descriptions, shared (with a count) after dup or spawn.
local function hold(description)
  description.refs = (description.refs or 0) + 1
  return description
end

local function release(description)
  description.refs = description.refs - 1
  if description.refs == 0 then
    return vfs.close(description)
  end
  return true
end

local function lowestfd(p, from)
  for fd = from or 0, MAX_FDS - 1 do
    if p.fds[fd] == nil then
      return fd
    end
  end
end

local respawn

-- A zombie reaped: gone from the table and the run order.
local function reap(p)
  processes[p.pid] = nil
  for i, pid in ipairs(order) do
    if pid == p.pid then
      table.remove(order, i)
      return
    end
  end
end

-- A process ends: everything it held is let go (its files closed, as Linux's exit_files: data it
-- wrote reaches the disk), its children go to the kernel, and it waits as a zombie for its parent.
local function exit(p, how, value)
  if p.zombie then
    return
  end
  if p.co then
    board.removestop(p.co)
    if not isfrozen(p.co) and coroutine.status(p.co) == "suspended" then
      coroutine.close(p.co)
    end
    p.co = nil -- dropped where it stopped: none of its code runs (SIGKILL)
  end
  for fd, description in pairs(p.fds) do
    release(description)
    p.fds[fd] = nil
  end
  if p.tty and p.tty.session == p.pid then
    p.tty.session = nil -- its session's over: the tty is free for another
  end
  p.zombie, p.status = true, {how, value}
  p.request, p.reply, p.blocked = nil, nil, false
  for _, child in ipairs(children(p)) do
    if child.zombie then
      reap(child) -- an orphan that had ended: the kernel reaps it (as init reaps orphans)
    else
      child.ppid = 0
    end
  end
  if p.ppid == 0 then
    reap(p) -- the kernel's own child: reaped at once
    if p.respawn or p.sysinit then
      respawn() -- (after the boot's script: init's first start)
    end
  end
end

-- Delivers a signal: ignored, caught (the process's handler runs when its syscall returns; a
-- blocked syscall returns EINTR), or it ends the process.
local function post(p, signal)
  if p.zombie then
    return
  end
  local disposition = signal ~= "SIGKILL" and p.handlers[signal]
  if disposition == "ignore" or (not disposition and IGNORED_BY_DEFAULT[signal]) then
    return
  elseif type(disposition) == "function" then
    p.caught[#p.caught + 1] = signal
    if p.blocked then
      p.blocked, p.request = false, nil
      p.reply = table.pack(true, fail("EINTR"))
    end
  else
    p.killed = signal
  end
end

local function signalgroup(pgid, signal)
  for _, pid in ipairs(order) do
    local p = processes[pid]
    if p.pgid == pgid then
      post(p, signal)
    end
  end
end

local syscalls = {}

-- The syscall table a process is given: each traps to the kernel and returns its answer; then any
-- signal the process caught is handled, in the process, as Linux does on the way back to user mode.
local function systable(p)
  -- (each made the first time the process looks it up: most use a few, and a syscall costs a
  -- process nothing until it does)
  return setmetatable({SIGNALS = SIGNALS}, {__index = function(sys, name)
    if not syscalls[name] then
      return nil
    end
    local function call(...)
      p.request = table.pack(name, ...)
      trap()
      local reply = p.reply
      p.reply = nil
      if reply == nil then
        error("sys." .. name .. ": not called from its process", 2)
      end
      while p.caught[1] do
        local signal = table.remove(p.caught, 1)
        local handler = p.handlers[signal]
        if type(handler) == "function" then
          handler(signal)
        end
      end
      return table.unpack(reply, 2, reply.n)
    end
    sys[name] = call
    return call
  end})
end

local function copy(t)
  local c = {}
  for key, value in pairs(t) do
    c[key] = value
  end
  return c
end

-- copy_from_user: a table a process passed, copied raw (next, never pairs or an index: none of its
-- metamethods runs on the kernel's stack), strings, numbers and booleans only, tables one level
-- deeper; nil if it isn't a table.
local function fromuser(t, deeper)
  if type(t) ~= "table" then
    return nil
  end
  local c = {}
  local key, value = next(t)
  while key ~= nil do
    local kind = type(value)
    if kind == "table" then
      c[key] = deeper and fromuser(value) or nil
    elseif kind == "string" or kind == "number" or kind == "boolean" then
      c[key] = value
    end
    key, value = next(t, key)
  end
  return c
end

-- A new process's globals: Lua's base library (no board: the hardware is the kernel's) and its
-- syscalls; the loader (/lib/ld.lua) adds the rest (io, os, require).
local BASE = {"assert", "error", "getmetatable", "ipairs", "next", "pairs", "pcall", "rawequal", "rawget",
  "rawlen", "rawset", "select", "setmetatable", "tonumber", "tostring", "type", "xpcall", "_VERSION",
  "collectgarbage"}
local LIBRARIES = {"coroutine", "string", "table", "math", "utf8"}

local function environment(p)
  local env = {}
  for _, name in ipairs(BASE) do
    env[name] = _G[name]
  end
  for _, name in ipairs(LIBRARIES) do
    env[name] = copy(_G[name])
  end
  env.load = function(chunk, name, mode, scope)
    return load(chunk, name, mode, scope or env)
  end
  env.debug = {traceback = debug.traceback} -- (for error reports; the rest of debug is the kernel's)
  env._G = env
  env.sys = systable(p)
  return env
end

-- Memory: a new process's stack is made without the emergency collection other allocations get
-- (Lua's), and a compile needs a lot for a moment, so with garbage about either could fail though
-- there's room. Past half the RAM, the kernel collects first (as Linux reclaims memory before a fork
-- or a big allocation would fail).
local RECLAIM_SHARE = 0.5 -- of the RAM in use (counted with the firmware's own) before a spawn collects
local KIB = 1024
local ram = board.hardware().memoryTotal or 0

local function reclaim()
  if collectgarbage("count") * KIB > ram * RECLAIM_SHARE then
    collectgarbage("collect")
  end
end

-- Program images, shared (as the page cache shares a program's text between the processes running
-- it): a Lua file is compiled once, as function(_ENV, ...) around its text, and each process runs it
-- with its own globals, so the compiled code (the most of a program's memory) is in memory once.
-- Kept while some process holds one (weak), and compiled again when the file changes.
local images = setmetatable({}, {__mode = "v"}) -- by path and what the file was (size, modified)

local function image(path)
  local st, why, errno = vfs.stat(path)
  if not st then
    return nil, why, errno
  elseif st.type == "directory" then
    return fail("EISDIR")
  end
  path = st.path -- (by where links lead: one image for every name of a file)
  local key = path .. ":" .. st.size .. ":" .. tostring(st.modified)
  if images[key] then
    return images[key]
  end
  reclaim() -- (compiling takes far more for a moment than the image keeps)
  local parts, chunk
  parts, why, errno = vfs.readparts(path)
  if not parts then
    return nil, why, errno
  end
  parts[1] = parts[1] and (parts[1]):gsub("^#![^\n]*", "") -- a #! line is the kernel's (its line stays, empty)
  -- compiled straight from the pieces read, inside function(_ENV, ...) ... end: the text is never
  -- joined into one string (nor copied to wrap it), and each piece goes once the compiler has it
  local n, at = #parts, 0
  chunk, why = load(function()
    at = at + 1
    local piece = at == 1 and "return function(_ENV, ...) " or parts[at - 1] or at == n + 2 and "\nend" or nil
    parts[at - 1] = nil
    return piece
  end, "=" .. path, "t", {})
  if not chunk then
    return nil, why, "ENOEXEC"
  end
  images[key] = chunk()
  return images[key]
end

-- posix_spawn: a new process running 'path' (through the loader) with 'argv', as the parent's
-- child. opts: fds (the child's descriptors: {[0] = a parent fd, ...}; 0, 1 and 2 are inherited
-- when not given, the others aren't: close-on-exec), env (its environment; the parent's when not
-- given), ignore (signals it starts ignoring, as an async list's SIGINT), pgid (0: a group of its
-- own), setsid (a session of its own, as glibc's POSIX_SPAWN_SETSID: its own group; its controlling
-- tty is its stdin's, if that's a tty of no session, as login_tty makes it, and it's in front there;
-- else it has none, so no tty's signals reach it: a daemon). A descriptor mapped to false starts
-- closed in the child (a shell's n>&-), even 0, 1 or 2.
local function spawn(parent, path, argv, opts)
  opts = opts or {}
  reclaim()
  path = vfs.normalize(path, parent.cwd)
  local st, why, errno = vfs.stat(path)
  if not st then
    return nil, why, errno
  elseif st.type == "directory" then
    return fail("EACCES")
  end
  local loader
  loader, why, errno = image(LOADER)
  if not loader then
    return nil, why, errno
  end
  local p = {
    pid = nextpid, ppid = parent.pid, pgid = parent.pgid, path = path, argv = argv or {},
    fds = {}, cwd = parent.cwd, environ = copy(opts.env or parent.environ), tty = parent.tty,
    handlers = {}, caught = {},
  }
  local env = environment(p)
  if opts.pgid == 0 or opts.setsid then
    p.pgid = p.pid
  end
  for signal, disposition in pairs(parent.handlers) do
    if disposition == "ignore" then
      p.handlers[signal] = "ignore" -- ignored stays ignored across exec; handlers don't
    end
  end
  for _, signal in ipairs(opts.ignore or {}) do
    p.handlers[signal] = "ignore"
  end
  local map = opts.fds or {}
  for fd = 0, 2 do
    if map[fd] == nil and parent.fds[fd] then
      map[fd] = fd
    end
  end
  for fd, from in pairs(map) do
    local description = from ~= false and parent.fds[from]
    if math.type(fd) ~= "integer" or fd < 0 or fd >= MAX_FDS or (from ~= false and not description) then
      for _, held in pairs(p.fds) do
        release(held)
      end
      return fail("EBADF")
    end
    p.fds[fd] = description and hold(description) or nil
  end
  if opts.setsid then
    -- a session of its own: its controlling tty is its stdin's, if that's a tty no session has
    -- (as login_tty does after setsid), and it's in front there; else it has none
    local t = p.fds[0] and p.fds[0].tty
    p.tty = t and not t.session and t or nil
    if p.tty then
      p.tty.session, p.tty.foreground = p.pid, p.pgid
    end
  end
  p.co = coroutine.create(function()
    return loader(env, path, table.unpack(p.argv))
  end)
  board.addstop(p.co)
  nextpid = nextpid + 1
  processes[p.pid] = p
  order[#order + 1] = p.pid
  return p
end

-- The time, in ticks, read once a pass of the scheduler (a board call).
local now

-- ---- syscalls -----------------------------------------------------------------------------------
-- Each: (process, request, args...) -> its answer, or k.BLOCK to be asked again when things change.

local function description(p, fd)
  local d = math.type(fd) == "integer" and p.fds[fd]
  if not d then
    return fail("EBADF")
  end
  if d.mount and d.mount.gone then
    return fail("EIO") -- its filesystem was taken away (the disk left)
  end
  return d
end

-- A syscall on a file descriptor: f(p, request, description, fd, args...), given an open one.
local function withfd(f)
  return function(p, request, fd, ...)
    local d, why, errno = description(p, fd)
    if not d then
      return nil, why, errno
    end
    return f(p, request, d, fd, ...)
  end
end

local function path(p, given)
  if type(given) ~= "string" then
    return nil
  end
  return vfs.normalize(given, p.cwd)
end

function syscalls.open(p, request, file, mode)
  mode = mode or "r"
  local at = path(p, file)
  if not at then
    return fail("EINVAL")
  end
  local fd = lowestfd(p)
  if not fd then
    return fail("EMFILE")
  end
  local d, why, errno = vfs.open(at, mode:gsub("b", ""), p)
  if not d then
    return nil, why, errno
  end
  p.fds[fd] = hold(d)
  return fd
end

syscalls.close = withfd(function(p, request, d, fd)
  p.fds[fd] = nil
  return release(d)
end)

syscalls.read = withfd(function(p, request, d, fd, n)
  if not d.read then
    return fail(d.type == "directory" and "EISDIR" or "EBADF")
  end
  return d:read(math.type(n) == "integer" and n > 0 and n or READ_SIZE)
end)

-- Writes all of 'data', waiting for room as long as it takes (a pipe's reader); the count written.
syscalls.write = withfd(function(p, request, d, fd, data)
  if not d.write then
    return fail("EBADF")
  end
  if type(data) == "number" then
    data = tostring(data)
  elseif type(data) ~= "string" then
    return fail("EINVAL")
  end
  request.done = request.done or 0
  while request.done < #data do
    local count, why, errno = d:write(request.done == 0 and data or data:sub(request.done + 1))
    if count == k.BLOCK then
      return k.BLOCK
    elseif not count then
      if errno == "EPIPE" then
        post(p, "SIGPIPE")
      end
      return nil, why, errno
    end
    request.done = request.done + count
  end
  return request.done
end)

syscalls.lseek = withfd(function(p, request, d, fd, offset, whence)
  if not d.seek then
    return fail("ESPIPE")
  end
  return d:seek(whence or "set", offset or 0)
end)

syscalls.getdents = withfd(function(p, request, d)
  if not d.getdents then
    return fail("ENOTDIR")
  end
  return d:getdents()
end)

syscalls.dup = withfd(function(p, request, d, fd, from)
  local new = lowestfd(p, from)
  if not new then
    return fail("EMFILE")
  end
  p.fds[new] = hold(d)
  return new
end)

function syscalls.pipe(p)
  local r = lowestfd(p)
  local w = r and lowestfd(p, r + 1)
  if not w then
    return fail("EMFILE")
  end
  local reader, writer = newpipe()
  p.fds[r], p.fds[w] = hold(reader), hold(writer)
  return r, w
end

-- openpty(): a pseudo-terminal, as openpty(3): its master end (the program playing the terminal:
-- what it writes is typed, what it reads was shown) and its terminal end, a tty
-- (lib/modules/pty.lua, loaded at the first).
local newpty
function syscalls.openpty(p)
  local m = lowestfd(p)
  local s = m and lowestfd(p, m + 1)
  if not s then
    return fail("EMFILE")
  end
  newpty = newpty or module("pty", k, vfs, tty)
  local master, terminal = newpty()
  p.fds[m], p.fds[s] = hold(master), hold(terminal)
  return m, s
end

-- isatty, and the terminal's ioctls by their Linux names: TCGETS and TCSETS (termios flags, as a
-- table), TIOCGWINSZ (columns, rows), TIOCSWINSZ ({columns, rows}: a pty's, set by its master),
-- TIOCSTI (bytes put in as if typed), on a tty or a pty's master; a socket's are net's.
local net -- (lib/modules/net.lua, once a socket is made)
syscalls.ioctl = withfd(function(p, request, d, fd, op, arg)
  if d.type == "socket" then
    return net.ioctl(d, op, type(arg) == "table" and fromuser(arg) or arg)
  elseif d.type ~= "tty" and d.type ~= "pty" then
    return fail("ENOTTY")
  end
  local t = d.tty
  if op == "TCGETS" then
    return copy(t.flags)
  elseif op == "TCSETS" and type(arg) == "table" then
    t:setattr(fromuser(arg))
    return true
  elseif op == "TIOCGWINSZ" and t.size then
    return t.size[1], t.size[2]
  elseif op == "TIOCSWINSZ" and type(arg) == "table" and math.type(arg[1]) == "integer" and math.type(arg[2]) == "integer" then
    t.size = {arg[1], arg[2]} -- (a pty's: its master says how big its terminal is)
    return true
  elseif op == "TIOCGWINSZ" and t.device then
    local ok, width, height = board.invoke(t.device, "getSize")
    if ok then
      return width, height
    end
  elseif op == "TIOCSTI" and type(arg) == "string" then
    t:receive(arg) -- input, as if typed (Linux's TIOCSTI): a shell gives back what it read ahead
    return true
  end
  return fail("EINVAL")
end)

function syscalls.isatty(p, request, fd)
  local d = description(p, fd)
  return d ~= nil and d.type == "tty"
end

-- Syscalls on paths (made absolute from the process's working directory), answered by the VFS:
-- stat, lstat (a link itself: type "link", its target), readlink, realpath (every link followed),
-- mkdir, unlink, rmdir, umount, statfs ({type, size, used}), image (a Lua file's shared image: see images; a compile's error is
-- ENOEXEC), and with two paths rename and link (a hard link).
local ONE_PATH = {stat = vfs.stat, lstat = vfs.lstat, readlink = vfs.readlink, realpath = vfs.realpath,
  mkdir = vfs.mkdir, umount = vfs.umount, statfs = vfs.statfs, image = image,
  unlink = function(at)
    return vfs.remove(at, false)
  end,
  rmdir = function(at)
    return vfs.remove(at, true)
  end}
for name, f in pairs(ONE_PATH) do
  syscalls[name] = function(p, request, file)
    local at = path(p, file)
    if not at then
      return fail("EINVAL")
    end
    return f(at)
  end
end
for name, f in pairs({rename = vfs.rename, link = vfs.link}) do
  syscalls[name] = function(p, request, from, to)
    local a, b = path(p, from), path(p, to)
    if not (a and b) then
      return fail("EINVAL")
    end
    return f(a, b)
  end
end

-- symlink(target, path): a symbolic link at path; the target is kept as given (relative: from the
-- link's directory, when it's followed).
function syscalls.symlink(p, request, target, file)
  local at = path(p, file)
  if type(target) ~= "string" or not at then
    return fail("EINVAL")
  end
  return vfs.symlink(target, at)
end

function syscalls.chdir(p, request, file)
  local at = path(p, file)
  if not at then
    return fail("EINVAL")
  end
  local st, why, errno = vfs.stat(at)
  if not st then
    return nil, why, errno
  elseif st.type ~= "directory" then
    return fail("ENOTDIR")
  end
  p.cwd = st.path -- where links led (getcwd gives the real path, as Linux's does)
  return true
end

function syscalls.getcwd(p)
  return p.cwd
end

-- mount(source, target[, type]): a disk (its bus address: an hfs disk) or a kernel filesystem
-- ("devfs") on a path.
function syscalls.mount(p, request, source, target, fstype)
  local at = path(p, target)
  if type(source) ~= "string" or not at then
    return fail("EINVAL")
  end
  local fs
  if fstype == "devfs" or source == "devfs" then
    fs = devfs
  elseif fstype == "tmpfs" or source == "tmpfs" then
    fs = tmpfs()
  else
    local ok, kind = board.invoke(source, "type")
    if not ok or kind ~= "storage" then
      return fail("ENODEV")
    end
    local _, mode = board.invoke(source, "getMode")
    if mode ~= "hfs" then
      return fail("EINVAL") -- a raw disk: no filesystem the kernel knows
    end
    fs = hfs(source)
  end
  return vfs.mount(vfs.realpath(at) or at, fs, source)
end

-- What's mounted (Linux's /proc/mounts): {source, path, type} each.
function syscalls.mounts()
  local list = {}
  for i, mount in ipairs(vfs.mounts) do
    list[i] = {source = mount.source, path = mount.path, type = mount.fs.type}
  end
  table.sort(list, function(a, b)
    return a.path < b.path
  end)
  return list
end

function syscalls.spawn(p, request, file, argv, opts)
  if type(file) ~= "string" or (argv ~= nil and type(argv) ~= "table") then
    return fail("EINVAL")
  end
  local child, why, errno = spawn(p, file, fromuser(argv), fromuser(opts, true))
  if not child then
    return nil, why, errno
  end
  return child.pid
end

-- waitpid(pid | -1[, "nohang"]): a child that ended, reaped: its pid, how ("exited" or "killed")
-- and its status or signal (luaposix's wait); 0 with nohang when none has.
function syscalls.waitpid(p, request, pid, option)
  pid = pid or -1
  local any = false
  for _, child in ipairs(children(p)) do
    if pid == -1 or child.pid == pid then
      any = true
      if child.zombie then
        reap(child)
        return child.pid, child.status[1], child.status[2]
      end
    end
  end
  if not any then
    return fail("ECHILD")
  end
  if option == "nohang" then
    return 0
  end
  return k.BLOCK
end

function syscalls.exit(p, request, code)
  exit(p, "exited", math.type(code) == "integer" and code or 0)
  return k.BLOCK -- (never answered: it's gone)
end

function syscalls.getpid(p)
  return p.pid
end

-- kill(pid, signal): a process; a negative pid is a process group.
function syscalls.kill(p, request, pid, signal)
  signal = signal or "SIGTERM"
  if not SIGNALS[signal] or math.type(pid) ~= "integer" then
    return fail("EINVAL")
  end
  if pid < 0 then
    signalgroup(-pid, signal)
    return true
  end
  local target = processes[pid]
  if not target or target.zombie then
    return fail("ESRCH")
  end
  post(target, signal)
  return true
end

-- signal(name, handler | "ignore" | "default"): what the process does with a signal; the old one.
function syscalls.signal(p, request, signal, disposition)
  if not SIGNALS[signal] or signal == "SIGKILL" then
    return fail("EINVAL")
  end
  local old = p.handlers[signal] or "default"
  if disposition == "default" or disposition == nil then
    p.handlers[signal] = nil
  elseif disposition == "ignore" or type(disposition) == "function" then
    p.handlers[signal] = disposition
  else
    return fail("EINVAL")
  end
  return old
end

-- The devices on the bus, as the kernel knows them (it may be a moment behind): {name, id, path,
-- type, label, get, set} each, in bus order (lsdev).
function syscalls.devices()
  local list = {}
  for i, device in ipairs(devices.list) do
    list[i] = copy(device)
  end
  return list
end

-- The environment it was started with (execve's envp); the process keeps its own copy.
function syscalls.environ(p)
  return copy(p.environ)
end

-- The processes (Linux's /proc): {pid, ppid, pgid, path, argv, state} each.
function syscalls.processes()
  local list = {}
  for _, pid in ipairs(order) do
    local q = processes[pid]
    list[#list + 1] = {
      pid = q.pid, ppid = q.ppid, pgid = q.pgid, path = q.path, argv = copy(q.argv),
      state = q.zombie and "zombie" or q.blocked and "sleeping" or "running",
    }
  end
  return list
end

-- Seconds since boot (CLOCK_MONOTONIC; with no real-time clock it's the time, too, as a board
-- without an RTC starts at the epoch).
function syscalls.uptime()
  return board.uptime() / TICKS_PER_SECOND
end

-- sysinfo: the memory (the board's: the RAM installed, and what the VM holds of it), in bytes,
-- and the seconds since boot.
function syscalls.sysinfo()
  local hardware = board.hardware()
  local total, used = hardware.memoryTotal or 0, hardware.memoryUsed or 0
  return {totalram = total, usedram = used, freeram = math.max(0, total - used),
    uptime = board.uptime() / TICKS_PER_SECOND, procs = #order}
end

-- nanosleep: waits 'seconds'.
function syscalls.sleep(p, request, seconds)
  if type(seconds) ~= "number" or seconds < 0 then
    return fail("EINVAL")
  end
  now = now or board.uptime()
  request.deadline = request.deadline or now + math.ceil(seconds * TICKS_PER_SECOND)
  if now >= request.deadline then
    return true
  end
  p.wakeat = request.deadline
  return k.BLOCK
end

-- inotify() and watch(fd, path), as Linux's inotify_init and inotify_add_watch: an open file whose
-- reads are file changes, a line each, and the paths it watches (lib/modules/inotify.lua, loaded at
-- the first: until then, k.changed (tmpfs's notices) does nothing).
local inotify
function k.changed()
end

function syscalls.inotify(p)
  local fd = lowestfd(p)
  if not fd then
    return fail("EMFILE")
  end
  inotify = inotify or module("inotify", k, vfs)
  p.fds[fd] = hold(inotify.open())
  return fd
end

syscalls.watch = withfd(function(p, request, d, fd, file)
  local at = path(p, file)
  if d.type ~= "inotify" or not at then
    return fail("EINVAL")
  end
  return inotify.watch(d, at)
end)

-- Sockets, as Linux's: socket([type]) an open file (a UDP socket), bind(fd, port), sendto(fd, data,
-- address, port), recvfrom(fd) (data, address, port), and their ioctls (lib/modules/net.lua, loaded
-- at the first).
function syscalls.socket(p, request, kind)
  local fd = lowestfd(p)
  if not fd then
    return fail("EMFILE")
  end
  net = net or module("net", k, vfs)
  local d, why, errno = net.socket(kind)
  if not d then
    return nil, why, errno
  end
  p.fds[fd] = hold(d)
  return fd
end

for _, name in ipairs({"bind", "sendto", "recvfrom"}) do
  syscalls[name] = withfd(function(p, request, d, fd, ...)
    if d.type ~= "socket" then
      return nil, "Socket operation on non-socket", "ENOTSOCK"
    end
    return net[name](d, request, ...)
  end)
end

-- poll(fds, seconds), as poll(2): 'fds' maps each open file to the events wanted, a set of Linux's
-- names (IN: a read won't wait; PRI: something exceptional, as a device's state changing; OUT: a
-- write won't wait); the answer maps each file something happened to, to what (HUP: the other end
-- is gone; ERR; both come asked or not), waiting for one until 'seconds' pass (nil: as long as it
-- takes); empty: the time passed. Each file says what's true of it now (its events: a tty's line,
-- a pipe's data, a device's event or change); one that can't, a disk's file, always is readable and
-- writable, as on Linux. A device it waits on is listened to (k.listen).
local ALWAYS = {HUP = true, ERR = true}

function syscalls.poll(p, request, fds, seconds)
  if type(fds) ~= "table" or seconds ~= nil and (type(seconds) ~= "number" or seconds < 0) then
    return fail("EINVAL")
  end
  local ready = {}
  for fd, wanted in pairs(fds) do
    local d, why, errno = description(p, fd)
    if not d then
      return nil, why, errno
    elseif type(wanted) ~= "table" then
      return fail("EINVAL")
    end
    local state = d.events and d:events() or {IN = d.read ~= nil, OUT = d.write ~= nil}
    for name, on in pairs(state) do
      if on and (wanted[name] or ALWAYS[name]) then
        ready[fd] = ready[fd] or {}
        ready[fd][name] = true
      end
    end
  end
  if next(ready) or seconds == nil then
    return next(ready) and ready or k.BLOCK
  end
  now = now or board.uptime()
  request.deadline = request.deadline or now + math.ceil(seconds * TICKS_PER_SECOND)
  if now >= request.deadline then
    return ready
  end
  p.wakeat = request.deadline
  return k.BLOCK
end

-- reboot("halt" | "poweroff" | "restart"), as Linux's reboot(2) with LINUX_REBOOT_CMD_HALT,
-- _POWER_OFF and _RESTART: every process ends (its files closed: the disks get what was written),
-- then the board halts the CPU, turns the machine off, or restarts it.
local REBOOT = {halt = "halt", poweroff = "poweroff", restart = "reboot"}

function syscalls.reboot(p, request, how)
  local board_call = REBOOT[how]
  if not board_call then
    return fail("EINVAL")
  end
  kernel.respawning = false
  for _, pid in ipairs(copy(order)) do
    if processes[pid] then
      exit(processes[pid], "killed", "SIGKILL")
    end
  end
  board[board_call]()
end

-- Serves a process's syscall: it's answered (and runs on), or still blocked.
local function serve(p)
  local request = p.request
  if p.zombie or not request then
    return
  end
  -- (the reply is pcall's: true and the answer; a process only ever asks for what its sys holds)
  local reply = table.pack(pcall(syscalls[request[1]], p, request, table.unpack(request, 2, request.n)))
  if not reply[1] then
    printk("sys." .. request[1] .. ": " .. tostring(reply[2])) -- a kernel bug, not the process's
    reply = table.pack(true, fail("EINVAL"))
  elseif reply[2] == k.BLOCK then
    p.blocked = true
    return
  end
  p.blocked, p.request, p.reply, p.wakeat = false, nil, reply, nil
end

-- ---- the console and init -----------------------------------------------------------------------

-- A signal from a tty goes to its foreground process group.
local function ttysignal(t, signal)
  if t.foreground then
    signalgroup(t.foreground, signal)
  end
end

k.ttysignal = ttysignal
console = tty.new(options.console or CONSOLE, ttysignal)
k.console = console
kernel.tty = console

vfs.mount("/", hfs(root), root)
vfs.mount("/dev", devfs, "devfs")
vfs.mount("/tmp", tmpfs(), "tmpfs")

-- Disks on the bus, mounted as the kernel finds them (as udisks does: sata:1 at /mnt/sata/1, id:x at
-- /mnt/id/x) and unmounted as they go.
local function hotplug()
  local present, mounted = {[root] = true}, {}
  for _, mount in ipairs(vfs.mounts) do
    mounted[mount.source] = true
  end
  for _, device in ipairs(k.refreshdevices()) do
    local address = device.path or device.id
    if device.type == "storage" and address ~= root and device.id ~= root then
      present[address] = true
      if not mounted[address] then
        local _, mode = board.invoke(address, "getMode")
        if mode == "hfs" then
          local kind, name = address:match("^(%w+):(.+)$")
          vfs.mount(MOUNT_BASE .. "/" .. (kind or "id") .. "/" .. (name or address), hfs(address), address)
        end
      end
    end
  end
  for i = #vfs.mounts, 1, -1 do
    local mount = vfs.mounts[i]
    if mount.fs.type == "hfs" and not present[mount.source] then
      vfs.umount(mount.path, true) -- the disk's gone: what was open on it fails (EIO)
    end
  end
end

-- init runs on the console as long as the machine does: when it ends it's started again (as
-- init respawns a getty). If it can't be started, the kernel says why and tries again later.
local retryat = nil -- when to try starting it again (ticks), while it can't be
function respawn()
  if kernel.respawning == false then
    return
  end
  local p, why = spawn(kernel, options.init or INIT, {}, {pgid = 0})
  if not p then
    printk((options.init or INIT) .. ": " .. tostring(why))
    retryat = board.uptime() + RESPAWN_RETRY_TICKS
    return
  end
  retryat = nil
  p.respawn = true
  console.foreground, console.session = p.pgid, p.pid
end

-- The console as init's stdin, stdout and stderr (as Linux's init opens /dev/console).
local consolefile = k.ttyfile(console)
kernel.fds[0], kernel.fds[1], kernel.fds[2] = hold(consolefile), hold(consolefile), hold(consolefile)

-- ---- the scheduler ------------------------------------------------------------------------------

-- Runs a process until it traps, is stopped (input, the timer) or ends.
local function run(p)
  local co = p.co
  local ok, result
  if isfrozen(co) then
    ok, result = thaw(co)
  else
    ok, result = coroutine.resume(co)
  end
  if p.zombie then
    return -- (it ended itself: exit)
  end
  if isfrozen(co) then
    serve(p) -- (if it trapped; else it was stopped for input or by the timer: the handlers run first)
  elseif coroutine.status(co) == "dead" then
    if ok then
      exit(p, "exited", math.type(result) == "integer" and result or 0)
    else
      local stderr = p.fds[2]
      if stderr and stderr.write then
        stderr:write(p.path .. ": " .. tostring(result) .. "\n")
      end
      exit(p, "exited", 1)
    end
  end
  -- (it yielded at its top: it gave up its turn, sched_yield)
end

-- Devices a blocked read waits on (a sensor's events), by id: the idle wait listens to them too. A
-- read that still waits says so again (k.listen) each time it's retried.
local listening = {}
function k.listen(id)
  listening[id] = true
end

-- Idles (the CPU's HLT) until a device has something, or the soonest sleeper's time.
local function idle()
  local wake
  for _, pid in ipairs(order) do
    local p = processes[pid]
    if p.blocked and p.wakeat and (not wake or p.wakeat < wake) then
      wake = p.wakeat
    end
  end
  if retryat and (not wake or retryat < wake) then
    wake = retryat
  end
  local seconds
  if wake then
    seconds = math.max(0, wake - board.uptime()) / TICKS_PER_SECOND
  end
  local sources = {console.device, "bus"}
  for id in pairs(listening) do
    sources[#sources + 1] = id
  end
  listening = {}
  local raised = board.waitForInterrupt(seconds, table.unpack(sources))
  for _, source in ipairs(raised) do
    if source == "bus" then
      hotplug()
    end
  end
  if retryat and board.uptime() >= retryat then
    respawn()
  end
end

console:write("\27[0m\27[2J\27[H" .. "mcos " .. VERSION .. "\n")
-- The boot's script (/etc/rc, as BSD's; inittab's sysinit), run by the shell on the console to
-- its end, before init starts: it mounts what the system needs and starts its services (rc).
local function sysinit()
  local path = options.rc or RC
  local p = vfs.stat(path) and spawn(kernel, SHELL, {path}, {pgid = 0})
  if not p then
    return respawn()
  end
  p.sysinit = true
  console.foreground = p.pgid
end

hotplug()
sysinit()
while true do
  interrupt()
  now = nil
  local progressed = false
  for _, pid in ipairs(copy(order)) do
    local p = processes[pid]
    if p and not p.zombie then
      if not p.killed then
        if p.blocked then
          serve(p) -- what it waits for may have come
        end
        if not p.blocked then
          run(p)
          interrupt()
          progressed = true
        end
      end
      if p.killed then
        exit(p, "killed", p.killed) -- (nothing, if it ended meanwhile)
        progressed = true
      end
    end
  end
  if not progressed then
    idle()
  end
end
