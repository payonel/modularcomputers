-- The robotic piston's driver, for its /dev file: its events as lines, a move's end each: the
-- method, then ok or failed and why ("push ok", "retract failed stopped"), as the piston's command
-- ended (each move a command, done once its arm has moved); a blocked one's block and face follow,
-- the block - when none is named ("push failed blocked minecraft:obsidian east", "push failed
-- blocked - up").
return {
  get = function(event)
    if event.ok then
      return event.event .. " ok\n"
    end
    local line = event.event .. " failed " .. tostring(event.why)
    if event.why == "blocked" then
      line = line .. " " .. (event.block or "-") .. " " .. tostring(event.face)
    end
    return line .. "\n"
  end,
}
