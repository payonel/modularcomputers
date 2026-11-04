-- The operator card's driver, for its /dev file: a line written runs a server command (as a
-- command block does: "time set day"; a leading / is allowed), taking effect at once; a write
-- that failed says why. A read gives what the last command said.
return {
  get = function(output)
    return output
  end,
  line = function(text)
    local command = text:match("^%s*(.-)%s*$")
    if command ~= "" then
      return "command", command
    end
  end,
  -- (a command that didn't succeed fails the write)
  failed = function(answer)
    return type(answer) == "table" and answer.success == false
  end,
}
