-- A network card as OpenComputers' modem component, over mcos's UDP sockets (ports are the
-- kernel's here, a socket each, so one port's open for every modem that opened it): a message is a
-- datagram to its port, modem_message when one comes in on a card that opened its port. A message
-- of one string is that string (so mcos's programs, nc -u, talk with it); any other is its values
-- serialized after MARK (nil, booleans, numbers and strings, as OpenComputers carried).
local sys = require("sys")
local devices = require("devices")
local serialization = require("serialization")

local MARK = "\0openos\0" -- what starts a message of several values
local MAX_PORT = 65535
local MAX_PACKET = 8187 -- a datagram's data, at most (a frame's 8192, less UDP's header)

local ports = {} -- by number: {fd, cards = {address = true}}, shared by every modem
local sender -- an unbound socket, for sending

-- A message's values as a datagram's data, and back.
local function encode(...)
  local values = table.pack(...)
  for i = 1, values.n do
    local kind = type(values[i])
    if kind ~= "nil" and kind ~= "boolean" and kind ~= "number" and kind ~= "string" then
      error("unsupported data type: " .. kind, 3)
    end
  end
  if values.n == 1 and type(values[1]) == "string" and values[1]:sub(1, #MARK) ~= MARK then
    return values[1]
  end
  return MARK .. serialization.serialize(values)
end

local function decode(data)
  if data:sub(1, #MARK) ~= MARK then
    return table.pack(data)
  end
  return serialization.unserialize(data:sub(#MARK + 1)) or table.pack(data)
end

return function(name, info, ctx, address)
  local card = assert(devices.open(name))
  sender = sender or assert(sys.socket("dgram"))
  local wireless = false
  for _, interface in ipairs(sys.ioctl(sender, "SIOCGIFCONF") or {}) do
    if interface.address == address then
      wireless = interface.wireless
    end
  end

  local function port(n)
    n = math.tointeger(n)
    if not n or n < 1 or n > MAX_PORT then
      error("invalid port number", 3)
    end
    return n
  end

  local methods, docs = {}, {}

  docs.isWireless = "function():boolean -- Whether this is a wireless network card."
  function methods.isWireless()
    return wireless
  end

  docs.isWired = "function():boolean -- Whether this is a wired network card."
  function methods.isWired()
    return not wireless
  end

  docs.maxPacketSize = "function():number -- Gets the maximum packet size (config setting)."
  function methods.maxPacketSize()
    return MAX_PACKET
  end

  docs.isOpen = "function(port:number):boolean -- Whether the specified port is open."
  function methods.isOpen(n)
    local open = ports[port(n)]
    return open ~= nil and open.cards[address] == true
  end

  docs.open = "function(port:number):boolean -- Opens the specified port. Returns true if the port was opened."
  function methods.open(n)
    n = port(n)
    local open = ports[n]
    if open and open.cards[address] then
      return false
    end
    if not open then
      local fd = assert(sys.socket("dgram"))
      local ok, why = sys.bind(fd, n)
      if not ok then
        sys.close(fd)
        error(why, 2)
      end
      open = {fd = fd, cards = {}}
      ports[n] = open
      ctx.source(fd, {IN = true}, function()
        local data, from, sent, where = sys.recvmsg(fd)
        if data and open.cards[where.to] then
          local values = decode(data)
          ctx.push("modem_message", where.to, from, n, where.distance, table.unpack(values, 1, values.n))
        end
      end)
    end
    open.cards[address] = true
    return true
  end

  docs.close = "function([port:number]):boolean -- Closes the specified port (default: all ports). Returns true if ports were closed."
  function methods.close(n)
    local closed = false
    for number, open in pairs(ports) do
      if (n == nil or number == port(n)) and open.cards[address] then
        open.cards[address] = nil
        closed = true
        if not next(open.cards) then
          ctx.source(open.fd, nil)
          sys.close(open.fd)
          ports[number] = nil
        end
      end
    end
    return closed
  end

  docs.send = "function(address:string, port:number, data...) -- Sends the specified data to the specified target."
  function methods.send(to, n, ...)
    if type(to) ~= "string" then
      error("bad argument #1 (string expected, got " .. type(to) .. ")", 2)
    end
    local sent, why = sys.sendto(sender, encode(...), to, port(n))
    if not sent then
      return nil, why
    end
    return true
  end

  docs.broadcast = "function(port:number, data...) -- Broadcasts the specified data on the specified port."
  function methods.broadcast(n, ...)
    local sent, why = sys.sendto(sender, encode(...), "broadcast", port(n))
    if not sent then
      return nil, why
    end
    return true
  end

  docs.getStrength = "function():number -- Get the signal strength (range) used when sending messages."
  function methods.getStrength()
    return card.getStrength()
  end

  docs.setStrength = "function(strength:number):number -- Set the signal strength (range) used when sending messages."
  function methods.setStrength(strength)
    return card.setStrength(strength)
  end

  docs.getWakeMessage = "function():string, boolean -- Get the current wake-up message."
  function methods.getWakeMessage()
    return card.getWake(), true
  end

  docs.setWakeMessage = "function(message:string[, fuzzy:boolean]):string -- Set the wake-up message (found anywhere in a message's data)."
  function methods.setWakeMessage(message)
    return card.setWake(message)
  end

  return methods, docs, function()
    methods.close()
    devices.close(card)
  end
end
