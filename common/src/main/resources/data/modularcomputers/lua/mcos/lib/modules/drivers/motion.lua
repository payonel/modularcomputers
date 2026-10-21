-- The motion sensor's driver, for its /dev file: its events are tables ({mob, x, y, z}); its file
-- reads one a line, in Lua's syntax (lib/serialization.lua), so a program reads a line and
-- unserializes it. Loaded (k.driver) the first time a motion file is opened.
local k = ...
local serialization = k.kload("/lib/serialization.lua")

return {
  get = function(event)
    return serialization.serialize(event) .. "\n"
  end,
}
