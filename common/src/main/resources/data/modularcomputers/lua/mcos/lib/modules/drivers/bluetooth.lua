-- The Bluetooth card's driver, for its /dev file (as a sysfs attribute: text, read whole, a line
-- written taking effect at once). A read gives two lines:
--   link 5f0c...   (the board it links to; "link -": none)
--   board 9a21...  (the board it's in: what another card links to, to reach it)
-- A line written relinks it: "link <id>", or "link -" for none.
local ID = "^%s*link%s+([%x%-]+)%s*$"

return {
  reads = {"getLink", "getBoard"},
  get = function(link, board)
    return "link " .. (link or "-") .. "\nboard " .. (board or "-") .. "\n"
  end,
  line = function(text)
    local id = text:match(ID)
    if id == "-" then
      return "setLink", nil
    end
    if id then
      return "setLink", id
    end
  end,
}
