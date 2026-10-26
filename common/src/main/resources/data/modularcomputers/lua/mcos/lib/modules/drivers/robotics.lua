-- The robotics device's driver, for its /dev file (as a sysfs attribute: text, a write taking
-- effect at once). A read gives what it's doing now, a word (idle, walking, turning, breaking,
-- flying, hovering or landing), then where it is: "position forward right up heading" (from its
-- origin).
-- A line written is an action:
--   walk forward [right [up]]   (whole blocks from where it is, in its own frame)
--   walkto forward [right [up]] (whole blocks from its origin, in the origin's frame)
--   fly forward [right [up]]    (on its thrusters: its legs left standing where it was)
--   flyto forward [right [up]]  (from its origin)
--   hover
--   land                        (onto its legs, below it)
--   turn left|right|around
--   use FACE                    (front, back, left, right, up or down: its own)
--   swing FACE
--   stop
--   position [forward [right [up [heading]]]]  (says where it is, its origin moved to make it so; none: its origin here)
--   pathfinding on|off
-- How an action went comes as an event (poll's PRI; the pull ioctl: arrived, blocked, turned,
-- stopped, landed, broke, failed). Its scan, its hand and containers are ioctls (scan, held, take...).
local function number(word)
  return word and tonumber(word)
end

local MOVES = {walk = "walk", walkto = "walkTo", fly = "fly", flyto = "flyTo"}

return {
  get = function(now)
    local at = now.position
    if not at then
      return now.state .. "\n"
    end
    return now.state .. "\nposition " .. at.forward .. " " .. at.right .. " " .. at.up .. " " .. at.heading .. "\n"
  end,
  line = function(text)
    local words = {}
    for word in text:gmatch("%S+") do
      words[#words + 1] = word
    end
    local action = words[1]
    if MOVES[action] and number(words[2]) and #words <= 4 then
      local right, up = number(words[3]), number(words[4])
      if (words[3] and not right) or (words[4] and not up) then
        return nil
      end
      return {action = MOVES[action], forward = number(words[2]), right = right or 0, up = up or 0}
    elseif action == "turn" and #words == 2 then
      return {action = "turn", way = words[2]}
    elseif (action == "use" or action == "swing") and #words == 2 then
      return {action = action, face = words[2]}
    elseif (action == "stop" or action == "hover" or action == "land") and #words == 1 then
      return {action = action}
    elseif action == "position" and #words <= 5 then
      local at = {action = "setPosition"}
      for i, field in ipairs({"forward", "right", "up", "heading"}) do
        if words[i + 1] then
          at[field] = number(words[i + 1])
          if not at[field] then
            return nil
          end
        end
      end
      return at
    elseif action == "pathfinding" and (words[2] == "on" or words[2] == "off") and #words == 2 then
      return {action = "pathfinding", on = words[2] == "on"}
    end
  end,
}
