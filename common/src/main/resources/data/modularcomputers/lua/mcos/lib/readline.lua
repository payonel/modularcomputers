-- readline: line editing at a terminal, as GNU readline is (a library: the shell and the Lua
-- interpreter both read their lines with it, as bash and lua.c do with readline). The tty goes raw
-- (no canonical editing or echo; ^C still signals) while a line is typed, and back before the line is
-- returned. It draws the line, so it's edited anywhere in it, across rows it wrapped onto (the tty's
-- own erase, as Linux's, stops at a row's start), with a history.
--   Left/Right, Home/End (^A/^E) move; Ctrl+Left/Right (Alt+Left/Right, ESC b/f) move a word, as
--   bash's do (to the start of the word before, the end of the word after); Backspace, Delete (^D) erase; ^U, ^K cut before/after the
--   cursor, ^W the word before it; Up/Down go through the history; ^L clears the screen; ^D on an
--   empty line is the end of input; ^C drops the line; Tab completes the word before the cursor.
--   A key's escape sequence that comes in parts is waited for (KEYSEQ_SECONDS, GNU's keyseq-timeout).
--   Pasted text (bracketed paste, xterm's mode 2004, on while a line is read, as bash turns it on)
--   goes into the line whole: its new lines stay in it (drawn as a return arrow) and nothing runs
--   until Enter, then all of it does.
--
-- Completion (Tab), as readline's: the program's completer (file names by default) gives the words
-- the one before the cursor could be. One: it's put in (and a space after it, unless it's a
-- directory). More: what they all start with is put in; a second Tab lists them under the line, in
-- columns, and draws the line again.
--
--   local readline = require("readline")
--   local editor = readline.new({complete = function(before, word) ... end, breaks = "[ \t]"})
--   local line, why = editor:read("prompt> ")   -- nil, "EINTR" for ^C; nil at the end of input
local sys = require("sys")
local fs = require("fs")
local wcwidth = require("wcwidth")

local readline = {}

