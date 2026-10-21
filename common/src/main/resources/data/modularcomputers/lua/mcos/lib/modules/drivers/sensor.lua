-- The sensors' driver, for their /dev files: a sensor's events are tables (a motion sensor's
-- {mob, x, y, z}); its file reads one a line, in Lua's syntax (lib/serialization.lua), so a program
-- reads a line and unserializes it. Loaded (k.driver) the first time a sensor's file is opened.
local k = ...
local serialization = k.kload("/lib/serialization.lua")

return {
  get = function(event)
    return serialization.serialize(event) .. "\n"
  end,
}
