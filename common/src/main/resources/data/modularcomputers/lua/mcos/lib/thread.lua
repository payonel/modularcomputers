-- thread: threads of a process, as POSIX's pthreads (the kernel's tasks: sys.thread, join, cancel,
-- mutex). Each thread is preempted as a process is, and blocks only itself: one waiting on a device
-- (sys.poll, a read, sys.sleep) leaves the others running. They share the process's globals and
-- files; a device's events are its file's, taken by whichever thread reads them (nothing is copied
-- to each). The process ends when its main thread does, its threads with it.
--
--   local thread = require("thread")
--   local t = thread.create(function(n) sys.sleep(n) return "slept " .. n end, 1)
--   print(t:join())                        -- true   slept 1
--   local lock = thread.mutex()
--   lock:run(function() shared = shared + 1 end)   -- one thread at a time
local sys = require("sys")

local thread = {}

local Thread = {}
Thread.__index = Thread
Thread.__name = "thread"

local Mutex = {}
Mutex.__index = Mutex
Mutex.__name = "mutex"

-- thread.create(f, ...): a new thread running f(...); f's returns are its join's.
function thread.create(f, ...)
  local tid, why = sys.thread(f, ...)
  if not tid then
    return nil, why
  end
  return setmetatable({tid = tid}, Thread)
end

-- thread.current(): the calling thread's id (the main thread's is the process's pid).
function thread.current()
  return sys.gettid()
end

-- t:join([seconds]): waits for it to end: true and what it returned, or false and its error
-- ("killed": cancelled); nil and ETIMEDOUT after seconds. Once joined, it's gone.
function Thread:join(seconds)
  return sys.join(self.tid, seconds)
end

-- t:kill(): ends it where it is (its join gives false, "killed").
function Thread:kill()
  return sys.cancel(self.tid)
end

-- t:status(): "running", "sleeping" (blocked in a call), "ended" (not yet joined), or nil, gone.
function Thread:status()
  for _, each in ipairs(sys.threads()) do
    if each.tid == self.tid then
      return each.state
    end
  end
  return nil
end

function Thread:__tostring()
  return "thread " .. self.tid
end

-- thread.mutex(): a mutex: m:lock([seconds]) waits until no other thread holds it, and takes it;
-- m:unlock() lets it go; m:run(f, ...) runs f(...) holding it, and lets it go even if f fails.
function thread.mutex()
  return setmetatable({id = assert(sys.mutex())}, Mutex)
end

function Mutex:lock(seconds)
  return sys.lock(self.id, seconds)
end

function Mutex:unlock()
  return sys.unlock(self.id)
end

function Mutex:run(f, ...)
  local ok, why = self:lock()
  if not ok then
    return nil, why
  end
  local results = table.pack(pcall(f, ...))
  self:unlock()
  if not results[1] then
    error(results[2], 0)
  end
  return table.unpack(results, 2, results.n)
end

return thread
