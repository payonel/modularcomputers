-- The operator card's driver, for its /dev file: a line written runs a server command (as a
-- command block does: "time set day"; a leading / is allowed), taking effect at once; a write
-- whose command succeeded in no run fails (EINVAL). A read gives what the last command written to
-- it said (its message, kept by the kernel: the card keeps nothing to repeat, one door).
return {
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
  answers = function(answer)
    return type(answer) == "table" and answer.message or ""
  end,
}
