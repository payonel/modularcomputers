-- Click on the screen: each click (and wheel notch) in the screen view is reported where it was,
-- until q is typed. The program asks the terminal for the mouse as on any xterm: ESC[?1000h
-- (presses and releases), ESC[?1006h (SGR's encoding: ESC[<button;column;rowM, m for a release),
-- and reads them as typed input, raw.
local sys = require("sys")

local saved = io.popen("stty -g"):read("l")
os.execute("stty raw -echo")
io.write("\27[?1000h\27[?1006h", "click anywhere (q quits)\r\n")
io.stdout:flush()

local BUTTONS = {[0] = "left", [1] = "middle", [2] = "right", [64] = "wheel up", [65] = "wheel down"}
local pending = ""
while true do
  pending = pending .. assert(sys.read(0, 64))
  if pending:find("q", 1, true) then
    break
  end
  -- Every whole report in what's come so far.
  for button, column, row, kind in pending:gmatch("\27%[<(%d+);(%d+);(%d+)([Mm])") do
    local what = BUTTONS[tonumber(button) & ~28] or ("button " .. button) -- (28: the modifiers' bits)
    io.write(what, kind == "m" and " released" or "", " at column ", column, ", row ", row, "\r\n")
  end
  pending = pending:match("\27%[<[%d;]*$") or "" -- (keep a report still coming)
  io.stdout:flush()
end

io.write("\27[?1006l\27[?1000l")
os.execute("stty " .. saved)
