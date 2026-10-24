-- http: requests through the internet card, for wget and curl: its /dev file and its methods, as
-- any device is used (ioctls by the method's name: request, response, read, close), and poll's PRI
-- to wait for its news. request(url, options) makes one, following redirects when options.follow
-- (each hop a new request, judged by the card again: a redirect can't lead it where a URL couldn't),
-- up to MAX_REDIRECTS; it gives the response ({status, message, headers, url: where it came from})
-- and its body ({read = function(n), close = function()}: read gives a piece, "" at the end, or nil
-- and why), or nil and why. options.device: the card's file (the first internet card's, else).
local sys = require("sys")

local http = {MAX_REDIRECTS = 20}

local REDIRECTS = {[301] = true, [302] = true, [303] = true, [307] = true, [308] = true}
local KIND = "internet"

-- A URL a Location names, from the one it came in answer to.
function http.resolve(base, location)
  if location:match("^%a[%w+.-]*://") then
    return location
  end
  local origin = base:match("^(%a[%w+.-]*://[^/?#]*)")
  if location:sub(1, 2) == "//" then
    return base:match("^(%a[%w+.-]*:)") .. location
  elseif location:sub(1, 1) == "/" then
    return origin .. location
  end
  local dir = base:match("^(%a[%w+.-]*://[^?#]*/)") or origin .. "/"
  return dir .. location
end

-- The first internet card's file, or nil.
local function card()
  for _, device in ipairs(sys.devices()) do
    if device.type == KIND then
      return "/dev/" .. device.name
    end
  end
end

-- Asks a method again, waiting for the card's news, until it answers (false: not yet) or fails.
local function await(fd, method, handle, ...)
  while true do
    local answer, why = sys.ioctl(fd, method, handle, ...)
    if answer ~= false then
      return answer, why
    end
    sys.poll({[fd] = {PRI = true}})
  end
end

function http.request(url, options)
  options = options or {}
  local path = options.device or card()
  if not path then
    return nil, "no internet card"
  end
  local fd, why = sys.open(path)
  if not fd then
    return nil, why
  end
  local hops = 0
  local method, body = options.method, options.body
  while true do
    local handle, err = sys.ioctl(fd, "request", url, method, options.headers, body)
    if not handle then
      sys.close(fd)
      return nil, err
    end
    local response, failed = await(fd, "response", handle)
    if not response then
      sys.ioctl(fd, "close", handle)
      sys.close(fd)
      return nil, failed
    end
    response.url = url
    local location = response.headers.location
    if not (options.follow and REDIRECTS[response.status] and location) then
      local reader = {}
      function reader.read(_, n)
        return await(fd, "read", handle, n)
      end
      function reader.close()
        sys.ioctl(fd, "close", handle)
        sys.close(fd)
      end
      return response, reader
    end
    sys.ioctl(fd, "close", handle)
    hops = hops + 1
    if hops > http.MAX_REDIRECTS then
      sys.close(fd)
      return nil, "too many redirects"
    end
    url = http.resolve(url, location)
    if response.status == 303 or (response.status == 301 or response.status == 302) and method == "POST" then
      method, body = "GET", nil -- (as browsers and curl do)
    end
  end
end

return http
