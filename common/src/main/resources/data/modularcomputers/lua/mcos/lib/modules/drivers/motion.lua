-- The motion sensor's driver, for its /dev file: an event a line, "mob forward right up" (the mob's
-- type, and where, in its computer's frame: forward out of its front, right, up; a robot's own:
-- minecraft:pig 1.5 -3.0 0.0); "lost n" before one when n were dropped, the sensor's buffer full
-- (as an input device's SYN_DROPPED).
return {
  get = function(event)
    return (event.lost and "lost " .. event.lost .. "\n" or "") .. event.mob .. " " .. event.forward .. " " .. event.right .. " " .. event.up .. "\n"
  end,
}
