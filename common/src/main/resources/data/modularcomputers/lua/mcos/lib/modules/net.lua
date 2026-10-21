-- Sockets: UDP over the network cards, loaded at the first sys.socket() (nothing of it is in memory
-- until then). A socket is an open file: bind(port) gives it a port, sendto(data, address, port)
-- sends a datagram, recvfrom() takes one (its data, the sender's address and port), and a read takes
-- one's data. It's ready to poll (IN) while one waits.
--
-- The cards know only addresses (their ids, as a NIC's MAC): ports are UDP's, this module's, as
-- Linux's (OpenComputers' modem opened ports itself). A datagram is a frame whose data starts with
-- UDP's header: source and destination port, two bytes each (its length and checksum are skipped: a
-- frame knows its length, and nothing corrupts one). No IP: an address is a card's, and the network is
-- what the cards reach (a wired card's cables, a WLAN card's range). "broadcast" is every card's
-- address (255.255.255.255's role). A datagram to one of this computer's own cards, or a broadcast,
-- reaches its own sockets too, as Linux loops them back.
--
-- Interfaces are named as Linux's: eth0 for the first wired card, wlan0 for the first WLAN card.
-- The ioctls (by their Linux names): SIOCGIFCONF (the interfaces: {name, address, wireless} each),
-- and two of ethtool's: ETHTOOL_GWOL (an interface's wake message, or nil) and ETHTOOL_SWOL
-- ({name, wake}: sets it; nil or empty, none).
local k, vfs = ...

