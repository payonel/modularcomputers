-- The operator card's driver, for its /dev file: a line written runs a server command (as a
-- command block does: "time set day"; a leading / is allowed), taking effect at once; a write
-- that failed says why. A read gives what the last command said.
return {
  get = function(output)
    return output
  end,
  line = function(text)
    local command = text:match("^%s*(.-)%s*$")
    return command ~= "" and {command = command} or nil
  end,
}
