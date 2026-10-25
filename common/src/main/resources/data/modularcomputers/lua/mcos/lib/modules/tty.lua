-- tty: a terminal line, as Linux's tty layer. A tty belongs to one input device (a terminal, or
-- the board's console) and holds its input buffer and termios flags, whatever process is running.
-- What the device sends arrives by itself in the tty's ring (board.ring: the firmware fills it, as
-- a device fills its driver's ring by DMA; Linux's flip buffer), with no call; the line discipline
-- (pump, receive: the kernel runs it as the interrupt handler for the device) edits, echoes and
-- signals, and the kernel's read(2) on it takes what's ready (read). Output goes to the device after
-- the tty's output processing (onlcr). A pty's tty (lib/modules/pty.lua) has no device: its input
-- is what the master end writes (receive), its output a function the master end reads from.
local tty = {}
tty.__index = tty

-- The control characters (termios's c_cc), Linux's defaults.
local VINTR = "\3" -- Ctrl+C: SIGINT to the foreground process
local VEOF = "\4" -- Ctrl+D: the line so far is ready; at the start of a line, end of file
local VKILL = "\21" -- Ctrl+U: erases the line
local BS = "\b" -- Ctrl+H erases too
local CR, NL, TAB = "\r", "\n", "\t"
local DEL = "\127" -- Backspace's erase (VERASE: xterm sends DEL), and how echoctl shows it (^?)
local CARET_OFFSET = 64 -- a control character echoes as ^ and the character this far on (\3 as ^C)
local RING_SIZE = 64 -- packets of input the ring holds (each a few hundred bytes at most); past it the device keeps the rest

-- In the ready queue: an end of file (Ctrl+D at the start of a line), read as nothing.
local EOF = {}

-- A tty on 'device' (or with 'out' its output instead, a pty's). 'signal(tty, name)' is the
-- kernel's: it delivers a signal to the tty's foreground process group.
function tty.new(device, signal, out)
  return setmetatable({
    device = device,
    out = out,
    input = device and board.ring(device, RING_SIZE), -- what the device sends, as it sends it
    signal = signal,
    -- termios's flags, Linux's defaults for a terminal; and its c_cc's VMIN and VTIME (a raw read:
    -- the bytes it waits for, and tenths of a second it waits)
    flags = {icanon = true, echo = true, echoe = true, echoctl = true, isig = true, icrnl = true, onlcr = true, iutf8 = true,
      vmin = 1, vtime = 0},
    line = {}, -- the line being typed (canonical mode), a byte each
    ready = {}, -- what processes can read: whole lines and EOF marks (canonical), or bytes as typed
    -- foreground: the process group its signals go to (tcsetpgrp), set by the kernel
    echoes = {}, -- echo not written yet
  }, tty)
end

-- Output: to the device (or a pty's master), after output processing. A pty's may answer k.BLOCK:
-- its master holds all it can ('echo': taken anyway, as the line discipline's own).
function tty:write(text, echo)
  if self.flags.onlcr then
    text = text:gsub(NL, CR .. NL)
  end
  if self.out then
    return self.out(text, echo)
  end
  board.invoke(self.device, "write", text)
end

-- How a byte echoes: control characters as ^X with echoctl (not a tab or a new line).
local function echoed(flags, c)
  if flags.echoctl and c ~= TAB and c ~= NL and (c < " " or c == DEL) then
    return "^" .. (c == DEL and "?" or string.char(c:byte() + CARET_OFFSET))
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
  while self.flags.iutf8 and #line > 0 and c:byte() >= 0x80 and c:byte() < 0xC0 do
    c = table.remove(line) -- a continuation byte: on to the character's first
  end
  if self.flags.echo and self.flags.echoe then
    self.echoes[#self.echoes + 1] = ("\b \b"):rep(#echoed(self.flags, c)) -- a column a character, two for ^X
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
    local c = bytes:sub(i, i)
    if flags.icrnl and c == CR then
      c = NL
    end
    if flags.isig and c == VINTR then
      self:flush()
      self:echo(c)
      self.signal(self, "SIGINT")
    elseif not flags.icanon then
      self.ready[#self.ready + 1] = c
      self:echo(c)
    elseif c == DEL or c == BS then
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
    self:write(echoes, true)
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
    ready[1] = chunk:sub(n + 1)
    return chunk:sub(1, n)
  end
  local bytes = table.concat(ready)
  self.ready = {}
  if #bytes > n then
    self.ready[1] = bytes:sub(n + 1)
    bytes = bytes:sub(1, n)
  end
  return bytes
end

-- The bytes a raw read would take now.
function tty:pending()
  local n = 0
  for _, chunk in ipairs(self.ready) do
    n = n + #chunk
  end
  return n
end

local CC_MAX = 255 -- a c_cc value is a byte

-- termios (tcsetattr): set from a table of flags (unknown names are ignored), and vmin and vtime
-- (integers, 0 to 255); tcgetattr is the kernel's copy of tty.flags.
function tty:setattr(flags)
  local wascanonical = self.flags.icanon
  for name, was in pairs(self.flags) do
    local value = flags[name]
    if value ~= nil and type(was) == "number" then
      if math.type(value) == "integer" and value >= 0 and value <= CC_MAX then
        self.flags[name] = value
      end
    elseif value ~= nil then
      self.flags[name] = value and true or false
    end
  end
  if wascanonical and not self.flags.icanon then
    -- leaving canonical mode: what's been typed of a line is input now, and an end of file waiting
    -- is the ^D that was typed (back in canonical mode, it's an end of file again)
    for i, chunk in ipairs(self.ready) do
      if chunk == EOF then
        self.ready[i] = VEOF
      end
    end
    for i = 1, #self.line do
      self.ready[#self.ready + 1] = self.line[i]
    end
    self.line = {}
  elseif not wascanonical and self.flags.icanon then
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

return tty