local HEADER = ">I2I2" -- source port, destination port
local HEADER_SIZE = 4
local MTU = 8192 -- the most data a card's frame carries
local QUEUE = 64 -- datagrams a socket holds before it drops the next (SO_RCVBUF: UDP loses, it never waits)
local EPHEMERAL_FIRST, EPHEMERAL_LAST = 32768, 60999 -- the ports given to a socket that sends unbound (Linux's ip_local_port_range)
local MAX_PORT = 65535
local BROADCAST = "broadcast"

local ERRNO = {
  ENOTSOCK = "Socket operation on non-socket",
  EMSGSIZE = "Message too long",
  EPROTONOSUPPORT = "Protocol not supported",
  EADDRINUSE = "Address already in use",
  ENETUNREACH = "Network is unreachable",
  EHOSTUNREACH = "No route to host",
  EDESTADDRREQ = "Destination address required",
  ENODEV = "No such device",
  EINVAL = "Invalid argument",
}
local function fail(name)
  return nil, ERRNO[name] or name, name
end

local ports = {} -- bound sockets, by port
local about = {} -- each card asked once, by id: {address, wireless}, or false (a Bluetooth card: no frames)

-- The network cards on the bus, as interfaces: {name, id, address, wireless} each, in bus order.
local function interfaces()
  local list, counts = {}, {}
  for _, device in ipairs(k.devices.list) do
    if device.type == "network" then
      local card = about[device.id]
      if card == nil then
        local ok, address = board.invoke(device.id, "address")
        local info = board.info(device.id) or {}
        card = ok and address and {address = address, wireless = info.wireless} or false
        about[device.id] = card
      end
      if card then
        local kind = card.wireless and "wlan" or "eth"
        local n = counts[kind] or 0
        counts[kind] = n + 1
        list[#list + 1] = {name = kind .. n, id = device.id, address = card.address, wireless = card.wireless}
      end
    end
  end
  return list
end

-- A datagram for a port on this computer: to the socket bound to it (dropped when there's none, or
-- its queue is full, as UDP's are).
local function deliver(data, from)
  if #data < HEADER_SIZE then
    return
  end
  local source, port = string.unpack(HEADER, data)
  local socket = ports[port]
  if socket and #socket.queue < QUEUE then
    socket.queue[#socket.queue + 1] = {data:sub(HEADER_SIZE + 1), from, source}
  end
end

-- The frames the cards took, to their sockets. A card with none raises its interrupt when one comes:
-- the kernel listens (a read or poll waiting on a socket).
local function pump()
  for _, nic in ipairs(interfaces()) do
    while true do
      local ok, frame = board.invoke(nic.id, "receive")
      if not (ok and frame) then
        k.listen(nic.id)
        break
      end
      deliver(frame.data, frame.from)
    end
  end
end

local function bind(socket, port)
  if ports[port] then
    return fail("EADDRINUSE")
  end
  socket.port = port
  ports[port] = socket
  return true
end

local net = {}

-- socket([type]): a UDP socket ("dgram", the only type).
function net.socket(kind)
  if kind ~= nil and kind ~= "dgram" then
    return fail("EPROTONOSUPPORT")
  end
  local socket = {type = "socket", queue = {}}
  function socket.events()
    pump()
    return {IN = #socket.queue > 0, OUT = true}
  end
  -- A read takes one datagram's data (the rest of it past n is lost, as a datagram socket's is).
  function socket.read(self, n)
    local data = net.recvfrom(socket)
    if data == k.BLOCK or not data then
      return data
    end
    return data:sub(1, n)
  end
  function socket.close()
    if socket.port then
      ports[socket.port] = nil
    end
    return true
  end
  return socket
end

-- bind(port): the port it takes datagrams on (0: any free one); the port.
function net.bind(socket, request, port)
  if socket.port then
    return fail("EINVAL")
  elseif math.type(port) ~= "integer" or port < 0 or port > MAX_PORT then
    return fail("EINVAL")
  end
  if port == 0 then
    for free = EPHEMERAL_FIRST, EPHEMERAL_LAST do
      if not ports[free] then
        port = free
        break
      end
    end
  end
  local ok, why, errno = bind(socket, port)
  if not ok then
    return nil, why, errno
  end
  return port
end

-- sendto(data, address, port): a datagram, through the card that reaches the address (a unicast's
-- acknowledgement says which: none did, EHOSTUNREACH, as an ARP that's never answered), or every
-- card (a broadcast); the bytes sent.
function net.sendto(socket, request, data, address, port)
  if type(data) ~= "string" or type(address) ~= "string" or math.type(port) ~= "integer" or port < 1 or port > MAX_PORT then
    return fail(address == nil and "EDESTADDRREQ" or "EINVAL")
  elseif #data + HEADER_SIZE > MTU then
    return fail("EMSGSIZE")
  end
  if not socket.port then
    net.bind(socket, request, 0)
  end
  address = address:gsub("^id:", "")
  local frame = string.pack(HEADER, socket.port, port) .. data
  local nics = interfaces()
  for _, nic in ipairs(nics) do
    if address == nic.address then
      deliver(frame, address) -- its own: looped back
      return #data
    end
  end
  for _, nic in ipairs(nics) do
    if address == BROADCAST then
      board.invoke(nic.id, "broadcast", frame)
    else
      local ok, heard = board.invoke(nic.id, "send", address, frame)
      if ok and heard then
        return #data
      end
    end
  end
  if not nics[1] then
    return fail("ENETUNREACH")
  elseif address ~= BROADCAST then
    return fail("EHOSTUNREACH")
  end
  deliver(frame, nics[1].address) -- a broadcast reaches this computer too
  return #data
end

-- recvfrom(): the oldest datagram: its data, the sender's address and port; it waits for one.
function net.recvfrom(socket)
  pump()
  local datagram = table.remove(socket.queue, 1)
  if not datagram then
    return k.BLOCK
  end
  return datagram[1], datagram[2], datagram[3]
end

local function named(name)
  for _, nic in ipairs(interfaces()) do
    if nic.name == name then
      return nic
    end
  end
end

function net.ioctl(socket, op, arg)
  if op == "SIOCGIFCONF" then
    local list = {}
    for i, nic in ipairs(interfaces()) do
      list[i] = {name = nic.name, address = nic.address, wireless = nic.wireless or false}
    end
    return list
  end
  local nic = named(type(arg) == "table" and arg.name or arg)
  if not nic then
    return fail("ENODEV")
  elseif op == "ETHTOOL_GWOL" then
    local ok, wake = board.invoke(nic.id, "getWake")
    return ok and wake or nil
  elseif op == "ETHTOOL_SWOL" and type(arg) == "table" then
    if not board.invoke(nic.id, "setWake", arg.wake) then
      return fail("EINVAL")
    end
    return true
  end
  return fail("EINVAL")
end

return net
