-- Endless day: at dusk, set the world's time back to morning and clear the weather.
-- lua operator-daylight.lua &
--
-- The operator card's file runs a line written to it as a server command, as a command block
-- would, at once. A command that fails fails the write, and the card's get (an ioctl on the same
-- file) gives its last answer: what the server said. The world's clock is the computer's (os.date's
-- hours are the game's), so the program looks once a minute of game time and sleeps in between.
local sys = require("sys")

local DUSK = 18          -- the hour (of the game's 24) after which it is time to act
local CHECK = 50         -- seconds between looks (a game hour is 50 seconds)
local COMMANDS = {"time set day", "weather clear"}

local card = assert(io.open("/dev/operator0", "w"))

while true do
  if os.date("*t").hour >= DUSK then
    for _, command in ipairs(COMMANDS) do
      card:write(command, "\n")
      local ok = card:flush()
      if not ok then
        local answer = sys.ioctl(sys.fileno(card), "get")
        io.stderr:write("daylight: ", command, ": ", answer and answer.message or "?\n")
      end
    end
  end
  sys.sleep(CHECK)
end
