-- mcos (Lua): the kernel. The bootloader hands it its boot disk's address, then its command line
-- (words: console=<address> names the input device its console tty reads, the board's console by
-- default; init=<path> the program it starts on it, /bin/sh by default).
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
local LOADER = "/lib/ld.lua" -- where every process starts (an ELF program's PT_INTERP): every program is Lua, native to this CPU
local MODULES = "/lib/modules/" -- the kernel's own parts
-- When init can't be started (its file missing or broken), how long before the kernel tries again.
local RESPAWN_RETRY_TICKS = 100 -- 5 seconds
local READ_SIZE = 4096 -- the most one read(2) of a file takes (a disk's most)
local MAX_FDS = 64 -- file descriptors a process may hold (RLIMIT_NOFILE)
local TICKS_PER_SECOND = 20
local MOUNT_BASE = "/mnt" -- where disks found on the bus are mounted (sata:1 at /mnt/sata/1, id:x at /mnt/id/x)

local options = {}
for word in string.gmatch(cmdline or "", "%S+") do
  local key, value = string.match(word, "^([^=]+)=(.*)$")
  options[key or word] = value or true
end

local isfrozen, thaw = debug.isfrozen, debug.thaw
local trap = board.trap

-- What the kernel's parts share.
local k = {
  READ_SIZE = READ_SIZE,
  BLOCK = setmetatable({}, {__name = "BLOCK"}), -- a description's answer: try again when things change
}

-- The boot disk's files, before the VFS is up: the kernel's modules.
local function bootfile(path)
  local opened, file = board.invoke(root, "open", path)
  if not opened then
    return nil, file
  end
  local parts = {}
  while true do
    local ok, chunk = board.invoke(file, "read", READ_SIZE)
    if not ok or chunk == nil then
      break
    end
    parts[#parts + 1] = chunk
  end
  board.invoke(file, "close")
  return table.concat(parts)
end

local function module(name, ...)
  local path = MODULES .. name .. ".lua"
  local code, why = bootfile(path)
  if not code then
    error(path .. ": " .. tostring(why), 0)
  end
  return assert(load(code, "=" .. path))(...)
end

local vfs = module("vfs", k)
local fail = vfs.fail
local tty = module("tty")
local hfs = module("hfs", k, vfs)
local devfs = module("devfs", k, vfs)
local newpipe = module("pipe", k, vfs)
local tmpfs = module("tmpfs", k, vfs)

-- The bus as the kernel last read it: each device's name (its type and its place among those of its
-- type in bus order: storage0, eeprom0, as Linux's sda, ttyS0), id (the address it's used by, whatever
-- slot or cable it's on), slot path (nil when it isn't in one), type, label, and whether it has a
-- get and a set (data /dev can read and write). It may be out of date, and is correct eventually
-- (as Linux's /dev, which udev fills in after the fact): it's read again when the bus's hot-plug
-- interrupt comes and when a call to a device fails because the device is gone, never on a /dev
-- lookup. Nothing depends on it being fresh: an open file holds a device's id, so it reaches that
-- device or fails (ENODEV), never another one.
local devices = {list = {}, byname = {}}
k.devices = devices
local described = {} -- by id: {label, get, set}, asked once a device (an id never changes what it is)

function k.refreshdevices()
  local list, byname, counts = {}, {}, {}
  for _, device in ipairs(board.list()) do
    local kind = string.gsub(device.type or "device", "[^%w]", "")
    local n = counts[kind] or 0
    counts[kind] = n + 1
    local id = device.id or device.address
    local about = described[id]
    if not about then
      local info = board.info(device.address) or {}
      about = {label = info.label}
      for _, method in ipairs(board.methods(device.address) or {}) do
        if method == "get" or method == "set" then
          about[method] = true
        end
      end
      described[id] = about
    end
    local slot = device.address ~= device.id and not string.find(device.address, "^id:") and device.address or nil
    local entry = {name = kind .. n, id = id, path = slot,
      type = device.type, label = about.label, get = about.get, set = about.set}
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

-- The handlers drivers register (a tty's line discipline for its device's ring), run whenever the
-- kernel gets control: a process stopped for input or by the timer, a syscall, waking from idle.
local handlers = {}

local function interrupt()
  for i = 1, #handlers do
    handlers[i]()
  end
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
  p.zombie, p.status = true, {how, value}
  p.request, p.reply, p.blocked = nil, nil, false
  for _, child in ipairs(children(p)) do
    child.ppid = 0
  end
  if p.ppid == 0 then
    -- the kernel's own child: reaped at once
    processes[p.pid] = nil
    for i, pid in ipairs(order) do
      if pid == p.pid then
        table.remove(order, i)
        break
      end
    end
    if p.respawn then
      respawn()
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
      p.reply = table.pack(fail("EINTR"))
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
  local sys = {}
  for name in pairs(syscalls) do
    sys[name] = function(...)
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
      return table.unpack(reply, 1, reply.n)
    end
  end
  sys.SIGNALS = SIGNALS
  return sys
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
local images = setmetatable({}, {__mode = "v"})
local versions = {} -- by path: what the file was (size, modified) when its image was made

local function image(path)
  local st, why, errno = vfs.stat(path)
  if not st then
    return nil, why, errno
  elseif st.type == "directory" then
    return fail("EISDIR")
  end
  path = st.path -- (by where links lead: one image for every name of a file)
  local version = st.size .. ":" .. tostring(st.modified)
  local cached = images[path]
  if cached and versions[path] == version then
    return cached
  end
  reclaim() -- (compiling takes far more for a moment than the image keeps)
  local code
  code, why, errno = vfs.readfile(path)
  if not code then
    return nil, why, errno
  end
  code = string.gsub(code, "^#![^\n]*", "") -- a #! line is the kernel's (its line stays, empty)
  local chunk
  chunk, why = load("return function(_ENV, ...) " .. code .. "\nend", "=" .. path, "t", {})
  if not chunk then
    return nil, why, "ENOEXEC"
  end
  cached = chunk()
  images[path], versions[path] = cached, version
  return cached
end

-- posix_spawn: a new process running 'path' (through the loader) with 'argv', as the parent's
-- child. opts: fds (the child's descriptors: {[0] = a parent fd, ...}; 0, 1 and 2 are inherited
-- when not given, the others aren't: close-on-exec), env (its environment; the parent's when not
-- given), ignore (signals it starts ignoring, as an async list's SIGINT), pgid (0: a group of its
-- own), cwd.
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
  if opts.pgid == 0 then
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
    local description = parent.fds[from]
    if math.type(fd) ~= "integer" or fd < 0 or fd >= MAX_FDS or not description then
      for _, held in pairs(p.fds) do
        release(held)
      end
      return fail("EBADF")
    end
    p.fds[fd] = hold(description)
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
local function clock()
  now = now or board.uptime()
  return now
end

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
  local d, why, errno = vfs.open(at, string.gsub(mode, "b", ""), p)
  if not d then
    return nil, why, errno
  end
  p.fds[fd] = hold(d)
  return fd
end

function syscalls.close(p, request, fd)
  local d, why, errno = description(p, fd)
  if not d then
    return nil, why, errno
  end
  p.fds[fd] = nil
  return release(d)
end

function syscalls.read(p, request, fd, n)
  local d, why, errno = description(p, fd)
  if not d then
    return nil, why, errno
  elseif not d.read then
    return fail(d.type == "directory" and "EISDIR" or "EBADF")
  end
  n = math.type(n) == "integer" and n > 0 and n or READ_SIZE
  return d:read(n)
end

-- Writes all of 'data', waiting for room as long as it takes (a pipe's reader); the count written.
function syscalls.write(p, request, fd, data)
  local d, why, errno = description(p, fd)
  if not d then
    return nil, why, errno
  elseif not d.write then
    return fail("EBADF")
  end
  if type(data) == "number" then
    data = tostring(data)
  elseif type(data) ~= "string" then
    return fail("EINVAL")
  end
  request.done = request.done or 0
  while request.done < #data do
    local count
    count, why, errno = d:write(request.done == 0 and data or string.sub(data, request.done + 1))
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
end

function syscalls.lseek(p, request, fd, offset, whence)
  local d, why, errno = description(p, fd)
  if not d then
    return nil, why, errno
  elseif not d.seek then
    return fail("ESPIPE")
  end
  return d:seek(whence or "set", offset or 0)
end

function syscalls.getdents(p, request, fd)
  local d, why, errno = description(p, fd)
  if not d then
    return nil, why, errno
  elseif not d.getdents then
    return fail("ENOTDIR")
  end
  return d:getdents()
end

function syscalls.dup(p, request, fd, from)
  local d, why, errno = description(p, fd)
  if not d then
    return nil, why, errno
  end
  local new = lowestfd(p, from)
  if not new then
    return fail("EMFILE")
  end
  p.fds[new] = hold(d)
  return new
end

function syscalls.dup2(p, request, fd, to)
  local d, why, errno = description(p, fd)
  if not d then
    return nil, why, errno
  elseif math.type(to) ~= "integer" or to < 0 or to >= MAX_FDS then
    return fail("EBADF")
  end
  if fd ~= to then
    hold(d)
    if p.fds[to] then
      release(p.fds[to])
    end
    p.fds[to] = d
  end
  return to
end

function syscalls.pipe(p)
  local r = lowestfd(p)
  if not r then
    return fail("EMFILE")
  end
  local reader, writer = newpipe()
  p.fds[r] = hold(reader)
  local w = lowestfd(p)
  if not w then
    p.fds[r] = nil
    return fail("EMFILE")
  end
  p.fds[w] = hold(writer)
  return r, w
end

-- isatty, and the terminal's ioctls by their Linux names: TCGETS and TCSETS (termios flags, as a
-- table), TIOCGWINSZ (columns, rows), TIOCGPGRP and TIOCSPGRP (its foreground process group),
-- TIOCSTI (bytes put in as if typed).
function syscalls.ioctl(p, request, fd, op, arg)
  local d, why, errno = description(p, fd)
  if not d then
    return nil, why, errno
  elseif d.type ~= "tty" then
    return fail("ENOTTY")
  end
  local t = d.tty
  if op == "TCGETS" then
    return t:getattr()
  elseif op == "TCSETS" and type(arg) == "table" then
    t:setattr(fromuser(arg))
    return true
  elseif op == "TIOCGWINSZ" then
    return t:size()
  elseif op == "TIOCGPGRP" then
    return t.foreground
  elseif op == "TIOCSPGRP" and math.type(arg) == "integer" then
    t.foreground = arg
    return true
  elseif op == "TIOCSTI" and type(arg) == "string" then
    t:receive(arg) -- input, as if typed (Linux's TIOCSTI): a shell gives back what it read ahead
    return true
  end
  return fail("EINVAL")
end

function syscalls.isatty(p, request, fd)
  local d = description(p, fd)
  return d ~= nil and d.type == "tty"
end

function syscalls.stat(p, request, file)
  local at = path(p, file)
  if not at then
    return fail("EINVAL")
  end
  return vfs.stat(at)
end

-- lstat: a link itself, not what it points to (type "link", its target).
function syscalls.lstat(p, request, file)
  local at = path(p, file)
  if not at then
    return fail("EINVAL")
  end
  return vfs.lstat(at)
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

-- link(from, to): a hard link.
function syscalls.link(p, request, from, to)
  local a, b = path(p, from), path(p, to)
  if not (a and b) then
    return fail("EINVAL")
  end
  return vfs.link(a, b)
end

function syscalls.readlink(p, request, file)
  local at = path(p, file)
  if not at then
    return fail("EINVAL")
  end
  return vfs.readlink(at)
end

-- realpath: an absolute path with every link followed.
function syscalls.realpath(p, request, file)
  local at = path(p, file)
  if not at then
    return fail("EINVAL")
  end
  return vfs.realpath(at)
end

function syscalls.mkdir(p, request, file)
  local at = path(p, file)
  if not at then
    return fail("EINVAL")
  end
  return vfs.mkdir(at)
end

function syscalls.unlink(p, request, file)
  local at = path(p, file)
  if not at then
    return fail("EINVAL")
  end
  return vfs.remove(at, false)
end

function syscalls.rmdir(p, request, file)
  local at = path(p, file)
  if not at then
    return fail("EINVAL")
  end
  return vfs.remove(at, true)
end

function syscalls.rename(p, request, from, to)
  local a, b = path(p, from), path(p, to)
  if not (a and b) then
    return fail("EINVAL")
  end
  return vfs.rename(a, b)
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

function syscalls.umount(p, request, target)
  local at = path(p, target)
  if not at then
    return fail("EINVAL")
  end
  return vfs.umount(at)
end

-- What's mounted (Linux's /proc/mounts): {source, path, type} each.
function syscalls.mounts()
  local list = {}
  for i = #vfs.mounts, 1, -1 do
    local mount = vfs.mounts[i]
    list[#list + 1] = {source = mount.source, path = mount.path, type = mount.fs.type}
  end
  table.sort(list, function(a, b)
    return a.path < b.path
  end)
  return list
end

function syscalls.statfs(p, request, file)
  local at = path(p, file or "/")
  if not at then
    return fail("EINVAL")
  end
  return vfs.statfs(at)
end

function syscalls.spawn(p, request, file, argv, opts)
  if type(file) ~= "string" then
    return fail("EINVAL")
  end
  if argv ~= nil and type(argv) ~= "table" then
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
        processes[child.pid] = nil
        for i, id in ipairs(order) do
          if id == child.pid then
            table.remove(order, i)
            break
          end
        end
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

function syscalls.getppid(p)
  return p.ppid
end

function syscalls.getpgid(p)
  return p.pgid
end

function syscalls.setpgid(p, request, pgid)
  p.pgid = math.type(pgid) == "integer" and pgid > 0 and pgid or p.pid
  return true
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

-- The environment it was started with (execve's envp); the process keeps its own copy.
-- A Lua file's shared image (see images): call it with the globals to run it in, and its arguments.
-- Its errors are a compile's: nil, the message and ENOEXEC.
function syscalls.image(p, request, file)
  local at = path(p, file)
  if not at then
    return fail("EINVAL")
  end
  return image(at)
end

-- The devices on the bus, as the kernel knows them (it may be a moment behind): {name, id, path,
-- type, label} each, in bus order (lsdev).
function syscalls.devices()
  local list = {}
  for i, device in ipairs(devices.list) do
    list[i] = {name = device.name, id = device.id, path = device.path, type = device.type, label = device.label}
  end
  return list
end

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
  local now = clock()
  request.deadline = request.deadline or now + math.ceil(seconds * TICKS_PER_SECOND)
  if now >= request.deadline then
    return true
  end
  p.wakeat = request.deadline
  return k.BLOCK
end

-- Ends every process, closing what it held (the disks get what was written), as a shutdown does.
local function shutdown()
  for _, pid in ipairs(copy(order)) do
    local p = processes[pid]
    if p then
      exit(p, "killed", "SIGKILL")
    end
  end
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
  shutdown()
  board[board_call]()
end

-- Serves a process's syscall: it's answered (and runs on), or still blocked.
local function serve(p)
  local request = p.request
  if p.zombie or not request then
    return
  end
  local call = syscalls[request[1]]
  local reply
  if call then
    reply = table.pack(pcall(call, p, request, table.unpack(request, 2, request.n)))
    if not reply[1] then
      printk("sys." .. request[1] .. ": " .. tostring(reply[2])) -- a kernel bug, not the process's
      reply = table.pack(fail("EINVAL"))
    elseif reply[2] == k.BLOCK then
      p.blocked = true
      return
    else
      reply = table.pack(table.unpack(reply, 2, reply.n))
    end
  else
    reply = table.pack(fail("ENOSYS"))
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

console = tty.new(options.console or CONSOLE, ttysignal)
k.console = console
handlers[#handlers + 1] = function()
  console:pump()
end
kernel.tty = console

vfs.mount("/", hfs(root), root)
vfs.mount("/dev", devfs, "devfs")
vfs.mount("/tmp", tmpfs(), "tmpfs")

-- Disks on the bus, mounted as the kernel finds them (as udisks does) and unmounted as they go.
local function mountpoint(address)
  local kind, name = string.match(address, "^(%w+):(.+)$")
  return MOUNT_BASE .. "/" .. (kind or "id") .. "/" .. (name or address)
end

local function hotplug()
  local present = {[root] = true}
  for _, device in ipairs(k.refreshdevices()) do
    local address = device.path or device.id
    if device.type == "storage" and address ~= root and device.id ~= root then
      present[address] = true
      local mounted = false
      for _, mount in ipairs(vfs.mounts) do
        if mount.source == address then
          mounted = true
        end
      end
      if not mounted then
        local _, mode = board.invoke(address, "getMode")
        if mode == "hfs" then
          vfs.mount(mountpoint(address), hfs(address), address, true)
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
  console.foreground = p.pgid
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
    if p.request then
      serve(p)
    end
    -- else stopped for input or by the timer: the handlers run before anything else does
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
  local raised = board.waitForInterrupt(seconds, console.device, "bus")
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
hotplug()
respawn()
while true do
  interrupt()
  now = nil
  local progressed = false
  for _, pid in ipairs(copy(order)) do
    local p = processes[pid]
    if p and not p.zombie then
      if p.killed then
        exit(p, "killed", p.killed)
        progressed = true
      else
        if p.blocked then
          serve(p) -- what it waits for may have come
        end
        if not p.blocked then
          run(p)
          interrupt()
          if p.killed and not p.zombie then
            exit(p, "killed", p.killed)
          end
          progressed = true
        end
      end
    end
  end
  if not progressed then
    idle()
  end
end
