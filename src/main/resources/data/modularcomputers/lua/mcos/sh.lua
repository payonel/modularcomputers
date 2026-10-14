-- sh: the shell, for now: a prompt, a line read, and the line echoed back. "halt" halts the
-- computer; "poweroff" ends the shell, as does an end of file (Ctrl+D); the kernel starts another.
local PROMPT = "$ "

while true do
  io.write(PROMPT)
  local line = io.read()
  if line == nil or line == "poweroff" then
    return
  elseif line == "halt" then
    board.halt()
  end
  io.write(line, "\n")
end
