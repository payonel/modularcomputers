-- hfs: the filesystem driver for a disk in hfs mode (the disk serves its files itself, as 9P: the
-- driver only passes calls on). Returns a constructor: hfs(address) is the filesystem on that disk.
local k, vfs = ...

local fail = vfs.fail

-- What the disk's failures mean, as errnos (its messages are its own).
local FAILURES = {
  {"^no such file", "ENOENT"},
  {"^already exists", "EEXIST"},
  {"^directory not empty", "ENOTEMPTY"},
  {"^is a directory", "EISDIR"},
  {"^too many open files", "EMFILE"},
  {"^No space left", "ENOSPC"},
  {"^can't move a directory into itself", "EINVAL"},
  {"^bad ", "EINVAL"},
  {"^outside the disk", "EINVAL"},
  {"^raw disk", "ENODEV"},
  {"^no storage", "ENODEV"},
  {"^can't remove the root", "EBUSY"},
  {"^closed handle", "EBADF"},
  {"^not open for", "EBADF"},
  {"^no such device", "ENODEV"},
  {"^name too long", "ENAMETOOLONG"},
  {"^not a link", "EINVAL"},
}

local REPARSE = "symbolic link\0" -- the disk met a link: its path, a NUL, its target

local function failed(why)
  why = tostring(why)
  if string.sub(why, 1, #REPARSE) == REPARSE then
    local at, target = string.match(why, "^([^%z]*)%z(.*)$", #REPARSE + 1)
    return nil, {at = at, target = target}, "REPARSE"
  end
  for _, failure in ipairs(FAILURES) do
    if string.find(why, failure[1]) then
      return fail(failure[2])
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

local fs = {}
fs.__index = fs

local function file(handle, mode)
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
      local ok, why = board.invoke(handle, "close") -- commits what was written, at once
      if not ok then
        return failed(why)
      end
      return true
    end,
  }
end

function fs:open(path, mode)
  local handle, why, errno = call(self.address, "open", path, mode)
  if not handle then
    return nil, why, errno
  end
  return file(handle, mode)
end

function fs:stat(path, nofollow)
  local st, why, errno = call(self.address, "stat", path, nofollow or nil)
  if st == nil then
    if why then
      return nil, why, errno
    end
    return fail("ENOENT")
  end
  if st.link then
    return {type = "link", size = st.size, modified = st.modified, target = st.link}
  end
  return {type = st.directory and "directory" or "file", size = st.size, modified = st.modified}
end

function fs:list(path)
  local entries, why, errno = call(self.address, "list", path, true)
  if not entries then
    return nil, why, errno
  end
  return entries
end

function fs:mkdir(path)
  local ok, why, errno = call(self.address, "mkdir", path)
  if why then
    return nil, why, errno
  end
  return true
end

function fs:remove(path)
  local ok, why, errno = call(self.address, "remove", path)
  if why then
    return nil, why, errno
  end
  return true
end

function fs:rename(from, to)
  local ok, why, errno = call(self.address, "rename", from, to)
  if why then
    return nil, why, errno
  end
  return true
end

function fs:symlink(target, path)
  local ok, why, errno = call(self.address, "symlink", target, path)
  if why then
    return nil, why, errno
  end
  return true
end

function fs:statfs()
  local size = call(self.address, "getCapacity")
  local used = call(self.address, "getUsed")
  return {type = "hfs", size = size or 0, used = used or 0}
end

return function(address)
  return setmetatable({address = address, type = "hfs"}, fs)
end
