-- Lift a friend to a higher floor: hold the nearest player (or any creature) in the energy field,
-- carry them up FLOORS floors and over onto the floor's edge in front of the computer, and let
-- them go there. Each move is waited for: its event says it arrived, or what stopped it.
local sys = require("sys")

local FLOOR = 4 -- blocks a floor
local FLOORS = tonumber(arg[1] or "1")
local OVER = 2 -- blocks forward, onto the floor above

local card = assert(sys.open("/dev/field0"))

-- Waits for the card's next event, and gives it.
local function event()
  while true do
    local got = sys.ioctl(card, "pull")
    if got then
      return got
    end
    sys.poll({[card] = {PRI = true}})
  end
end

local function go(forward, up)
  assert(sys.ioctl(card, "move", forward, 0, up))
  local moved = event()
  if moved.event ~= "arrived" then
    print(moved.event .. ": " .. tostring(moved.why))
    sys.ioctl(card, "release")
    os.exit(1)
  end
end

local near = assert(sys.ioctl(card, "scan"))
local friend
for _, seen in ipairs(near) do
  if seen.name == "minecraft:player" then
    friend = seen
    break
  end
end
friend = friend or near[1]
if not friend then
  print("no one near")
  os.exit(1)
end

assert(sys.ioctl(card, "capture", friend.id))
go(0, FLOOR * FLOORS)
go(OVER, 0)
sys.ioctl(card, "release")
print("lifted " .. friend.name .. " " .. FLOORS .. " floor(s)")
