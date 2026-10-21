-- The weather sensor's driver, for its /dev file: an event a line, the weather now (clear, rain, snow
-- or thunder); "lost n" before one when n were dropped.
return {
  get = function(event)
    return (event.lost and "lost " .. event.lost .. "\n" or "") .. event.new .. "\n"
  end,
}
