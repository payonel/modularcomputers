-- fs: conveniences for programs over the filesystem syscalls (what C programs get from libc and
-- shell utilities): paths, directory listings, making and removing trees, copying, globbing. Errors
-- are Lua's convention: nil, a message naming the path, and the errno's name.
local sys = require("sys")

local fs = {}

local COPY_SIZE = 4096 -- bytes a copy moves at a time

-- ---- paths --------------------------------------------------------------------------------------

function fs.join(...)
  local parts = table.pack(...)
  local path = ""
  for i = 1, parts.n do
    local part = parts[i]
    if string.sub(part, 1, 1) == "/" or path == "" then
      path = part
    else
      path = (string.sub(path, -1) == "/" and path or path .. "/") .. part
    end
  end
  return path
end

-- A path made absolute (from the working directory) with no ".", ".." or repeated "/".
function fs.canonical(path)
  if string.sub(path, 1, 1) ~= "/" then
    path = sys.getcwd() .. "/" .. path
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

-- The last part of a path (basename), and all before it (dirname).
function fs.name(path)
  return string.match(path, "([^/]*)/*$")
end

function fs.dirname(path)
  local dir = string.match(path, "^(.*[^/])/+[^/]+/*$")
  if dir then
    return dir
  end
  return string.sub(path, 1, 1) == "/" and "/" or "."
end

-- ---- what's there -------------------------------------------------------------------------------

function fs.stat(path)
  local st, why, errno = sys.stat(path)
  if not st then
    return nil, path .. ": " .. why, errno
  end
  return st
end

function fs.exists(path)
  return sys.stat(path) ~= nil
end

function fs.isdir(path)
  local st = sys.stat(path)
  return st ~= nil and st.type == "directory"
end

-- A directory's entries ({name, type} each, sorted), as opendir and readdir.
function fs.list(path)
  local fd, why, errno = sys.open(path, "r")
  if not fd then
    return nil, path .. ": " .. why, errno
  end
  local entries
  entries, why, errno = sys.getdents(fd)
  sys.close(fd)
  if not entries then
    return nil, path .. ": " .. why, errno
  end
  return entries
end

-- Iterates a directory's names.
function fs.dir(path)
  local entries = assert(fs.list(path))
  local i = 0
  return function()
    i = i + 1
    local entry = entries[i]
    return entry and entry.name, entry and entry.type
  end
end

-- ---- making and removing ------------------------------------------------------------------------

function fs.mkdir(path, parents)
  if parents then
    local at = string.sub(path, 1, 1) == "/" and "" or "."
    for part in string.gmatch(path, "[^/]+") do
      at = at .. "/" .. part
      local st = sys.stat(at)
      if not st then
        local ok, why, errno = sys.mkdir(at)
        if not ok then
          return nil, at .. ": " .. why, errno
        end
      elseif st.type ~= "directory" then
        return nil, at .. ": Not a directory", "ENOTDIR"
      end
    end
    return true
  end
  local ok, why, errno = sys.mkdir(path)
  if not ok then
    return nil, path .. ": " .. why, errno
  end
  return true
end

-- Removes a file or an empty directory; 'recursive' a directory and all in it.
function fs.remove(path, recursive)
  local st, why, errno = fs.stat(path)
  if not st then
    return nil, why, errno
  end
  if st.type == "directory" then
    if recursive then
      local entries
      entries, why, errno = fs.list(path)
      if not entries then
        return nil, why, errno
      end
      for _, entry in ipairs(entries) do
        local ok
        ok, why, errno = fs.remove(fs.join(path, entry.name), true)
        if not ok then
          return nil, why, errno
        end
      end
    end
    local ok
    ok, why, errno = sys.rmdir(path)
    if not ok then
      return nil, path .. ": " .. why, errno
    end
    return true
  end
  local ok
  ok, why, errno = sys.unlink(path)
  if not ok then
    return nil, path .. ": " .. why, errno
  end
  return true
end

-- Copies a file (or, 'recursive', a directory and all in it) to a path that doesn't exist yet or
-- a file to replace.
function fs.copy(from, to, recursive)
  local st, why, errno = fs.stat(from)
  if not st then
    return nil, why, errno
  end
  if st.type == "directory" then
    if not recursive then
      return nil, from .. ": Is a directory", "EISDIR"
    end
    local ok
    if not fs.isdir(to) then
      ok, why, errno = fs.mkdir(to)
      if not ok then
        return nil, why, errno
      end
    end
    local entries
    entries, why, errno = fs.list(from)
    if not entries then
      return nil, why, errno
    end
    for _, entry in ipairs(entries) do
      ok, why, errno = fs.copy(fs.join(from, entry.name), fs.join(to, entry.name), true)
      if not ok then
        return nil, why, errno
      end
    end
    return true
  end
  local source
  source, why, errno = sys.open(from, "r")
  if not source then
    return nil, from .. ": " .. why, errno
  end
  local target
  target, why, errno = sys.open(to, "w")
  if not target then
    sys.close(source)
    return nil, to .. ": " .. why, errno
  end
  while true do
    local data
    data, why, errno = sys.read(source, COPY_SIZE)
    if not data or data == "" then
      break
    end
    local ok
    ok, why, errno = sys.write(target, data)
    if not ok then
      break
    end
  end
  sys.close(source)
  local closed, err, code = sys.close(target)
  if why then
    return nil, from .. ": " .. why, errno
  elseif not closed then
    return nil, to .. ": " .. err, code
  end
  return true
end

-- Moves a file or directory; across filesystems by copying and removing, as mv does (EXDEV).
function fs.move(from, to)
  local ok, why, errno = sys.rename(from, to)
  if ok then
    return true
  elseif errno ~= "EXDEV" then
    return nil, from .. ": " .. why, errno
  end
  ok, why, errno = fs.copy(from, to, true)
  if not ok then
    return nil, why, errno
  end
  return fs.remove(from, true)
end

-- ---- globbing -----------------------------------------------------------------------------------

-- A shell pattern as a Lua pattern: * any run, ? any one character; the rest as itself.
local function pattern(glob)
  return "^" .. string.gsub(glob, "[%^%$%(%)%%%.%[%]%*%+%-%?]", function(c)
    if c == "*" then
      return "[^/]*"
    elseif c == "?" then
      return "[^/]"
    end
    return "%" .. c
  end) .. "$"
end

-- The paths matching a shell pattern (* and ? in any part), sorted; names starting with . only
-- when the pattern's part does.
function fs.glob(glob)
  local absolute = string.sub(glob, 1, 1) == "/"
  local parts = {}
  for part in string.gmatch(glob, "[^/]+") do
    parts[#parts + 1] = part
  end
  local found = {absolute and "/" or ""}
  for _, part in ipairs(parts) do
    local next = {}
    for _, base in ipairs(found) do
      if string.find(part, "[%*%?]") then
        local entries = fs.list(base == "" and "." or base)
        for _, entry in ipairs(entries or {}) do
          if string.find(entry.name, pattern(part))
            and (string.sub(entry.name, 1, 1) ~= "." or string.sub(part, 1, 1) == ".") then
            next[#next + 1] = base == "" and entry.name or fs.join(base, entry.name)
          end
        end
      else
        local path = base == "" and part or fs.join(base, part)
        if fs.exists(path) then
          next[#next + 1] = path
        end
      end
    end
    found = next
  end
  table.sort(found)
  return found
end

return fs
