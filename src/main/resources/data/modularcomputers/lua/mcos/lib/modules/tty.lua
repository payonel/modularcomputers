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
local DEFAULT_WIDTH = 80 -- columns, if the device doesn't say
local TAB_STOP = 8
local CSI = "\27["

-- In the ready queue: an end of file (Ctrl+D at the start of a line), read as nothing.
local EOF = {}

tty.SIGINT = "SIGINT"

-- A tty on 'device'. 'signal(tty, name)' is the kernel's: it delivers a signal to the tty's
-- foreground process group.
function tty.new(device, signal)
  local ok, width = board.invoke(device, "getSize")
  return setmetatable({
    -- Where the terminal's cursor is, as worked out from everything written through the tty (n_tty
    -- tracks its column the same way, for tabs and erasing): the column, and whether it's waiting
    -- in the last column to wrap (as a VT100 does after printing there).
    column = 0,
    pending = false,
    width = ok and math.type(width) == "integer" and width > 0 and width or DEFAULT_WIDTH,
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

-- Output processing (opost): with onlcr, a new line goes out as CR LF.
local function opost(self, text)
  if self.flags.onlcr then
    text = string.gsub(text, NL, CR .. NL)
  end
  return text
end

-- Printable characters from the cursor: where it ends, as the terminal wraps them.
local function printed(self, n)
  if n == 0 then
    return
  end
  local column = self.column
  if self.pending then
    column, self.pending = 0, false -- the first wraps to the next row
  end
  local total = column + n
  if total < self.width then
    self.column = total
  elseif total % self.width == 0 then
    self.column, self.pending = self.width - 1, true
  else
    self.column = total % self.width
  end
end

-- The cursor after 'text' is written (as the terminal will move it).
function tty:advance(text)
  local at, n = 1, #text
  while at <= n do
    local special = string.find(text, "[\0-\31\127]", at)
    local run = string.sub(text, at, (special or n + 1) - 1)
    printed(self, utf8.len(run) or #run)
    if not special then
      return
    end
    local c = string.sub(text, special, special)
    at = special + 1
    if c == "\27" then
      local params, final = string.match(text, "^%[([%d;?]*)[ -/]*([@-~])", at)
      if final then
        at = at + #string.match(text, "^%[[%d;?]*[ -/]*[@-~]", at)
        local first = tonumber(string.match(params, "^(%d+)")) or 1
        if final == "G" or final == "`" then
          self.column, self.pending = math.min(first, self.width) - 1, false
        elseif final == "H" or final == "f" then
          self.column, self.pending = math.min(tonumber(string.match(params, ";(%d+)")) or 1, self.width) - 1, false
        elseif final == "C" then
          self.column, self.pending = math.min(self.column + first, self.width - 1), false
        elseif final == "D" then
          self.column, self.pending = math.max(self.column - first, 0), false
        elseif final == "A" or final == "B" then
          self.pending = false
        end
      end
    elseif c == CR then
      self.column, self.pending = 0, false
    elseif c == NL then
      self.pending = false
    elseif c == BS then
      self.column, self.pending = math.max(self.column - 1, 0), false
    elseif c == TAB then
      self.column, self.pending = math.min(self.width - 1, (self.column // TAB_STOP + 1) * TAB_STOP), false
    end
  end
end

-- Output: to the device, after output processing.
function tty:write(text)
  text = opost(self, text)
  self:advance(text)
  board.invoke(self.device, "write", text)
end

-- Rubs out the 'columns' before the cursor on the screen: back a column, a blank, back again; at a
-- row's start, up to the end of the row above (a line typed past the screen's width wrapped there),
-- which a backspace can't reach (a VT100's stops at the left margin). Moves by column number (CHA)
-- where the cursor may wait to wrap, as a backspace there is ambiguous between terminals.
function tty:rubout(columns)
  for _ = 1, columns do
    local sequence
    if self.pending then
      sequence = CSI .. (self.column + 1) .. "G " .. CSI .. (self.column + 1) .. "G"
      self.pending = false
    elseif self.column == 0 then
      sequence = CSI .. "A" .. CSI .. self.width .. "G " .. CSI .. self.width .. "G"
      self.column = self.width - 1
    else
      sequence = "\b \b"
      self.column = self.column - 1
    end
    self.echoes[#self.echoes + 1] = sequence
  end
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
    local text = opost(self, echoed(self.flags, c))
    self:advance(text)
    self.echoes[#self.echoes + 1] = text
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
    self:rubout(#echoed(self.flags, c)) -- a column a character, two for ^X
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
    board.invoke(self.device, "write", echoes) -- (output processing and the cursor done as each was made)
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
  for name in pairs(self.flags) do
    if flags[name] ~= nil then
      self.flags[name] = flags[name] and true or false
    end
  end
  if self.flags.icanon and #self.line == 0 then
    return
  end
  if not self.flags.icanon and #self.line > 0 then
    -- leaving canonical mode: what's been typed of a line is input now
    for i = 1, #self.line do
      self.ready[#self.ready + 1] = self.line[i]
    end
    self.line = {}
  end
end

-- Its window size (TIOCGWINSZ): the device's (and the tty's own idea of its width follows it).
function tty:size()
  local ok, width, height = board.invoke(self.device, "getSize")
  if ok then
    if math.type(width) == "integer" and width > 0 then
      self.width = width
    end
    return width, height
  end
end

return tty
