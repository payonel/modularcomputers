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

-- The node at a path, its parent and its name; a link met on the way (or at the end, with 'follow')
-- is a reparse: nil, {at, target}, "REPARSE" (vfs.lua).
local function walk(root, path, follow)
  local node, parent, name = root, nil, nil
  local walked = ""
  local rest = path
  for part in string.gmatch(path, "[^/]+") do
    if node.link then
      return nil, {at = walked, target = node.link}, "REPARSE"
    elseif not node.dir then
      return nil -- through a file: nothing there
    end
    parent, name = node, part
    node = node.dir[part]
    walked = walked .. "/" .. part
    if not node then
      return nil, parent, name
    end
  end
  if node.link and follow then
    return nil, {at = walked, target = node.link}, "REPARSE"
  end
  return node, parent, name
end

local function reparse(parent, errno)
  if errno == "REPARSE" then
    return nil, parent, errno
  end
end

local function file(node, mode)
  local at = string.sub(mode, 1, 1) == "a" and #node.data or 0
  local readable = string.sub(mode, 1, 1) == "r" or string.find(mode, "+", 1, true)
  local writable = string.sub(mode, 1, 1) ~= "r" or string.find(mode, "+", 1, true)
  return {
    type = "file",
    read = function(self, n)
      if not readable then
        return fail("EBADF")
      end
      local data = string.sub(node.data, at + 1, at + n)
      at = at + #data
      return data
    end,
    write = function(self, data)
      if not writable then
        return fail("EBADF")
      end
      if string.sub(mode, 1, 1) == "a" then
        at = #node.data
      end
      if at > #node.data then
        node.data = node.data .. string.rep("\0", at - #node.data)
      end
      node.data = string.sub(node.data, 1, at) .. data .. string.sub(node.data, at + #data + 1)
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
    return reparse(parent, name)
  end
  if not node and not parent then
    return fail("ENOENT")
  end
  if node and node.dir then
    return fail("EISDIR")
  end
  if not node then
    if string.sub(mode, 1, 1) == "r" then
      return fail("ENOENT")
    end
    node = {data = ""}
    parent.dir[name] = node
  elseif string.sub(mode, 1, 1) == "w" then
    node.data = ""
  end
  return file(node, mode)
end

function fs:stat(path, nofollow)
  local node, link, errno = walk(self.root, path, not nofollow)
  if errno == "REPARSE" then
    return reparse(link, errno)
  elseif not node then
    return fail("ENOENT")
  end
  local size = node.data and #node.data or node.link and #node.link or 0
  return {type = kind(node), size = size, modified = 0, target = node.link}
end

function fs:list(path)
  local node, link, errno = walk(self.root, path, true)
  if errno == "REPARSE" then
    return reparse(link, errno)
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

function fs:mkdir(path)
  local node, parent, name = walk(self.root, path)
  if name == "REPARSE" then
    return reparse(parent, name)
  end
  if node then
    return fail("EEXIST")
  elseif not parent then
    return fail("ENOENT")
  end
  parent.dir[name] = {dir = {}}
  return true
end

function fs:remove(path)
  local node, parent, name = walk(self.root, path)
  if name == "REPARSE" then
    return reparse(parent, name)
  end
  if not node then
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
    return reparse(parent, name)
  elseif not node then
    return fail("ENOENT")
  end
  local there, toparent, toname = walk(self.root, to)
  if toname == "REPARSE" then
    return reparse(toparent, toname)
  elseif there then
    return fail("EEXIST")
  elseif not toparent then
    return fail("ENOENT")
  end
  if string.sub(to, 1, #from + 1) == from .. "/" then
    return fail("EINVAL")
  end
  parent.dir[name] = nil
  toparent.dir[toname] = node
  return true
end

function fs:symlink(target, path)
  local node, parent, name = walk(self.root, path)
  if name == "REPARSE" then
    return reparse(parent, name)
  elseif node then
    return fail("EEXIST")
  elseif not parent then
    return fail("ENOENT")
  end
  parent.dir[name] = {link = target}
  return true
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
