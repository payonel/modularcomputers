-- A hidden door: a lever opens it, and it closes again when the lever is off.
-- lua piston-door.lua &
--
-- A sticky robotic piston holds the door's block: extend pushes it out into the doorway (closed),
-- retract pulls it back (open). The lever's signal comes in on a redstone card (/dev/redstone0, a
-- line a face: "face level powered"), and sys.poll waits for its change (PRI). The piston's file,
-- /dev/actuator0, has no data: its ioctls are its methods, extend and retract, by the side to move.
-- "front" is the side away from its cable, so the program works however the piston is turned.
local sys = require("sys")

local LEVER = "west"   -- the face the lever's signal comes in on
local SIDE = "front"   -- the piston's side that holds the door's block

local redstone = assert(io.open("/dev/redstone0"))
local piston = assert(sys.open("/dev/actuator0"))
local watch = {[sys.fileno(redstone)] = {PRI = true}}

local function lever()
  redstone:seek("set", 0)
  return (tonumber(redstone:read("a"):match(LEVER .. " (%d+)")) or 0) > 0
end

while true do
  local open = lever()
  local done, why
  if open then
    done, why = sys.ioctl(piston, "retract", SIDE)
    if not done and why == "not extended" then
      done = true  -- (open already)
    end
  else
    done, why = sys.ioctl(piston, "extend", SIDE)  -- (closed already: true, and free)
  end
  if not done then
    io.stderr:write("door: ", open and "open" or "close", ": ", tostring(why), "\n")
  end
  sys.poll(watch)  -- block until the lever moves
end
