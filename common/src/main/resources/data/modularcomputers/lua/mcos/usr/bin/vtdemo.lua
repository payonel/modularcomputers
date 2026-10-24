-- vtdemo.lua: what mcos's terminals show, tier by tier, as escape sequences draw it: a VT102's
-- bold and reverse video (tier 1: text only, its phosphor's one color); a VT525's 16 ANSI colors,
-- on text and behind it (tier 2); xterm's 256 colors and 24-bit color (tier 3: every color); and a
-- VT340's sixel graphics, a picture drawn in pixels (tier 3). A terminal shows what it can: a lower
-- tier draws the nearest it has, and skips the picture. An example to read, as much as to run.
local sys = require("sys")

local ESC = "\27"
local COLUMNS = sys.ioctl(1, "TIOCGWINSZ") or 80 -- the terminal's width (into a file: 80)
local MOST_HUES = 72 -- the rainbow's cells, at most
local RESET = ESC .. "[0m"

local function csi(...)
  return ESC .. "[" .. table.concat({...}, ";") .. "m"
end

local function title(text)
  io.write("\n", csi(1), text, RESET, "\n")
end

-- VT102: bold and reverse video, the attributes a VT102 had that mcos's terminals draw.
title("VT102: attributes (tier 1)")
io.write("  plain  ", csi(1), "bold", RESET, "  ", csi(7), "reverse", RESET, "  ", csi(1, 7), "both", RESET, "\n")

-- VT525: the 16 ANSI colors (30-37, 90-97 the bright ones; 40-47 and 100-107 behind).
title("VT525: 16 colors (tier 2)")
local names = {"black", "red", "green", "yellow", "blue", "magenta", "cyan", "white"}
for bright = 0, 1 do
  io.write("  ")
  for i = 0, 7 do
    io.write(csi((bright == 1 and 90 or 30) + i), names[i + 1]:sub(1, 3), " ", RESET)
  end
  io.write("  ")
  for i = 0, 7 do
    io.write(csi((bright == 1 and 100 or 40) + i), "   ", RESET)
  end
  io.write("\n")
end

-- xterm: 256 colors (38;5;n and 48;5;n): the 16, a 6x6x6 cube, and 24 grays.
title("xterm: 256 colors (tier 3)")
for row = 0, 5 do
  io.write("  ")
  for column = 0, 35 do
    local n = 16 + row * 36 + column
    io.write(ESC, "[48;5;", n, "m ")
  end
  io.write(RESET, "\n")
end
io.write("  ")
for n = 232, 255 do
  io.write(ESC, "[48;5;", n, "m  ")
end
io.write(RESET, "\n")

-- 24-bit color (38;2;r;g;b and 48;2;r;g;b): a rainbow, a hue a cell.
local function hue(h)
  local x = 1 - math.abs(h / 60 % 2 - 1)
  local r, g, b
  if h < 60 then r, g, b = 1, x, 0
  elseif h < 120 then r, g, b = x, 1, 0
  elseif h < 180 then r, g, b = 0, 1, x
  elseif h < 240 then r, g, b = 0, x, 1
  elseif h < 300 then r, g, b = x, 0, 1
  else r, g, b = 1, 0, x end
  return math.floor(r * 255), math.floor(g * 255), math.floor(b * 255)
end
title("xterm: 24-bit color (tier 3)")
io.write("  ")
local CELLS = math.max(8, math.min(MOST_HUES, COLUMNS - 4))
for i = 0, CELLS - 1 do
  local r, g, b = hue(i * 360 / CELLS)
  io.write(ESC, "[48;2;", r, ";", g, ";", b, "m ")
end
io.write(RESET, "\n")

-- VT340: sixel graphics (DCS q ... ST). Each data character is six pixels down (its value less 63,
-- the low bit on top); "#n;2;r;g;b" defines color register n (in percent) and "#n" picks it;
-- "!n" repeats the next character; "$" goes back to the band's start; "-" to the next band, six
-- pixels down. This draws a ringed planet, a band and a color at a time.
title("VT340: sixel graphics (tier 3)")
local WIDTH, HEIGHT = 144, 72
local COLORS = 32
local function pixel(x, y)
  local dx, dy = (x - WIDTH / 2) / (WIDTH / 2), (y - HEIGHT / 2) / (HEIGHT / 2)
  local ring = math.abs(dx * dx * 0.55 + (dy * 2.2 + dx * 0.35) ^ 2 - 0.42)
  local planet = dx * dx * 4 + dy * dy
  if planet < 0.62 then
    return 1 + math.floor((dy + 1) / 2 * (COLORS / 2 - 1)) -- its bands, light to dark
  elseif ring < 0.05 then
    return COLORS / 2 + 1 + math.floor((dx + 1) / 2 * (COLORS / 2 - 2)) -- the ring, across
  end
  return 0 -- (nothing: the screen shows through)
end
local out = {ESC, "P0;1q", '"1;1;', WIDTH, ";", HEIGHT}
for n = 1, COLORS / 2 do -- the planet: amber to rust
  out[#out + 1] = ("#%d;2;%d;%d;%d"):format(n, 95 - n * 2, 70 - n * 3, 20)
end
for n = COLORS / 2 + 1, COLORS do -- the ring: pale blue to violet
  local t = n - COLORS / 2
  out[#out + 1] = ("#%d;2;%d;%d;%d"):format(n, 50 + t * 2, 70 - t * 2, 95)
end
local grid = {}
for y = 0, HEIGHT - 1 do
  local row = {}
  for x = 0, WIDTH - 1 do
    row[x] = pixel(x, y)
  end
  grid[y] = row
end
for top = 0, HEIGHT - 1, 6 do
  for color = 1, COLORS do
    local line, any = {}, false
    local last, run = nil, 0
    local function flush()
      if run > 3 then
        line[#line + 1] = "!" .. run .. last
      elseif run > 0 then
        line[#line + 1] = last:rep(run)
      end
    end
    for x = 0, WIDTH - 1 do
      local bits = 0
      for bit = 0, 5 do
        local row = grid[top + bit]
        if row and row[x] == color then
          bits = bits | 1 << bit
        end
      end
      any = any or bits ~= 0
      local c = string.char(63 + bits)
      if c == last then
        run = run + 1
      else
        flush()
        last, run = c, 1
      end
    end
    flush()
    if any then
      out[#out + 1] = "#" .. color .. table.concat(line) .. "$"
    end
  end
  out[#out + 1] = "-"
end
out[#out + 1] = ESC .. "\\"
io.write(table.concat(out))
io.write("  (a tier 3 screen draws it; the others skip it)\n")
