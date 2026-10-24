-- The redstone card's driver, for its /dev file (as a sysfs attribute: text, read whole, a write
-- taking effect at once). A read gives a line a face, "face level powered", in the card's order
-- (north 7 7: 7 there, 7 of it this card's); a line written, "face powered", sets that face
-- (north 11).
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
    local face, powered = text:match("^%s*(%a+)%s+(%d+)%s*$")
    return face and {[face] = {powered = math.tointeger(tonumber(powered))}} or nil
  end,
}
