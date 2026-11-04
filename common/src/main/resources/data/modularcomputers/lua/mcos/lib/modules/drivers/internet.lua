-- The internet card's driver, for its /dev file: its access log, as a web server's, a line a
-- request as it ended: the method, the URL, the status (or "-" and why it failed: its failure's why
-- and its fields' values, as said), how long it took and the bytes it brought, e.g.
--   GET https://example.com/ 200 143ms 1256
--   GET http://10.0.0.1/ - 2ms 0 denied private 10.0.0.1
-- (The card keeps only the last few: someone reading sees what's current.)
-- A failure's fields, in the order its why's shape gives them (the card's: denied, network).
local FIELDS = {"reason", "value", "message"}

-- A failure as words: its why, then each field that says something.
local function said(failure)
  local words = {failure.why}
  for _, field in ipairs(FIELDS) do
    local value = failure[field]
    if value ~= nil and value ~= "" then
      words[#words + 1] = tostring(value)
    end
  end
  return table.concat(words, " ")
end

return {
  -- A request opens a connection that only close ends: what a file's requests leave open is closed
  -- with the file (devfs), as a socket's connection is, so a process that dies mid-download doesn't
  -- hold one of the card's few requests for good.
  opens = {request = "close"},
  get = function(entry)
    local status = entry.failure and "-" or tostring(entry.status)
    return entry.method .. " " .. entry.url .. " " .. status .. " " .. entry.millis .. "ms " .. entry.bytes
      .. (entry.failure and " " .. said(entry.failure) or "") .. "\n"
  end,
}
