-- hfs: the filesystem driver for a disk in hfs mode (the disk serves its files itself, as 9P: the
-- driver only passes calls on). Returns a constructor: hfs(address) is the filesystem on that disk.
local k, vfs = ...

local fail = vfs.fail

-- What the disk's failures mean, as errnos (its messages are its own): pattern, errno, pattern...
local FAILURES = {
  "^no such file", "ENOENT",
  "^already exists", "EEXIST",
  "^directory not empty", "ENOTEMPTY",
  "^is a directory", "EISDIR",
  "^too many open files", "EMFILE",
  "^No space left", "ENOSPC",
  "^can't move a directory into itself", "EINVAL",
  "^bad ", "EINVAL",
  "^outside the disk", "EINVAL",
  "^raw disk", "ENODEV",
  "^no storage", "ENODEV",
  "^can't remove the root", "EBUSY",
  "^closed handle", "EBADF",
  "^not open for", "EBADF",
  "^no such device", "ENODEV",
  "^name too long", "ENAMETOOLONG",
}

local REPARSE = "symbolic link\0" -- the disk met a link: its path, a NUL, its target

local function failed(why)
  why = tostring(why)
  if string.sub(why, 1, #REPARSE) == REPARSE then
    local at, target = string.match(why, "^([^%z]*)%z(.*)$", #REPARSE + 1)
    return nil, {at = at, target = target}, "REPARSE"
  end
  for i = 1, #FAILURES, 2 do
    if string.find(why, FAILURES[i]) then
      return fail(FAILURES[i + 1])
    end
  end
  return fail("EIO")
end

-- A device call: its values, or the failure as an errno.
local function call(target, method, ...)
  local result = table.pack(board.invoke(target, method, ...))
  if not result[1] then
    return failed(result[2])
  end
  return table.unpack(result, 2, result.n)
end

-- A call answered with nothing but success: true, or the failure.
local function done(_, why, errno)
  if why then
    return nil, why, errno
  end
  return true
end

local fs = {}
fs.__index = fs

local function file(handle)
  return {
    type = "file",
    read = function(self, n)
      local ok, data = board.invoke(handle, "read", math.min(n, k.READ_SIZE))
      if not ok then
        return failed(data)
      end
      return data or ""
    end,
    write = function(self, data)
      local ok, why = board.invoke(handle, "write", data)
      if not ok then
        return failed(why)
      end
      return #data
    end,
    seek = function(self, whence, offset)
      return call(handle, "seek", whence, offset)
    end,
    close = function(self)
      return done(call(handle, "close")) -- (commits what was written, at once)
    end,
  }
end

function fs:open(path, mode)
  local handle, why, errno = call(self.address, "open", path, mode)
  if not handle then
    return nil, why, errno
  end
  return file(handle)
end

function fs:stat(path, nofollow)
  local st, why, errno = call(self.address, "stat", path, nofollow or nil)
  if st == nil then
    if why then
      return nil, why, errno
    end
    return fail("ENOENT")
  end
  return {type = st.link and "link" or st.directory and "directory" or "file", size = st.size,
    modified = st.modified, target = st.link}
end

function fs:list(path)
  return call(self.address, "list", path, true)
end

-- mkdir(path), remove(path), rename(from, to), symlink(target, path): the disk's own.
for _, method in ipairs({"mkdir", "remove", "rename", "symlink"}) do
  fs[method] = function(self, ...)
    return done(call(self.address, method, ...))
  end
end

function fs:statfs()
  local size = call(self.address, "getCapacity")
  local used = call(self.address, "getUsed")
  return {type = "hfs", size = size or 0, used = used or 0}
end

return function(address)
  return setmetatable({address = address, type = "hfs"}, fs)
end
