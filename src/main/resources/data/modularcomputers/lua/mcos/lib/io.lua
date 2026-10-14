-- io: a process's standard streams, over the tty it inherited, with Lua's io library on them
-- (io.read, io.write, io.stdin, io.stdout, io.stderr). Returns a function making a process's io
-- table: given its tty, and 'block', which waits (the process yields to the kernel, which resumes
-- it when the tty has input).
return function(tty, block)
  -- One read(2) on the tty: what it has ready, waiting for it; "" at an end of file.
  local function fill()
    while true do
      local chunk = tty:take()
      if chunk then
        return chunk
      end
      block()
    end
  end

  -- stdin keeps what it read and hasn't handed out, as stdio's buffer.
  local buffer = ""

  local function line(keep)
    while true do
      local at = string.find(buffer, "\n", 1, true)
      if at then
        local result = string.sub(buffer, 1, keep and at or at - 1)
        buffer = string.sub(buffer, at + 1)
        return result
      end
      local chunk = fill()
      if chunk == "" then
        local result = buffer
        buffer = ""
        return result ~= "" and result or nil -- the last line, without its new line; or end of file
      end
      buffer = buffer .. chunk
    end
  end

  local function count(n)
    while #buffer < n do
      local chunk = fill()
      if chunk == "" then
        break
      end
      buffer = buffer .. chunk
    end
    if buffer == "" and n > 0 then
      return nil
    end
    local result = string.sub(buffer, 1, n)
    buffer = string.sub(buffer, n + 1)
    return result
  end

  local function all()
    while true do
      local chunk = fill()
      if chunk == "" then
        break
      end
      buffer = buffer .. chunk
    end
    local result = buffer
    buffer = ""
    return result
  end

  local stdin, stdout = {}, {}

  -- file:read(...): each format's value ("l" a line, "L" with its new line, "a" all, n bytes), until one fails.
  function stdin:read(...)
    local formats = table.pack(...)
    if formats.n == 0 then
      formats = {"l", n = 1}
    end
    local results = {}
    for i = 1, formats.n do
      local format = formats[i]
      local value
      if math.type(format) == "integer" then
        value = count(format)
      else
        format = string.gsub(tostring(format), "^%*", "") -- ("*l" as Lua 5.1 wrote it)
        local kind = string.sub(format, 1, 1)
        if kind == "l" then
          value = line(false)
        elseif kind == "L" then
          value = line(true)
        elseif kind == "a" then
          value = all()
        else
          error("bad argument #" .. i .. " to 'read' (invalid format)", 2)
        end
      end
      results[i] = value
      if value == nil then
        return table.unpack(results, 1, i)
      end
    end
    return table.unpack(results, 1, formats.n)
  end

  -- file:write(...): strings and numbers, written at once (a tty is unbuffered here); the file.
  function stdout:write(...)
    local args = table.pack(...)
    for i = 1, args.n do
      local value = args[i]
      if type(value) == "number" then
        value = tostring(value)
      elseif type(value) ~= "string" then
        error("bad argument #" .. i .. " to 'write' (string expected, got " .. type(value) .. ")", 2)
      end
      tty:write(value)
    end
    return self
  end

  function stdout:flush()
    return self
  end

  local stderr = {write = stdout.write, flush = stdout.flush}

  return {
    stdin = stdin,
    stdout = stdout,
    stderr = stderr,
    read = function(...)
      return stdin:read(...)
    end,
    write = function(...)
      return stdout:write(...)
    end,
    flush = function()
      return stdout:flush()
    end,
  }
end
