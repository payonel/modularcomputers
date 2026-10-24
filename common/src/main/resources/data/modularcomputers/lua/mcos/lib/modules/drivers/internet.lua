-- The internet card's driver, for its /dev file: its access log, as a web server's, a line a
-- request as it ended: the method, the URL, the status (or "-" and why it failed), how long it
-- took and the bytes it brought, e.g.
--   GET https://example.com/ 200 143ms 1256
--   GET http://10.0.0.1/ - 2ms 0 refused: a private address (the server's network)
-- (The card keeps only the last few: someone reading sees what's current.)
return {
  get = function(entry)
    local status = entry.why and "-" or tostring(entry.status)
    return entry.method .. " " .. entry.url .. " " .. status .. " " .. entry.millis .. "ms " .. entry.bytes
      .. (entry.why and " " .. entry.why or "") .. "\n"
  end,
}
