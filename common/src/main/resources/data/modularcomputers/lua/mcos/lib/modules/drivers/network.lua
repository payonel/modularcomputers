-- A network card's driver, for its /dev file: its traffic, as tcpdump shows a card's, a line a
-- frame, out (a send's, a broadcast's) or in (one that arrived): the other card's address (or
-- broadcast), how far it is, the frame's size, and how it went (taken; refused, its why and its
-- fields' values; a broadcast's: how many took it; one that came as a broadcast: broadcast), e.g.
--   out 5f0c2a9e-... 3.0m 42 taken
--   out 5f0c2a9e-... 3.0m 42 limit frames 32
--   out 9a1b2c3d-... - 42 network unreachable no card in reach
--   out broadcast - 17 2 took it
--   in 7e3d1f00-... 12.5m 17 broadcast
-- (The card keeps only its last few frames: someone reading sees what's current.)
local NO_DISTANCE = -1 -- a send's distance when no card was reached

-- A failure's fields, in the order its why's shape gives them (the card's: limit, network).
local FIELDS = {"of", "max", "reason", "message"}

-- How a frame went, as words.
local function went(entry)
  if entry.event == "broadcast" then
    return entry.took .. " took it"
  elseif entry.ok then
    return entry.broadcast and "broadcast" or "taken"
  end
  local words = {entry.why}
  for _, field in ipairs(FIELDS) do
    local value = entry[field]
    if value ~= nil and value ~= "" then
      words[#words + 1] = tostring(value)
    end
  end
  return table.concat(words, " ")
end

return {
  get = function(entry)
    local direction = entry.event == "arrived" and "in" or "out"
    local address = entry.event == "broadcast" and "broadcast" or entry.address
    local distance = (entry.distance == nil or entry.distance == NO_DISTANCE) and "-" or entry.distance .. "m"
    return direction .. " " .. address .. " " .. distance .. " " .. entry.bytes .. " " .. went(entry) .. "\n"
  end,
}
