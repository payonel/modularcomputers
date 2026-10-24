-- A chat between every computer on the network: what you type goes to all of them, and what they
-- send shows here, with who sent it.
--
-- Each line typed is a UDP datagram broadcast to PORT ("name: text"), and each datagram that comes
-- in on PORT is shown. A broadcast comes back to this computer's own sockets too, so lines from its
-- own cards (their addresses, from the socket's SIOCGIFCONF ioctl) are skipped. sys.poll waits for
-- whichever comes first, the keyboard or the network, so the program uses no time while nobody
-- talks. It ends at the end of its input (Ctrl+D at the start of a line).
--
-- usage: lua network-chat.lua [name]
local sys = require("sys")

local PORT = 4000 -- every computer in the chat listens and sends here
local READ_SIZE = 4096 -- the most one read of the keyboard takes

local name = arg[1] or sys.gethostname()
local sock = assert(sys.socket()) -- UDP
assert(sys.bind(sock, PORT))

local own = {} -- this computer's cards' addresses
for _, interface in ipairs(sys.ioctl(sock, "SIOCGIFCONF")) do
  own[interface.address] = true
end

local typed = "" -- keys read, up to a line's end
local watch = {[0] = {IN = true}, [sock] = {IN = true}}
while true do
  local ready = sys.poll(watch) -- blocks until a line is typed or a datagram comes
  if ready[sock] then
    local data, from = sys.recvfrom(sock)
    if not own[from] then
      io.write(data)
      io.flush()
    end
  end
  if ready[0] then
    local keys = sys.read(0, READ_SIZE)
    if not keys or keys == "" then
      break -- the end of the input
    end
    typed = typed .. keys
    for line in typed:gmatch("([^\n]*)\n") do
      local ok, why = sys.sendto(sock, name .. ": " .. line .. "\n", "broadcast", PORT)
      if not ok then
        io.stderr:write("chat: not sent: ", why, "\n")
      end
    end
    typed = typed:match("[^\n]*$")
  end
end
sys.close(sock)
