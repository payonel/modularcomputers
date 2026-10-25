-- /etc/rc.d/visitors, every action written out: what rc does for a service when its definition
-- only sets command (start, stop, restart, status), done here by the service's own functions, and
-- an action of its own (rc visitors count). rc runs the one named; a function this file doesn't
-- define, rc does itself. 'name' is the service's name (visitors), set by rc.
local sys = require("sys")

description = "logs what the motion sensor sees"
command = "/bin/lua"
command_args = {"/home/visitors.lua"}
output_log = "/tmp/visitors.out"

local LOG = "/home/visitors.log" -- what the program writes
local PIDFILE = "/run/" .. name .. ".pid"
local STOP_SECONDS = 5 -- how long it has to end after SIGTERM, before SIGKILL
local STOP_POLL = 0.1
local NOT_RUNNING = 3 -- status's answer when it isn't running (LSB's)

-- Its process id while it runs, from its pid file; else nil.
local function running()
  local file = io.open(PIDFILE)
  if not file then
    return nil
  end
  local pid = math.tointeger(tonumber(file:read("l") or ""))
  file:close()
  for _, process in ipairs(sys.processes()) do
    if process.pid == pid and process.state ~= "zombie" then
      return pid
    end
  end
end

-- rc visitors start: runs it as a daemon (a session of its own, so no terminal's Ctrl+C reaches it;
-- its input /dev/null, its output and errors to output_log), its pid in /run/visitors.pid.
function start()
  if running() then
    print(name .. " is already running")
    return 0
  end
  local null = assert(sys.open("/dev/null"))
  local out = assert(sys.open(output_log, "a"))
  local pid = assert(sys.spawn(command, command_args, {setsid = true, fds = {[0] = null, [1] = out, [2] = out}}))
  sys.close(null)
  sys.close(out)
  local file = assert(io.open(PIDFILE, "w"))
  file:write(pid, "\n")
  file:close()
  return 0
end

-- rc visitors stop: SIGTERM, then SIGKILL if it hasn't ended in STOP_SECONDS.
function stop()
  local pid = running()
  if not pid then
    print(name .. " is not running")
    return 0
  end
  sys.kill(pid, "SIGTERM")
  local waited = 0
  while running() and waited < STOP_SECONDS do
    sys.sleep(STOP_POLL)
    waited = waited + STOP_POLL
  end
  if running() then
    sys.kill(pid, "SIGKILL")
  end
  sys.unlink(PIDFILE)
  return 0
end

-- rc visitors restart.
function restart()
  stop()
  return start()
end

-- rc visitors status: says whether it runs; 0 when it does, 3 when not.
function status()
  local pid = running()
  print(name .. (pid and " is running (pid " .. pid .. ")" or " is stopped"))
  return pid and 0 or NOT_RUNNING
end

-- rc visitors count: an action of its own: how many visitors its log has.
function count()
  local seen = 0
  local file = io.open(LOG)
  if file then
    for line in file:lines() do
      if not line:find(" stopped$") then
        seen = seen + 1
      end
    end
    file:close()
  end
  print(seen .. " visitors")
  return 0
end
