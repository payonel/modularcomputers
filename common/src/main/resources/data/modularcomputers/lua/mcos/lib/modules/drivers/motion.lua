-- The motion sensor's driver, for its /dev file: an event a line, "mob x y z" (the mob's type, and
-- where, relative to the sensor's block: minecraft:pig 1.5 0.0 -3.0); "lost n" before one when n
-- were dropped, the sensor's buffer full (as an input device's SYN_DROPPED).
return {
  get = function(event)
    return (event.lost and "lost " .. event.lost .. "\n" or "") .. event.mob .. " " .. event.x .. " " .. event.y .. " " .. event.z .. "\n"
  end,
}
