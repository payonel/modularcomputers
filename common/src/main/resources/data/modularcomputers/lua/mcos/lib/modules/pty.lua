-- pty: pseudo-terminals, as Linux's (openpty(3)), loaded at the first sys.openpty() (nothing of it
-- is in memory until then). A pty is a tty whose terminal is a program: its master end. What the
-- master writes is typed (it goes through the tty's line discipline: echo, line editing, ^C), and
-- what's shown on the terminal (the terminal end's output, and echo) is what the master reads. The
-- terminal end is a tty file like any other (a shell on it is interactive; stty works).
--
-- A writer to the terminal end waits while the master holds BUFFER bytes unread (echo is taken
-- anyway). The master reads EIO once no file is open on the terminal end (its poll: HUP), as
-- Linux's. Closing the master hangs the line up: SIGHUP to its foreground group, and from then the
-- terminal end reads as ended and its writes fail (EIO).
local k, vfs, tty = ...

local BUFFER = 4096 -- what the master holds unread before a writer to the terminal end waits

local fail = vfs.fail

-- A new pty: (its master end, its terminal end), each an open file description.
return function()
  local shown, held = {}, 0 -- output for the master, in pieces, and its bytes
  local t
  t = tty.new(nil, k.ttysignal, function(text, echo)
    if held >= BUFFER and not echo then
      return k.BLOCK
    end
    shown[#shown + 1] = text
    held = held + #text
    return #text
  end)
  t.ends = 0 -- files open on the terminal end
  function t:opened()
    self.ends = self.ends + 1
  end
  function t:closed()
    self.ends = self.ends - 1
  end
  local master = {type = "pty", tty = t}
  function master.events()
    return {IN = held > 0, OUT = true, HUP = held == 0 and t.ends == 0}
  end
  function master.read(self, n)
    if held == 0 then
      if t.ends == 0 then
        return fail("EIO")
      end
      return k.BLOCK
    end
    local data = table.concat(shown)
    shown = {}
    if #data > n then
      shown[1] = data:sub(n + 1)
      data = data:sub(1, n)
    end
    held = #data < held and held - #data or 0
    return data
  end
  function master.write(self, data)
    t:receive(data)
    return #data
  end
  function master.close()
    t.hungup = true
    k.ttysignal(t, "SIGHUP")
    return true
  end
  return master, k.ttyfile(t)
end
