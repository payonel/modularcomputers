-- devfs: /dev, the kernel's devices as files, as Linux's devtmpfs: console (the kernel's console
-- tty), tty (the opening process's own terminal), null and zero.
local k, vfs = ...

local fail = vfs.fail

local fs = {type = "devfs"}

-- A tty as a file: reads block until a line (or what's typed, in raw mode) is ready.
function k.ttyfile(t)
  return {
    type = "tty",
    tty = t,
    read = function(self, n)
      local data = t:read(n)
      if data == nil then
        return k.BLOCK
      end
      return data
    end,
    write = function(self, data)
      t:write(data)
      return #data
    end,
  }
end

local devices = {
  console = function()
    return k.ttyfile(k.console)
  end,
  tty = function(process)
    if not (process and process.tty) then
      return fail("ENODEV")
    end
    return k.ttyfile(process.tty)
  end,
  null = function()
    return {
      type = "device",
      read = function()
        return ""
      end,
      write = function(self, data)
        return #data
      end,
    }
  end,
  zero = function()
    return {
      type = "device",
      read = function(self, n)
        return string.rep("\0", n)
      end,
      write = function(self, data)
        return #data
      end,
    }
  end,
}

local function name(path)
  return string.match(path, "^/([^/]+)$")
end

function fs:open(path, mode, process)
  local make = devices[name(path) or ""]
  if not make then
    return fail(path == "/" and "EISDIR" or "ENOENT")
  end
  return make(process)
end

function fs:stat(path)
  if path == "/" then
    return {type = "directory", size = 0, modified = 0}
  end
  if devices[name(path) or ""] then
    return {type = "device", size = 0, modified = 0}
  end
  return fail("ENOENT")
end

function fs:list(path)
  if path ~= "/" then
    return fail("ENOTDIR")
  end
  local entries = {}
  for device in pairs(devices) do
    entries[#entries + 1] = {name = device, type = "device"}
  end
  return entries
end

function fs:mkdir()
  return fail("EPERM")
end

function fs:remove()
  return fail("EPERM")
end

function fs:rename()
  return fail("EPERM")
end

function fs:statfs()
  return {type = "devfs", size = 0, used = 0}
end

return fs
