-- Mirror a list of files from a web server into a folder, several downloads at once.
--
-- A card has up to 4 requests open at a time. Each is started with the request method and its
-- handle kept; then one loop waits for the card's news (its file's PRI) and asks every open
-- request for what it has: its response first (false: not yet), then pieces of its body
-- (false: none yet; "": its end). A download whose file is full of unread pieces waits for the
-- program, so nothing is lost however slowly it goes.
--
-- usage: lua internet-mirror.lua BASE-URL FOLDER NAME...
local sys = require("sys")

local CARD = "/dev/internet0"
local OPEN = 4 -- requests open at once: the card's most
local PIECE = 4096 -- the most one read of a body takes

local base, folder = arg[1], arg[2]
local queue = table.move(arg, 3, #arg, 1, {}) -- the names still to start
if not (base and folder and queue[1]) then
  io.stderr:write("usage: internet-mirror BASE-URL FOLDER NAME...\n")
  os.exit(2)
end
base = base:gsub("/*$", "/")

local card = assert(sys.open(CARD))
local open = {} -- each request's handle, to {name, out (its file, once the response came)}
local failed = 0

-- Starts requests from the queue while there's room.
local function start()
  while queue[1] and #open < OPEN do
    local name = table.remove(queue, 1)
    local handle, why = sys.ioctl(card, "request", base .. name)
    if handle then
      open[#open + 1] = {handle = handle, name = name}
    else
      io.stderr:write(name, ": ", why, "\n")
      failed = failed + 1
    end
  end
end

-- Ends a download: closes its request and its file.
local function finish(index, why)
  local download = table.remove(open, index)
  sys.ioctl(card, "close", download.handle)
  if download.out then
    download.out:close()
  end
  if why then
    io.stderr:write(download.name, ": ", why, "\n")
    failed = failed + 1
  end
end

-- What a download has now: true when it's done (or failed), false when it waits for more.
local function step(download)
  if not download.out then
    local response, why = sys.ioctl(card, "response", download.handle)
    if response == false then
      return false
    elseif not response then
      return true, why
    elseif response.status ~= 200 then
      return true, response.status .. " " .. response.message
    end
    download.out = assert(io.open(folder .. "/" .. download.name, "w"))
  end
  while true do
    local piece, why = sys.ioctl(card, "read", download.handle, PIECE)
    if piece == false then
      return false
    elseif not piece then
      return true, why
    elseif piece == "" then
      return true
    end
    download.out:write(piece)
  end
end

start()
while open[1] do
  sys.poll({[card] = {PRI = true}}) -- blocks until a request has news
  for index = #open, 1, -1 do
    local done, why = step(open[index])
    if done then
      finish(index, why)
    end
  end
  start()
end
sys.close(card)
os.exit(failed == 0 and 0 or 1)
