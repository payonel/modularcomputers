-- The robotics device's driver, for its /dev file (as a sysfs attribute: text, a write taking
-- effect at once). A read gives what it's doing now, a word: idle, walking, turning or breaking.
-- A line written is an action:
--   walk forward [right [up]]   (blocks from where it is, in its own frame)
--   turn left|right|around|degrees
--   use FACE                    (front, back, left, right, up or down: its own)
--   swing FACE
--   stop
-- How an action went comes as an event (poll's PRI; the pull ioctl: arrived, blocked, turned,
-- stopped, broke, failed). Its scan, its hand and containers are ioctls (scan, held, take...).
local function number(word)
  return word and tonumber(word)
end

return {
  get = function(now)
    return now.state .. "\n"
  end,
  line = function(text)
    local words = {}
    for word in text:gmatch("%S+") do
      words[#words + 1] = word
    end
    local action = words[1]
    if action == "walk" and number(words[2]) and #words <= 4 then
      local right, up = number(words[3]), number(words[4])
      if (words[3] and not right) or (words[4] and not up) then
        return nil
      end
      return {action = "walk", forward = number(words[2]), right = right or 0, up = up or 0}
    elseif action == "turn" and #words == 2 then
      return {action = "turn", way = number(words[2]) or words[2]}
    elseif (action == "use" or action == "swing") and #words == 2 then
      return {action = action, face = words[2]}
    elseif action == "stop" and #words == 1 then
      return {action = "stop"}
    end
  end,
}
