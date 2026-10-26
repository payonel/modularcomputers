-- vt100: the escape sequences OpenOS's tty understands, drawn with the gpu (ported from OpenOS, MIT:
-- see /usr/share/openos/LICENSE), its full rules merged in (loaded at once: no lazy loading).
local text = require("text")

local rules = {}
local vt100 = {rules=rules}

-- colors, blinking, and reverse
-- [%d+;%d+;..%d+m
-- cost: 2,250
rules[{"%[", "[%d;]*", "m"}] = function(window, _, number_text)
  -- prefix and suffix ; act as reset
  -- e.g. \27[41;m is actually 41 followed by a reset
  local colors = {0x0,0xff0000,0x00ff00,0xffff00,0x0000ff,0xff00ff,0x00B6ff,0xffffff}
  local fg, bg = window.gpu.setForeground, window.gpu.setBackground
  if window.flip then
    fg, bg = bg, fg
  end
  number_text = " _ " .. number_text:gsub("^;$", ""):gsub(";", " _ ") .. " _ "
  local parts = text.internal.tokenize(number_text)
  local last_was_break
  for _,part in ipairs(parts) do
    local num = tonumber(part[1].txt)
    last_was_break, num = not num, num or last_was_break and 0

    local flip = num == 7
    if flip then
      if not window.flip then
        local rgb, pal = bg(window.gpu.getForeground())
        fg(pal or rgb, not not pal)
        fg, bg = bg, fg
      end
    elseif num == 5 then
      window.blink = true
    elseif num == 0 then
      bg(colors[1])
      fg(colors[8])
    elseif num then
      num = num - 29
      local set = fg
      if num > 10 then
        num = num - 10
        set = bg
      end
      local color = colors[num]
      if color then
        set(color)
      end
    end
    window.flip = flip
  end
end

local function save_attributes(window, seven, s)
  if seven == "7" or s == "s" then
    window.saved =
    {
      window.x,
      window.y,
      {window.gpu.getBackground()},
      {window.gpu.getForeground()},
      window.flip,
      window.blink
    }
  else
    local data = window.saved or {1, 1, {0x0}, {0xffffff}, window.flip, window.blink}
    window.x = data[1]
    window.y = data[2]
    window.gpu.setBackground(table.unpack(data[3]))
    window.gpu.setForeground(table.unpack(data[4]))
    window.flip = data[5]
    window.blink = data[6]
  end
end

-- 7               save cursor position and attributes
-- 8               restore cursor position and attributes
rules[{"[78]"}] = save_attributes

-- s               save cursor position
-- u               restore cursor position
rules[{"%[", "[su]"}] = save_attributes

-- returns: anything that failed to parse
function vt100.parse(window)
  if window.output_buffer:sub(1, 1) ~= "\27" then
    return ""
  end
  local any_valid

  for rule,action in pairs(rules) do
    local last_index = 1 -- start at 1 to skip the \27
    local captures = {}
    for _,pattern in ipairs(rule) do
      if last_index >= #window.output_buffer then
        any_valid = true
        break
      end
      local si, ei, capture = window.output_buffer:find("^(" .. pattern .. ")", last_index + 1)
      if not si then
        break
      end
      captures[#captures + 1] = capture
      last_index = ei
    end

    if #captures == #rule then
      action(window, table.unpack(captures))
      window.output_buffer = window.output_buffer:sub(last_index + 1)
      return ""
    end
  end

  if not any_valid then
    -- malformed
    window.output_buffer = window.output_buffer:sub(2)
    return "\27"
  end

  -- else, still consuming
end

-- [?7[hl]        wrap mode
rules[{"%[", "%?", "7", "[hl]"}] = function(window, _, _, _, nowrap)
  window.nowrap = nowrap == "l"
end

-- helper scroll function
local function set_cursor(window, x, y)
  window.x = math.min(math.max(x, 1), window.width)
  window.y = math.min(math.max(y, 1), window.height)
end

-- -- These DO NOT SCROLL
-- [(%d*)A        move cursor up n lines
-- [(%d*)B        move cursor down n lines
-- [(%d*)C        move cursor right n lines
-- [(%d*)D        move cursor left n lines
rules[{"%[", "%d*", "[ABCD]"}] = function(window, _, n, dir)
  local dx, dy = 0, 0
  n = tonumber(n) or 1
  if dir == "A" then
    dy = -n
  elseif dir == "B" then
    dy = n
  elseif dir == "C" then
    dx = n
  else -- D
    dx = -n
  end
  set_cursor(window, window.x + dx, window.y + dy)
end

-- [Line;ColumnH  Move cursor to screen location v,h
-- [Line;Columnf  ^ same
-- [;H            Move cursor to upper left corner
-- [;f            ^ same
rules[{"%[", "%d*", ";", "%d*", "[Hf]"}] = function(window, _, y, _, x)
  set_cursor(window, tonumber(x) or 1, tonumber(y) or 1)
end

-- [H             move cursor to upper left corner
-- [f             ^ same
rules[{"%[[Hf]"}] = function(window)
  set_cursor(window, 1, 1)
end

-- [K             clear line from cursor right
-- [0K            ^ same
-- [1K            clear line from cursor left
-- [2K            clear entire line
local function clear_line(window, _, n)
  n = tonumber(n) or 0
  local x = (n == 0 and window.x or 1)
  local rep = n == 1 and window.x or (window.width - x + 1)
  window.gpu.fill(x + window.dx, window.y + window.dy, rep, 1, " ")
end
rules[{"%[", "[012]?", "K"}] = clear_line

-- [J             clear screen from cursor down
-- [0J            ^ same
-- [1J            clear screen from cursor up
-- [2J            clear entire screen
rules[{"%[", "[012]?", "J"}] = function(window, _, n)
  clear_line(window, _, n)
  n = tonumber(n) or 0
  local y = n == 0 and (window.y + 1) or 1
  local rep = n == 1 and (window.y - 1) or (window.height)
  window.gpu.fill(1 + window.dx, y + window.dy, window.width, rep, " ")
end

-- [6n            get the cursor position [ EscLine;ColumnR 	Response: cursor is at v,h ]
rules[{"%[", "6", "n"}] = function(window)
  -- this solution puts the response on stdin, but it isn't echo'd
  -- I'm personally fine with the lack of echo
  io.stdin.bufferRead = string.format("%s%s[%d;%dR", io.stdin.bufferRead, string.char(0x1b), window.y, window.x)
end

-- D               scroll up one line -- moves cursor down
-- E               move to next line (acts the same ^, but x=1)
-- M               scroll down one line -- moves cursor up
rules[{"[DEM]"}] = function(window, _, dir)
  if dir == "D" then
    window.y = window.y + 1
  elseif dir == "E" then
    window.y = window.y + 1
    window.x = 1
  else -- M
    window.y = window.y -  1
  end
end

return vt100
