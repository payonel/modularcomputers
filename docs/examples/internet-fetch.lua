-- Download a URL into a file, through the internet card's own methods.
--
-- The card's methods are ioctls on its file: request starts a request and gives its handle at
-- once; response gives the status and headers once they've come, and read the body a piece at a
-- time, both answering false while there's nothing yet; close ends it. While a request waits, the
-- card's file is ready for sys.poll's PRI when it has news, so the program asks again only then.
--
-- usage: lua internet-fetch.lua URL FILE
local sys = require("sys")

local CARD = "/dev/internet0"
local PIECE = 4096 -- the most one read of the body takes

local url, path = arg[1], arg[2]
if not (url and path) then
  io.stderr:write("usage: internet-fetch URL FILE\n")
  os.exit(2)
end

local card = assert(sys.open(CARD))

-- Asks a method until it answers something other than "not yet" (false), waiting for the card's
-- news between asks. A failure is nil and why, as the method's.
local function await(method, ...)
  while true do
    local answer, why = sys.ioctl(card, method, ...)
    if answer ~= false then
      return answer, why
    end
    sys.poll({[card] = {PRI = true}})
  end
end

local handle = assert(sys.ioctl(card, "request", url))
local response, why = await("response", handle)
if not response then
  io.stderr:write("internet-fetch: ", why, "\n") -- refused, an unknown host, timed out
  os.exit(1)
elseif response.status ~= 200 then
  io.stderr:write("internet-fetch: ", response.status, " ", response.message, "\n")
  sys.ioctl(card, "close", handle)
  os.exit(1)
end

local out = assert(io.open(path, "w"))
local size = 0
while true do
  local piece = assert(await("read", handle, PIECE))
  if piece == "" then
    break -- the end of the body
  end
  out:write(piece)
  size = size + #piece
end
out:close()
sys.ioctl(card, "close", handle)
print(string.format("%s: %d bytes (%s)", path, size, response.headers["content-type"] or "no type"))
