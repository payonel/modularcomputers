-- tty: a terminal line, as Linux's tty layer. A tty belongs to one input device (a terminal, or
-- the board's console) and holds its input buffer and termios flags, whatever process is running.
-- What the device sends arrives by itself in the tty's ring (board.ring: the firmware fills it, as
-- a device fills its driver's ring by DMA; Linux's flip buffer), with no call; the line discipline
-- (pump, receive: the kernel runs it as the interrupt handler for the device) edits, echoes and
-- signals, and the kernel's read(2) on it takes what's ready (read). Output goes to the device after
-- the tty's output processing (onlcr).
local tty = {}
tty.__index = tty

-- The control characters (termios's c_cc), Linux's defaults.
local VINTR = "\3" -- Ctrl+C: SIGINT to the foreground process
local VEOF = "\4" -- Ctrl+D: the line so far is ready; at the start of a line, end of file
local VKILL = "\21" -- Ctrl+U: erases the line
local VERASE = "\127" -- Backspace (xterm sends DEL)
local BS = "\b" -- Ctrl+H erases too
local CR, NL, TAB, DEL = "\r", "\n", "\t", "\127"
local CARET_OFFSET = 64 -- a control character echoes as ^ and the character this far on (\3 as ^C)
local RING_SIZE = 64 -- packets of input the ring holds (each a few hundred bytes at most); past it the device keeps the rest

-- In the ready queue: an end of file (Ctrl+D at the start of a line), read as nothing.
local EOF = {}

tty.SIGINT = "SIGINT"

-- A tty on 'device'. 'signal(tty, name)' is the kernel's: it delivers a signal to the tty's
-- foreground process group.
function tty.new(device, signal)
  return setmetatable({
    device = device,
    input = board.ring(device, RING_SIZE), -- what the device sends, as it sends it
    signal = signal,
    -- termios's flags, Linux's defaults for a terminal
    flags = {icanon = true, echo = true, echoe = true, echoctl = true, isig = true, icrnl = true, onlcr = true, iutf8 = true},
    line = {}, -- the line being typed (canonical mode), a byte each
    ready = {}, -- what processes can read: whole lines and EOF marks (canonical), or bytes as typed
    foreground = nil, -- the process group its signals go to (tcsetpgrp)
    echoes = {}, -- echo not written yet
  }, tty)
end

-- Output: to the device, after output processing.
function tty:write(text)
  if self.flags.onlcr then
    text = string.gsub(text, NL, CR .. NL)
  end
  board.invoke(self.device, "write", text)
end

-- How a byte echoes: control characters as ^X with echoctl (not a tab or a new line).
local function echoed(flags, c)
  if flags.echoctl and c ~= TAB and c ~= NL and (c < " " or c == DEL) then
    return "^" .. (c == DEL and "?" or string.char(string.byte(c) + CARET_OFFSET))
  end
  return c
end

-- Echo is buffered while a batch of input is handled and written once at its end, as n_tty's echo
-- buffer is: one write for a burst of typing, not one a key.
function tty:echo(c)
  if self.flags.echo then
    self.echoes[#self.echoes + 1] = echoed(self.flags, c)
  end
end

-- Erases the last character of the line being typed (with iutf8, all of a UTF-8 character's bytes),
-- and with echoe, from the screen.
function tty:erase()
  local line = self.line
  if #line == 0 then
    return
  end
  local c = table.remove(line)
  while self.flags.iutf8 and #line > 0 and string.byte(c) >= 0x80 and string.byte(c) < 0xC0 do
    c = table.remove(line) -- a continuation byte: on to the character's first
  end
  if self.flags.echo and self.flags.echoe then
    self.echoes[#self.echoes + 1] = string.rep("\b \b", #echoed(self.flags, c)) -- a column a character, two for ^X
  end
end

-- Drops what's typed and not read (Ctrl+C does, as without noflsh).
function tty:flush()
  self.line = {}
  self.ready = {}
end

-- The line discipline: what the device sent (every byte it had), a byte at a time.
function tty:receive(bytes)
  local flags = self.flags
  for i = 1, #bytes do
    local c = string.sub(bytes, i, i)
    if flags.icrnl and c == CR then
      c = NL
    end
    if flags.isig and c == VINTR then
      self:flush()
      self:echo(c)
      self.signal(self, tty.SIGINT)
    elseif not flags.icanon then
      self.ready[#self.ready + 1] = c
      self:echo(c)
    elseif c == VERASE or c == BS then
      self:erase()
    elseif c == VKILL then
      while #self.line > 0 do
        self:erase()
      end
    elseif c == VEOF then
      self.ready[#self.ready + 1] = #self.line == 0 and EOF or table.concat(self.line)
      self.line = {}
    elseif c == NL then
      self.line[#self.line + 1] = c
      self.ready[#self.ready + 1] = table.concat(self.line)
      self.line = {}
      self:echo(c)
    else
      self.line[#self.line + 1] = c
      self:echo(c)
    end
  end
  if #self.echoes > 0 then
    local echoes = table.concat(self.echoes)
    self.echoes = {}
    self:write(echoes)
  end
end

-- The bytes the device sent, through the line discipline: read from the ring, no call.
function tty:pump()
  local input = self.input
  while input.head < input.tail do
    local slot = input.head % input.size + 1
    local bytes = input[slot]
    input[slot] = false
    input.head = input.head + 1
    self:receive(bytes)
  end
end

-- A read of at most n bytes: the next line (canonical mode; what doesn't fit stays for the next
-- read), or every byte typed; "" at an end of file; nil when nothing is ready (the reader blocks).
function tty:read(n)
  local ready = self.ready
  if #ready == 0 then
    return nil
  end
  if self.flags.icanon then
    local chunk = ready[1]
    if chunk == EOF then
      table.remove(ready, 1)
      return ""
    elseif #chunk <= n then
      table.remove(ready, 1)
      return chunk
    end
    ready[1] = string.sub(chunk, n + 1)
    return string.sub(chunk, 1, n)
  end
  local bytes = table.concat(ready)
  self.ready = {}
  if #bytes > n then
    self.ready[1] = string.sub(bytes, n + 1)
    bytes = string.sub(bytes, 1, n)
  end
  return bytes
end

-- termios (tcgetattr, tcsetattr): its flags, as a copy; and set from one (unknown names are ignored).
function tty:getattr()
  local copy = {}
  for name, on in pairs(self.flags) do
    copy[name] = on
  end
  return copy
end

function tty:setattr(flags)
  local wascanonical = self.flags.icanon
  for name in pairs(self.flags) do
    if flags[name] ~= nil then
      self.flags[name] = flags[name] and true or false
    end
  end
  if wascanonical and not self.flags.icanon and #self.line > 0 then
    -- leaving canonical mode: what's been typed of a line is input now
    for i = 1, #self.line do
      self.ready[#self.ready + 1] = self.line[i]
    end
    self.line = {}
  elseif not wascanonical and self.flags.icanon and #self.ready > 0 then
    -- back to canonical mode: what's waiting unread is typed input again, made into lines (as n_tty
    -- rescans its buffer); it was echoed, or not, as it came
    local waiting = table.concat(self.ready)
    self.ready = {}
    local echo = self.flags.echo
    self.flags.echo = false
    self:receive(waiting)
    self.flags.echo = echo
  end
end

-- Its window size (TIOCGWINSZ): the device's.
function tty:size()
  local ok, width, height = board.invoke(self.device, "getSize")
  if ok then
    return width, height
  end
end

return tty
