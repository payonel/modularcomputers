-- A clock in the top right corner of the terminal, kept there while you work: lua terminal-clock.lua &
--
-- A program draws on its terminal by writing escape sequences to it, as on any VT100 or xterm:
-- ESC 7 saves the cursor, ESC [ row ; column H moves it, ESC [ 7 m turns on reverse video, ESC [ 0 m
-- turns it off, and ESC 8 puts the cursor back where it was, so whatever you are typing is not
-- disturbed. The terminal's width comes from the tty (TIOCGWINSZ), asked again at each redraw, so
-- the clock follows a resize. It redraws once a second of game time (sys.sleep blocks meanwhile).
local sys = require("sys")

local ESC = "\27"
local EVERY = 1             -- seconds between redraws
local FORMAT = "Day %d %R"  -- os.date's: the world's day and its time, hours and minutes

local TTY = 1  -- standard output: the terminal it was started from
assert(sys.isatty(TTY), "the clock needs a terminal")

while true do
  local columns = sys.ioctl(TTY, "TIOCGWINSZ")
  local text = " " .. os.date(FORMAT) .. " "
  sys.write(TTY, ESC .. "7" .. ESC .. "[1;" .. math.max(1, columns - #text + 1) .. "H"
    .. ESC .. "[7m" .. text .. ESC .. "[0m" .. ESC .. "8")
  sys.sleep(EVERY)
end
