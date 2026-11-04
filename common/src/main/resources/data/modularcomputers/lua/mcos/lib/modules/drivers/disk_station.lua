-- The Disk Station's driver, for its /dev file (as a sysfs attribute: text, read whole, a write
-- taking effect at once). A read gives a line a slot:
--   target 2 hfs 40960 4194304 backups   (its disk: tier, mode, bytes used, capacity, label)
--   source -                             (an empty slot)
-- A line written calls the method it names, as its GUI's buttons do: format, wipe, clone, install,
-- update.
local ACTIONS = {format = true, wipe = true, clone = true, install = true, update = true}

local function disk(name, about)
  if not about then
    return name .. " -\n"
  end
  return table.concat({name, about.tier, about.mode, about.used, about.capacity, about.label}, " ") .. "\n"
end

return {
  get = function(state)
    return disk("target", state.target) .. disk("source", state.source)
  end,
  line = function(text)
    local word = text:match("^%s*(%S+)%s*$")
    if ACTIONS[word] then
      return word
    end
  end,
}
