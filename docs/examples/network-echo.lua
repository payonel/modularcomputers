-- An echo server over TCP: everything a client sends comes back to it, for as many clients as
-- connect at once. Try it from another computer with nc: echo hello | nc <this card's id> 7
--
-- The listening socket is ready (IN) when a connection waits to be accepted, and a connection when
-- it has data or its client is done sending (a read gives "" then: the connection is closed).
-- sys.poll waits on all of them at once, so the server uses no time while nobody talks.
--
-- usage: lua network-echo.lua [port]
local sys = require("sys")

local PORT = tonumber(arg[1]) or 7 -- echo's own port
local READ_SIZE = 4096 -- the most one read of a connection takes

local server = assert(sys.socket("stream"))
assert(sys.bind(server, PORT))
assert(sys.listen(server))

-- Writes all of 'data' (a write takes what the connection has room for, and waits when it has none).
local function send(fd, data)
  while #data > 0 do
    local count = sys.write(fd, data)
    if not count then
      return false -- the client went away
    end
    data = data:sub(count + 1)
  end
  return true
end

local clients = {} -- each connection's descriptor, to its client's address
while true do
  local watch = {[server] = {IN = true}}
  for fd in pairs(clients) do
    watch[fd] = {IN = true}
  end
  local ready = sys.poll(watch)
  for fd in pairs(ready) do
    if fd == server then
      local conn, address = sys.accept(server)
      if conn then
        clients[conn] = address
      end
    else
      local data = sys.read(fd, READ_SIZE)
      if not (data and data ~= "" and send(fd, data)) then
        sys.close(fd) -- its end, or it's gone
        clients[fd] = nil
      end
    end
  end
end
