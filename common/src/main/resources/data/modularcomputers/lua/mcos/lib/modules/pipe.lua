-- pipe: a kernel buffer with a read end and a write end, as pipe(2). A read waits for data (or
-- gets end of file once every write end is closed); a write waits for room, and fails with EPIPE
-- (and SIGPIPE) once every read end is. A write of at most PIPE_BUF bytes is atomic, as POSIX
-- requires: it waits until all of it fits, so it's never split around another writer's data; a
-- larger one is taken as room comes, in pieces that may interleave with others' (as Linux's). The
-- write end polls OUT only with PIPE_BUF bytes free (Linux's pipe_poll: a write it promises won't
-- block), so here, the pipe being PIPE_BUF bytes, only when it's empty.
local k, vfs = ...

-- What a pipe holds before a writer waits. A Linux pipe holds 64 KiB (16 pages); mcos's holds 4 KiB,
-- one Linux page, as a computer's memory is its RAM, a few MiB, not a PC's gigabytes.
local PIPE_SIZE = 4096
-- The largest write that's atomic: POSIX's PIPE_BUF, 4096 on Linux too (at least 512, POSIX says).
-- No bigger than the pipe, or a write of it would never fit.
local PIPE_BUF = PIPE_SIZE

local fail = vfs.fail

-- The two ends: (read end, write end), each an open file description.
return function()
  local pipe = {buffer = "", readers = 1, writers = 1}
  local reader = {
    type = "fifo",
    -- poll: IN with data waiting, HUP once every writer is gone (a read gives the end then)
    events = function(self)
      return {IN = pipe.buffer ~= "", HUP = pipe.writers == 0}
    end,
    read = function(self, n)
      if pipe.buffer == "" then
        return pipe.writers == 0 and "" or k.BLOCK
      end
      local data = pipe.buffer:sub(1, n)
      pipe.buffer = pipe.buffer:sub(n + 1)
      return data
    end,
    close = function()
      pipe.readers = pipe.readers - 1
      return true
    end,
  }
  local writer = {
    type = "fifo",
    -- poll: OUT with PIPE_BUF bytes free, ERR once every reader is gone (a write fails: EPIPE)
    events = function(self)
      return {OUT = PIPE_SIZE - #pipe.buffer >= PIPE_BUF, ERR = pipe.readers == 0}
    end,
    -- Up to PIPE_BUF bytes: all of it once it fits, else nothing yet. More: what fits, a partial
    -- count telling the kernel to wait with the rest (which is given again as a write of its own:
    -- a rest of PIPE_BUF or less then waits to fit whole too, which POSIX allows).
    write = function(self, data)
      if pipe.readers == 0 then
        return fail("EPIPE")
      end
      local room = PIPE_SIZE - #pipe.buffer
      if room <= 0 or #data <= PIPE_BUF and room < #data then
        return k.BLOCK
      end
      local taken = data:sub(1, room)
      pipe.buffer = pipe.buffer .. taken
      return #taken
    end,
    close = function()
      pipe.writers = pipe.writers - 1
      return true
    end,
  }
  return reader, writer
end
