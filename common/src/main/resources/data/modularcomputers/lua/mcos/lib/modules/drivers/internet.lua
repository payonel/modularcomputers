-- The internet card's driver, for its /dev file: its access log, as a web server's, a line a
-- request as it ended (the card's entry, the request's end): the method, the URL, the status (or "-"
-- and why it failed: its why and its fields' values, as said), how long it took and the bytes it
-- brought, e.g.
--   GET https://example.com/ 200 143ms 1256
--   GET http://10.0.0.1/ - 2ms 0 denied private 10.0.0.1
--   GET https://slow.example/ - 30012ms 65536 network timed_out timed out
-- (The card keeps only the last few: someone reading sees what's current.)
-- A failure's fields, in the order its why's shape gives them (the card's: denied, network, a fault).
local FIELDS = {"reason", "condition", "part", "value", "message"}

-- A failed end as words: its why, then each field that says something.
local function said(entry)
  local words = {entry.why}
  for _, field in ipairs(FIELDS) do
    local value = entry[field]
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
    local how = entry.ok and entry.result or entry.call.progress -- (done: its result; else how far it got)
    local status = entry.ok and tostring(how.status) or "-"
    return entry.method .. " " .. entry.url .. " " .. status .. " " .. how.millis .. "ms " .. how.bytes
      .. (entry.ok and "" or " " .. said(entry)) .. "\n"
  end,
}
