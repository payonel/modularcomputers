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
  PROBE_SECONDS = 0.5, -- how long a probe listens for rshds answering
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

-- The address of the computer 'name' names: its card's whole id, or the one rshd in reach whose
-- id starts with it.
function rsh.resolve(sys, name)
  name = name:gsub("^id:", ""):gsub("%*$", "")
  if name:match("^%x+%-%x+%-%x+%-%x+%-%x+$") then
    return name
  end
  local probe = assert(sys.socket())
  local ok, why = sys.sendto(probe, rsh.PROBE, "broadcast", rsh.PORT)
  if not ok then
    return nil, why
  end
  local found, seen = {}, {}
  local waited, step = 0, rsh.PROBE_SECONDS / 5
  while waited < rsh.PROBE_SECONDS do
    if next(sys.poll({[probe] = {IN = true}}, step)) then
      local data, from = sys.recvfrom(probe)
      if data == rsh.HERE and not seen[from] and from:sub(1, #name) == name then
        seen[from] = true
        found[#found + 1] = from
      end
    else
      waited = waited + step
    end
  end
  sys.close(probe)
  if #found == 0 then
    return nil, "unknown host"
  elseif #found > 1 then
    table.sort(found)
    return nil, "ambiguous: " .. table.concat(found, ", ")
  end
  return found[1]
end

return rsh
