-- The Bluetooth card's driver, for its /dev file (as a sysfs attribute: text, read whole, a line
-- written taking effect at once). A read gives two lines, its state:
--   link 5f0c...   (the board it links to; "link -": none)
--   board 9a21...  (the board it's in: what another card links to, to reach it)
-- A line written is a method's call: its name, then its arguments, as the device's methods and
-- ioctls name them (setLink 5f0c... is setLink("5f0c...")); a line that isn't one fails (EINVAL):
--   setLink [id]   (links it to the board with that id; none: to no board)
return {
  reads = {"getLink", "getBoard"},
  get = function(link, board)
    return "link " .. (link or "-") .. "\nboard " .. (board or "-") .. "\n"
  end,
  line = function(text)
    local words = {}
    for word in text:gmatch("%S+") do
      words[#words + 1] = word
    end
    if words[1] == "setLink" and #words <= 2 then
      return table.unpack(words, 1, #words) -- (setLink alone: setLink(), linked to none)
    end
  end,
}
