-- rsh's protocol, for rsh, rshd and rcp: one TCP connection to port 514, framed both ways (a
-- frame: its kind, a byte, then its data with a 2-byte length), so a command's output, its errors
-- and how it ended stay apart on one stream. BSD's rsh used a second connection for errors and
-- never said how the command ended; this one does (as ssh does).
--
-- From rsh: REQUEST first (the command, empty for a login on a pty, then "\0", then the columns and
-- rows of rsh's terminal), then INPUT (what it reads), END (its input ended), SIZE (its terminal's
-- size changed: "columns rows"). From rshd: OUTPUT, ERRORS, EXIT ("exited 0", "killed SIGHUP"),
-- the last thing it sends. Finding a host: rsh broadcasts PROBE to UDP port 514; each rshd answers
-- HERE, from its card's address.
local rsh = {
  PORT = 514,
  PROBE = "rsh?", HERE = "rshd",
  REQUEST = "H", INPUT = "I", END = "E", SIZE = "W",
  OUTPUT = "O", ERRORS = "R", EXIT = "X",
  CHUNK = 4096, -- the most data a frame carries (what one read gives)
}

-- A frame of 'kind' carrying 'data'.
function rsh.frame(kind, data)
  return kind .. string.pack(">s2", data or "")
end

-- Takes a stream's bytes as they come and gives the frames whole: feed(data), then next() until it
-- gives nil (kind, data each).
function rsh.decoder()
  local held = ""
  local decoder = {}
  function decoder.feed(data)
    held = held .. data
  end
  function decoder.next()
    if #held < 3 then
      return nil
    end
    local length = string.unpack(">I2", held, 2)
    if #held < 3 + length then
      return nil
    end
    local kind, data = held:sub(1, 1), held:sub(4, 3 + length)
    held = held:sub(4 + length)
    return kind, data
  end
  return decoder
end

-- Writes a frame of each piece of 'data' (CHUNK at most) to 'fd'; false when the connection's gone.
function rsh.send(sys, fd, kind, data)
  local at = 1
  repeat
    local piece = data and data:sub(at, at + rsh.CHUNK - 1) or ""
    if not sys.write(fd, rsh.frame(kind, piece)) then
      return false
    end
    at = at + rsh.CHUNK
  until not data or at > #data
  return true
end

return rsh
