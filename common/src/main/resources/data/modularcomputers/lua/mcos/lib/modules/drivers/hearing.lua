-- The audio sensor's driver, for its /dev file: an event a line, the sound's subtitle in double
-- quotes and where it came from, relative to the sensor's block ("Pig oinks" 1.5 0.0 -3.0); in the
-- quotes, a quote or a backslash has a backslash before it (as a shell's double quotes). "lost n"
-- before one when n were dropped, the sensor's buffer full (as an input device's SYN_DROPPED).
local function quoted(text)
  return '"' .. text:gsub('[\\"]', "\\%0") .. '"'
end

return {
  get = function(event)
    return (event.lost and "lost " .. event.lost .. "\n" or "") .. quoted(event.sound) .. " " .. event.x .. " " .. event.y .. " " .. event.z .. "\n"
  end,
}
