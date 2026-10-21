-- inotify: file change notices, as Linux's inotify(7), loaded at the first sys.inotify() (nothing of
-- it is in memory until then). An instance is an open file: watch(path) adds a path, a read gives a
-- notice a line, "EVENT path", Linux's names: CREATE, MODIFY, DELETE, MOVED_FROM and MOVED_TO (a
-- rename: one each), and OVERFLOW (no path: notices were dropped, or a disk was formatted: look
-- again). It's ready to poll (IN) while one waits. Watching a directory reports its entries too.
--
-- The notices come from where the changes are made (user design, 2026-10-21: a device reports every
-- change to its state): a disk reports its own, in its own paths (its 'changes'), which this turns
-- into full paths by where it's mounted; tmpfs, the kernel's own memory, reports through k.changed.
-- Paths are watched, not files (Linux follows a file across a rename), and a change made on a
-- disk's host, outside any computer, isn't seen.
local k, vfs = ...

local instances = {} -- open instances: {watches = {path = true}, queue = {lines}}
-- Linux's names for the changes devices and tmpfs report.
local NAMES = {create = "CREATE", modify = "MODIFY", remove = "DELETE"}

-- Whether a watch on 'watched' sees a change to 'path': the path itself, or an entry right in it.
local function sees(watched, path)
  return path == watched or path:match("^(.*)/[^/]+$") == (watched == "/" and "" or watched)
end

-- A notice, to every instance watching its path (OVERFLOW, to all).
local function deliver(name, path)
  for instance in pairs(instances) do
    for watched in pairs(instance.watches) do
      if not path or sees(watched, path) then
        instance.queue[#instance.queue + 1] = name .. (path and " " .. path or "") .. "\n"
        break
      end
    end
  end
end

-- A change as notices (a rename: two; a format: everything changed).
local function changed(path, event, from)
  if event == "rename" then
    deliver("MOVED_FROM", from)
    deliver("MOVED_TO", path)
  elseif NAMES[event] then
    deliver(NAMES[event], path)
  else
    deliver("OVERFLOW")
  end
end
k.changed = changed -- (tmpfs's, and any other the kernel makes itself)

local function join(mount, path)
  return path and (mount == "/" and path or path == "/" and mount or mount .. path)
end

-- The disks' reports, taken: each mounted disk's, as full paths. A disk with none raises its
-- interrupt when it has one: the kernel listens (a read or poll waiting on an instance).
local function pump()
  for _, mount in ipairs(vfs.mounts) do
    local address = mount.fs.type == "hfs" and mount.fs.address
    while address do
      local ok, change = board.invoke(address, "changes")
      if not (ok and change) then
        k.listen(address)
        break
      end
      if change.lost then
        deliver("OVERFLOW")
      end
      changed(join(mount.path, change.path), change.event, join(mount.path, change.from))
    end
  end
end

local inotify = {}

-- A new instance, an open file. The disks' reports from before it are dropped: they're no news.
function inotify.open()
  if not next(instances) then
    pump()
  end
  local instance = {type = "inotify", watches = {}, queue = {}}
  instances[instance] = true
  function instance.events()
    pump()
    return {IN = #instance.queue > 0}
  end
  function instance.read(self, n)
    pump()
    local line = instance.queue[1]
    if not line then
      return k.BLOCK
    end
    table.remove(instance.queue, 1)
    return line
  end
  function instance.close()
    instances[instance] = nil
    return true
  end
  return instance
end

function inotify.watch(instance, path)
  instance.watches[path] = true
  return true
end

return inotify