local HISTORY_SIZE = 100 -- lines kept for Up and Down
local DEFAULT_COLUMNS = 80
local READ_SIZE = 256
local GAP = 2 -- spaces between columns of completions
local CSI = "\27["
local REPORT_SECONDS = 0.5 -- how long the terminal has to say where its cursor is (no answer: column 0)
local KEYSEQ_SECONDS = 0.5 -- how long the rest of an escape sequence is waited for (GNU readline's keyseq-timeout)
local KEYSEQ_STEP = 0.05 -- (a poll's, while waiting)
local PASTE_START, PASTE_END = "\27[200~", "\27[201~" -- what a terminal wraps a paste in (bracketed paste)
local NEWLINE_SHOWN = "\226\134\181" -- a new line in the line, as it's drawn: U+21B5, a return arrow
local DEFAULT_BREAKS = "[ \t\n\"'`<>;|&()=]" -- what ends a word, for completion (as rl_completer_word_break_characters)
local SPECIAL = "[ \t\n\"'`<>;|&()$\\*?%[%]#~=!{}]" -- characters a file name has quoted when it's put in

-- How many columns text takes on the screen (escape sequences take none; each character its
-- width, as the terminal lays it out: wcwidth).
local function columns(text)
  return wcwidth.string((text:gsub("\27%[[%d;?]*[ -/]*[@-~]", "")))
end

-- A character's width in cells (a slot: its combining marks go with it).
local function cells(c)
  local ok, code = pcall(utf8.codepoint, c)
  return ok and wcwidth.of(code) or 1
end

-- A string's characters (UTF-8), one a slot; a combining mark (no width) in its character's slot,
-- so the cursor moves over the two, and erases them, as one.
local function characters(text)
  local list = {}
  for c in text:gmatch("[\0-\127\194-\244][\128-\191]*") do
    if #list > 0 and #c > 1 and cells(c) == 0 then
      list[#list] = list[#list] .. c
    else
      list[#list + 1] = c
    end
  end
  return list
end

-- A name with what the shell would read as special backslashed, and back.
function readline.escape(text)
  return (text:gsub(SPECIAL, "\\%0"))
end

function readline.unescape(text)
  return (text:gsub("\\(.)", "%1"))
end

-- The default completer: the file names a word could be (as typed, backslashes and all), each a
-- word to put in its place; directories end in /. ('before', the line before the word, isn't used.)
function readline.filenames(before, word)
  local typed = readline.unescape(word)
  local dir, prefix = typed:match("^(.*/)([^/]*)$")
  if not dir then
    dir, prefix = "", typed
  end
  local listed = fs.list(dir == "" and "." or dir)
  local found = {}
  for _, entry in ipairs(listed or {}) do
    if entry.name:sub(1, #prefix) == prefix and (entry.name:sub(1, 1) ~= "." or prefix:sub(1, 1) == ".") then
      local isdir = entry.type == "directory" or (entry.type == "link" and fs.isdir(dir .. entry.name))
      found[#found + 1] = readline.escape(dir .. entry.name) .. (isdir and "/" or "")
    end
  end
  table.sort(found)
  return found
end

-- What every one of the words starts with.
local function common(words)
  local prefix = words[1] or ""
  for k = 2, #words do
    local word = words[k]
    local n = 0
    while n < #prefix and n < #word and prefix:byte(n + 1) == word:byte(n + 1) do
      n = n + 1
    end
    prefix = prefix:sub(1, n)
  end
  return prefix
end

-- Words in columns down then across, as wide as the screen (as readline's list, and ls's).
local function grid(words, width)
  local widest = 0
  for _, word in ipairs(words) do
    widest = math.max(widest, columns(word))
  end
  local cols = math.max(1, (width + GAP) // (widest + GAP))
  local rows = math.ceil(#words / cols)
  local lines = {}
  for r = 1, rows do
    local line = {}
    for c = 1, cols do
      local word = words[(c - 1) * rows + r]
      if word then
        local last = (c * rows + r) > #words
        line[#line + 1] = word .. (last and "" or (" "):rep(widest - columns(word) + GAP))
      end
    end
    lines[#lines + 1] = table.concat(line)
  end
  return table.concat(lines, "\r\n")
end

-- Keys that do the same as another: Home and End's other codes, ^H, and LF.
local SAME = {["\27[H"] = "\1", ["\27[1~"] = "\1", ["\27[F"] = "\5", ["\27[4~"] = "\5", ["\8"] = "\127", ["\n"] = "\r",
  ["\27[1;5D"] = "\27b", ["\27[1;3D"] = "\27b", ["\27[1;5C"] = "\27f", ["\27[1;3C"] = "\27f"} -- (a word back, a word on)
local WORD = "[%w_]" -- what a word is made of, for moving by words (readline's: letters and digits)

local editor = {}
editor.__index = editor

-- An editor: its history and completion. options: complete(before, word) (the words 'word' could
-- be; 'before' is the line before it; a second value false: no space after a single one), breaks
-- (a Lua pattern: a character that ends a word).
function readline.new(options)
  options = options or {}
  return setmetatable({
    history = {},
    complete = options.complete or readline.filenames,
    breaks = options.breaks or DEFAULT_BREAKS,
    -- Keys read past the end of a line: the next line's (the editor reads all that's waiting); a
    -- program gives them back to the tty before it runs a command (TIOCSTI), so it gets them.
    typeahead = "",
  }, editor)
end

-- Reads a line at a terminal, after 'shown' (the prompt): the line; or nil and "EINTR" (^C), or nil
-- at the end of input. Not at a terminal: the input's next line.
function editor:read(shown)
  local saved = sys.ioctl(0, "TCGETS")
  if not saved then
    io.stdout:write(shown)
    io.stdout:flush()
    local line, _, errno = io.read("l")
    return line, errno
  end
  local raw = {}
  for name, on in pairs(saved) do
    raw[name] = on
  end
  raw.icanon, raw.echo, raw.icrnl = false, false, false -- (isig stays: ^C is still SIGINT)
  sys.ioctl(0, "TCSETS", raw)
  local width = sys.ioctl(1, "TIOCGWINSZ") or DEFAULT_COLUMNS
  local history = self.history

  local buffer, cursor = {}, 0
  local pending = self.typeahead -- keys read and not yet handled
  self.typeahead = ""
  -- The column the cursor is at (from 0), as the terminal reports it (DSR, ESC[6n: ESC[row;colR),
  -- as linenoise asks: the prompt may not start a row (keys typed ahead were echoed by the tty, or
  -- a program's output had no newline at its end). Keys that come meanwhile stay pending.
  local function column()
    if not sys.isatty(1) then
      return 0
    end
    io.stdout:write(CSI .. "6n")
    io.stdout:flush()
    local got, waited, step = "", 0, REPORT_SECONDS / 5
    while waited < REPORT_SECONDS do
      if next(sys.poll({[0] = {IN = true}}, step) or {}) then
        got = got .. (sys.read(0, READ_SIZE) or "")
        local a, b, col = got:find("\27%[%d+;(%d+)R")
        if a then
          pending = pending .. got:sub(1, a - 1) .. got:sub(b + 1)
          return (math.tointeger(tonumber(col)) or 1) - 1
        end
      else
        waited = waited + step
      end
    end
    pending = pending .. got
    return 0
  end
  -- The screen as a line of cells from the start of the row the prompt's last line is on: the text
  -- starts after the prompt; 'at' is where the terminal's cursor is.
  local start = columns(shown:match("[^\n]*$"))
  local origin = shown:find("\n") and 0 or column() -- (where the prompt's last line starts)
  local at = start
  local out = {}
  -- The prompt, drawn where the cursor is: 'origin' its column (0: a row's start).
  local function prompt()
    start = origin + columns(shown:match("[^\n]*$"))
    origin = 0 -- (drawn again, it's after a newline or a cleared screen)
    out[#out + 1] = shown
    at = start
    if start > 0 and start % width == 0 then
      out[#out + 1] = " \r" -- (a cursor waiting to wrap: make it wrap)
    end
  end
  prompt()
  -- The cell after the line's first n characters (counting from the row the prompt's last line
  -- is on): each its width, and a wide one that won't fit in a row's last cell starting the next
  -- row, as the terminal wraps it.
  local function pos(n)
    local p = start
    for i = 1, n do
      local w = cells(buffer[i])
      if w == 2 and p % width == width - 1 then
        p = p + 1
      end
      p = p + w
    end
    return p
  end
  local function moveto(target)
    local from, to = at // width, target // width
    if to < from then
      out[#out + 1] = CSI .. (from - to) .. "A"
    elseif to > from then
      out[#out + 1] = CSI .. (to - from) .. "B"
    end
    out[#out + 1] = CSI .. (target % width + 1) .. "G"
    at = target
  end
  -- Draws the line from character 'from' on, clears what's after it, and puts the cursor back.
  local function redraw(from)
    moveto(pos(from))
    local tail = table.concat(buffer, "", from + 1):gsub("\n", NEWLINE_SHOWN)
    out[#out + 1] = tail
    at = pos(#buffer)
    if #tail > 0 and at % width == 0 then
      out[#out + 1] = " \r" -- (it ended in the last column: on to the next row, as the model has it)
    end
    out[#out + 1] = CSI .. "J"
    moveto(pos(cursor))
  end
  local function set(text)
    buffer = characters(text)
    cursor = #buffer
    redraw(0)
  end
  local function finish(line, why)
    self.typeahead = why == "EINTR" and "" or pending -- (^C flushes what was typed, as the tty does)
    moveto(pos(#buffer))
    out[#out + 1] = (why == "EINTR" and "^C\r\n" or "\r\n") .. CSI .. "?2004l" -- (bracketed paste off: the program's again)
    io.stdout:write(table.concat(out))
    io.stdout:flush()
    sys.ioctl(0, "TCSETS", saved)
    if line and line ~= "" and line ~= history[#history] then
      history[#history + 1] = line
      if #history > HISTORY_SIZE then
        table.remove(history, 1)
      end
    end
    return line, why
  end
  -- Takes characters a..b out of the line; the cursor goes to where they were.
  local function cut(a, b)
    for _ = a, b do
      table.remove(buffer, a)
    end
    cursor = a - 1
  end
  local function go(at)
    cursor = at
    moveto(pos(at))
  end
  -- Tab: the word before the cursor completed, or (a second Tab with nothing to add) the words it
  -- could be, listed.
  local listing = false
  local function complete()
    local from = cursor
    while from > 0 and not (buffer[from]:find(self.breaks) and buffer[from - 1] ~= "\\") do
      from = from - 1
    end
    local before = table.concat(buffer, "", 1, from)
    local word = table.concat(buffer, "", from + 1, cursor)
    local ok, words, space = pcall(self.complete, before, word)
    if not ok or type(words) ~= "table" or #words == 0 then
      return false
    end
    local put = #words == 1 and words[1] or common(words)
    if #words == 1 and space ~= false and put:sub(-1) ~= "/" then
      put = put .. " "
    end
    if #put > #word then
      cut(from + 1, cursor)
      local chars = characters(put)
      for k, c in ipairs(chars) do
        table.insert(buffer, from + k, c)
      end
      cursor = from + #chars
      redraw(from)
      return #words > 1 -- (still more than one: the next Tab lists them)
    elseif #words > 1 and listing then
      local shownwords = {}
      for k, w in ipairs(words) do
        shownwords[k] = readline.unescape(w:match("([^/]*/?)$")) -- (a file's name, not its directory)
      end
      moveto(pos(#buffer))
      out[#out + 1] = "\r\n" .. grid(shownwords, width) .. "\r\n"
      prompt()
      redraw(0)
      return false
    end
    return #words > 1 -- (ambiguous: the next Tab lists them)
  end

  local entry, stash = #history + 1, ""
  out[#out + 1] = CSI .. "?2004h" -- bracketed paste on while the line is read (as bash does)
  local pasting = false
  while true do
    io.stdout:write(table.concat(out))
    io.stdout:flush()
    out = {}
    if pending == "" then
      local data, _, errno = sys.read(0, READ_SIZE)
      if data == nil then
        return finish(nil, errno)
      elseif data == "" then
        return finish(nil)
      end
      pending = data
    end
    -- Pasted: everything to its end goes into the line as it is (a new line stays a new line).
    if pending:sub(1, #PASTE_START) == PASTE_START then
      pending, pasting = pending:sub(#PASTE_START + 1), true
    end
    if pasting then
      local stop = pending:find(PASTE_END, 1, true)
      local hold = not stop and pending:find("\27", 1, true) -- (its end, in part: waits for the rest)
      local text = stop and pending:sub(1, stop - 1) or hold and pending:sub(1, hold - 1) or pending
      pending = stop and pending:sub(stop + #PASTE_END) or hold and pending:sub(hold) or ""
      pasting = not stop
      local from = cursor
      for _, c in ipairs(characters((text:gsub("\r\n?", "\n")))) do
        table.insert(buffer, cursor + 1, c)
        cursor = cursor + 1
      end
      redraw(from)
      if hold and not stop then
        local more = sys.read(0, READ_SIZE)
        pending = pending .. (more or "")
      end
      goto continue
    end
    -- An escape sequence come only in part (its rest in a later read: a key's bytes over a network,
    -- or typed apart) waits a moment for the rest, as GNU readline's keyseq-timeout does, before
    -- ESC is taken alone.
    local waited = 0
    while (pending == "\27" or pending:match("^\27[%[O][%d;]*$")) and waited < KEYSEQ_SECONDS do
      if next(sys.poll({[0] = {IN = true}}, KEYSEQ_STEP) or {}) then
        local more = sys.read(0, READ_SIZE)
        if not more or more == "" then
          break
        end
        pending = pending .. more
      else
        waited = waited + KEYSEQ_STEP
      end
    end
    -- the next key: an escape sequence, a control character, or a character typed
    local key = pending:match("^\27%[[%d;]*[%a~]") or pending:match("^\27O%a") or pending:match("^\27[bf]")
      or pending:match("^[\0-\127\194-\244][\128-\191]*") or pending:sub(1, 1)
    pending = pending:sub(#key + 1)
    key = key:gsub("^\27O", "\27[") -- (ESC O x, as in cursor-keys application mode: the same keys)
    key = SAME[key] or key
    local tabbed = false
    if key == "\r" then
      return finish(table.concat(buffer))
    elseif key == "\t" then
      tabbed = complete()
    elseif key == "\4" and #buffer == 0 then
      return finish(nil)
    elseif key == "\127" then
      if cursor > 0 then
        cut(cursor, cursor)
        redraw(cursor)
      end
    elseif key == "\27[3~" or key == "\4" then
      if cursor < #buffer then
        cut(cursor + 1, cursor + 1)
        redraw(cursor)
      end
    elseif key == "\27[D" then
      go(math.max(cursor - 1, 0))
    elseif key == "\27[C" then
      go(math.min(cursor + 1, #buffer))
    elseif key == "\1" then
      go(0)
    elseif key == "\5" then
      go(#buffer)
    elseif key == "\21" then -- ^U
      cut(1, cursor)
      redraw(0)
    elseif key == "\11" then -- ^K
      cut(cursor + 1, #buffer)
      redraw(cursor)
    elseif key == "\23" then -- ^W: the word before the cursor (and the spaces after it)
      local from = cursor
      while from > 0 and buffer[from] == " " do
        from = from - 1
      end
      while from > 0 and buffer[from] ~= " " do
        from = from - 1
      end
      cut(from + 1, cursor)
      redraw(cursor)
    elseif key == "\12" then -- ^L
      out[#out + 1] = CSI .. "H" .. CSI .. "2J"
      prompt()
      redraw(0)
    elseif key == "\27[A" then
      if entry > 1 then
        if entry == #history + 1 then
          stash = table.concat(buffer)
        end
        entry = entry - 1
        set(history[entry])
      end
    elseif key == "\27[B" then
      if entry <= #history then
        entry = entry + 1
        set(entry == #history + 1 and stash or history[entry])
      end
    elseif key == "\27b" then -- back to the start of this word, or the one before
      local to = cursor
      while to > 0 and not buffer[to]:find(WORD) do
        to = to - 1
      end
      while to > 0 and buffer[to]:find(WORD) do
        to = to - 1
      end
      go(to)
    elseif key == "\27f" then -- on to the end of this word, or the one after
      local to = cursor
      while to < #buffer and not buffer[to + 1]:find(WORD) do
        to = to + 1
      end
      while to < #buffer and buffer[to + 1]:find(WORD) do
        to = to + 1
      end
      go(to)
    elseif #key > 1 and key:sub(1, 1) == "\27" then
      -- (a key it has no use for)
    elseif key:byte() >= 32 and key ~= "\127" then
      if cursor > 0 and #key > 1 and cells(key) == 0 then
        buffer[cursor] = buffer[cursor] .. key -- (a combining mark: on the character before)
        redraw(cursor - 1)
      else
        table.insert(buffer, cursor + 1, key)
        cursor = cursor + 1
        redraw(cursor - 1)
      end
    end
    listing = tabbed
    ::continue::
  end
end

return readline
