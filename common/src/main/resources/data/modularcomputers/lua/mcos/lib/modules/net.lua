-- Sockets: UDP and TCP over the network cards, loaded at the first sys.socket() (nothing of it is in
-- memory until then). A socket is an open file.
--
-- The cards know only addresses (their ids, as a NIC's MAC): ports, and what a frame carries, are
-- the kernel's, as Linux's (OpenComputers' modem opened ports itself). A frame's data starts with a
-- protocol byte (IP's numbers: 17 UDP, 6 TCP), then the protocol's header. No IP: an address is a
-- card's, and the network is what the cards reach (a wired card's cables, a WLAN card's range).
-- "broadcast" is every card's address (255.255.255.255's role). A frame to one of this computer's
-- own cards, or a broadcast, reaches its own sockets too, as Linux loops them back.
--
-- UDP ("dgram"): bind(port), sendto(data, address, port), recvfrom() (data, address, port), a read
-- takes one datagram's data. Its header: source and destination port (length and checksum are
-- skipped: a frame knows its length, and nothing corrupts one). It's lossy, as UDP is: a datagram
-- with no socket for it, or no room, is dropped.
--
-- TCP ("stream"): listen(backlog) and accept() (a new socket, the peer's address and port), or
-- connect(address, port); then a byte stream both ways, in order, none lost: segments carry a
-- sequence number and an acknowledgement, a receiver says how much room it has (its window: a
-- sender never sends more), and what isn't acknowledged in RTO_TICKS is sent again (a card's queue
-- may overflow), up to MAX_TRIES times (then the connection is reset: ETIMEDOUT). A connection opens
-- with SYN, SYN|ACK, ACK and closes with a FIN each way (close, or shutdown: no more to send); a
-- segment for nothing gets RST (connect: ECONNREFUSED). Not modeled: congestion control, TIME_WAIT,
-- urgent data, options. Its header: protocol, ports, flags, sequence, acknowledgement, window.
--
-- Interfaces are named as Linux's: eth0 for the first wired card, wlan0 for the first WLAN card.
-- The ioctls (by their Linux names): SIOCGIFCONF (the interfaces: {name, address, wireless} each),
-- and two of ethtool's: ETHTOOL_GWOL (an interface's wake message, or nil) and ETHTOOL_SWOL
-- ({name, wake}: sets it; nil or empty, none).
local k, vfs = ...

local UDP, TCP = 17, 6
local UDP_HEADER, UDP_SIZE = ">BI2I2", 5 -- protocol, source port, destination port
local TCP_HEADER, TCP_SIZE = ">BI2I2BI4I4I2", 16 -- protocol, ports, flags, sequence, acknowledgement, window
local SYN, ACK, FIN, RST = 1, 2, 4, 8
local MTU = 8192 -- the most data a card's frame carries
local QUEUE = 64 -- datagrams a UDP socket holds before it drops the next (SO_RCVBUF)
local MSS = 2048 -- the most data a TCP segment carries
local WINDOW = 8192 -- what a TCP connection holds received and unread (its window), and unsent
local BACKLOG = 8 -- connections waiting to be accepted, unless listen says
local RTO_TICKS = 20 -- what isn't acknowledged in this long (a second) is sent again
local MAX_TRIES = 8 -- sent this many times with no acknowledgement: the connection is reset
local UNREACHED_TRIES = 3 -- a SYN no card took this many times: no route to the host (ARP's tries, as Linux's)
local SEQ_SPACE = 4294967296 -- sequence numbers are 32 bits
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
  ECONNREFUSED = "Connection refused",
  ECONNRESET = "Connection reset by peer",
  ETIMEDOUT = "Connection timed out",
  ENOTCONN = "Transport endpoint is not connected",
  EISCONN = "Transport endpoint is already connected",
  EOPNOTSUPP = "Operation not supported",
  EPIPE = "Broken pipe",
  ENODEV = "No such device",
  EINVAL = "Invalid argument",
}
local function fail(name)
  return nil, ERRNO[name] or name, name
