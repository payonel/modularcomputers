-- A network card's driver, for its /dev file: its traffic, as tcpdump shows a card's, a line a
-- frame, in or out: the other card's address (or broadcast), how far it is, the frame's size, and
-- how it went (taken, busy, unreachable; a broadcast's: how many took it), e.g.
--   out 5f0c2a9e-... 3.0m 42 taken
--   in 5f0c2a9e-... 3.0m 17 taken
--   out broadcast - 4 2 took it
-- (The card keeps only its last few frames: someone reading sees what's current.)
return {
  get = function(entry)
    return entry.direction .. " " .. entry.address .. " " .. (entry.distance and entry.distance .. "m" or "-") .. " " .. entry.bytes .. " "
      .. entry.outcome .. "\n"
  end,
}
