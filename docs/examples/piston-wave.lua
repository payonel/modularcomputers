-- Fire every robotic piston on the bus in turn, a wave down a row of them:
-- lua piston-wave.lua [side] [rounds]
--
-- sys.devices() lists the pistons (type "actuator") in bus order, which you set at the computer;
-- each has a file in /dev (actuator0, actuator1...) whose ioctls are its methods. Each piston is
-- pushed on its front side (or the side given), one after another, DELAY seconds apart, for as
-- many rounds as asked. A piston that refuses ("cannot push") is reported and the wave goes on.
local sys = require("sys")

local DELAY = 0.25   -- seconds between one piston and the next

local side = arg[1] or "front"
local rounds = tonumber(arg[2]) or 1

local pistons = {}
for _, device in ipairs(sys.devices()) do
  if device.type == "actuator" then
    pistons[#pistons + 1] = {name = device.name, fd = assert(sys.open("/dev/" .. device.name))}
  end
end
if #pistons == 0 then
  io.stderr:write("piston-wave: no robotic piston on the bus\n")
  os.exit(1)
end

for _ = 1, rounds do
  for _, piston in ipairs(pistons) do
    local done, why = sys.ioctl(piston.fd, "push", side)
    if not done then
      io.stderr:write("piston-wave: ", piston.name, ": ", tostring(why), "\n")
    end
    sys.sleep(DELAY)
  end
end

for _, piston in ipairs(pistons) do
  sys.close(piston.fd)
end
