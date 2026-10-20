-- readline: line editing at a terminal, as GNU readline is (a library: the shell and the Lua
-- interpreter both read their lines with it, as bash and lua.c do with readline). The tty goes raw
-- (no canonical editing or echo; ^C still signals) while a line is typed, and back before the line is
-- returned. It draws the line, so it's edited anywhere in it, across rows it wrapped onto (the tty's
-- own erase, as Linux's, stops at a row's start), with a history.
--   Left/Right, Home/End (^A/^E) move; Backspace, Delete (^D) erase; ^U, ^K cut before/after the
--   cursor, ^W the word before it; Up/Down go through the history; ^L clears the screen; ^D on an
--   empty line is the end of input; ^C drops the line; Tab completes the word before the cursor.
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

local readline = {}

local HISTORY_SIZE = 100 -- lines kept for Up and Down
local DEFAULT_COLUMNS = 80
local READ_SIZE = 256
local GAP = 2 -- spaces between columns of completions
local CSI = "\27["
local DEFAULT_BREAKS = "[ \t\n\"'`<>;|&()=]" -- what ends a word, for completion (as rl_completer_word_break_characters)
local SPECIAL = "[ \t\n\"'`<>;|&()$\\*?%[%]#~=!{}]" -- characters a file name has quoted when it's put in

-- How many columns text takes on the screen (escape sequences take none).
local function columns(text)
  text = string.gsub(text, "\27%[[%d;?]*[ -/]*[@-~]", "")
  return utf8.len(text) or #text
end

