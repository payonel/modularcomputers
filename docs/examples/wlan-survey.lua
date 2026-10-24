-- A site survey: listen to the air for a while, and write down every card heard and how far away
-- it is, nearest first.
--
-- The card's file, /dev/network0, is its traffic: a line a frame, as "in 5f0c2a9e-... 12.0m 17
-- broadcast" (the direction, the other card, the distance in meters, the bytes, how it went). The
-- distance is the card's own measure, which no socket gives. sys.poll waits for the next line, or
-- for the time left, so the program uses nothing while the air is quiet.
--
-- usage: lua wlan-survey.lua [report [seconds]]
local sys = require("sys")

local CARD = "/dev/network0"
local REPORT = arg[1] or "/home/survey.txt"
local SECONDS = tonumber(arg[2]) or 10 -- how long it listens
local READ_SIZE = 512 -- the most one read takes

local traffic = assert(sys.open(CARD))
local nearest = {} -- each card heard, to its nearest distance
local pending = "" -- what has been read of a line not yet ended
local stop = sys.uptime() + SECONDS

while true do
  local left = stop - sys.uptime()
  if left <= 0 then
    break
  end
  local ready = sys.poll({[traffic] = {IN = true}}, left)
  if ready[traffic] then
    pending = pending .. assert(sys.read(traffic, READ_SIZE))
    for line in pending:gmatch("([^\n]*)\n") do
      local direction, address, meters = line:match("^(%S+) (%S+) ([%d.]+)m ")
      if direction == "in" then
        local distance = tonumber(meters)
        if not nearest[address] or distance < nearest[address] then
          nearest[address] = distance
        end
      end
    end
    pending = pending:match("[^\n]*$")
  end
end

local cards = {}
for address in pairs(nearest) do
  cards[#cards + 1] = address
end
table.sort(cards, function(a, b)
  return nearest[a] < nearest[b]
end)
local out = assert(io.open(REPORT, "w"))
for _, address in ipairs(cards) do
  out:write(string.format("%s %.1f\n", address, nearest[address]))
end
out:close()
print(#cards .. " cards heard; see " .. REPORT)
