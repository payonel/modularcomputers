-- Send a file to a web service (a paste site, a webhook, your own server) and write its answer
-- out, with the http library that wget and curl use.
--
-- http.request makes the request through the internet card's methods, waits for its answer, and
-- follows redirects when asked (each hop a new request, judged by the card again). It gives the
-- response (status, message, headers, and the URL it came from) and a reader for the body, whose
-- read gives a piece at a time and "" at its end. The body goes out as a POST with its own
-- Content-Type; the card writes the headers that frame a request (Host, Content-Length) itself.
--
-- usage: lua internet-post.lua URL FILE [content-type]
local sys = require("sys")
local http = require("http")

local PIECE = 4096 -- the most one read of the answer takes

local url, path = arg[1], arg[2]
local kind = arg[3] or "text/plain"
if not (url and path) then
  io.stderr:write("usage: internet-post URL FILE [content-type]\n")
  os.exit(2)
end

local input = assert(io.open(path))
local body = input:read("a")
input:close()

local response, reply = http.request(url, {
  method = "POST",
  headers = {["Content-Type"] = kind, Accept = "text/plain"},
  body = body,
  follow = true,
})
if not response then
  io.stderr:write("internet-post: ", reply, "\n")
  os.exit(1)
end

while true do
  local piece, why = reply:read(PIECE)
  if not piece then
    io.stderr:write("internet-post: ", why, "\n")
    os.exit(1)
  elseif piece == "" then
    break
  end
  io.write(piece)
end
reply:close()
io.stderr:write(response.url, ": ", response.status, " ", response.message, "\n")
os.exit(response.status < 300 and 0 or 1)
