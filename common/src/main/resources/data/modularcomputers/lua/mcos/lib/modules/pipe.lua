-- pipe: a kernel buffer with a read end and a write end, as pipe(2). A read waits for data (or
-- gets end of file once every write end is closed); a write waits for room, and fails with EPIPE
-- (and SIGPIPE) once every read end is.
local k, vfs = ...

local PIPE_SIZE = 4096 -- bytes a pipe holds before a writer waits (Linux's PIPE_BUF)

local fail = vfs.fail

-- The two ends: (read end, write end), each an open file description.
return function()
  local pipe = {buffer = "", readers = 1, writers = 1}
  local reader = {
    type = "fifo",
    ready = function(self)
      return pipe.buffer ~= "" or pipe.writers == 0
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
    -- Takes what fits; a partial count tells the kernel to wait with the rest.
    write = function(self, data)
      if pipe.readers == 0 then
        return fail("EPIPE")
      end
      local room = PIPE_SIZE - #pipe.buffer
      if room <= 0 then
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
