-- The redstone terminal's driver, for its /dev file: the device answers get and takes set as a table
-- (each side by name: mode, level, powered), its file reads as that table in Lua's syntax
-- (lib/serialization.lua, a table to a line or more) and takes one written back. Loaded (k.driver) the
-- first time a redstone file is opened.
local k = ...
local serialization = k.kload("/lib/serialization.lua")

return {
  get = function(value)
    return serialization.serialize(value, true) .. "\n"
  end,
  -- (nil when the text isn't a table: the write fails, EINVAL)
  set = function(text)
    local value = serialization.unserialize(text)
    return type(value) == "table" and value or nil
  end,
}
