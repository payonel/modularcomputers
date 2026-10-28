-- The audio sensor's driver, for its /dev file: an event a line, "sound forward right up" (the id
-- of the sound the game played, and where it came from, in its computer's frame, as the motion
-- sensor's: minecraft:entity.pig.ambient 1.5 -3.0 0.0); "lost n" before one when n were dropped,
-- the sensor's buffer full (as an input device's SYN_DROPPED).
return {
  get = function(event)
    return (event.lost and "lost " .. event.lost .. "\n" or "") .. event.sound .. " " .. event.forward .. " " .. event.right .. " " .. event.up .. "\n"
  end,
}
