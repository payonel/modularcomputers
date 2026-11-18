-- The redstone card's driver, for its /dev file (as a sysfs attribute: text, read whole, a write
-- taking effect at once). A read gives its state, a line a face, "face level powered", in the
-- card's order (north 7 7: 7 there, 7 of it this card's).
-- A line written is a method's call: its name, then its arguments, as the device's methods and
-- ioctls name them; a line that isn't one fails (EINVAL):
--   set FACE POWERED [FACE POWERED...]   (sets each face named: the signal it puts out, 0 to 15;
--                                         set north 15 south 0 is set({north = {powered = 15},
--                                         south = {powered = 0}}), one command)
local FACES = {"north", "south", "east", "west", "up", "down"}

return {
  get = function(faces)
    local lines = {}
    for _, face in ipairs(FACES) do
      local state = faces[face]
      if state then
        lines[#lines + 1] = face .. " " .. state.level .. " " .. state.powered .. "\n"
      end
    end
    return table.concat(lines)
  end,
  line = function(text)
    local words = {}
    for word in text:gmatch("%S+") do
      words[#words + 1] = word
    end
    if words[1] ~= "set" or #words < 3 or #words % 2 == 0 then
      return nil -- (faces and their signals, in pairs, one at least)
    end
    local sides = {}
    for i = 2, #words, 2 do
      local powered = math.tointeger(tonumber(words[i + 1]))
      if not powered or tonumber(words[i]) then
        return nil
      end
      sides[words[i]] = {powered = powered}
    end
    return "set", sides
  end,
}
