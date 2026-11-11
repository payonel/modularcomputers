-- The operator card's driver, for its /dev file: a line written runs a server command (as a
-- command block does: "time set day"; a leading / is allowed), taking effect at once; a write
-- whose command succeeded in no run fails (EINVAL). A read gives its log from the file's open on,
-- a line a command it ran (anyone's): the command, how many of its runs succeeded, its result and
-- what it said, e.g.
--   time set day: 1 1000 Set the time to 1000
-- (a sitting cat, or tail -f, shows them as they run); "lost n" before one when n went before it,
-- the card's log full.
return {
  stream = true, -- (its read is its events: its log)
  line = function(text)
    local command = text:match("^%s*(.-)%s*$")
    if command ~= "" then
      return "command", command
    end
  end,
  -- (a command that succeeded in no run fails the write)
  failed = function(answer)
    return type(answer) == "table" and answer.successes == 0
  end,
  get = function(entry)
    local said = entry.message:gsub("\n+$", ""):gsub("\n", " / ")
    return (entry.lost and "lost " .. entry.lost .. "\n" or "") .. entry.command .. ": " .. entry.successes .. " " .. entry.result
      .. (said ~= "" and " " .. said or "") .. "\n"
  end,
}
