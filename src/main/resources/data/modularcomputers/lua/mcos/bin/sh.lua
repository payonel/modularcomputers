-- sh: a basic shell for now. It reads what's typed at the console and echoes it back: Enter
-- starts a new line, Backspace takes back what was typed on this one; other keys that aren't
-- printable (arrows, other escape sequences) aren't echoed.
local term = mcos.term

local PROMPT = "$ "
local typed = 0 -- characters typed on this line (Backspace stops at the prompt)

term.write(PROMPT)
while true do
  board.waitForInterrupt(nil, "console")
  while true do
    local _, key = board.invoke("console", "read")
    if key == nil then
      break
    end
    if key == "\r" or key == "\n" then
      term.write("\n" .. PROMPT)
      typed = 0
    elseif key == "\127" or key == "\b" then
      if typed > 0 then
        term.write("\b \b")
        typed = typed - 1
      end
    elseif #key == 1 and key:byte() >= 32 then
      term.write(key)
      typed = typed + 1
    end
  end
end
