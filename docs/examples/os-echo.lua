-- /init.lua: an OS that reads a line from the keyboard and says it back, forever. Typing "off"
-- turns the computer off. It shows the two calls every OS is built on: waiting for a device
-- (board.waitForInterrupt) and calling one of its methods (board.invoke).

-- Writes text to every screen. A terminal moves down a row on \n, but only \r goes back to the
-- left, so each \n is sent as \r\n.
local function write(text)
  board.invoke("console", "write", (text:gsub("\n", "\r\n")))
end

-- Reads a line: shows what's typed, takes Backspace, ends at Enter.
local function readline(prompt)
  write(prompt)
  local line = ""
  local escape = false -- in the middle of a key's escape sequence (an arrow key sends ESC [ A)
  while true do
    -- Sleep until a key is pressed: the console's interrupt goes up when it has keys.
    board.waitForInterrupt(nil, "console")
    -- Take what was typed: a string of bytes, or nil if someone else took it first.
    local ok, keys = board.invoke("console", "read")
    if ok and keys then
      for i = 1, #keys do
        local key = keys:sub(i, i)
        local byte = key:byte()
        if escape then
          escape = not key:match("[%a~]") -- a sequence ends at a letter or ~
        elseif byte == 27 then
          escape = true -- ESC: a key this OS doesn't use (arrows, F-keys): skipped
        elseif key == "\r" then
          write("\n")
          return line
        elseif byte == 127 or byte == 8 then
          if #line > 0 then
            line = line:sub(1, -2)
            write("\b \b") -- back, blank it out, back again
          end
        elseif byte >= 32 then
          line = line .. key
          write(key) -- the screen shows nothing by itself: the OS echoes what you type
        end
      end
    end
  end
end

write("Type something. Type off to turn the computer off.\n")
while true do
  local line = readline("> ")
  if line == "off" then
    board.poweroff()
  end
  write("you typed: " .. line .. "\n")
end
