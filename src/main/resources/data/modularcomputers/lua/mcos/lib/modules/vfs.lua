-- vfs: one tree of paths over every mounted filesystem, as Linux's VFS. A filesystem (a driver's
-- instance: hfs on a disk, devfs, ...) is mounted on a path; a path goes to the filesystem mounted on
-- its longest prefix, as a path relative to that filesystem's root. Opening a file gives an open
-- file description (what a file descriptor points to: Linux's struct file), whose read and write may
-- answer BLOCK: the caller (the kernel's syscall) waits and asks again.
--
-- Errors are Lua's convention, as io.open's: nil, the message (strerror) and the errno's name.
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
}
vfs.ERRNO = ERRNO

function vfs.fail(name)
  return nil, ERRNO[name] or name, name
end
local fail = vfs.fail

-- Mounts, longest path first: {path, fs, source, auto (mounted by the kernel for a disk it found)}.
local mounts = {}
vfs.mounts = mounts

-- A path made absolute (from 'cwd') and canonical: no ".", "..", or repeated "/".
function vfs.normalize(path, cwd)
  if string.sub(path, 1, 1) ~= "/" then
    path = (cwd or "/") .. "/" .. path
  end
  local parts = {}
  for part in string.gmatch(path, "[^/]+") do
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
  return prefix == "/" or path == prefix or string.sub(path, 1, #prefix + 1) == prefix .. "/"
end

-- The mount holding an absolute path, and the path within its filesystem.
function vfs.resolve(path)
  for _, mount in ipairs(mounts) do
    if under(path, mount.path) then
      local rest = mount.path == "/" and path or string.sub(path, #mount.path + 1)
      return mount, rest == "" and "/" or rest
    end
  end
end

function vfs.mount(path, fs, source, auto)
  for _, mount in ipairs(mounts) do
    if mount.path == path then
      return fail("EBUSY")
    end
  end
  mounts[#mounts + 1] = {path = path, fs = fs, source = source, auto = auto}
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
      local rest = string.sub(mount.path, path == "/" and 2 or #path + 2)
      names[string.match(rest, "^[^/]+")] = true
    end
  end
  return names
end

-- stat: {type = "file" | "directory" | "device" | "fifo", size, modified, mount = its mount's path}.
function vfs.stat(path)
  local mount, rel = vfs.resolve(path)
  if not mount then
    return fail("ENOENT")
  end
  local st, why, errno = mount.fs:stat(rel)
  if not st and next(mountpoints(path)) then
    st = {type = "directory", size = 0, modified = 0}
  end
  if st then
    st.mount = mount.path
    return st
  end
  return nil, why, errno
end

-- A directory's entries, sorted: {name, type} each.
function vfs.list(path)
  local mount, rel = vfs.resolve(path)
  local entries, why, errno
  if mount then
    entries, why, errno = mount.fs:list(rel)
  end
  local extra = mountpoints(path)
  if not entries then
    if not next(extra) then
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

-- A directory's open description: its entries, read once (getdents).
local function directory(entries)
  return {
    type = "directory",
    getdents = function(self)
      local all = self.entries
      self.entries = {}
      return all
    end,
    entries = entries,
  }
end

-- open: an open file description; mode is fopen's (r, w, a, r+, w+, a+). Its 'mount' counts it.
function vfs.open(path, mode, process)
  local mount, rel = vfs.resolve(path)
  if not mount then
    return fail("ENOENT")
  end
  local st = vfs.stat(path)
  local description, why, errno
  if st and st.type == "directory" then
    if mode ~= "r" then
      return fail("EISDIR")
    end
    local entries
    entries, why, errno = vfs.list(path)
    if not entries then
      return nil, why, errno
    end
    description = directory(entries)
  else
    description, why, errno = mount.fs:open(rel, mode, process)
    if not description then
      return nil, why, errno
    end
  end
  description.path = path
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

local function isroot(path)
  for _, mount in ipairs(mounts) do
    if mount.path == path then
      return true
    end
  end
  return false
end

function vfs.mkdir(path)
  if vfs.stat(path) then
    return fail("EEXIST")
  end
  local mount, rel = vfs.resolve(path)
  return mount.fs:mkdir(rel)
end

-- unlink (a file) or rmdir (a directory: 'directory' true).
function vfs.remove(path, directory)
  local st, why, errno = vfs.stat(path)
  if not st then
    return nil, why, errno
  end
  if isroot(path) then
    return fail("EBUSY")
  end
  if directory and st.type ~= "directory" then
    return fail("ENOTDIR")
  elseif not directory and st.type == "directory" then
    return fail("EISDIR")
  end
  local mount, rel = vfs.resolve(path)
  return mount.fs:remove(rel)
end

function vfs.rename(from, to)
  local st, why, errno = vfs.stat(from)
  if not st then
    return nil, why, errno
  end
  if isroot(from) or isroot(to) then
    return fail("EBUSY")
  end
  local a, relfrom = vfs.resolve(from)
  local b, relto = vfs.resolve(to)
  if a ~= b then
    return fail("EXDEV") -- mv copies, as across filesystems in Linux
  end
  return a.fs:rename(relfrom, relto)
end

-- statfs: {type, size, used} of the filesystem holding a path.
function vfs.statfs(path)
  local mount = vfs.resolve(path)
  if not mount then
    return fail("ENOENT")
  end
  return mount.fs:statfs()
end

-- A whole file's contents (the kernel's own reads: its modules, a program to run).
function vfs.readfile(path)
  local description, why, errno = vfs.open(path, "r")
  if not description then
    return nil, why, errno
  end
  if description.type == "directory" then
    vfs.close(description)
    return fail("EISDIR")
  end
  local parts = {}
  while true do
    local chunk, err, code = description:read(k.READ_SIZE)
    if chunk == nil or chunk == "" or chunk == k.BLOCK then
      if err then
        vfs.close(description)
        return nil, err, code
      end
      break
    end
    parts[#parts + 1] = chunk
  end
  vfs.close(description)
  return table.concat(parts)
end

return vfs
