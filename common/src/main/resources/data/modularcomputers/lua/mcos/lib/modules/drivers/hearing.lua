-- The audio sensor's driver, for its /dev file: an event a line, "sound x y z" (the id of the sound
-- the game played, and where it came from, relative to the sensor's block:
-- minecraft:entity.pig.ambient 1.5 0.0 -3.0); "lost n" before one when n were dropped, the
-- sensor's buffer full (as an input device's SYN_DROPPED).
return {
  get = function(event)
    return (event.lost and "lost " .. event.lost .. "\n" or "") .. event.sound .. " " .. event.x .. " " .. event.y .. " " .. event.z .. "\n"
  end,
}
