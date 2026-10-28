-- Point at the last sound: twice a second, the redstone card lights the one face that looks most
-- toward the last sound the audio sensor heard (front, back, left, right, up or down: the
-- computer's own), and darkens the others. Put lamps on the block's faces and they show where the
-- noise is, however the computer is turned.
--
-- It uses the sensor's method, pull, through its file's ioctls (each sound a table: how far forward,
-- right and up it was from the computer, its front's way), and the redstone card's set, every face
-- in one call (faces named from its front, as the sensor's numbers are).
local sys = require("sys")

local LOOK = 0.5 -- seconds between looks

local sensor = assert(sys.open("/dev/hearing0"))
local card = assert(sys.open("/dev/redstone0"))

-- The face toward a direction: the axis it is most along.
local function toward(forward, right, up)
  local af, ar, au = math.abs(forward), math.abs(right), math.abs(up)
  if af >= ar and af >= au then
    return forward >= 0 and "front" or "back"
  elseif au >= ar then
    return up >= 0 and "up" or "down"
  end
  return right >= 0 and "right" or "left"
end

local lit
while true do
  local last
  local sound = sys.ioctl(sensor, "pull")
  while sound do -- (the newest one heard since the last look)
    last = sound
    sound = sys.ioctl(sensor, "pull")
  end
  if last then
    local face = toward(last.forward, last.right, last.up)
    if face ~= lit then
      local faces = {}
      for _, name in ipairs({"front", "back", "left", "right", "up", "down"}) do
        faces[name] = {powered = name == face and 15 or 0}
      end
      sys.ioctl(card, "set", faces)
      lit = face
    end
  end
  sys.sleep(LOOK)
end
