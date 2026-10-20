-- io: Lua's io library in user space, over the kernel's file descriptors, as C's stdio is over
-- read(2) and write(2). Files buffer in the process (lost if it's killed, as stdio's are; what was
-- written to the kernel isn't); a tty's output is line buffered, a file's fully, stderr not at all;
-- reading any file flushes stdout first, so a prompt shows before the read waits (as glibc does).
-- Returns the io table, given the process's syscalls and its environment (io.popen's children's).
local sys, environ = ...

local BUFFER_SIZE = 4096 -- what a buffered file holds before it writes (BUFSIZ)
local READ_SIZE = 4096 -- what one read(2) asks for

local io = {}
local FILE = {__name = "FILE*"}
FILE.__index = FILE

local open = setmetatable({}, {__mode = "k"}) -- every open file, to flush at exit

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

-- One read(2) into the read buffer: whether it got anything (false at end of file, or on an error,
-- kept in file.why and file.errno for read to give). End of file is sticky, as in C's stdio (glibc
-- since 2.28): once seen, reads don't ask again (a tty's ^D is read once; a second read would wait
-- for more) until a seek.
local function fill(file)
  if file.eof or file.why then
    return false
  end
  if file ~= io.stdout and io.stdout and not io.stdout.closed then
    io.stdout:flush()
  end
  local data, why, errno = sys.read(file.fd, READ_SIZE)
  if data == nil then
    file.why, file.errno = why, errno
    return false
  elseif data == "" then
    file.eof = true
    return false
  end
  file.rbuf = file.rbuf .. data
  return true
end

-- The read buffer's first n bytes (all of it, with no n), taken out of it.
local function take(file, n)
  local data = string.sub(file.rbuf, 1, n)
  file.rbuf = string.sub(file.rbuf, (n or #file.rbuf) + 1)
  return data
end

-- (Each reader gives nothing on an error: the buffer stays as it was, and read gives the error.)
local function readline(file, keep)
  local at = string.find(file.rbuf, "\n", 1, true)
  while not at and fill(file) do
    at = string.find(file.rbuf, "\n", 1, true)
  end
  if file.why then
    return nil
  elseif at then
    local line = take(file, at)
    return keep and line or string.sub(line, 1, -2)
  elseif file.rbuf ~= "" then
    return take(file) -- the last line, with no new line after it
  end
end

local function readcount(file, n)
  while #file.rbuf < n and fill(file) do
  end
  if file.why or (file.rbuf == "" and n > 0) then
    return nil
  end
  return take(file, n)
end

local function readall(file)
  while fill(file) do
  end
  if not file.why then
    return take(file)
  end
end

-- "n": a numeral, as Lua reads one (skipping white space first).
local function readnumber(file)
  while true do
    local start = string.find(file.rbuf, "%S")
    if start then
      file.rbuf = string.sub(file.rbuf, start)
      -- enough to see the whole numeral: it ends before the buffer does, or the file ends
      if string.find(file.rbuf, "^[%w%.%+%-]*[^%w%.%+%-]") then
        break
      end
    end
    if not fill(file) then
      break
    end
  end
  if file.why then
    return nil
  end
  local numeral = string.match(file.rbuf, "^[%+%-]?0[xX][%x%.]+[pP]?[%+%-]?%d*")
    or string.match(file.rbuf, "^[%+%-]?%d*%.?%d*[eE][%+%-]?%d+")
    or string.match(file.rbuf, "^[%+%-]?%d*%.?%d*")
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
  if kind == "l" or kind == "L" then
    return readline(file, kind == "L")
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
    local value = readformat(self, formats[i], i)
    if self.why then
      local why, errno = self.why, self.errno
      self.why, self.errno = nil, nil -- (not sticky: the next read tries again)
      return nil, why, errno
    end
    results[i] = value
    if value == nil then
      return table.unpack(results, 1, i)
    end
  end
  return table.unpack(results, 1, formats.n)
end

-- An iterator over a file's reads (in read's formats), closing it at the end when 'close' (io.lines).
local function lines(file, close, ...)
  local formats = table.pack(...)
  local function after(value, ...)
    if value == nil and close then
      file:close()
    end
    return value, ...
  end
  return function()
    if file.closed then
      return nil
    end
    return after(file:read(table.unpack(formats, 1, formats.n)))
  end
end

function FILE:lines(...)
  checkopen(self)
  return lines(self, false, ...)
end

function FILE:seek(whence, offset)
  checkopen(self)
  whence = whence or "cur"
  self:flush()
  if whence == "cur" then
    offset = (offset or 0) - #self.rbuf -- what's buffered hasn't been read yet
  end
  self.rbuf = ""
  self.eof = false
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
  elseif not closed then
    return nil, err, code
  end
  return true
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
  return lines(file, true, ...), nil, nil, file
end

-- io.input and io.output: the default file, set (by name, opened in 'mode', or a file) or not.
local function default(current, file, mode)
  if type(file) == "string" then
    return assert(io.open(file, mode))
  elseif file then
    checkopen(file)
    return file
  end
  return current
end

function io.input(file)
  input = default(input, file, "r")
  return input
end

function io.output(file)
  output = default(output, file, "w")
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
  local mine, theirs = r, w -- (the parent reads, the command writes; the other way with "w")
  if mode == "w" then
    mine, theirs = w, r
  end
  local pid, why, errno = sys.spawn("/bin/sh", {"-c", command}, {fds = {[mode == "r" and 1 or 0] = theirs}, env = environ})
  sys.close(theirs)
  if not pid then
    sys.close(mine)
    return nil, why, errno
  end
  local file = wrap(mine, mode, command)
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
