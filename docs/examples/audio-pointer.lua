-- Point at the last sound: twice a second, the redstone card lights the one face that looks most
-- toward the last sound the audio sensor heard (east, west, up, down, south or north), and darkens
-- the others. Put lamps on the block's faces and they show where the noise is.
--
-- It uses the sensor's method, pull, through its file's ioctls (each sound a table, its x, y and z
-- numbers: x grows east, y up, z south), and the redstone card's set, every face in one call.
local sys = require("sys")

local LOOK = 0.5 -- seconds between looks

local sensor = assert(sys.open("/dev/hearing0"))
local card = assert(sys.open("/dev/redstone0"))

-- The face toward a direction: the axis it is most along.
local function toward(x, y, z)
  local ax, ay, az = math.abs(x), math.abs(y), math.abs(z)
  if ax >= ay and ax >= az then
    return x >= 0 and "east" or "west"
  elseif ay >= az then
    return y >= 0 and "up" or "down"
  end
  return z >= 0 and "south" or "north"
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
    local face = toward(last.x, last.y, last.z)
    if face ~= lit then
      local faces = {}
      for _, name in ipairs({"north", "south", "east", "west", "up", "down"}) do
        faces[name] = {powered = name == face and 15 or 0}
      end
      sys.ioctl(card, "set", faces)
      lit = face
    end
  end
  sys.sleep(LOOK)
end
