-- OpenOS's filesystem library for openos's programs, over mcos's files (OpenOS's was a VFS over
-- filesystem components; mcos's kernel is the VFS here, so its paths are mcos's: the disks as
-- mounted). Its functions answer as OpenOS's did: a directory's entries by name, a subdirectory's
-- with a slash; makeDirectory makes the parents; remove takes a directory and what's in it. Its
-- streams (open) are a file descriptor's reads and writes. Mounting, and filesystem proxies, are
-- the kernel's here, not a program's.
local sys = require("sys")
local fs = require("fs")

local filesystem = {}

local function segments(path)
  local parts = {}
  for part in path:gmatch("[^\\/]+") do
    if part == ".." then
      parts[#parts] = nil
    elseif part ~= "." then
      parts[#parts + 1] = part
    end
  end
  return parts
end

function filesystem.segments(path)
  return segments(path)
end

function filesystem.canonical(path)
  local result = table.concat(segments(path), "/")
  if path:sub(1, 1) == "/" then
    return "/" .. result
  end
  return result
end

function filesystem.concat(...)
  local set = table.pack(...)
  for index, value in ipairs(set) do
    if type(value) ~= "string" then
      error(("bad argument #%d (string expected, got %s)"):format(index, type(value)), 2)
    end
  end
  return filesystem.canonical(table.concat(set, "/"))
end

function filesystem.path(path)
  local parts = segments(path)
  local result = table.concat(parts, "/", 1, #parts - 1) .. "/"
  if path:sub(1, 1) == "/" and result:sub(1, 1) ~= "/" then
    return "/" .. result
  end
  return result
end

function filesystem.name(path)
  local parts = segments(path)
  return parts[#parts]
end

function filesystem.exists(path)
  return sys.lstat(path) ~= nil
end

function filesystem.isDirectory(path)
  local st = sys.stat(path)
  return st ~= nil and st.type == "directory"
end

function filesystem.isLink(path)
  local st = sys.lstat(path)
  if st and st.type == "link" then
    return true, st.target
  end
  return false
end

function filesystem.link(target, linkpath)
  local ok, why = sys.symlink(target, linkpath)
  if not ok then
    return nil, why
  end
  return true
end

function filesystem.size(path)
  local st = sys.stat(path)
  return st and st.type ~= "directory" and st.size or 0
end

-- When it last changed, in milliseconds (the world's time).
function filesystem.lastModified(path)
  local st = sys.stat(path)
  return st and st.modified or 0
end

-- The entries' names, a directory's with a slash, as an iterator.
function filesystem.list(path)
  local entries, why = fs.list(path)
  if not entries then
    return nil, why
  end
  local i = 0
  return function()
    i = i + 1
    local entry = entries[i]
    if entry then
      local full = fs.join(path, entry.name)
      return entry.name .. (filesystem.isDirectory(full) and "/" or "")
    end
  end
end

function filesystem.makeDirectory(path)
  if sys.stat(path) then
    return nil, "file or directory with that name already exists"
  end
  local ok, why = fs.mkdir(path, true)
  if not ok then
    return nil, why
  end
  return true
end

function filesystem.remove(path)
  if not sys.lstat(path) then
    return nil, "no such file or directory: " .. path
  end
  local ok, why = fs.remove(path, true)
  if not ok then
    return nil, why
  end
  return true
end

function filesystem.rename(from, to)
  local ok, why = fs.move(from, to)
  if not ok then
    return nil, why
  end
  return true
end

function filesystem.copy(from, to)
  local ok, why = fs.copy(from, to)
  if not ok then
    return nil, why
  end
  return true
end

-- A stream over a file descriptor: read (nil at the end), write, seek, close, as OpenOS's handles.
local Stream = {}
Stream.__index = Stream

function Stream:read(n)
  if not self.fd then
    return nil, "file is closed"
  end
  local data, why = sys.read(self.fd, math.min(math.max(math.tointeger(n) or 4096, 1), 4096))
  if data == "" then
    return nil
  end
  return data, why
end

function Stream:write(data)
  if not self.fd then
    return nil, "file is closed"
  end
  local ok, why = sys.write(self.fd, data)
  if not ok then
    return nil, why
  end
  return true
end

function Stream:seek(whence, offset)
  if not self.fd then
    return nil, "file is closed"
  end
  return sys.lseek(self.fd, offset or 0, whence or "cur")
end

function Stream:close()
  if self.fd then
    sys.close(self.fd)
    self.fd = nil
  end
  return true
end

function filesystem.stream(fd)
  return setmetatable({fd = fd}, Stream)
end

function filesystem.open(path, mode)
  mode = tostring(mode or "r")
  local fd, why = sys.open(path, (mode:gsub("b", "")))
  if not fd then
    return nil, why
  end
  return filesystem.stream(fd)
end

for _, name in ipairs({"mount", "umount", "mounts", "get", "proxy", "realPath", "isAutorunEnabled",
  "setAutorunEnabled"}) do
  filesystem[name] = function()
    error("openos: filesystem." .. name .. " is not supported (mcos's kernel mounts the disks)", 2)
  end
end

return filesystem
