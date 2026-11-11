-- A hidden door: a lever opens it, and it closes again when the lever is off.
-- lua piston-door.lua &
--
-- A sticky robotic piston holds the door's block: extend pushes it out into the doorway (closed),
-- retract pulls it back (open). The lever's signal comes in on a redstone card (/dev/redstone0, a
-- line a face: "face level powered"), and sys.poll waits for its change (PRI). The piston's
-- file, /dev/actuator0, reads as its moves' ends; its ioctls are its methods, extend and retract,
-- by the face to move (each waits for the one before: the piston does one move at a time).
-- "front" is the face away from its cable, so the program works however the piston is turned.
local sys = require("sys")

local LEVER = "west"   -- the face the lever's signal comes in on
local FACE = "front"   -- the piston's face that holds the door's block

local redstone = assert(io.open("/dev/redstone0"))
local piston = assert(sys.open("/dev/actuator0"))
local watch = {[sys.fileno(redstone)] = {PRI = true}}

local function lever()
  redstone:seek("set", 0)
  return (tonumber(redstone:read("a"):match(LEVER .. " (%d+)")) or 0) > 0
end

while true do
  local open = lever()
  -- (open or closed already: true, and nothing moves)
  local done, why = sys.ioctl(piston, open and "retract" or "extend", FACE)
  if not done then
    io.stderr:write("door: ", open and "open" or "close", ": ", tostring(why), "\n")
  end
  sys.poll(watch)  -- block until the lever moves
end
