-- The program's terminal as OpenComputers' gpu, screen and keyboard (openos's machine: a native
-- part, over mcos's calls). The terminal is the process's own (/dev/tty): what the gpu draws is
-- escape sequences to it, positioned (CUP), in 24-bit color (SGR), its copies and fills the VT420's
-- rectangle sequences (DECCRA, DECFRA), so the terminal does the work; the gpu keeps what it drew
-- (a shadow of the screen) for get and for redrawing palette colors. What it didn't draw (the
-- screen before, a command run meanwhile) it doesn't know: get reads such a cell as a blank.
--
-- The keyboard and the mouse are the terminal's reports: kitty's keyboard protocol with every key
-- (presses, repeats, releases, with the text each typed), xterm's SGR mouse, bracketed paste;
-- each becomes OpenComputers' signal (key_down, key_up, touch, drag, drop, scroll, clipboard). Its
-- size, its largest and its kind (Device Attributes: its colors) are asked once, at the start.
local sys = require("sys")
local unicode = require("openos.unicode")
local wcwidth = require("wcwidth")
local keyboard = require("openos.lib.keyboard") -- (its key codes: data only)

local terminal = {}

local ESC = "\27"
local FLUSH_SIZE = 8192 -- output kept before it's written (it's written before any wait, too)
local KEYBOARD_FLAGS = 27 -- kitty's: disambiguate, event types, every key, associated text
local FUNCTIONAL = 57344 -- where kitty's private-use key codes start
local PRESS, REPEAT, RELEASE = 1, 2, 3
local SHIFT, CONTROL = 1, 4
local WHITE, BLACK = 0xFFFFFF, 0x000000

-- OpenComputers' palettes: a tier 2 screen's 16 colors, and a tier 3's 16 grays (its 240 others
-- are a fixed cube: 6 reds, 8 greens, 5 blues).
local FOUR_BIT = {[0] = 0xFFFFFF, 0xFFCC33, 0xCC66CC, 0x6699FF, 0xFFFF33, 0x33CC33, 0xFF6699, 0x333333,
  0xCCCCCC, 0x336699, 0x9933CC, 0x333399, 0x663300, 0x336600, 0xFF3333, 0x000000}
local function grays()
  local palette = {}
  for i = 0, 15 do
    local shade = (i + 1) * 255 // 17
    palette[i] = shade << 16 | shade << 8 | shade
  end
  return palette
end
local DEPTH_NAMES = {[1] = "OneBit", [4] = "FourBit", [8] = "EightBit"}

