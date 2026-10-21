-- vfs: one tree of paths over every mounted filesystem, as Linux's VFS. A filesystem (a driver's
-- instance: hfs on a disk, devfs, ...) is mounted on a path; a path goes to the filesystem mounted on
-- its longest prefix, as a path relative to that filesystem's root. Opening a file gives an open
-- file description (what a file descriptor points to: Linux's struct file), whose read and write may
-- answer BLOCK: the caller (the kernel's syscall) waits and asks again.
--
-- Errors are Lua's convention, as io.open's: nil, the message (strerror) and the errno's name.
--
-- Symbolic links: the VFS follows them, as Linux's does (a target can be on any filesystem). A
-- filesystem never follows one itself: a call that meets one (on the way, or at the end for a call
-- that follows it) fails with errno REPARSE and {at = the link's path in it, target = its text},
-- and the VFS goes on from the target, at most MAX_LINKS times (ELOOP). '..' is taken lexically,
-- before links are (as a shell's logical paths do; Linux takes it from where a link led).
local k = ...

local vfs = {}

-- errno: names as Linux's, messages as its strerror.
local ERRNO = {
  EPERM = "Operation not permitted",
  ENOENT = "No such file or directory",
  ESRCH = "No such process",
  EINTR = "Interrupted system call",
  EIO = "Input/output error",
  EBADF = "Bad file descriptor",
  ECHILD = "No child processes",
  EACCES = "Permission denied",
  EBUSY = "Device or resource busy",
  EEXIST = "File exists",
  EXDEV = "Invalid cross-device link",
  ENODEV = "No such device",
  ENOTDIR = "Not a directory",
  EISDIR = "Is a directory",
  EINVAL = "Invalid argument",
  EMFILE = "Too many open files",
  ENOTTY = "Inappropriate ioctl for device",
  ENOSPC = "No space left on device",
  ESPIPE = "Illegal seek",
  EPIPE = "Broken pipe",
  ENOSYS = "Function not implemented",
  ENOTEMPTY = "Directory not empty",
  ENOEXEC = "Exec format error",
  ELOOP = "Too many levels of symbolic links",
  ENAMETOOLONG = "File name too long",
}

function vfs.fail(name)
  return nil, ERRNO[name] or name, name
end
local fail = vfs.fail

local MAX_LINKS = 40 -- links followed in one lookup (Linux's MAXSYMLINKS)
local REPARSE = "REPARSE" -- a filesystem's errno for a call that met a link

-- Mounts, longest path first: {path, fs, source}.
local mounts = {}
vfs.mounts = mounts

-- A path made absolute (from 'cwd') and canonical: no ".", "..", or repeated "/".
function vfs.normalize(path, cwd)
  if path:sub(1, 1) ~= "/" then
    path = (cwd or "/") .. "/" .. path
  end
  local parts = {}
  for part in path:gmatch("[^/]+") do
    if part == ".." then
      parts[#parts] = nil
    elseif part ~= "." then
      parts[#parts + 1] = part
    end
  end
  return "/" .. table.concat(parts, "/")
end

-- Whether 'path' is 'prefix' or under it.
local function under(path, prefix)
  return prefix == "/" or path == prefix or path:sub(1, #prefix + 1) == prefix .. "/"
end

-- The mount holding an absolute path, and the path within its filesystem.
function vfs.resolve(path)
  for _, mount in ipairs(mounts) do
    if under(path, mount.path) then
      local rest = mount.path == "/" and path or path:sub(#mount.path + 1)
      return mount, rest == "" and "/" or rest
    end
  end
end

function vfs.mount(path, fs, source)
  if select(2, vfs.resolve(path)) == "/" then
    return fail("EBUSY") -- (something's mounted there already)
  end
  mounts[#mounts + 1] = {path = path, fs = fs, source = source}
  fs.at = path -- (a filesystem that reports its own changes, tmpfs, names them by it)
  table.sort(mounts, function(a, b)
    return #a.path > #b.path
  end)
  return true
end

-- Takes a mount away; 'force' even with files open on it (they fail from then on: EIO).
function vfs.umount(path, force)
  for i, mount in ipairs(mounts) do
    if mount.path == path then
      if (mount.open or 0) > 0 and not force then
        return fail("EBUSY")
      end
      table.remove(mounts, i)
      mount.gone = true
      return true
    end
  end
  return fail("EINVAL")
end

-- The names of mount points directly under 'path' (a mount point needs no directory beneath it:
-- the kernel shows one, as if made for it).
local function mountpoints(path)
  local names = {}
  for _, mount in ipairs(mounts) do
    if mount.path ~= path and under(mount.path, path) then
      local rest = mount.path:sub(path == "/" and 2 or #path + 2)
      names[rest:match("^[^/]+")] = true
    end
  end
  return names
end

-- Runs 'attempt(mount, relative path, path)' on the filesystem holding 'path', going on from the
-- target of each link it meets: the path it ended on (links followed), then what attempt gave.
local function lookup(path, attempt)
  for _ = 0, MAX_LINKS do
    local mount, rel = vfs.resolve(path)
    if not mount then
      return path, fail("ENOENT")
    end
    local results = table.pack(attempt(mount, rel, path))
    if results[1] ~= nil or results[3] ~= REPARSE then
      return path, table.unpack(results, 1, results.n)
    end
    local link = results[2]
    local at = mount.path == "/" and link.at or mount.path .. link.at
    local rest = rel:sub(#link.at + 1)
    local target = link.target
    if target:sub(1, 1) ~= "/" then
      target = (at:match("^(.*)/[^/]*$") or "") .. "/" .. target
    end
    path = vfs.normalize(target .. rest)
  end
  return path, fail("ELOOP")
end

-- stat: {type = "file" | "directory" | "link" | "device" | "fifo", size, modified, target (a link's),
-- mount = its mount's path, path = where it is with links followed}. 'nofollow': a link itself (lstat).
local function stat(path, nofollow)
  return lookup(path, function(mount, rel, at)
    local st, why, errno = mount.fs:stat(rel, nofollow)
    if not st and errno ~= REPARSE and next(mountpoints(at)) then
      st = {type = "directory", size = 0, modified = 0}
    end
    if st then
      st.mount = mount.path
      st.path = at
    end
    return st, why, errno
  end)
end

-- The path with its directory's links followed and its last name kept (for calls on a name itself:
-- unlink, rename, mkdir, symlink, lstat); nil and the error if the directory isn't there.
local function parentfollowed(path)
  if path == "/" then
    return path
  end
  local dir, name = path:match("^(.*)/([^/]+)$")
  local real, st, why, errno = stat(dir == "" and "/" or dir)
  if not st then
    return nil, why, errno
  elseif st.type ~= "directory" then
    return fail("ENOTDIR")
  end
  return (real == "/" and "" or real) .. "/" .. name
end

function vfs.stat(path)
  return select(2, stat(path))
end

function vfs.lstat(path)
  local at, why, errno = parentfollowed(path)
  if not at then
    return nil, why, errno
  end
  return select(2, stat(at, true))
end

-- realpath: the path with every link followed; it must exist.
function vfs.realpath(path)
  local real, st, why, errno = stat(path)
  if not st then
    return nil, why, errno
  end
  return real
end

-- A directory's entries, sorted: {name, type} each.
function vfs.list(path)
  local real, entries, why, errno = lookup(path, function(mount, rel)
    return mount.fs:list(rel)
  end)
  local extra = mountpoints(real)
  if not entries then
    if errno == "ELOOP" or not next(extra) then
      return nil, why, errno
    end
    entries = {}
  end
  local seen = {}
  for _, entry in ipairs(entries) do
    seen[entry.name] = true
  end
  for name in pairs(extra) do
    if not seen[name] then
      entries[#entries + 1] = {name = name, type = "directory"}
    end
  end
  table.sort(entries, function(a, b)
    return a.name < b.name
  end)
  return entries
end

-- open: an open file description; mode is fopen's (r, w, a, r+, w+, a+). Its 'mount' counts it.
-- Links are followed (a link to nothing yet makes its target, as O_CREAT does). The mode is enforced
-- here, for every filesystem and device: a description opened only to read loses its write, one
-- opened only to write its read, so the syscall says EBADF (as Linux's file mode checks); a
-- filesystem's open only does what the mode means for it (making, truncating, appending).
function vfs.open(path, mode, process)
  local real, st, why, errno = stat(path)
  if not st and errno ~= "ENOENT" then
    return nil, why, errno
  end
  local description, mount
  if st and st.type == "directory" then
    if mode ~= "r" then
      return fail("EISDIR")
    end
    local entries
    entries, why, errno = vfs.list(real)
    if not entries then
      return nil, why, errno
    end
    -- a directory's entries, read once (getdents)
    description, mount = {type = "directory", getdents = function(self)
      local all = entries
      entries = {}
      return all
    end}, vfs.resolve(real)
  else
    real, description, why, errno = lookup(real, function(m, rel)
      mount = m
      return m.fs:open(rel, mode, process)
    end)
    if not description then
      return nil, why, errno
    end
    local plus = mode:find("+", 1, true)
    if mode:sub(1, 1) == "r" and not plus then
      description.write = nil
    elseif mode:sub(1, 1) ~= "r" and not plus then
      description.read = nil
    end
  end
  description.path = real
  description.mount = mount
  mount.open = (mount.open or 0) + 1
  return description
end

-- Closes a description (the last file descriptor on it went).
function vfs.close(description)
  local mount = description.mount
  if mount then
    mount.open = mount.open - 1
  end
  if description.close and not (mount and mount.gone) then
    return description:close()
  end
  return true
end

-- A call on a name itself (its directory's links followed, not a link it names): its filesystem
-- and the path in it (which is "/" for a mount point), or nil and the error; 'new': a name to make,
-- so EEXIST if something's there.
local function named(path, new)
  local at, why, errno = parentfollowed(path)
  if not at then
    return nil, why, errno
  end
  local mount, rel = vfs.resolve(at)
  if not mount then
    return fail("ENOENT")
  elseif new and select(2, stat(at, true)) then
    return fail("EEXIST")
  end
  return mount, rel, at
end

function vfs.mkdir(path)
  local mount, rel, at = named(path, true)
  if not mount then
    return nil, rel, at
  end
  return mount.fs:mkdir(rel)
end

-- unlink (a file or a link: not what it points to) or rmdir (a directory: 'directory' true).
function vfs.remove(path, directory)
  local mount, rel, at = named(path)
  if not mount then
    return nil, rel, at
  end
  local _, st, why, errno = stat(at, true)
  if not st then
    return nil, why, errno
  end
  if rel == "/" then
    return fail("EBUSY") -- (a mount point)
  elseif directory and st.type ~= "directory" then
    return fail("ENOTDIR")
  elseif not directory and st.type == "directory" then
    return fail("EISDIR")
  end
  return mount.fs:remove(rel)
end

-- rename: moves a name (a link moves as itself).
function vfs.rename(from, to)
  local a, relfrom, atfrom = named(from)
  if not a then
    return nil, relfrom, atfrom
  end
  local b, relto, atto = named(to)
  if not b then
    return nil, relto, atto
  end
  local _, st, why, errno = stat(atfrom, true)
  if not st then
    return nil, why, errno
  end
  if relfrom == "/" or relto == "/" then
    return fail("EBUSY")
  elseif a ~= b then
    return fail("EXDEV") -- mv copies, as across filesystems in Linux
  end
  return a.fs:rename(relfrom, relto)
end

-- symlink: a link at 'path' to 'target' (any text: it's followed when used, so it may lead nowhere).
function vfs.symlink(target, path)
  if target == "" then
    return fail("ENOENT")
  end
  local mount, rel, at = named(path, true)
  if not mount then
    return nil, rel, at
  elseif not mount.fs.symlink then
    return fail("EPERM") -- (a filesystem with no links)
  end
  return mount.fs:symlink(target, rel)
end

-- link: a hard link (another name for the same file). No filesystem here has them (as FAT): EPERM,
-- once the names are good (EXDEV first across filesystems, as Linux checks it).
function vfs.link(from, to)
  local mount, rel, at = named(to, true)
  if not mount then
    return nil, rel, at
  end
  local st, why, errno = vfs.stat(from)
  if not st then
    return nil, why, errno
  end
  return fail(st.mount ~= mount.path and "EXDEV" or "EPERM")
end

-- readlink: a link's target.
function vfs.readlink(path)
  local st, why, errno = vfs.lstat(path)
  if not st then
    return nil, why, errno
  elseif st.type ~= "link" then
    return fail("EINVAL")
  end
  return st.target
end

-- statfs: {type, size, used} of the filesystem holding a path.
function vfs.statfs(path)
  local mount = vfs.resolve(vfs.realpath(path) or path)
  if not mount then
    return fail("ENOENT")
  end
  return mount.fs:statfs()
end

-- A whole file's contents, as the pieces read (the kernel's own reads: a program to compile, which
-- needn't be joined first; never a directory).
function vfs.readparts(path)
  local description, why, errno = vfs.open(path, "r")
  if not description then
    return nil, why, errno
  end
  local parts, chunk = {}, nil
  repeat
    chunk, why, errno = description:read(k.READ_SIZE)
    parts[#parts + 1] = type(chunk) == "string" and chunk ~= "" and chunk or nil
  until type(chunk) ~= "string" or chunk == ""
  vfs.close(description)
  if why then
    return nil, why, errno
  end
  return parts
end

return vfs
