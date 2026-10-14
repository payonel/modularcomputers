-- tty: a terminal line, as Linux's tty layer. A tty belongs to one input device (a terminal, or
-- the board's console) and holds its input buffer and termios flags, whatever process is running:
-- the kernel's input service hands it what the device sends (receive), its line discipline edits,
-- echoes and signals, and processes read what's ready (take). Output goes to the device after the
-- tty's output processing (onlcr).
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

-- In the ready queue: an end of file (Ctrl+D at the start of a line), read as nothing.
local EOF = {}

tty.SIGINT = "SIGINT"

-- A tty on 'device'. 'signal(tty, name)' is the kernel's: it delivers a signal to the tty's
-- foreground process.
function tty.new(device, signal)
  return setmetatable({
    device = device,
    signal = signal,
    -- termios's flags, Linux's defaults for a terminal
    flags = {icanon = true, echo = true, echoe = true, echoctl = true, isig = true, icrnl = true, onlcr = true, iutf8 = true},
    line = {}, -- the line being typed (canonical mode), a byte each
    ready = {}, -- what processes can read: whole lines and EOF marks (canonical), or bytes as typed
    foreground = nil, -- the process its signals go to
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

function tty:echo(c)
  if self.flags.echo then
    self:write(echoed(self.flags, c))
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
    self:write(string.rep("\b \b", #echoed(self.flags, c))) -- a column a character, two for ^X
  end
end

-- Drops what's typed and not read (Ctrl+C does, as without noflsh).
function tty:flush()
  self.line = {}
  self.ready = {}
end

-- The line discipline: what the device sent, a byte at a time.
function tty:receive(key)
  local flags = self.flags
  for i = 1, #key do
    local c = string.sub(key, i, i)
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
end

-- Whether a read would get something.
function tty:readable()
  return #self.ready > 0
end

-- A read: the next line (canonical mode), or every byte typed; "" at an end of file; nil when
-- nothing is ready.
function tty:take()
  if #self.ready == 0 then
    return nil
  end
  if self.flags.icanon then
    local chunk = table.remove(self.ready, 1)
    return chunk == EOF and "" or chunk
  end
  local bytes = table.concat(self.ready)
  self.ready = {}
  return bytes
end

return tty
