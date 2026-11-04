-- An energy field card's driver, for its /dev file (as a sysfs attribute: text, a write taking
-- effect at once). A read gives what it's doing, what it holds and where, in its computer's frame:
--   holding minecraft:pig 0.0 0.0 3.0   (forward right up)
--   idle
-- A line written calls the method it names:
--   capture self | capture ID | capture forward [right [up]]
--   move forward [right [up]]
--   release
--   stop
-- A move and a release wait for the move before (commands); a stop never waits: it ends a move
-- under way where it is, holding on (to let go at once: stop, then release). How a move went comes as
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
    if (action == "release" or action == "stop") and #words == 1 then
      return action
    elseif action == "capture" and #words == 2 and not number(words[2]) then
      return "capture", words[2]
    elseif (action == "capture" or action == "move") and #words >= 2 and #words <= 4 then
      local at = point(words, 2)
      if not at then
        return nil
      end
      if action == "move" then
        return "move", at[1], at[2], at[3]
      end
      return "capture", at
    end
  end,
}
