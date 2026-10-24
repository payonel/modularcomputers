-- Keep a census of what walks past: a count of each kind of mob the motion sensor reports, kept in
-- a file, a line a kind ("minecraft:pig 12"), sorted by name. `cat /home/census.txt` shows it.
--
-- It reads the sensor's file, a line an event ("minecraft:pig 1.5 0.0 -3.0"): read blocks until
-- the sensor has one, so the loop never spins. A mob that keeps moving is counted each time it is
-- seen moving: this counts sightings, not mobs.
local CENSUS = "/home/census.txt"

local counts = {}
local sensor = assert(io.open("/dev/motion0"))

local function save()
  local kinds = {}
  for kind in pairs(counts) do
    kinds[#kinds + 1] = kind
  end
  table.sort(kinds)
  local out = assert(io.open(CENSUS, "w"))
  for _, kind in ipairs(kinds) do
    out:write(kind, " ", counts[kind], "\n")
  end
  out:close()
end

for line in sensor:lines() do -- (each read waits for the next event)
  local mob = line:match("^(%S+) %S+ %S+ %S+$") -- ("lost n" lines are not sightings)
  if mob then
    counts[mob] = (counts[mob] or 0) + 1
    save()
  end
end
