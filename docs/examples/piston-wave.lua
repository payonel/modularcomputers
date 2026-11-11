-- Fire every robotic piston on the bus in turn, a wave down a row of them:
-- lua piston-wave.lua [face] [rounds]
--
-- sys.devices() lists the pistons (type "actuator") in bus order, which you set at the computer;
-- each has a file in /dev (actuator0, actuator1...) whose ioctls are its methods. Each piston is
-- pushed on its front face (or the face given), one after another, DELAY seconds apart, for as
-- many rounds as asked. A push that couldn't move (its end: blocked, the block that won't move, the
-- face) is reported and the wave goes on.
local sys = require("sys")

local DELAY = 0.25   -- seconds between one piston and the next

local face = arg[1] or "front"
local rounds = tonumber(arg[2]) or 1

-- The end of the piston's last 'method' command (its event): ok, or not, why, and for blocked the
-- block that won't move (nil: no one block) and the face.
local function ended(fd, method)
  while true do
    local got = sys.ioctl(fd, "pull")
    if got and got.event == method then
      return got
    elseif not got then
      sys.poll({[fd] = {PRI = true}})
    end
  end
end

-- What a failed end says: its why, and a blocked one's block's name (- for none: its name is "") and face.
local function said(got)
  if got.why == "blocked" then
    return "blocked " .. (got.name ~= "" and got.name or "-") .. " " .. got.face
  end
  return tostring(got.why)
end

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
    local done, why = sys.ioctl(piston.fd, "push", face)
    local got = done and ended(piston.fd, "push")
    if not done or not got.ok then
      io.stderr:write("piston-wave: ", piston.name, ": ", got and said(got) or tostring(why), "\n")
    end
    sys.sleep(DELAY)
  end
end

for _, piston in ipairs(pistons) do
  sys.close(piston.fd)
end
