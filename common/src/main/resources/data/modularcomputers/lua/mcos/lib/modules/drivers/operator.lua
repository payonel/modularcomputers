-- The operator card's driver, for its /dev file. A line written is a method's call: its name, then
-- its arguments, as the device's methods and ioctls name them; a line that isn't one fails (EINVAL):
--   command TEXT   (runs a server command, as a command block does, taking effect at once:
--                   everything after "command" is its text, one argument, so command time set day
--                   is command("time set day"); a leading / is allowed)
-- A write whose command succeeded in no run fails (EINVAL). A read gives its log from the file's
-- open on, a line a command it ran (anyone's): the command, how many of its runs succeeded, its
-- result and what it said, e.g.
--   time set day: 1 1000 Set the time to 1000
-- (a sitting cat, or tail -f, shows them as they run); "lost n" before one when n went before it,
-- the card's log full.
return {
  stream = true, -- (its read is its events: its log)
  line = function(text)
    local name, rest = text:match("^%s*(%S+)%s*(.-)%s*$")
    if name == "command" then
      if rest == "" then
        return "command" -- (no text: the card's own refusal)
      end
      return "command", rest
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
