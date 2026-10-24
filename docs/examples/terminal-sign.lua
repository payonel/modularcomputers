-- Put up a notice on a screen: lua terminal-sign.lua terminal0 "Back in five minutes"
--
-- The program writes to the terminal itself, through its file's write ioctl (the terminal's
-- method), not through a tty: so it works from a service with no terminal of its own too. A
-- terminal's writes are raw (no tty turns "\n" into "\r\n"), and the program places every line
-- with the cursor-position sequence anyway. It clears the screen, draws a framed box in the middle
-- in bright yellow on blue, and hides the cursor. getSize says how big the screen is.
local sys = require("sys")

local ESC = "\27"
local COLORS = ESC .. "[1;33;44m"  -- bold, yellow text, blue background
local PLAIN = ESC .. "[0m"
local PADDING = 2                  -- spaces between the message and the frame

local name, message = arg[1], arg[2]
if not (name and message) then
  io.stderr:write("usage: terminal-sign.lua TERMINAL MESSAGE\n")
  os.exit(2)
end

local fd = assert(sys.open("/dev/" .. name))
local columns, rows = sys.ioctl(fd, "getSize")
assert(columns, rows)

local width = math.min(#message + 2 * PADDING + 2, columns)
message = message:sub(1, width - 2 - 2 * PADDING)
local inner = width - 2
local top = (rows - 3) // 2 + 1
local left = (columns - width) // 2 + 1

local function at(row, text)
  return ESC .. "[" .. row .. ";" .. left .. "H" .. text
end

local edge = "+" .. ("-"):rep(inner) .. "+"
local middle = "|" .. (" "):rep((inner - #message) // 2) .. message
middle = middle .. (" "):rep(inner + 1 - #middle) .. "|"

assert(sys.ioctl(fd, "write", ESC .. "[2J" .. ESC .. "[?25l" .. COLORS
  .. at(top, edge) .. at(top + 1, middle) .. at(top + 2, edge) .. PLAIN))
sys.close(fd)
