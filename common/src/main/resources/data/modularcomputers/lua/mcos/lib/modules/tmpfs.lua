-- tmpfs: a filesystem in the kernel's memory (the computer's RAM), as Linux's: gone at power-off.
-- Returns a constructor: tmpfs() is a new, empty one.
local k, vfs = ...

local fail = vfs.fail

local fs = {}
fs.__index = fs

-- A node: a directory ({dir = {name = node}}), a file ({data = "..."}) or a symbolic link
-- ({link = "target"}: the VFS follows it).
local function kind(node)
  return node.dir and "directory" or node.link and "link" or "file"
end

-- The node at a path, its parent and its name (the node nil when only the last name is missing:
-- it could be made there); nothing at all when a directory on the way isn't there. A link met on
-- the way (or at the end, with 'follow') is a reparse: nil, {at, target}, "REPARSE" (vfs.lua).
local function walk(root, path, follow)
  local node, parent, name = root, nil, nil
  local walked = ""
  for part in path:gmatch("[^/]+") do
    if not node then
      return nil -- under a name that isn't there (ENOENT)
    elseif node.link then
      return nil, {at = walked, target = node.link}, "REPARSE"
    elseif not node.dir then
      return nil -- through a file: nothing there
    end
    parent, name = node, part
    node = node.dir[part]
    walked = walked .. "/" .. part
  end
  if node and node.link and follow then
    return nil, {at = walked, target = node.link}, "REPARSE"
  end
  return node, parent, name
end

-- An open file. Whether it may be read or written is the VFS's to enforce (by its mode); this
-- only keeps where it's at, appending when opened to append.
local function file(node, mode)
  local append = mode:sub(1, 1) == "a"
  local at = append and #node.data or 0
  return {
    type = "file",
    read = function(self, n)
      local data = node.data:sub(at + 1, at + n)
      at = at + #data
      return data
    end,
    write = function(self, data)
      if append then
        at = #node.data
      end
      if at > #node.data then
        node.data = node.data .. ("\0"):rep(at - #node.data)
      end
      node.data = node.data:sub(1, at) .. data .. node.data:sub(at + #data + 1)
      at = at + #data
      return #data
    end,
    seek = function(self, whence, offset)
      local base = whence == "cur" and at or whence == "end" and #node.data or 0
      if base + offset < 0 then
        return fail("EINVAL")
      end
      at = base + offset
      return at
    end,
  }
end

function fs:open(path, mode)
  local node, parent, name = walk(self.root, path, true)
  if name == "REPARSE" then
    return nil, parent, name
  elseif not node and (not parent or mode:sub(1, 1) == "r") then
    return fail("ENOENT")
  elseif node and node.dir then
    return fail("EISDIR")
  end
  if not node then
    node = {data = ""}
    parent.dir[name] = node
  elseif mode:sub(1, 1) == "w" then
    node.data = ""
  end
  return file(node, mode)
end

function fs:stat(path, nofollow)
  local node, link, errno = walk(self.root, path, not nofollow)
  if errno == "REPARSE" then
    return nil, link, errno
  elseif not node then
    return fail("ENOENT")
  end
  local size = node.data and #node.data or node.link and #node.link or 0
  return {type = kind(node), size = size, modified = 0, target = node.link}
end

function fs:list(path)
  local node, link, errno = walk(self.root, path, true)
  if errno == "REPARSE" then
    return nil, link, errno
  elseif not node then
    return fail("ENOENT")
  elseif not node.dir then
    return fail("ENOTDIR")
  end
  local entries = {}
  for name, child in pairs(node.dir) do
    entries[#entries + 1] = {name = name, type = kind(child)}
  end
  return entries
end

-- A new node at a path whose last name isn't there yet (mkdir, symlink, rename's target).
local function create(self, path, new)
  local node, parent, name = walk(self.root, path)
  if name == "REPARSE" then
    return nil, parent, name
  elseif node then
    return fail("EEXIST")
  elseif not parent then
    return fail("ENOENT")
  end
  parent.dir[name] = new
  return true
end

function fs:mkdir(path)
  return create(self, path, {dir = {}})
end

function fs:symlink(target, path)
  return create(self, path, {link = target})
end

function fs:remove(path)
  local node, parent, name = walk(self.root, path)
  if name == "REPARSE" then
    return nil, parent, name
  elseif not node then
    return fail("ENOENT")
  elseif node.dir and next(node.dir) then
    return fail("ENOTEMPTY")
  end
  parent.dir[name] = nil
  return true
end

function fs:rename(from, to)
  local node, parent, name = walk(self.root, from)
  if name == "REPARSE" then
    return nil, parent, name
  elseif not node then
    return fail("ENOENT")
  elseif to:sub(1, #from + 1) == from .. "/" then
    return fail("EINVAL") -- (a directory into itself: Linux says so before anything else)
  end
  local ok, why, errno = create(self, to, node)
  if ok then
    parent.dir[name] = nil
  end
  return ok, why, errno
end

local function used(node)
  if node.data or node.link then
    return #(node.data or node.link)
  end
  local total = 0
  for _, child in pairs(node.dir) do
    total = total + used(child)
  end
  return total
end

function fs:statfs()
  local hardware = board.hardware()
  return {type = "tmpfs", size = hardware.memoryTotal or 0, used = used(self.root)}
end

return function()
  return setmetatable({root = {dir = {}}, type = "tmpfs"}, fs)
end
