-- wcwidth: how many cells a character takes on the screen, as the terminals lay it out (glibc's
-- wcwidth): 0 for a combining mark (it joins the character before), 2 for a wide one (CJK, most
-- emoji), else 1. The table is the terminals' own, /usr/share/wcwidth, read at the first
-- character past ASCII (a line of plain text costs nothing).
local TABLE = "/usr/share/wcwidth"

local ranges -- first, last, width: three numbers a range, sorted

local function load()
  ranges = {}
  local file = io.open(TABLE)
  if not file then
    return -- (no table: every character one cell)
  end
  for line in file:lines() do
    local first, last, width = line:match("^(%x+) (%x+) (%d)")
    if first then
      ranges[#ranges + 1] = tonumber(first, 16)
      ranges[#ranges + 1] = tonumber(last, 16)
      ranges[#ranges + 1] = tonumber(width)
    end
  end
  file:close()
end

local wcwidth = {}

-- A code point's width: 0, 1 or 2 (a control: 0).
function wcwidth.of(c)
  if c < 0x7F then
    return c >= 0x20 and 1 or 0
  end
  if not ranges then
    load()
  end
  local low, high = 1, #ranges // 3
  while low <= high do
    local middle = (low + high) // 2
    local at = middle * 3 - 2
    if c < ranges[at] then
      high = middle - 1
    elseif c > ranges[at + 1] then
      low = middle + 1
    else
      return ranges[at + 2]
    end
  end
  return 1
end

-- A string's width in cells (UTF-8; a byte that isn't one, a cell, as the terminal shows U+FFFD).
function wcwidth.string(text)
  local width = 0
  for char in text:gmatch("[\0-\127\194-\244]?[\128-\191]*") do
    if char ~= "" then
      local ok, c = pcall(utf8.codepoint, char)
      width = width + (ok and wcwidth.of(c) or 1)
    end
  end
  return width
end

return wcwidth
