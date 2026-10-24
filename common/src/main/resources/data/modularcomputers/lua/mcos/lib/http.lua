-- http: requests through the internet card, for wget and curl (sys.http underneath). request(url,
-- options) makes one, following redirects when options.follow (each hop a new request, judged by
-- the card again: a redirect can't lead it where a URL couldn't), up to MAX_REDIRECTS; it gives
-- the response ({status, message, headers, url: where it came from}) and its body's descriptor,
-- or nil and why.
local sys = require("sys")

local http = {MAX_REDIRECTS = 20}

local REDIRECTS = {[301] = true, [302] = true, [303] = true, [307] = true, [308] = true}

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

function http.request(url, options)
  options = options or {}
  local hops = 0
  local method, body = options.method, options.body
  while true do
    local fd, why = sys.http(url, {method = method, headers = options.headers, body = body})
    if not fd then
      return nil, why
    end
    local response, err = sys.ioctl(fd, "HTTP_RESPONSE")
    if not response then
      sys.close(fd)
      return nil, err
    end
    response.url = url
    local location = response.headers.location
    if not (options.follow and REDIRECTS[response.status] and location) then
      return response, fd
    end
    sys.close(fd)
    hops = hops + 1
    if hops > http.MAX_REDIRECTS then
      return nil, "too many redirects"
    end
    url = http.resolve(url, location)
    if response.status == 303 or (response.status == 301 or response.status == 302) and method == "POST" then
      method, body = "GET", nil -- (as browsers and curl do)
    end
  end
end

return http
