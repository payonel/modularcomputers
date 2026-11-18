-- A crafting card's driver, for its /dev file (as a sysfs attribute: text). A read gives what its
-- slots hold, a line each, its item and how many, e.g.
--   minecraft:oak_log 12
--   minecraft:stick 4
-- A line written is a method's call: its name, then its arguments, as the device's methods and
-- ioctls name them (drop up 3 is drop("up", 3)); a line that isn't one fails (EINVAL):
--   drop [face] [slot [count]]   (drops items at a face of the robot, front, back, left, right, up
--                                 or down, below when none is given: from a slot, all or count, or
--                                 with none, everything in its storage, not its grid; a container
--                                 there is filled first, the rest into the world)
-- So echo drop > /dev/crafting0 empties its storage below it. Its crafting, its other moves and its
-- events (poll's PRI; the pull ioctl: craft) are ioctls.
return {
  reads = {"list"},
  get = function(slots)
    local lines = {}
    for i, item in ipairs(slots) do
      lines[i] = item.name .. " " .. item.count .. "\n"
    end
    return table.concat(lines)
  end,
  line = function(text)
    local words = {}
    for word in text:gmatch("%S+") do
      words[#words + 1] = word
    end
    if words[1] ~= "drop" then
      return nil
    end
    local call, from = {"drop"}, 2
    if words[2] and not tonumber(words[2]) then
      call[2], from = words[2], 3 -- (a face: a name; a slot and a count are numbers)
    end
    if #words - from > 1 then
      return nil -- (a slot and a count at most)
    end
    for i = from, #words do
      local n = math.tointeger(tonumber(words[i]))
      if not n then
        return nil
      end
      call[#call + 1] = n
    end
    return table.unpack(call, 1, #call)
  end,
}