-- kitty's key codes, as OpenComputers' (LWJGL's) numbered them: the keys that type text by their
-- unshifted character, the others by kitty's codes.
local CODES = {[27] = 0x01, [13] = 0x1C, [9] = 0x0F, [127] = 0x0E, [32] = 0x39,
  ["-"] = "minus", ["="] = "equals", ["["] = "lbracket", ["]"] = "rbracket", ["\\"] = "backslash",
  [";"] = "semicolon", ["'"] = "apostrophe", ["`"] = "grave", [","] = "comma", ["."] = "period",
  ["/"] = "slash"}
local FUNCTIONAL_NAMES = {[57348] = "insert", [57349] = "delete", [57350] = "left", [57351] = "right",
  [57352] = "up", [57353] = "down", [57354] = "pageUp", [57355] = "pageDown", [57356] = "home",
  [57357] = "end", [57358] = "capital", [57359] = "scroll", [57360] = "numlock", [57362] = "pause",
  [57399] = "numpad0", [57400] = "numpad1", [57401] = "numpad2", [57402] = "numpad3",
  [57403] = "numpad4", [57404] = "numpad5", [57405] = "numpad6", [57406] = "numpad7",
  [57407] = "numpad8", [57408] = "numpad9", [57409] = "numpaddecimal", [57410] = "numpaddiv",
  [57411] = "numpadmul", [57412] = "numpadsub", [57413] = "numpadadd", [57414] = "numpadenter",
  [57415] = "numpadequals", [57441] = "lshift", [57442] = "lcontrol", [57443] = "lmenu",
  [57447] = "rshift", [57448] = "rcontrol", [57449] = "rmenu"}
local EXTRA_CODES = {[57361] = 0xB7, [57363] = 0xDD, [57444] = 0xDB, [57450] = 0xDC} -- print screen, menu, the super keys
local F1 = 57364
local F_CODES = {0x3B, 0x3C, 0x3D, 0x3E, 0x3F, 0x40, 0x41, 0x42, 0x43, 0x44, 0x57, 0x58,
  0x64, 0x65, 0x66, 0x67, 0x68, 0x69, 0x71}
local LETTERS = {A = 57352, B = 57353, C = 57351, D = 57350, H = 57356, F = 57357, P = F1, Q = F1 + 1,
  R = F1 + 2, S = F1 + 3} -- a key's letter form (CSI 1;mods X, SS3 X)
local TILDES = {[2] = 57348, [3] = 57349, [5] = 57354, [6] = 57355, [13] = F1 + 2, [15] = F1 + 4,
  [17] = F1 + 5, [18] = F1 + 6, [19] = F1 + 7, [20] = F1 + 8, [21] = F1 + 9, [23] = F1 + 10, [24] = F1 + 11}
local PASTE_START, PASTE_END = 200, 201
local MOUSE_BUTTONS = {[0] = 0, [1] = 2, [2] = 1} -- xterm's left, middle, right as OpenComputers': left 0, right 1, middle 2
local MOUSE_MOTION, MOUSE_WHEEL = 32, 64

-- The OpenComputers code of a kitty key (0: none).
local function codeOf(key)
  local code
  if key < FUNCTIONAL then
    local char = key < 0x110000 and utf8.char(key) or ""
    code = CODES[key] or CODES[char] or keyboard.keys[char]
  elseif key >= F1 and key < F1 + #F_CODES then
    code = F_CODES[key - F1 + 1]
  else
    code = EXTRA_CODES[key] or FUNCTIONAL_NAMES[key]
  end
  if type(code) == "string" then
    code = keyboard.keys[code]
  end
  return code or 0
end

-- The character a key types, when the terminal didn't say (a control key's: Enter's 13, Ctrl+C's 3).
local function charOf(key, mods)
  if key == 13 or key == 9 or key == 27 then
    return key
  elseif key == 127 then
    return 8 -- (backspace, as OpenComputers' keyboard gave it)
  elseif key >= FUNCTIONAL then
    return 0
  elseif mods & CONTROL ~= 0 then
    return key >= 97 and key <= 122 and key - 96 or 0
  elseif mods & SHIFT ~= 0 and key >= 97 and key <= 122 then
    return key - 32
  end
  return key
end

-- ---- the state ----------------------------------------------------------------------------------

local fd -- the terminal (/dev/tty), or nil: none
local saved -- its termios, as it was
local out, outsize = {}, 0
local push -- signals go to it
local width, height, maxWidth, maxHeight = 0, 0, 0, 0
local maxDepth, depth = 1, 1
local palette = {}
local fg, fgPal, bg, bgPal = WHITE, false, BLACK, false
local sentFg, sentBg -- the colors last written (nil: unknown)
local chars, attrs = {}, {} -- the shadow: a row a table, a cell its character ("" a wide one's right half; nil: not known) and its colors
local pending = "" -- bytes read and not yet decoded (a sequence's start)
local pasting -- a bracketed paste's text so far
local answers = {} -- the terminal's reports, as they came: cursor, size, largest, attributes
local typed = {} -- what each key down typed, by its code (its key_up says it too)
local held -- the mouse button held, as OpenComputers numbers it

terminal.screen = nil -- the addresses of the screen, gpu and keyboard (made for this run)
terminal.gpu = nil
terminal.keyboard = nil

local function emit(text)
  out[#out + 1] = text
  outsize = outsize + #text
  if outsize >= FLUSH_SIZE then
    terminal.flush()
  end
end

-- What's been drawn, to the terminal.
function terminal.flush()
  if fd and out[1] then
    local text = table.concat(out)
    out, outsize = {}, 0
    sys.write(fd, text)
  end
end

-- ---- colors -------------------------------------------------------------------------------------

local function distance(a, b)
  local dr = (a >> 16 & 0xFF) - (b >> 16 & 0xFF)
  local dg = (a >> 8 & 0xFF) - (b >> 8 & 0xFF)
  local db = (a & 0xFF) - (b & 0xFF)
  return 0.2126 * dr * dr + 0.7152 * dg * dg + 0.0722 * db * db
end

local function nearestPalette(rgb)
  local best, bestDistance = palette[0], math.huge
  for i = 0, 15 do
    local d = distance(rgb, palette[i])
    if d < bestDistance then
      best, bestDistance = palette[i], d
    end
  end
  return best, bestDistance
end

-- A color as the screen shows it at its depth, as OpenComputers' packed colors: one bit black or
-- white, four bits the palette's nearest, eight bits the nearer of the palette's and the cube's.
local function shown(value, isPalette)
  local rgb = isPalette and palette[value] or value
  if depth == 1 then
    return rgb == 0 and BLACK or WHITE
  elseif depth == 4 then
    return (nearestPalette(rgb))
  end
  local r = ((rgb >> 16 & 0xFF) * 5 + 127) // 255
  local g = ((rgb >> 8 & 0xFF) * 7 + 127) // 255
  local b = ((rgb & 0xFF) * 4 + 127) // 255
  local cube = (r * 255 // 5) << 16 | (g * 255 // 7) << 8 | (b * 255 // 4)
  local near, nearDistance = nearestPalette(rgb)
  return nearDistance < distance(rgb, cube) and near or cube
end

-- A cell's colors, packed: foreground and background (24 bits each), each with whether it's a
-- palette index (then the value is the index).
local function pack(f, fp, b, bp)
  return f | (fp and 1 << 24 or 0) | b << 25 | (bp and 1 << 49 or 0)
end
local function unpack(a)
  return a & 0xFFFFFF, a & 1 << 24 ~= 0, a >> 25 & 0xFFFFFF, a & 1 << 49 ~= 0
end

local function sgr(f, fp, b, bp)
  local frgb, brgb = shown(f, fp), shown(b, bp)
  if frgb ~= sentFg or brgb ~= sentBg then
    sentFg, sentBg = frgb, brgb
    emit(("\27[38;2;%d;%d;%d;48;2;%d;%d;%dm"):format(frgb >> 16 & 0xFF, frgb >> 8 & 0xFF, frgb & 0xFF,
      brgb >> 16 & 0xFF, brgb >> 8 & 0xFF, brgb & 0xFF))
  end
end

-- ---- the shadow ---------------------------------------------------------------------------------

-- Every cell not known (the screen as the gpu found it).
local function forget()
  chars, attrs = {}, {}
  for y = 1, height do
    chars[y], attrs[y] = {}, {}
  end
end

-- A cell written, the shadow mended as the terminal mends wide characters: writing over half of
-- one blanks its other half.
local function put(x, y, char, a)
  local row, colors = chars[y], attrs[y]
  if row[x] == "" and x > 1 then
    row[x - 1] = " " -- (its left half)
  elseif row[x] and row[x] ~= "" and unicode.isWide(row[x]) and x < width then
    row[x + 1] = " " -- (its right half)
  end
  row[x], colors[x] = char, a
end

-- What a character draws as: control characters as spaces (a gpu draws glyphs, never controls),
-- marks that join the one before as spaces too (a gpu gives every character its own cell).
local function printable(char)
  local ok, c = pcall(utf8.codepoint, char)
  if not ok or c < 32 or c == 127 or c >= 0x80 and c < 0xA0 then
    return " ", 1
  end
  local cells = wcwidth.of(c)
  if cells == 0 then
    return " ", 1
  end
  return char, cells
end

-- ---- the gpu ------------------------------------------------------------------------------------

local gpu = {}

local function checkInteger(n, value)
  if math.type(value) ~= "integer" then
    if type(value) == "number" and value == math.floor(value) then
      return math.tointeger(value)
    end
    error(("bad argument #%d (number expected, got %s)"):format(n, type(value)), 3)
  end
  return value
end

function gpu.bind(address, reset)
  if address ~= terminal.screen then
    return nil, "invalid address"
  end
  if reset ~= false then
    gpu.setResolution(maxWidth, maxHeight)
    gpu.setDepth(maxDepth)
    gpu.setForeground(WHITE)
    gpu.setBackground(BLACK)
  end
  return true
end

function gpu.getScreen()
  return terminal.screen
end

function gpu.getBackground()
  return bg, bgPal
end

function gpu.getForeground()
  return fg, fgPal
end

local function setColor(value, isPalette, current, currentPal)
  value = checkInteger(1, value)
  if isPalette and (value < 0 or value > 15) then
    error("invalid palette index", 3)
  end
  if currentPal then
    return palette[current], current
  end
  return current
end

function gpu.setBackground(value, isPalette)
  local old, oldIndex = setColor(value, isPalette, bg, bgPal)
  bg, bgPal = checkInteger(1, value), not not isPalette
  return old, oldIndex
end

function gpu.setForeground(value, isPalette)
  local old, oldIndex = setColor(value, isPalette, fg, fgPal)
  fg, fgPal = checkInteger(1, value), not not isPalette
  return old, oldIndex
end

function gpu.getPaletteColor(index)
  index = checkInteger(1, index)
  if not palette[index] then
    error("invalid palette index", 2)
  end
  return palette[index]
end

-- A palette color changed: the cells drawn in it, drawn again (as a screen's palette recolors them).
function gpu.setPaletteColor(index, color)
  index, color = checkInteger(1, index), checkInteger(2, color)
  if not palette[index] then
    error("invalid palette index", 2)
  end
  local old = palette[index]
  palette[index] = color
  for y = 1, height do
    for x = 1, width do
      local a = attrs[y][x]
      if a then
        local f, fp, b, bp = unpack(a)
        if fp and f == index or bp and b == index then
          sgr(f, fp, b, bp)
          emit(("\27[%d;%dH%s"):format(y, x, chars[y][x] == "" and "" or chars[y][x]))
        end
      end
    end
  end
  return old
end

function gpu.maxDepth()
  return maxDepth
end

function gpu.getDepth()
  return depth
end

function gpu.setDepth(bits)
  bits = checkInteger(1, bits)
  if not DEPTH_NAMES[bits] or bits > maxDepth then
    error("unsupported depth", 2)
  end
  local old = DEPTH_NAMES[depth]
  depth = bits
  palette = bits == 8 and grays() or table.move(FOUR_BIT, 0, 15, 0, {})
  return old
end

function gpu.maxResolution()
  return maxWidth, maxHeight
end

function gpu.getResolution()
  return width, height
end

-- The terminal resized (xterm's sequence), the cells it keeps kept; screen_resized, as a screen's.
function gpu.setResolution(w, h)
  w, h = checkInteger(1, w), checkInteger(2, h)
  if w < 1 or h < 1 or w > maxWidth or h > maxHeight then
    error("unsupported resolution", 2)
  end
  if w == width and h == height then
    return false
  end
  emit(("\27[8;%d;%dt"):format(h, w))
  for y = 1, h do
    chars[y], attrs[y] = chars[y] or {}, attrs[y] or {}
    for x = w + 1, width do
      chars[y][x], attrs[y][x] = nil, nil
    end
  end
  for y = h + 1, height do
    chars[y], attrs[y] = nil, nil
  end
  width, height = w, h
  push("screen_resized", terminal.screen, w, h)
  return true
end

function gpu.getViewport()
  return width, height
end

-- The viewport is the whole screen: a terminal shows all of itself.
function gpu.setViewport(w, h)
  w, h = checkInteger(1, w), checkInteger(2, h)
  if w ~= width or h ~= height then
    error("openos: a viewport smaller than the resolution is not supported", 2)
  end
  return false
end

function gpu.get(x, y)
  x, y = checkInteger(1, x), checkInteger(2, y)
  if x < 1 or y < 1 or x > width or y > height then
    error("index out of bounds", 2)
  end
  local char = chars[y][x]
  if char == nil then
    return " ", WHITE, BLACK -- (a cell this gpu didn't draw: blank, as the gpu would have left it)
  end
  local f, fp, b, bp = unpack(attrs[y][x])
  return char == "" and " " or char, fp and palette[f] or f, bp and palette[b] or b, fp and f or nil, bp and b or nil
end

function gpu.set(x, y, value, vertical)
  x, y = checkInteger(1, x), checkInteger(2, y)
  if type(value) ~= "string" then
    error(("bad argument #3 (string expected, got %s)"):format(type(value)), 2)
  end
  local a = pack(fg, fgPal, bg, bgPal)
  if vertical then
    for char in value:gmatch("[\0-\127\192-\255]?[\128-\191]*") do
      if char ~= "" then
        if y >= 1 and y <= height and x >= 1 and x <= width then
          local drawn, cells = printable(char)
          if cells == 2 and x == width then
            drawn, cells = " ", 1
          end
          sgr(fg, fgPal, bg, bgPal)
          emit(("\27[%d;%dH%s"):format(y, x, drawn))
          put(x, y, drawn, a)
          if cells == 2 then
            put(x + 1, y, "", a)
          end
        end
        y = y + 1
      end
    end
    return true
  end
  if y < 1 or y > height then
    return true
  end
  local text, start = {}, nil
  for char in value:gmatch("[\0-\127\192-\255]?[\128-\191]*") do
    if char ~= "" then
      local drawn, cells = printable(char)
      if x + cells - 1 > width then
        break
      end
      if x >= 1 then
        start = start or x
        text[#text + 1] = drawn
        put(x, y, drawn, a)
        if cells == 2 then
          put(x + 1, y, "", a)
        end
      end
      x = x + cells
    end
  end
  if start then
    sgr(fg, fgPal, bg, bgPal)
    emit(("\27[%d;%dH%s"):format(y, start, table.concat(text)))
  end
  return true
end

-- The part of a rectangle on the screen: its first and last column and row, or nil.
local function clip(x, y, w, h)
  local x1, y1 = math.max(x, 1), math.max(y, 1)
  local x2, y2 = math.min(x + w - 1, width), math.min(y + h - 1, height)
  if x1 > x2 or y1 > y2 then
    return nil
  end
  return x1, y1, x2, y2
end

function gpu.copy(x, y, w, h, tx, ty)
  x, y, w, h = checkInteger(1, x), checkInteger(2, y), math.max(0, checkInteger(3, w)), math.max(0, checkInteger(4, h))
  tx, ty = checkInteger(5, tx), checkInteger(6, ty)
  local x1, y1, x2, y2 = clip(x, y, w, h)
  if x1 then
    x1, y1, x2, y2 = clip(x1 + tx, y1 + ty, x2 - x1 + 1, y2 - y1 + 1) -- (where it goes, on the screen)
  end
  if not x1 then
    return true
  end
  local copied = {}
  for row = y1, y2 do
    local from = row - ty
    copied[row] = {table.move(chars[from], x1 - tx, x2 - tx, 1, {}), table.move(attrs[from], x1 - tx, x2 - tx, 1, {})}
  end
  for row = y1, y2 do
    for column = x1, x2 do
      chars[row][column] = copied[row][1][column - x1 + 1]
      attrs[row][column] = copied[row][2][column - x1 + 1]
    end
  end
  emit(("\27[%d;%d;%d;%d;1;%d;%d;1$v"):format(y1 - ty, x1 - tx, y2 - ty, x2 - tx, y1, x1))
  return true
end

function gpu.fill(x, y, w, h, char)
  x, y, w, h = checkInteger(1, x), checkInteger(2, y), checkInteger(3, w), checkInteger(4, h)
  if type(char) ~= "string" or unicode.len(char) ~= 1 then
    error("invalid fill value", 2)
  end
  local x1, y1, x2, y2 = clip(x, y, math.max(0, w), math.max(0, h))
  if not x1 then
    return true
  end
  local drawn, cells = printable(char)
  local a = pack(fg, fgPal, bg, bgPal)
  for row = y1, y2 do
    local column = x1
    while column <= x2 do
      if cells == 2 and column < x2 then
        put(column, row, drawn, a)
        put(column + 1, row, "", a)
        column = column + 2
      else
        put(column, row, cells == 2 and " " or drawn, a)
        column = column + 1
      end
    end
  end
  sgr(fg, fgPal, bg, bgPal)
  emit(("\27[%d;%d;%d;%d;%d$x"):format(utf8.codepoint(drawn), y1, x1, y2, x2))
  return true
end

-- What OpenComputers' video memory did (buffers, bitblt) has no terminal's equivalent.
for _, name in ipairs({"allocateBuffer", "freeBuffer", "freeAllBuffers", "setActiveBuffer", "getActiveBuffer",
  "buffers", "bitblt", "totalMemory", "freeMemory", "getBufferSize"}) do
  gpu[name] = function()
    error("openos: gpu." .. name .. " (video memory) is not supported", 2)
  end
end

terminal.gpuMethods = gpu

-- The screen's and the keyboard's methods.
terminal.screenMethods = {
  isOn = function() return true end,
  turnOn = function() return false, true end,
  turnOff = function() return false, true end,
  getAspectRatio = function() return 1, 1 end,
  getKeyboards = function() return {terminal.keyboard} end,
  isPrecise = function() return false end,
  setPrecise = function() error("openos: precise mouse positions are not supported", 2) end,
  isTouchModeInverted = function() return false end,
  setTouchModeInverted = function() error("openos: inverted touch mode is not supported", 2) end,
}
terminal.keyboardMethods = {}

-- ---- input --------------------------------------------------------------------------------------

local function key(code, mods, event, text)
  local oc = codeOf(code)
  local char
  if text then
    char = text
  elseif event == RELEASE then
    char = typed[oc] or charOf(code, mods)
  else
    char = charOf(code, mods)
  end
  if event == RELEASE then
    typed[oc] = nil
    push("key_up", terminal.keyboard, char, oc, nil)
  else
    typed[oc] = char
    push("key_down", terminal.keyboard, char, oc, nil)
  end
end

-- A character that came as text (not as a key's report: a paste without brackets, say): its key
-- down and up.
local function typedText(char)
  local c = utf8.codepoint(char)
  local code = codeOf(c >= 65 and c <= 90 and c + 32 or c)
  push("key_down", terminal.keyboard, c, code, nil)
  push("key_up", terminal.keyboard, c, code, nil)
end

local function numbers(field)
  local list = {}
  for part in (field or ""):gmatch("[^:]+") do
    list[#list + 1] = tonumber(part)
  end
  return list
end

-- A key's modifiers and event: "mods:event" (kitty's: mods + 1).
local function modsAndEvent(field)
  local list = numbers(field)
  return math.max((list[1] or 1) - 1, 0), list[2] or PRESS
end

local function csi(params, final)
  local private = params:match("^[?<>=]")
  local fields = {}
  for field in (params:gsub("^[?<>=]", "") .. ";"):gmatch("([^;]*);") do
    fields[#fields + 1] = field
  end
  if final == "u" and not private then
    local code = numbers(fields[1])[1]
    local mods, event = modsAndEvent(fields[2])
    local text
    if fields[3] and fields[3] ~= "" then
      text = numbers(fields[3])[1]
    end
    if code then
      key(code, mods, event, text)
    end
  elseif final == "~" and not private then
    local n = tonumber(fields[1])
    if n == PASTE_START then
      pasting = ""
    elseif TILDES[n] then
      local mods, event = modsAndEvent(fields[2])
      key(TILDES[n], mods, event)
    end
  elseif LETTERS[final] and not private and final ~= "R" then
    local mods, event = modsAndEvent(fields[2])
    key(LETTERS[final], mods, event)
  elseif final == "R" and not private then
    answers.cursor = {tonumber(fields[2]), tonumber(fields[1])} -- (a cursor report: F3 is CSI 13~ in kitty's mode)
  elseif final == "c" and private == "?" then
    local has = {}
    for _, field in ipairs(fields) do
      has[field] = true
    end
    answers.depth = has["22"] and (has["4"] and 8 or 4) or 1 -- (color, and sixel graphics: a tier 3's 24 bits)
  elseif final == "t" and not private then
    local kind, rows, columns = tonumber(fields[1]), tonumber(fields[2]), tonumber(fields[3])
    if kind == 8 then
      answers.size = {columns, rows}
    elseif kind == 9 then
      answers.largest = {columns, rows}
    end
  elseif (final == "M" or final == "m") and private == "<" then
    local b, x, y = tonumber(fields[1]) or 0, tonumber(fields[2]) or 1, tonumber(fields[3]) or 1
    if b & MOUSE_WHEEL ~= 0 then
      push("scroll", terminal.screen, x, y, b & 1 == 0 and 1 or -1, nil)
    elseif b & MOUSE_MOTION ~= 0 then
      if held then
        push("drag", terminal.screen, x, y, held, nil)
      end
    elseif final == "M" then
      held = MOUSE_BUTTONS[b & 3] or 0
      push("touch", terminal.screen, x, y, held, nil)
    else
      push("drop", terminal.screen, x, y, MOUSE_BUTTONS[b & 3] or held or 0, nil)
      held = nil
    end
  end
end

-- Bytes from the terminal, decoded: its reports become signals (or answers), a sequence cut short
-- waits for the rest.
local function feed(data)
  pending = pending .. data
  while #pending > 0 do
    if pasting then
      local stop = pending:find("\27[201~", 1, true)
      if not stop then
        pasting = pasting .. pending
        pending = ""
        break
      end
      local text = (pasting .. pending:sub(1, stop - 1)):gsub("\r\n?", "\n")
      pending = pending:sub(stop + 6)
      pasting = nil
      push("clipboard", terminal.keyboard, text, nil)
    elseif pending:sub(1, 1) == ESC then
      local second = pending:sub(2, 2)
      if second == "" then
        break -- (the rest to come)
      elseif second == "[" then
        local params, final, rest = pending:match("^\27%[([\48-\63]*)[\32-\47]*([\64-\126])(.*)$")
        if not params then
          if pending:match("^\27%[[\32-\63]*$") then
            break -- (cut short: the rest to come)
          end
          pending = pending:sub(3) -- (malformed: dropped)
        else
          pending = rest
          csi(params, final)
        end
      elseif second == "O" then
        local final = pending:sub(3, 3)
        if final == "" then
          break
        end
        pending = pending:sub(4)
        if LETTERS[final] then
          key(LETTERS[final], 0, PRESS)
        end
      else
        pending = pending:sub(2)
        key(27, 0, PRESS) -- (a lone escape: the Escape key)
        key(27, 0, RELEASE)
      end
    else
      local text = pending:match("^[^\27]+")
      local cut = #text
      local lead = text:find("[\192-\255][\128-\191]*$") -- (a character cut short at the end waits for the rest)
      if lead then
        local first = text:byte(lead)
        if #text - lead + 1 < (first >= 0xF0 and 4 or first >= 0xE0 and 3 or 2) then
          cut = lead - 1
        end
      end
      if cut == 0 then
        break
      end
      for char in text:sub(1, cut):gmatch("[\0-\127\192-\255]?[\128-\191]*") do
        if char ~= "" then
          typedText(char)
        end
      end
      pending = pending:sub(cut + 1)
    end
  end
end

-- What the terminal sent, read and decoded (the source's handler: its fd was ready).
function terminal.read()
  local data = sys.read(fd, 4096)
  if data and data ~= "" then
    feed(data)
  end
end

-- Waits for the terminal's answers to the questions asked (keys typed meanwhile are kept).
local function await(...)
  local wanted = {...}
  while true do
    local all = true
    for _, name in ipairs(wanted) do
      all = all and answers[name] ~= nil
    end
    if all then
      return
    end
    terminal.flush()
    sys.poll({[fd] = {IN = true}})
    terminal.read()
  end
end

-- ---- the terminal's modes -----------------------------------------------------------------------

-- The modes the gpu draws in: raw input, every key reported, the mouse, bracketed paste; the
-- terminal's own cursor hidden (OpenOS draws its own) and no autowrap (the gpu places every cell).
local function enter()
  saved = sys.ioctl(fd, "TCGETS")
  local raw = {}
  for name, value in pairs(saved) do
    raw[name] = value
  end
  raw.icanon, raw.echo, raw.isig, raw.icrnl, raw.vmin, raw.vtime = false, false, false, false, 0, 0
  sys.ioctl(fd, "TCSETS", raw)
  emit("\27[>" .. KEYBOARD_FLAGS .. "u\27[?1002h\27[?1006h\27[?2004h\27[?25l\27[?7l")
  sentFg, sentBg = nil, nil
end

local function leave(x, y)
  emit("\27[<u\27[?1002l\27[?1006l\27[?2004l\27[0m\27[?7h\27[?25h")
  if x then
    emit(("\27[%d;%dH"):format(y, x))
  end
  terminal.flush()
  sys.ioctl(fd, "TCSETS", saved)
end

-- Opens the process's terminal as the screen: its modes set, its size, largest and kind asked;
-- 'signals' takes what comes from it. The cursor, as the terminal says it is; nil when there's no
-- terminal.
function terminal.open(signals, addresses)
  push = signals
  fd = sys.open("/dev/tty", "r+")
  if not fd or not sys.isatty(fd) then
    fd = nil
    return nil
  end
  terminal.screen, terminal.gpu, terminal.keyboard = addresses.screen, addresses.gpu, addresses.keyboard
  enter()
  emit("\27[c\27[18t\27[19t\27[6n")
  await("depth", "size", "largest", "cursor")
  maxDepth, depth = answers.depth, answers.depth
  palette = depth == 8 and grays() or table.move(FOUR_BIT, 0, 15, 0, {})
  width, height = answers.size[1], answers.size[2]
  maxWidth, maxHeight = answers.largest[1], answers.largest[2]
  forget()
  return answers.cursor[1], answers.cursor[2]
end

function terminal.fd()
  return fd
end

-- Back to how the terminal was, the cursor at x, y (the tty's), for a command to run or at the end.
function terminal.suspend(x, y)
  if fd then
    leave(x, y)
  end
end

-- After a command ran on it: the modes again, and where its cursor is now; what's on the screen
-- isn't the gpu's any more.
function terminal.resume()
  if not fd then
    return nil
  end
  enter()
  answers.cursor = nil
  emit("\27[6n")
  await("cursor")
  forget()
  return answers.cursor[1], answers.cursor[2]
end

function terminal.close(x, y)
  if fd then
    leave(x, y)
    sys.close(fd)
    fd = nil
  end
end

return terminal
