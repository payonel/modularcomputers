-- unicode: OpenComputers' unicode library, for openos's programs (a machine's library there, in its
-- Lua; here over Lua's utf8 and the terminals' widths, /lib/wcwidth.lua). Strings are UTF-8, a
-- character a code point; a byte that isn't UTF-8 counts as one character, as OpenComputers read
-- such strings. Widths are as OpenComputers' were: a character at least one cell (a combining mark
-- too, as its font drew them).
local wcwidth = require("wcwidth")

local unicode = {}

-- How many characters: the bytes that don't continue one.
function unicode.len(value)
  local _, n = value:gsub("[^\128-\191]", "")
  return n
end

-- Lua's string.sub, by characters: i and j as string.sub takes them (negative from the end).
function unicode.sub(value, i, j)
  i, j = math.tointeger(i) or 1, math.tointeger(j) or -1
  if i < 0 or j < 0 then
    local n = unicode.len(value)
    i = i < 0 and math.max(n + i + 1, 1) or i
    j = j < 0 and n + j + 1 or j
  end
  i = math.max(i, 1)
  if j < i then
    return ""
  end
  local first = utf8.offset(value, i)
  if not first then
    return ""
  end
  local after = utf8.offset(value, j - i + 2, first)
  return value:sub(first, after and after - 1 or #value)
end

-- The characters of a string, each a string.
local function chars(value)
  return value:gmatch("[\0-\127\192-\255]?[\128-\191]*")
end

local function codepoint(char)
  local ok, c = pcall(utf8.codepoint, char)
  return ok and c or char:byte()
end

function unicode.char(...)
  return utf8.char(...)
end

-- The first character's width: 1 or 2 cells.
function unicode.charWidth(value)
  local char = value:match("^[\0-\127\192-\255]?[\128-\191]*")
  if not char or char == "" then
    return 1
  end
  return math.max(1, wcwidth.of(codepoint(char)))
end

function unicode.isWide(value)
  return unicode.charWidth(value) > 1
end

-- The cells a string takes.
function unicode.wlen(value)
  local width = 0
  for char in chars(value) do
    if char ~= "" then
      width = width + math.max(1, wcwidth.of(codepoint(char)))
    end
  end
  return width
end

-- The longest start of a string whose width is less than 'count', as OpenComputers' wtrunc.
function unicode.wtrunc(value, count)
  local width, ends = 0, 0
  for at, char in value:gmatch("()([\0-\127\192-\255]?[\128-\191]*)") do
    if char ~= "" then
      width = width + math.max(1, wcwidth.of(codepoint(char)))
      if width >= count then
        return value:sub(1, at - 1)
      end
      ends = at + #char - 1
    end
  end
  return value:sub(1, ends)
end

function unicode.upper(value)
  return value:upper()
end

function unicode.lower(value)
  return value:lower()
end

function unicode.reverse(value)
  local list = {}
  for char in chars(value) do
    table.insert(list, 1, char)
  end
  return table.concat(list)
end

return unicode
