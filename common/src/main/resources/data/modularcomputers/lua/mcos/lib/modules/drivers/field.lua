-- An energy field card's driver, for its /dev file (as a sysfs attribute: text, a write taking
-- effect at once). A read gives what it's doing, what it holds and where, in its computer's frame:
--   holding minecraft:pig 0.0 0.0 3.0   (forward right up)
--   idle
-- A line written is an action:
--   capture self | capture ID | capture forward [right [up]]
--   move forward [right [up]]
--   release
-- A move waits for the one before (a command); a release never waits. How a move went comes as
-- an event (poll's PRI; the pull ioctl: {event = "move", ok, why}), and losing what it held as
-- {event = "lost", why}.
local function number(word)
  return word and tonumber(word)
end

-- The numbers in words[from], words[from + 1], words[from + 2] (missing: 0), or nil if any isn't one.
local function point(words, from)
  local got = {}
  for i = 0, 2 do
    local word = words[from + i]
    if word and not number(word) then
      return nil
    end
    got[i + 1] = number(word) or 0
  end
  return got
end

return {
  get = function(now)
    if now.name then
      return now.state .. " " .. now.name .. " " .. now.forward .. " " .. now.right .. " " .. now.up .. "\n"
    end
    return now.state .. "\n"
  end,
  line = function(text)
    local words = {}
    for word in text:gmatch("%S+") do
      words[#words + 1] = word
    end
    local action = words[1]
    if action == "release" and #words == 1 then
      return {action = "release"}
    elseif action == "capture" and #words == 2 and not number(words[2]) then
      return {action = "capture", target = words[2]}
    elseif (action == "capture" or action == "move") and #words >= 2 and #words <= 4 then
      local at = point(words, 2)
      if not at then
        return nil
      end
      if action == "move" then
        return {action = "move", forward = at[1], right = at[2], up = at[3]}
      end
      return {action = "capture", target = at}
    end
  end,
}
