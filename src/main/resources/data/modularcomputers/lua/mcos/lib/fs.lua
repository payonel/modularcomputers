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

-- lstat: a link itself (type "link", target), not what it points to.
function fs.lstat(path)
  local st, why, errno = sys.lstat(path)
  if not st then
    return nil, path .. ": " .. why, errno
  end
  return st
end

function fs.readlink(path)
  local target, why, errno = sys.readlink(path)
  if not target then
    return nil, path .. ": " .. why, errno
  end
  return target
end

-- A symbolic link at 'path' to 'target'.
function fs.symlink(target, path)
  local ok, why, errno = sys.symlink(target, path)
  if not ok then
    return nil, path .. ": " .. why, errno
  end
  return true
end

function fs.realpath(path)
  local real, why, errno = sys.realpath(path)
  if not real then
    return nil, path .. ": " .. why, errno
  end
  return real
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

-- Where a command is, as execvp looks it up: a name with a / is itself; else the first file by that
-- name in the directories of PATH (the environment's, unless given).
function fs.command(name, path)
  if string.find(name, "/", 1, true) then
    local st = sys.stat(name)
    if not st then
      return nil, name .. ": No such file or directory", "ENOENT"
    end
    return name
  end
  for dir in string.gmatch(path or os.getenv("PATH") or "/bin", "[^:]+") do
    local candidate = fs.join(dir, name)
    local st = sys.stat(candidate)
    if st and st.type ~= "directory" then
      return candidate
    end
  end
  return nil, name .. ": command not found", "ENOENT"
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

-- Removes a file, a link (never what it points to) or an empty directory; 'recursive' a directory
-- and all in it (links in it are removed, never followed).
function fs.remove(path, recursive)
  local st, why, errno = fs.lstat(path)
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
-- a file to replace. A link is followed, except in a recursive copy, which copies links as links
-- (as cp -R does: -P).
function fs.copy(from, to, recursive)
  local st, why, errno = (recursive and fs.lstat or fs.stat)(from)
  if not st then
    return nil, why, errno
  end
  if st.type == "link" then
    return fs.symlink(st.target, to)
  elseif st.type == "directory" then
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

-- ---- globbing ---------------------------------------------------------------------------------

local CLASSES = {alpha = "%a", digit = "%d", alnum = "%w", space = "%s", upper = "%u", lower = "%l",
  punct = "%p", xdigit = "%x", cntrl = "%c", print = "\32-\126", graph = "\33-\126", blank = " \t"}

-- A shell pattern (one part of a path) as a Lua pattern, as POSIX fnmatch reads it: * any run of
-- characters, ? any one, [...] one of a set ([!...] or [^...] one not in it; ranges, [:class:]),
-- and a backslash makes the next character itself. Nil if it has nothing special (no glob).
function fs.pattern(glob)
  local out, at, n, special = {"^"}, 1, #glob, false
  while at <= n do
    local c = string.sub(glob, at, at)
    if c == "\\" and at < n then
      out[#out + 1] = "%" .. string.sub(glob, at + 1, at + 1)
      at = at + 2
    elseif c == "*" then
      out[#out + 1] = ".*"
      special = true
      at = at + 1
    elseif c == "?" then
      out[#out + 1] = "."
      special = true
      at = at + 1
    elseif c == "[" then
      -- a set ends at the first ] after its first character (so []] and [!]] hold a ])
      local start = at + 1
      local negate = string.sub(glob, start, start) == "!" or string.sub(glob, start, start) == "^"
      if negate then
        start = start + 1
      end
      local close = string.find(glob, "]", start + 1, true)
      while close and string.find(string.sub(glob, start, close - 1), "%[:%a*:$") do
        close = string.find(glob, "]", close + 1, true) -- (inside a [:class:])
      end
      if not close then
        out[#out + 1] = "%["
        at = at + 1
      else
        local body = string.sub(glob, start, close - 1)
        local set = {}
        local k = 1
        while k <= #body do
          local class = string.match(body, "^%[:(%a+):%]", k)
          local d = string.sub(body, k, k)
          if class and CLASSES[class] then
            set[#set + 1] = CLASSES[class]
            k = k + #class + 4
          elseif d == "\\" and k < #body then
            set[#set + 1] = "%" .. string.sub(body, k + 1, k + 1)
            k = k + 2
          elseif string.sub(body, k + 1, k + 1) == "-" and k + 2 <= #body then
            local a, b = d, string.sub(body, k + 2, k + 2)
            set[#set + 1] = (string.find(a, "%W") and "%" .. a or a) .. "-" .. (string.find(b, "%W") and "%" .. b or b)
            k = k + 3
          else
            set[#set + 1] = string.find(d, "%W") and "%" .. d or d
            k = k + 1
          end
        end
        out[#out + 1] = "[" .. (negate and "^" or "") .. table.concat(set) .. "]"
        special = true
        at = close + 1
      end
    else
      out[#out + 1] = string.find(c, "%W") and "%" .. c or c
      at = at + 1
    end
  end
  if not special then
    return nil
  end
  out[#out + 1] = "$"
  return table.concat(out)
end

-- What a pattern part without anything special names (its backslashes taken off).
local function literal(part)
  return (string.gsub(part, "\\(.)", "%1"))
end

-- The paths matching a shell pattern (special characters in any part), sorted; a name starting with
-- a dot only when its part of the pattern does. None: an empty list (the shell then keeps the word).
function fs.glob(glob)
  local absolute = string.sub(glob, 1, 1) == "/"
  local parts = {}
  for part in string.gmatch(glob, "[^/]+") do
    parts[#parts + 1] = part
  end
  local found = {absolute and "/" or ""}
  for index, part in ipairs(parts) do
    local next = {}
    local pattern = fs.pattern(part)
    for _, base in ipairs(found) do
      if pattern then
        local entries = fs.list(base == "" and "." or base)
        for _, entry in ipairs(entries or {}) do
          local hidden = string.sub(entry.name, 1, 1) == "."
          if string.find(entry.name, pattern) and (not hidden or string.sub(part, 1, 1) == ".")
            and (index == #parts or entry.type == "directory"
              or (entry.type == "link" and fs.isdir(fs.join(base == "" and "." or base, entry.name)))) then
            next[#next + 1] = base == "" and entry.name or fs.join(base, entry.name)
          end
        end
      else
        local name = literal(part)
        local path = base == "" and name or fs.join(base, name)
        if index == #parts or fs.isdir(path) then
          next[#next + 1] = path
        end
      end
    end
    found = next
  end
  if string.sub(glob, -1) == "/" then
    for i, path in ipairs(found) do
      found[i] = path .. "/"
    end
  end
  table.sort(found)
  return found
end

return fs
