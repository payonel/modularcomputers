-- The robotics device's driver, for its /dev file (as a sysfs attribute: text, a write taking
-- effect as it can). A read gives what it's doing now, a word (idle, walking, turning, breaking,
-- flying, hovering, landing, falling or held), then where it really is: "position
-- forward right up heading" (from its origin), where its last command left it ("planned ...") and,
-- when it's away from there, "drift forward right up".
-- A line written calls the method it names (walkto: walkTo, position: setPosition, pathfinding:
-- setPathfinding); the device does one at a time, so a line given while one runs
-- waits for it (the writing thread only), but a stop, which never waits:
--   walk [forward [right [up]]] (from where its last command left it, in its own frame, ending
--                                squared; none: back there)
--   walkto forward [right [up]] (whole blocks from its origin, in the origin's frame)
--   fly [forward [right [up]]]  (on its thrusters: its legs left standing where it was; none: take
--                                off where it stands, or back to where its last command left it)
--   flyto forward [right [up]]  (from its origin)
--   land                        (onto its legs, below it)
--   land force                  (with no legs there, straight down onto the ground without them)
--   turn left|right|around|north|south|east|west
--   use FACE                    (front, back, left, right, up or down: its own)
--   swing FACE
--   stop                        (where it is becomes where its last command left it; falling, a fly
--                                then catches it there)
--   position [forward [right [up [heading]]]]  (says where it is, its origin moved to make it so; none: its origin here)
--   pathfinding on|off
-- How an action that runs over time went comes as an event named after it (poll's PRI; the pull
-- ioctl: {event = "walk", ok, why}), and notices of what no program asked for ({event = "notice",
-- why = "shove", drift}, {event = "notice", why = "fall", drift, below}; a field's frozen, thawed).
-- containers are ioctls (scan, held, take...).
local function number(word)
  return word and tonumber(word)
end

local MOVES = {walk = "walk", walkto = "walkTo", fly = "fly", flyto = "flyTo"}

return {
  get = function(now)
    local text = now.state .. "\n"
    local at, plan, drift = now.position, now.planned, now.drift
    if at then
      text = text .. "position " .. at.forward .. " " .. at.right .. " " .. at.up .. " " .. at.heading .. "\n"
    end
    if plan then
      text = text .. "planned " .. plan.forward .. " " .. plan.right .. " " .. plan.up .. " " .. plan.heading .. "\n"
    end
    if drift then
      text = text .. "drift " .. drift.forward .. " " .. drift.right .. " " .. drift.up .. "\n"
    end
    return text
  end,
  line = function(text)
    local words = {}
    for word in text:gmatch("%S+") do
      words[#words + 1] = word
    end
    local action = words[1]
    if (action == "walk" or action == "fly") and #words == 1 then
      return action -- (back to where its last command left it; fly: off where it stands)
    elseif MOVES[action] and number(words[2]) and #words <= 4 then
      local right, up = number(words[3]), number(words[4])
      if (words[3] and not right) or (words[4] and not up) then
        return nil
      end
      return MOVES[action], number(words[2]), right or 0, up or 0
    elseif (action == "turn" or action == "use" or action == "swing") and #words == 2 then
      return action, words[2]
    elseif action == "land" and #words == 2 and words[2] == "force" then
      return action, true -- (straight down, without its legs)
    elseif (action == "stop" or action == "land") and #words == 1 then
      return action
    elseif action == "position" and #words <= 5 then
      local at = {}
      for i = 2, #words do
        at[i - 1] = number(words[i])
        if not at[i - 1] then
          return nil
        end
      end
      return "setPosition", table.unpack(at, 1, #words - 1)
    elseif action == "pathfinding" and (words[2] == "on" or words[2] == "off") and #words == 2 then
      return "setPathfinding", words[2] == "on"
    end
  end,
}
