-- http: requests through the internet card, for wget and curl: its /dev file and its methods, as
-- any device is used (ioctls by the method's name: request, response, read, close), and poll's PRI
-- to wait for its news. request(url, options) makes one, following redirects when options.follow
-- (each hop a new request, judged by the card again: a redirect can't lead it where a URL couldn't),
-- up to MAX_REDIRECTS; it gives the response ({status, message, headers, url: where it came from})
-- and its body ({read = function(n), close = function()}: read gives a piece, "" at the end, or nil
-- and why), or nil and why: its failure as words, then the failure itself ({why, ...}: the card's,
-- e.g. {why = "network", reason = "timed_out", message}; nil for http's own, too many redirects).
-- options.device: the card's file (the first internet card's, else).
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

-- A failure's fields, in the order its why's shape gives them (the card's: bad_argument, denied, limit,
-- network, a fault).
local FIELDS = {"reason", "condition", "part", "of", "max", "argument", "value", "expected", "message"}

-- A call's failure as words: its why, then each field that says something (the card's failure, the
-- ioctl's fourth answer); else as the call said it.
local function said(why, failure)
  if type(failure) ~= "table" then
    return why
  end
  local words = {failure.why}
  for _, field in ipairs(FIELDS) do
    local value = failure[field]
    if value ~= nil and value ~= "" then
      words[#words + 1] = tostring(value)
    end
  end
  return table.concat(words, " ")
end

-- Asks a method again, waiting for the card's news, until it answers (false: not yet) or fails: its
-- answer, or nil, its failure as words and the failure.
local function await(fd, method, handle, ...)
  while true do
    local answer, why, _, failure = sys.ioctl(fd, method, handle, ...)
    if answer ~= false then
      return answer, answer == nil and said(why, failure) or why, failure
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
    local handle, err, _, failure = sys.ioctl(fd, "request", url, method, options.headers, body)
    if not handle then
      sys.close(fd)
      return nil, said(err, failure), failure
    end
    local response, failed, why = await(fd, "response", handle)
    if not response then
      sys.ioctl(fd, "close", handle)
      sys.close(fd)
      return nil, failed, why
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
