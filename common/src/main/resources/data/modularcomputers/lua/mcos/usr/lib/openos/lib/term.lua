-- term: OpenOS's term library (ported from OpenOS, MIT: see /usr/share/openos/LICENSE), over its tty.
local tty = require("tty")
local computer = require("computer")
local event = require("event")
local core_cursor = require("core/cursor")

local kb = require("keyboard")
local keys = kb.keys

local term = setmetatable({internal={}}, {__index=tty})

-- One window: the program's terminal (OpenOS's windows were its processes', for its pty-like
-- windows; there's no process library here).
local function as_window(window, func, ...)
  if window then
    error("openos: term windows are not supported", 3)
  end
  return func(...)
end

function term.internal.open()
  error("openos: term windows are not supported", 2)
end

local function create_cursor(history, ops)
  local cursor = history or {}
  cursor.hint = ops.hint or cursor.hint

  local filter = ops.filter or cursor.filter
  if filter then
    if type(filter) == "string" then
      local filter_text = filter
      filter = function(text)
        return text:match(filter_text)
      end
    end

    function cursor:handle(name, char, code)
      if name == "key_down" and (code == keys.enter or code == keys.numpadenter) then
        if not filter(self.data) then
          computer.beep(2000, 0.1)
          return true -- handled
        end
      end
      return self.super.handle(self, name, char, code)
    end
  end

  local pwchar = ops.pwchar or cursor.pwchar
  local nobreak = ops.dobreak == false or cursor.dobreak == false
  if pwchar or nobreak then
    if type(pwchar) == "string" then
      local pwchar_text = pwchar
      pwchar = function(text)
        return text:gsub(".", pwchar_text)
      end
    end
    function cursor:echo(arg, ...)
      if pwchar and type(arg) == "string" and #arg > 0 and not arg:match("^\27") then -- "" is used for scrolling
        arg = pwchar(arg)
      elseif nobreak and arg == "\n" then
        arg = ""
      end
      return self.super.echo(self, arg, ...)
    end
  end

  return core_cursor.new(cursor, cursor.nowrap and core_cursor.horizontal)
end

-- cannot use term.write = io.write because io.write invokes metatable
function term.write(value, wrap)
  io.stdout:flush()
  local previous_nowrap = tty.window.nowrap
  tty.window.nowrap = wrap == false
  io.write(value)
  io.stdout:flush()
  tty.window.nowrap = previous_nowrap
end

function term.read(history, dobreak, hint, pwchar, filter)
  tty.window.cursor = create_cursor(history, {
    dobreak = dobreak,
    pwchar = pwchar,
    filter = filter,
    hint = hint
  })
  return io.stdin:readLine(false)
end

function term.getGlobalArea()
  local w,h,dx,dy = tty.getViewport()
  return dx+1,dy+1,w,h
end

function term.clearLine()
  term.write("\27[2K\27[999D")
end

function term.setCursorBlink(enabled)
  tty.window.blink = enabled
end

function term.getCursorBlink()
  return tty.window.blink
end

function term.pull(...)
  local args = table.pack(...)
  local timeout = math.huge
  if type(args[1]) == "number" then
    timeout = computer.uptime() + table.remove(args, 1)
    args.n = args.n - 1
  end
  local cursor = core_cursor.new()
  while timeout >= computer.uptime() do
    cursor:echo()
    local s = table.pack(event.pull(.5, table.unpack(args, 1, args.n)))
    cursor:echo(not s[1])
    if s.n > 1 then return table.unpack(s, 1, s.n) end
  end
end

function term.bind(gpu, window)
  return as_window(window, tty.bind, gpu)
end

term.scroll = tty.stream.scroll
term.internal.run_in_window = as_window

return term
