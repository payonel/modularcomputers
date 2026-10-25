-- A crafting card's driver, for its /dev file (as a sysfs attribute: text). A read gives what its
-- slots hold, a line each, its item and how many, e.g.
--   minecraft:oak_log 12
--   minecraft:stick 4
-- A line written drops everything in its storage (not its grid) at a face of the robot, a
-- container there filled first, the rest into the world: an empty line at its front, or a face's
-- name (front, back, left, right, up, down): echo down > /dev/crafting0. Its crafting, its moves and
-- its events (poll's PRI; the pull ioctl: crafted, failed) are ioctls.
local FACES = {front = true, back = true, left = true, right = true, up = true, down = true}

return {
  get = function(slots)
    local lines = {}
    for i, item in ipairs(slots) do
      lines[i] = item.name .. " " .. item.count .. "\n"
    end
    return table.concat(lines)
  end,
  line = function(text)
    local face = text:match("^%s*(%a*)%s*$")
    if face == "" then
      return "front"
    end
    return FACES[face] and face or nil
  end,
}
