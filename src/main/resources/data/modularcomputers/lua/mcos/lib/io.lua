-- io: Lua's io library in user space, over the kernel's file descriptors, as C's stdio is over
-- read(2) and write(2). Files buffer in the process (lost if it's killed, as stdio's are; what was
-- written to the kernel isn't); a tty's output is line buffered, a file's fully, stderr not at all;
-- reading any file flushes stdout first, so a prompt shows before the read waits (as glibc does).
-- Returns the io table, given the process's syscalls.
local sys = ...

local BUFFER_SIZE = 4096 -- what a buffered file holds before it writes (BUFSIZ)
local READ_SIZE = 4096 -- what one read(2) asks for

local io = {}
local FILE = {__name = "FILE*"}
FILE.__index = FILE

local open = setmetatable({}, {__mode = "k"}) -- every open file, to flush at exit

local function result(ok, why, errno)
  if ok == nil then
    return nil, why, errno
  end
  return ok
end

local function wrap(fd, mode, name)
  local file = setmetatable({fd = fd, mode = mode, name = name, rbuf = "", wbuf = {}, wsize = 0,
    vbuf = sys.isatty(fd) and "line" or "full"}, FILE)
  open[file] = true
  return file
end

function FILE:__tostring()
  return self.closed and "file (closed)" or string.format("file (fd %d)", self.fd)
end

function FILE:__close()
  if not self.closed then
    self:close()
  end
end

local function checkopen(file)
  if getmetatable(file) ~= FILE then
    error("bad argument #1 (FILE* expected, got " .. type(file) .. ")", 3)
  end
  if file.closed then
    error("attempt to use a closed file", 3)
  end
end

function FILE:flush()
  checkopen(self)
  if self.wsize > 0 then
    local data = table.concat(self.wbuf)
    self.wbuf, self.wsize = {}, 0
    local ok, why, errno = sys.write(self.fd, data)
    if not ok then
      return nil, why, errno
    end
  end
  return self
end

function FILE:setvbuf(mode)
  checkopen(self)
  if mode ~= "no" and mode ~= "line" and mode ~= "full" then
    error("bad argument #1 to 'setvbuf' (invalid option '" .. tostring(mode) .. "')", 2)
  end
  self:flush()
  self.vbuf = mode
  return true
end

function FILE:write(...)
  checkopen(self)
  local args = table.pack(...)
  for i = 1, args.n do
    local value = args[i]
    if math.type(value) then
      value = tostring(value)
    elseif type(value) ~= "string" then
      error("bad argument #" .. i .. " to 'write' (string expected, got " .. type(value) .. ")", 2)
    end
    self.wbuf[#self.wbuf + 1] = value
    self.wsize = self.wsize + #value
  end
  if self.vbuf == "no" or self.wsize >= BUFFER_SIZE
    or (self.vbuf == "line" and string.find(self.wbuf[#self.wbuf] or "", "\n", 1, true)) then
    local ok, why, errno = self:flush()
    if not ok then
      return nil, why, errno
    end
  end
  return self
end

-- One read(2) into the read buffer: whether it got anything (false at end of file).
local function fill(file)
  if file ~= io.stdout and io.stdout and not io.stdout.closed then
    io.stdout:flush()
  end
  local data, why, errno = sys.read(file.fd, READ_SIZE)
  if data == nil then
    return nil, why, errno
  end
  if data == "" then
    return false
  end
  file.rbuf = file.rbuf .. data
  return true
end

local function readline(file, keep)
  while true do
    local at = string.find(file.rbuf, "\n", 1, true)
    if at then
      local line = string.sub(file.rbuf, 1, keep and at or at - 1)
      file.rbuf = string.sub(file.rbuf, at + 1)
      return line
    end
    local got, why, errno = fill(file)
    if got == nil then
      return nil, why, errno
    elseif not got then
      local line = file.rbuf
      file.rbuf = ""
      if line == "" then
        return nil -- end of file
      end
      return line
    end
  end
end

local function readcount(file, n)
  while #file.rbuf < n do
    local got, why, errno = fill(file)
    if got == nil then
      return nil, why, errno
    elseif not got then
      break
    end
  end
  if file.rbuf == "" and n > 0 then
    return nil
  end
  local data = string.sub(file.rbuf, 1, n)
  file.rbuf = string.sub(file.rbuf, n + 1)
  return data
end

local function readall(file)
  while true do
    local got, why, errno = fill(file)
    if got == nil then
      return nil, why, errno
    elseif not got then
      break
    end
  end
  local data = file.rbuf
  file.rbuf = ""
  return data
end

-- "n": a numeral, as Lua reads one (skipping white space first).
local function readnumber(file)
  while true do
    local start = string.find(file.rbuf, "%S")
    if start then
      file.rbuf = string.sub(file.rbuf, start)
      -- enough to see the whole numeral: it ends before the buffer does, or the file ends
      if string.find(file.rbuf, "^[A-Za-z0-9%.%+%-]*[^A-Za-z0-9%.%+%-]") then
        break
      end
    end
    local got, why, errno = fill(file)
    if got == nil then
      return nil, why, errno
    elseif not got then
      break
    end
  end
  local numeral = string.match(file.rbuf, "^[%+%-]?0[xX][0-9A-Fa-f%.]+[pP]?[%+%-]?[0-9]*")
    or string.match(file.rbuf, "^[%+%-]?[0-9]*%.?[0-9]*[eE][%+%-]?[0-9]+")
    or string.match(file.rbuf, "^[%+%-]?[0-9]*%.?[0-9]*")
  local value = numeral and tonumber(numeral)
  if value == nil then
    return nil
  end
  file.rbuf = string.sub(file.rbuf, #numeral + 1)
  return value
end

local function readformat(file, format, i)
  if math.type(format) == "integer" then
    return readcount(file, format)
  end
  format = string.gsub(tostring(format), "^%*", "") -- ("*l" as Lua 5.1 wrote it)
  local kind = string.sub(format, 1, 1)
  if kind == "l" then
    return readline(file, false)
  elseif kind == "L" then
    return readline(file, true)
  elseif kind == "a" then
    return readall(file)
  elseif kind == "n" then
    return readnumber(file)
  end
  error("bad argument #" .. i .. " to 'read' (invalid format)", 3)
end

-- file:read(...): each format's value ("l", "L", "a", "n", a count) until one fails; on an error,
-- nil, its message and its errno (an interrupted read: EINTR).
function FILE:read(...)
  checkopen(self)
  if self.wsize > 0 then
    self:flush()
  end
  local formats = table.pack(...)
  if formats.n == 0 then
    formats = {"l", n = 1}
  end
  local results = {}
  for i = 1, formats.n do
    local value, why, errno = readformat(self, formats[i], i)
    if why then
      return nil, why, errno
    end
    results[i] = value
    if value == nil then
      return table.unpack(results, 1, i)
    end
  end
  return table.unpack(results, 1, formats.n)
end

function FILE:lines(...)
  checkopen(self)
  local formats = table.pack(...)
  return function()
    if self.closed then
      return nil
    end
    return self:read(table.unpack(formats, 1, formats.n))
  end
end

function FILE:seek(whence, offset)
  checkopen(self)
  whence = whence or "cur"
  self:flush()
  if whence == "cur" then
    offset = (offset or 0) - #self.rbuf -- what's buffered hasn't been read yet
  end
  self.rbuf = ""
  local at, why, errno = sys.lseek(self.fd, offset or 0, whence)
  if at == nil then
    return nil, why, errno
  end
  return at
end

function FILE:close()
  checkopen(self)
  local ok, why, errno = self:flush()
  self.closed = true
  open[self] = nil
  local closed, err, code = sys.close(self.fd)
  if self.process then
    -- io.popen's: its command's status
    local _, how, status = sys.waitpid(self.process)
    if how == "exited" then
      return status == 0 or nil, "exit", status
    end
    return nil, "signal", status
  end
  if not ok then
    return nil, why, errno
  end
  return result(closed, err, code)
end

io.stdin = wrap(0, "r", "stdin")
io.stdout = wrap(1, "w", "stdout")
io.stderr = wrap(2, "w", "stderr")
io.stderr.vbuf = "no"

local input, output = io.stdin, io.stdout

function io.open(path, mode)
  mode = mode or "r"
  if not string.match(mode, "^[rwa]%+?b?$") then
    error("bad argument #2 to 'open' (invalid mode '" .. tostring(mode) .. "')", 2)
  end
  local fd, why, errno = sys.open(path, mode)
  if not fd then
    return nil, path .. ": " .. why, errno
  end
  return wrap(fd, mode, path)
end

function io.close(file)
  return (file or output):close()
end

function io.read(...)
  return input:read(...)
end

function io.write(...)
  return output:write(...)
end

function io.flush()
  return output:flush()
end

function io.lines(path, ...)
  if path == nil then
    return input:lines(...)
  end
  local file, why = io.open(path)
  if not file then
    error(why, 2)
  end
  local formats = table.pack(...)
  return function()
    if file.closed then
      return nil
    end
    local value = file:read(table.unpack(formats, 1, formats.n))
    if value == nil then
      file:close()
    end
    return value
  end, nil, nil, file
end

function io.input(file)
  if type(file) == "string" then
    input = assert(io.open(file, "r"))
  elseif file then
    checkopen(file)
    input = file
  end
  return input
end

function io.output(file)
  if type(file) == "string" then
    output = assert(io.open(file, "w"))
  elseif file then
    checkopen(file)
    output = file
  end
  return output
end

function io.type(object)
  if getmetatable(object) ~= FILE then
    return nil
  end
  return object.closed and "closed file" or "file"
end

-- io.popen(command[, mode]): runs a command with sh -c, its output to read ("r") or its input to
-- write ("w"); closing it waits for it and gives its status.
function io.popen(command, mode)
  mode = mode or "r"
  if mode ~= "r" and mode ~= "w" then
    error("bad argument #2 to 'popen' (invalid mode '" .. tostring(mode) .. "')", 2)
  end
  local r, w = sys.pipe()
  if not r then
    return nil, w
  end
  local fds = mode == "r" and {[1] = w} or {[0] = r}
  local pid, why, errno = sys.spawn("/bin/sh", {"-c", command}, {fds = fds, env = io.environ and io.environ()})
  sys.close(mode == "r" and w or r)
  if not pid then
    sys.close(mode == "r" and r or w)
    return nil, why, errno
  end
  local file = wrap(mode == "r" and r or w, mode, command)
  file.process = pid
  return file
end

-- Everything written and not yet flushed goes out (exit).
function io.flushall()
  for file in pairs(open) do
    if not file.closed and file.wsize > 0 then
      file:flush()
    end
  end
end

return io
