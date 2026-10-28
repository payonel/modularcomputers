-- The robotic piston's driver, for its /dev file: its events as lines, a move's end each: the
-- method, then ok or failed and why ("push ok", "retract failed stopped"), as the piston's command
-- ended (each move a command, done once its arm has moved).
return {
  get = function(event)
    if event.ok then
      return event.event .. " ok\n"
    end
    return event.event .. " failed " .. tostring(event.why) .. "\n"
  end,
}
