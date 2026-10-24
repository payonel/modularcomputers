-- Log the sounds of a base's doors, chests and broken blocks: each sound the audio sensor hears
-- whose id matches one of WATCH is appended to a log with the world's time and where it came from
-- ("Day 2 14:03:10 minecraft:block.chest.open 2.5 0.0 -1.5").
--
-- It reads the sensor's file, a line a sound: read blocks until one is heard. The log is opened and
-- closed for each line, so it is on the disk at once (a disk's file shows its writes at close).
local LOG = "/home/sounds.log"
local WATCH = {               -- Lua patterns over a sound's id
  "door%.",                   -- any door or trapdoor opening or closing
  "^minecraft:block%.chest%.",
  "^minecraft:block%.barrel%.",
  "%.break$",                 -- a block broken
}

local function watched(sound)
  for _, pattern in ipairs(WATCH) do
    if sound:find(pattern) then
      return true
    end
  end
  return false
end

local sensor = assert(io.open("/dev/hearing0"))
for line in sensor:lines() do
  local sound = line:match("^(%S+) %S+ %S+ %S+$")
  if sound and watched(sound) then
    local log = assert(io.open(LOG, "a"))
    log:write(os.date("%c"), " ", line, "\n")
    log:close()
  elseif line:match("^lost ") then
    local log = assert(io.open(LOG, "a"))
    log:write(os.date("%c"), " (", line, " sounds)\n")
    log:close()
  end
end