-- A string's characters (UTF-8), one a slot.
local function characters(text)
  local list = {}
  for c in string.gmatch(text, "[\0-\127\194-\244][\128-\191]*") do
    list[#list + 1] = c
  end
  return list
end

-- A name with what the shell would read as special backslashed, and back.
function readline.escape(text)
  return (string.gsub(text, SPECIAL, "\\%0"))
end

function readline.unescape(text)
  return (string.gsub(text, "\\(.)", "%1"))
end

-- The default completer: the file names a word could be (as typed, backslashes and all), each a
-- word to put in its place; directories end in /.
function readline.filenames(word)
  local typed = readline.unescape(word)
  local dir, prefix = string.match(typed, "^(.*/)([^/]*)$")
  if not dir then
    dir, prefix = "", typed
  end
  local listed = fs.list(dir == "" and "." or dir)
  local found = {}
  for _, entry in ipairs(listed or {}) do
    if string.sub(entry.name, 1, #prefix) == prefix and (string.sub(entry.name, 1, 1) ~= "." or string.sub(prefix, 1, 1) == ".") then
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
    while n < #prefix and n < #word and string.byte(prefix, n + 1) == string.byte(word, n + 1) do
      n = n + 1
    end
    prefix = string.sub(prefix, 1, n)
  end
  return prefix
end

-- How a completion is listed: its last part (a file's name, not its directory).
local function display(word)
  return readline.unescape(string.match(word, "([^/]*/?)$"))
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
        line[#line + 1] = word .. (last and "" or string.rep(" ", widest - columns(word) + GAP))
      end
    end
    lines[#lines + 1] = table.concat(line)
  end
  return table.concat(lines, "\r\n")
end

local editor = {}
editor.__index = editor

-- An editor: its history and completion. options: complete(before, word) (the words 'word' could
-- be; 'before' is the line before it; a second value false: no space after a single one), breaks
-- (a Lua pattern: a character that ends a word), history (lines kept).
function readline.new(options)
  options = options or {}
  return setmetatable({
    history = {},
    size = options.history or HISTORY_SIZE,
    complete = options.complete or function(_, word)
      return readline.filenames(word)
    end,
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

  -- The screen as a line of cells from the prompt's last line's start: the text starts after the
  -- prompt; 'at' is where the terminal's cursor is.
  local start = columns(string.match(shown, "[^\n]*$"))
  local buffer, cursor = {}, 0
  local pending = self.typeahead -- keys read and not yet handled
  self.typeahead = ""
  local at = start
  local out = {}
  local function prompt()
    out[#out + 1] = shown
    at = start
    if start > 0 and start % width == 0 then
      out[#out + 1] = " \r" -- (a cursor waiting to wrap: make it wrap)
    end
  end
  prompt()
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
    moveto(start + from)
    local tail = table.concat(buffer, "", from + 1)
    out[#out + 1] = tail
    at = start + #buffer
    if #tail > 0 and at % width == 0 then
      out[#out + 1] = " \r" -- (it ended in the last column: on to the next row, as the model has it)
    end
    out[#out + 1] = CSI .. "J"
    moveto(start + cursor)
  end
  local function set(text)
    buffer = characters(text)
    cursor = #buffer
    redraw(0)
  end
  local function finish(line, why)
    self.typeahead = why == "EINTR" and "" or pending -- (^C flushes what was typed, as the tty does)
    moveto(start + #buffer)
    out[#out + 1] = why == "EINTR" and "^C\r\n" or "\r\n"
    io.stdout:write(table.concat(out))
    io.stdout:flush()
    sys.ioctl(0, "TCSETS", saved)
    if line and line ~= "" and line ~= history[#history] then
      history[#history + 1] = line
      if #history > self.size then
        table.remove(history, 1)
      end
    end
    return line, why
  end
  -- Tab: the word before the cursor completed, or (a second Tab with nothing to add) the words it
  -- could be, listed.
  local listing = false
  local function complete()
    local from = cursor
    while from > 0 and not (string.find(buffer[from], self.breaks) and buffer[from - 1] ~= "\\") do
      from = from - 1
    end
    local before = table.concat(buffer, "", 1, from)
    local word = table.concat(buffer, "", from + 1, cursor)
    local ok, words, space = pcall(self.complete, before, word)
    if not ok or type(words) ~= "table" or #words == 0 then
      return false
    end
    local put = #words == 1 and words[1] or common(words)
    if #words == 1 and space ~= false and string.sub(put, -1) ~= "/" then
      put = put .. " "
    end
    if #put > #word then
      for _ = from + 1, cursor do
        table.remove(buffer, from + 1)
      end
      for k, c in ipairs(characters(put)) do
        table.insert(buffer, from + k, c)
      end
      cursor = from + #characters(put)
      redraw(from)
      return #words > 1 -- (still more than one: the next Tab lists them)
    elseif #words > 1 and listing then
      local shownwords = {}
      for k, w in ipairs(words) do
        shownwords[k] = display(w)
      end
      moveto(start + #buffer)
      out[#out + 1] = "\r\n" .. grid(shownwords, width) .. "\r\n"
      prompt()
      redraw(0)
      return false
    end
    return #words > 1 -- (ambiguous: the next Tab lists them)
  end

  local entry, stash = #history + 1, ""
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
    -- the next key: an escape sequence, a control character, or a character typed
    local key = string.match(pending, "^\27%[[%d;]*[%a~]") or string.match(pending, "^\27O%a")
      or string.match(pending, "^[\0-\127\194-\244][\128-\191]*") or string.sub(pending, 1, 1)
    pending = string.sub(pending, #key + 1)
    local tabbed = false
    if key == "\r" or key == "\n" then
      return finish(table.concat(buffer))
    elseif key == "\t" then
      tabbed = complete()
    elseif key == "\4" and #buffer == 0 then
      return finish(nil)
    elseif key == "\127" or key == "\8" then
      if cursor > 0 then
        table.remove(buffer, cursor)
        cursor = cursor - 1
        redraw(cursor)
      end
    elseif key == "\27[3~" or key == "\4" then
      if cursor < #buffer then
        table.remove(buffer, cursor + 1)
        redraw(cursor)
      end
    elseif key == "\27[D" or key == "\27OD" then
      if cursor > 0 then
        cursor = cursor - 1
        moveto(start + cursor)
      end
    elseif key == "\27[C" or key == "\27OC" then
      if cursor < #buffer then
        cursor = cursor + 1
        moveto(start + cursor)
      end
    elseif key == "\1" or key == "\27[H" or key == "\27[1~" or key == "\27OH" then
      cursor = 0
      moveto(start)
    elseif key == "\5" or key == "\27[F" or key == "\27[4~" or key == "\27OF" then
      cursor = #buffer
      moveto(start + cursor)
    elseif key == "\21" then -- ^U
      for _ = 1, cursor do
        table.remove(buffer, 1)
      end
      cursor = 0
      redraw(0)
    elseif key == "\11" then -- ^K
      for _ = cursor + 1, #buffer do
        table.remove(buffer)
      end
      redraw(cursor)
    elseif key == "\23" then -- ^W: the word before the cursor (and the spaces after it)
      local from = cursor
      while from > 0 and buffer[from] == " " do
        from = from - 1
      end
      while from > 0 and buffer[from] ~= " " do
        from = from - 1
      end
      for _ = from + 1, cursor do
        table.remove(buffer, from + 1)
      end
      cursor = from
      redraw(cursor)
    elseif key == "\12" then -- ^L
      out[#out + 1] = CSI .. "H" .. CSI .. "2J"
      prompt()
      redraw(0)
    elseif key == "\27[A" or key == "\27OA" then
      if entry > 1 then
        if entry == #history + 1 then
          stash = table.concat(buffer)
        end
        entry = entry - 1
        set(history[entry])
      end
    elseif key == "\27[B" or key == "\27OB" then
      if entry <= #history then
        entry = entry + 1
        set(entry == #history + 1 and stash or history[entry])
      end
    elseif #key > 1 and string.sub(key, 1, 1) == "\27" then
      -- (a key it has no use for)
    elseif string.byte(key) >= 32 and key ~= "\127" then
      table.insert(buffer, cursor + 1, key)
      cursor = cursor + 1
      redraw(cursor - 1)
    end
    listing = tabbed
  end
end

return readline
