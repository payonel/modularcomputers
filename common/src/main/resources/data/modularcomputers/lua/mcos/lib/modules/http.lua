-- http: the internet card's requests as files (loaded at the first, as net is). sys.http(url,
-- {method, headers, body}) starts a request on the first internet card and gives a file: reading
-- it gives the response's body as it comes (a read with nothing yet waits, as a socket's does), its
-- end the body's; ioctl HTTP_RESPONSE gives {status, message, headers} (headers by name, in lower
-- case), waiting for them. A request that failed (refused, unknown host, timed out) fails its reads
-- and the ioctl with why (EIO). Closing it stops it. Linux has no such thing in its kernel (HTTP is
-- a library's, over sockets); here the card does HTTP itself (TLS is beyond a program's Lua), and
-- this is the kernel's driver for it.
--
-- The card's events are taken as reads need them (k.take: none there, the kernel listens for the
-- card), each kept by its request's file; a request whose body piles up unread (MAX_HELD) stops
-- the taking until it's read, so one never fills the kernel's memory.
local k, vfs = ...

local fail = vfs.fail
local MAX_HELD = 256 * 1024 -- the most of a request's body the kernel keeps unread, in bytes
local KIND = "internet"

local http = {}
local open = {} -- requests, by card id and handle

local function card()
  for _, device in ipairs(k.devices.list) do
    if device.type == KIND then
      return device
    end
  end
end

-- Takes the card's waiting events into their requests' files; stops at one holding too much.
local function pump(id)
  while true do
    local ok, event = k.take(id, "pull")
    if not ok or event == nil then
      return
    end
    local r = open[id .. ":" .. tostring(event.handle)]
    if r then
      if event.event == "response" then
        r.response = {status = event.status, message = event.message, headers = event.headers or {}}
      elseif event.event == "data" then
        r.chunks[#r.chunks + 1] = event.data
        r.held = r.held + #event.data
      elseif event.event == "end" then
        r.ended = true
      elseif event.event == "error" then
        r.error = event.why
      end
      if r.held > MAX_HELD then
        return
      end
    end
  end
end

function http.open(url, options)
  local device = card()
  if not device then
    return nil, "no internet card", "ENODEV"
  end
  local ok, handle = board.invoke(device.id, "request", url, options.method, options.headers, options.body)
  if not ok then
    return nil, tostring(handle), "EINVAL"
  end
  local id, key = device.id, device.id .. ":" .. tostring(handle)
  local r = {chunks = {}, held = 0}
  open[key] = r
  local function failed()
    return nil, r.error, "EIO"
  end
  return {
    type = "http",
    events = function(self)
      pump(id)
      return {IN = #r.chunks > 0 or r.ended or r.error ~= nil}
    end,
    read = function(self, n)
      pump(id)
      if #r.chunks > 0 then
        local chunk = r.chunks[1]
        if #chunk > n then
          r.chunks[1] = chunk:sub(n + 1)
          chunk = chunk:sub(1, n)
        else
          table.remove(r.chunks, 1)
        end
        r.held = r.held - #chunk
        return chunk
      elseif r.ended then
        return ""
      elseif r.error then
        return failed()
      end
      return k.BLOCK
    end,
    ioctl = function(self, op)
      if op ~= "HTTP_RESPONSE" then
        return fail("EINVAL")
      end
      pump(id)
      if r.response then
        local headers = {}
        for name, value in pairs(r.response.headers) do
          headers[name] = value
        end
        return {status = r.response.status, message = r.response.message, headers = headers}
      elseif r.error then
        return failed()
      end
      return k.BLOCK
    end,
    close = function(self)
      open[key] = nil
      board.invoke(id, "close", handle)
      return true
    end,
  }
end

return http
