-- Run a file of server commands, a line each, and log how each went:
-- lua operator-script.lua setup.txt [log]
--
-- Blank lines and lines starting with # are skipped. Each command goes to the operator card's
-- command method (an ioctl on its file, /dev/operator0), which answers a table: success, result
-- (the command's value) and output (what the server said). The log gets a line per command; the
-- first command that fails stops the run, and the exit status is 1, so a shell script can tell.
local sys = require("sys")

local LOG = "/home/operator.log"  -- where the log goes, unless named

local script, logpath = arg[1], arg[2] or LOG
if not script then
  io.stderr:write("usage: operator-script.lua FILE [LOG]\n")
  os.exit(2)
end

local input = assert(io.open(script))
local log = assert(io.open(logpath, "a"))
local card = assert(sys.open("/dev/operator0"))

local status = 0
local number = 0
for line in input:lines() do
  number = number + 1
  local command = line:match("^%s*(.-)%s*$")
  if command ~= "" and command:sub(1, 1) ~= "#" then
    local answer, why = sys.ioctl(card, "command", command)
    if not answer then
      log:write(("%d: %s: error: %s\n"):format(number, command, why))
      status = 1
    else
      local said = answer.output:gsub("\n", " "):gsub("%s+$", "")
      log:write(("%d: %s: %s %d %s\n"):format(number, command, answer.success and "ok" or "FAILED", answer.result, said))
      if not answer.success then
        status = 1
      end
    end
    if status ~= 0 then
      io.stderr:write("operator-script: stopped at line ", number, "\n")
      break
    end
  end
end

sys.close(card)
input:close()
log:close()
os.exit(status)