end

local ports = {[UDP] = {}, [TCP] = {}} -- the sockets that hold a port, by protocol: a UDP socket, a TCP listener or connection
local conns = {} -- TCP connections, by address/local port/remote port
local about = {} -- each card asked once, by id: {address, wireless}, or false (a Bluetooth card: no frames)
local loop = {} -- frames to this computer's own cards, delivered at the next pump: {data, from}
local nics = {} -- the cards' ids, as frames were taken from them (whose interrupt is news here)
local nextport = EPHEMERAL_FIRST

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

-- A free port, for a socket that sends unbound (as Linux picks one, going on from the last).
local function ephemeral(protocol)
  for _ = EPHEMERAL_FIRST, EPHEMERAL_LAST do
    local port = nextport
    nextport = nextport < EPHEMERAL_LAST and nextport + 1 or EPHEMERAL_FIRST
    if not ports[protocol][port] then
      return port
    end
  end
end

-- A frame out: to one of this computer's own cards (looped back), through the card that reaches the
-- address (its acknowledgement says which: a unicast frame no card took is EHOSTUNREACH, as an ARP
-- that's never answered), or to every card (a broadcast, which loops back too). True, or the error.
local function transmit(address, frame)
  local nics = interfaces()
  if not nics[1] then
    return fail("ENETUNREACH")
  end
  for _, nic in ipairs(nics) do
    if address == nic.address then
      loop[#loop + 1] = {frame, address}
      return true
    end
  end
  if address == BROADCAST then
    for _, nic in ipairs(nics) do
      board.invoke(nic.id, "broadcast", frame)
    end
    loop[#loop + 1] = {frame, nics[1].address}
    return true
  end
  for _, nic in ipairs(nics) do
    local ok, heard = board.invoke(nic.id, "send", address, frame)
    if ok and heard then
      return true
    end
  end
  return fail("EHOSTUNREACH")
end

-- ---- TCP ----------------------------------------------------------------------------------------

local CLOSED, LISTEN, SYN_SENT, SYN_RCVD, ESTABLISHED = "closed", "listen", "syn-sent", "syn-received", "established"

local function key(address, lport, rport)
  return address .. "/" .. lport .. "/" .. rport
end

-- Arms the connection's retransmission (or keeps it armed), and the kernel's timer with it.
local function arm(c)
  c.timer = c.timer or k.now() + RTO_TICKS
  if not k.timer or c.timer < k.timer then
    k.timer = c.timer
  end
end

-- A segment out, carrying what it acknowledges and the room this end has. A frame no card took
-- counts as a try that failed.
local function segment(c, flags, seq, data)
  local room = WINDOW - #c.inbuf
  local frame = string.pack(TCP_HEADER, TCP, c.lport, c.rport, flags, seq % SEQ_SPACE, c.rcv_nxt % SEQ_SPACE, room) .. (data or "")
  c.advertised = room
  return transmit(c.raddr, frame)
end

-- The connection ends: what waits on it hears (reads end, writes fail), and it's forgotten.
local function reset(c, errno)
  c.state, c.error, c.timer = CLOSED, c.error or errno, nil
  conns[c.key] = nil
  if c.owner and ports[TCP][c.lport] == c.owner and not c.owner.listening then
    ports[TCP][c.lport] = nil
  end
end

-- Whether everything this end is to send, its FIN too, has been acknowledged and the peer's FIN came:
-- the connection is over.
local function finished(c)
  return c.fin_rcvd and c.fin_sent and c.snd_una == c.snd_nxt and #c.out == 0
end

-- Sends what the window lets go: unsent data, a segment at most at a time, then the FIN once
-- everything's sent ('probe': one byte past a closed window, so the peer says when it opens).
local function output(c, probe)
  if c.state ~= ESTABLISHED then
    return
  end
  while true do
    local inflight = c.snd_nxt - c.snd_una
    local unsent = #c.out - inflight
    local room = math.min(c.peerwnd, WINDOW) - inflight
    if probe and room <= 0 and unsent > 0 then
      room, probe = 1, false
    end
    if unsent <= 0 or room <= 0 then
      break
    end
    local n = math.min(unsent, room, MSS)
    segment(c, ACK, c.snd_nxt, c.out:sub(inflight + 1, inflight + n))
    c.snd_nxt = c.snd_nxt + n
    arm(c)
  end
  if c.closing and not c.fin_sent and c.snd_nxt - c.snd_una == #c.out then
    segment(c, FIN | ACK, c.snd_nxt)
    c.snd_nxt = c.snd_nxt + 1 -- (a FIN takes a sequence number)
    c.fin_sent = true
    arm(c)
  elseif #c.out > c.snd_nxt - c.snd_una then
    arm(c) -- (a closed window: probed when the timer comes)
  end
end

-- A new connection: its sequence numbers start where nothing old could be confused for it.
local function newconn(address, lport, rport, owner)
  local isn = (k.now() * 7919 + lport * 31 + rport) % SEQ_SPACE
  local c = {key = key(address, lport, rport), raddr = address, lport = lport, rport = rport, owner = owner,
    snd_una = isn, snd_nxt = isn, rcv_nxt = 0, peerwnd = 0, out = "", inbuf = "", tries = 0}
  conns[c.key] = c
  return c
end

-- A segment came for this computer's TCP.
local function tcp(data, from)
  if #data < TCP_SIZE then
    return
  end
  local _, sport, dport, flags, seq, ack, wnd = string.unpack(TCP_HEADER, data)
  local payload = data:sub(TCP_SIZE + 1)
  local c = conns[key(from, dport, sport)]
  if not c then
    local listener = ports[TCP][dport]
    if flags & RST ~= 0 then
      return
    elseif flags & SYN ~= 0 and flags & ACK == 0 and listener and listener.listening and #listener.pending < listener.backlog then
      c = newconn(from, dport, sport, nil)
      c.state, c.rcv_nxt, c.peerwnd, c.listener = SYN_RCVD, seq + 1, wnd, listener
      segment(c, SYN | ACK, c.snd_nxt)
      c.snd_nxt = c.snd_nxt + 1
      arm(c)
    else
      -- nothing here for it: refused (or reset), as TCP answers
      local rst = string.pack(TCP_HEADER, TCP, dport, sport, RST | ACK, ack, (seq + #payload + 1) % SEQ_SPACE, 0)
      transmit(from, rst)
    end
    return
  end
  if flags & RST ~= 0 then
    reset(c, c.state == SYN_SENT and "ECONNREFUSED" or "ECONNRESET")
    return
  end
  -- (sequence numbers as this end counts them: the peer's are taken near its own, so they never wrap)
  seq = c.rcv_nxt + (seq - c.rcv_nxt % SEQ_SPACE)
  ack = c.snd_una + ((ack - c.snd_una) % SEQ_SPACE)
  if c.state == SYN_SENT then
    if flags & SYN ~= 0 and flags & ACK ~= 0 and ack == c.snd_nxt then
      c.state, c.snd_una, c.rcv_nxt, c.peerwnd, c.tries, c.timer = ESTABLISHED, ack, seq + 1, wnd, 0, nil
      segment(c, ACK, c.snd_nxt)
    end
    return
  end
  if c.state == SYN_RCVD then
    if flags & SYN ~= 0 then
      segment(c, SYN | ACK, c.snd_nxt - 1) -- (its ACK was lost: again)
      return
    elseif flags & ACK ~= 0 and ack == c.snd_nxt then
      c.state, c.snd_una, c.tries, c.timer = ESTABLISHED, ack, 0, nil
      local listener = c.listener
      c.listener = nil
      if listener.listening then
        listener.pending[#listener.pending + 1] = c
      else
        reset(c, "ECONNRESET")
        return
      end
    else
      return
    end
  end
  if flags & ACK ~= 0 and ack > c.snd_una and ack <= c.snd_nxt then
    local acked = ack - c.snd_una
    local fin = c.fin_sent and ack == c.snd_nxt and 1 or 0 -- (its FIN's number is acknowledged too)
    c.out = c.out:sub(acked - fin + 1)
    c.snd_una, c.tries = ack, 0
    c.timer = c.snd_una ~= c.snd_nxt and k.now() + RTO_TICKS or nil
  end
  c.peerwnd = wnd
  local answer = false
  if #payload > 0 then
    answer = true
    if seq == c.rcv_nxt and #c.inbuf + #payload <= WINDOW and not c.fin_rcvd then
      c.inbuf = c.inbuf .. payload
      c.rcv_nxt = c.rcv_nxt + #payload
    end
  end
  if flags & FIN ~= 0 and seq + #payload == c.rcv_nxt and not c.fin_rcvd then
    c.fin_rcvd, c.rcv_nxt, answer = true, c.rcv_nxt + 1, true
  end
  if answer then
    segment(c, ACK, c.snd_nxt) -- (every data segment is acknowledged, in order or not)
  end
  output(c)
  if finished(c) and c.closed then
    reset(c)
  end
end

-- What's due: unacknowledged segments sent again (from the first: go back N), a closed window
-- probed; a connection tried MAX_TRIES times with no answer is reset.
local function timers()
  local now = k.now()
  local soonest
  for _, c in pairs(conns) do
    if c.timer and c.timer <= now then
      c.tries = c.tries + 1
      c.timer = now + RTO_TICKS
      if c.tries > MAX_TRIES then
        reset(c, "ETIMEDOUT")
      elseif c.state == SYN_SENT then
        if segment(c, SYN, c.snd_una) then
          c.unreached = nil
        else
          c.unreached = (c.unreached or 0) + 1
          if c.unreached >= UNREACHED_TRIES then
            reset(c, "EHOSTUNREACH")
          end
        end
      elseif c.state == SYN_RCVD then
        segment(c, SYN | ACK, c.snd_una)
      else
        if c.fin_sent and c.snd_una ~= c.snd_nxt then
          c.fin_sent = false -- (its FIN is sent again after the data)
        end
        c.snd_nxt = c.snd_una
        output(c, true)
      end
    end
    if c.timer and (not soonest or c.timer < soonest) then
      soonest = c.timer
    end
  end
  k.timer = soonest
end

-- ---- receiving ----------------------------------------------------------------------------------

-- A UDP datagram: to the socket bound to its port (dropped when there's none, or its queue is full).
local function udp(data, from)
  if #data < UDP_SIZE then
    return
  end
  local _, source, port = string.unpack(UDP_HEADER, data)
  local socket = ports[UDP][port]
  if socket and #socket.queue < QUEUE then
    socket.queue[#socket.queue + 1] = {data:sub(UDP_SIZE + 1), from, source}
  end
end

local function receive(data, from)
  local protocol = data:byte(1)
  if protocol == UDP then
    udp(data, from)
  elseif protocol == TCP then
    tcp(data, from)
  end
end

-- The frames the cards took (and this computer's own), to their sockets. A card with none raises
-- its interrupt when one comes: the kernel listens (a read or poll waiting, or a connection open).
local function pump()
  while loop[1] do
    local frame = table.remove(loop, 1)
    receive(frame[1], frame[2])
  end
  for _, nic in ipairs(interfaces()) do
    nics[nic.id] = true
    while true do
      local ok, frame = board.invoke(nic.id, "receive")
      if not (ok and frame) then
        k.listen(nic.id)
        break
      end
      receive(frame.data, frame.from)
    end
  end
  while loop[1] do
    local frame = table.remove(loop, 1)
    receive(frame[1], frame[2])
  end
end

-- The kernel's hooks: a card's interrupt (its frames), and each pass (timers that are due; while a
-- connection is open, the cards are listened to).
function k.raised(source)
  if nics[source] then
    pump()
  end
end

function k.service()
  if not next(conns) then
    k.timer = nil -- (nothing left to time)
  else
    for _, nic in ipairs(interfaces()) do
      nics[nic.id] = true
      k.listen(nic.id)
    end
    if loop[1] then
      pump()
    end
    if k.timer and k.now() >= k.timer then
      timers()
    end
  end
end

-- ---- sockets ------------------------------------------------------------------------------------

local net = {}

local function bind(socket, port)
  local protocol = socket.kind == "stream" and TCP or UDP
  if port == 0 then
    port = ephemeral(protocol)
  end
  if not port or ports[protocol][port] then
    return fail("EADDRINUSE")
  end
  socket.port = port
  ports[protocol][port] = socket
  return port
end

-- A stream socket's file: its connection's data both ways.
local function streamfile(socket, c)
  socket.conn = c
  c.owner = socket
  function socket.events()
    pump()
    if socket.listening then
      return {IN = #socket.pending > 0}
    end
    local over = c.state == CLOSED
    return {IN = #c.inbuf > 0 or c.fin_rcvd or over, OUT = c.state == ESTABLISHED and #c.out < WINDOW and not c.closing,
      HUP = c.fin_rcvd and (c.closing or over) or over, ERR = c.error ~= nil}
  end
  function socket.read(self, n)
    pump()
    if #c.inbuf > 0 then
      local data = c.inbuf:sub(1, n)
      c.inbuf = c.inbuf:sub(#data + 1)
      if c.state == ESTABLISHED and (c.advertised or 0) < MSS and WINDOW - #c.inbuf >= MSS then
        segment(c, ACK, c.snd_nxt) -- room again: the peer hears (a window update)
      end
      return data
    elseif c.fin_rcvd then
      return "" -- the peer's done: the end
    elseif c.error then
      return fail(c.error)
    elseif c.state == CLOSED then
      return ""
    end
    return k.BLOCK
  end
  function socket.write(self, data)
    pump()
    if c.error then
      return fail(c.error == "ECONNRESET" and "EPIPE" or c.error)
    elseif c.closing or c.state == CLOSED then
      return fail("EPIPE")
    elseif c.state ~= ESTABLISHED then
      return k.BLOCK
    end
    local room = WINDOW - #c.out
    if room <= 0 then
      return k.BLOCK
    end
    local taken = data:sub(1, room)
    c.out = c.out .. taken
    output(c)
    return #taken
  end
  return socket
end

-- socket([type]): "dgram" (UDP, the default) or "stream" (TCP).
function net.socket(kind)
  kind = kind or "dgram"
  if kind ~= "dgram" and kind ~= "stream" then
    return fail("EPROTONOSUPPORT")
  end
  local socket = {type = "socket", kind = kind, queue = {}}
  if kind == "dgram" then
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
  else
    function socket.events()
      return {}
    end
  end
  function socket.close()
    if socket.listening then
      socket.listening = false
      for _, c in ipairs(socket.pending) do
        segment(c, RST | ACK, c.snd_nxt)
        reset(c)
      end
    end
    local c = socket.conn
    if c and c.state ~= CLOSED then
      c.closing, c.closed = true, true
      output(c) -- (its FIN goes once what's unsent is sent)
      if c.state ~= ESTABLISHED then
        reset(c)
      end
    end
    local protocol = kind == "stream" and TCP or UDP
    if socket.port and ports[protocol][socket.port] == socket and not (c and c.state ~= CLOSED) then
      ports[protocol][socket.port] = nil
    end
    return true
  end
  return socket
end

-- bind(port): the port it takes datagrams or connections on (0: any free one); the port.
function net.bind(socket, request, port)
  if socket.port or socket.conn then
    return fail("EINVAL")
  elseif math.type(port) ~= "integer" or port < 0 or port > MAX_PORT then
    return fail("EINVAL")
  end
  return bind(socket, port)
end

-- sendto(data, address, port): a UDP datagram; the bytes sent.
function net.sendto(socket, request, data, address, port)
  if socket.kind ~= "dgram" then
    return fail("EOPNOTSUPP")
  elseif type(data) ~= "string" or type(address) ~= "string" or math.type(port) ~= "integer" or port < 1 or port > MAX_PORT then
    return fail(address == nil and "EDESTADDRREQ" or "EINVAL")
  elseif #data + UDP_SIZE > MTU then
    return fail("EMSGSIZE")
  end
  if not socket.port then
    local ok, why, errno = bind(socket, 0)
    if not ok then
      return nil, why, errno
    end
  end
  local ok, why, errno = transmit((address:gsub("^id:", "")), string.pack(UDP_HEADER, UDP, socket.port, port) .. data)
  if not ok then
    return nil, why, errno
  end
  return #data
end

-- recvfrom(): the oldest datagram: its data, the sender's address and port; it waits for one.
function net.recvfrom(socket)
  if socket.kind ~= "dgram" then
    return fail("EOPNOTSUPP")
  end
  pump()
  local datagram = table.remove(socket.queue, 1)
  if not datagram then
    return k.BLOCK
  end
  return datagram[1], datagram[2], datagram[3]
end

-- listen([backlog]): it takes connections on its port (one is given, if it has none).
function net.listen(socket, request, backlog)
  if socket.kind ~= "stream" then
    return fail("EOPNOTSUPP")
  elseif socket.conn then
    return fail("EISCONN")
  end
  if not socket.port then
    local ok, why, errno = bind(socket, 0)
    if not ok then
      return nil, why, errno
    end
  end
  socket.listening, socket.pending = true, {}
  socket.backlog = math.type(backlog) == "integer" and backlog > 0 and backlog or BACKLOG
  function socket.events()
    pump()
    return {IN = #socket.pending > 0}
  end
  return true
end

-- accept(): the oldest connection made to it, as a new socket, and the peer's address and port; it
-- waits for one.
function net.accept(socket, request)
  if not socket.listening then
    return fail("EINVAL")
  end
  pump()
  local c = table.remove(socket.pending, 1)
  if not c then
    return k.BLOCK
  end
  local conn = streamfile({type = "socket", kind = "stream", queue = {}, port = c.lport}, c)
  conn.close = function()
    c.closing, c.closed = true, true
    output(c)
    if c.state ~= ESTABLISHED then
      reset(c)
    elseif finished(c) then
      reset(c)
    end
    return true
  end
  return conn, c.raddr, c.rport
end

-- connect(address, port): a connection to the socket listening there; it waits until it's open
-- (or refused, or no card reaches the address, or it timed out).
function net.connect(socket, request, address, port)
  if socket.kind ~= "stream" then
    return fail("EOPNOTSUPP")
  elseif socket.listening or socket.conn and not request.c then
    return fail("EISCONN")
  elseif type(address) ~= "string" or math.type(port) ~= "integer" or port < 1 or port > MAX_PORT then
    return fail("EINVAL")
  end
  local c = request.c
  if not c then
    if not socket.port then
      local ok, why, errno = bind(socket, 0)
      if not ok then
        return nil, why, errno
      end
    end
    c = newconn((address:gsub("^id:", "")), socket.port, port, socket)
    c.state = SYN_SENT
    streamfile(socket, c)
    request.c = c
    local ok, why, errno = segment(c, SYN, c.snd_nxt)
    c.snd_nxt = c.snd_nxt + 1
    if errno == "ENETUNREACH" then
      reset(c, errno)
      return nil, why, errno
    end
    c.unreached = not ok and 1 or nil -- (no card took it: sent again, as a lost frame is; no route after a few)
    arm(c)
  end
  pump()
  if c.state == ESTABLISHED then
    return true
  elseif c.state == CLOSED then
    return fail(c.error or "ECONNREFUSED")
  end
  return k.BLOCK
end

-- shutdown(): no more to send (the peer reads its end once it has the rest); reading goes on.
function net.shutdown(socket)
  local c = socket.conn
  if not c or c.state == CLOSED then
    return fail("ENOTCONN")
  end
  c.closing = true
  output(c)
  return true
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
