-- The Disk Station's driver, for its /dev file (as a sysfs attribute: text, read whole, a write
-- taking effect at once). A read gives a line a slot and its eject face:
--   target 2 hfs 40960 2097152 backups   (its disk: tier, mode, bytes used, capacity, label)
--   source -                             (an empty slot)
--   eject north
-- A line written is an action, as its GUI's buttons and its methods: format, wipe, clone, install,
-- update, eject [target|source], or eject-face FACE (north..., or front, back, left, right).
local ACTIONS = {format = true, wipe = true, clone = true, install = true, update = true}

local function disk(name, about)
  if not about then
    return name .. " -\n"
  end
  return table.concat({name, about.tier, about.mode, about.used, about.capacity, about.label}, " ") .. "\n"
end

return {
  get = function(state)
    return disk("target", state.target) .. disk("source", state.source) .. "eject " .. tostring(state.ejectFace) .. "\n"
  end,
  line = function(text)
    local word, rest = text:match("^%s*(%S+)%s*(%S*)%s*$")
    if ACTIONS[word] and rest == "" then
      return {action = word}
    elseif word == "eject" then
      return {action = "eject", which = rest ~= "" and rest or nil}
    elseif word == "eject-face" and rest ~= "" then
      return {ejectFace = rest}
    end
  end,
}
