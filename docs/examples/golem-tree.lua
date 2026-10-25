-- Cut down a tree up on a ledge and bring its wood home: a bot golem with a crafting card (its
-- storage) and an energy field card, a chest in front of it holding an axe, and a tree on a ledge
-- ahead of it: its trunk FORWARD blocks ahead and its lowest log UP blocks up, standing on the
-- ledge, the golem's way there clear.
--   lua golem-tree.lua [up [forward]]       (10 and 5)
-- It takes the axe and holds itself in its field, legs and all (so it keeps its tools: off the
-- ground, it cuts at a fifth of its speed, as a player in the air does), flies up beside the trunk,
-- cuts it from the air (its field lifting it to reach the logs above, clearing the leaves over it),
-- flies home, lets its field go to land on its legs at the chest, and puts the wood and the axe in
-- it (what else the tree dropped, its saplings and sticks, it keeps).
local sys = require("sys")
local devices = require("devices")

local UP = tonumber(arg[1] or "10")
local FORWARD = tonumber(arg[2] or "5")
local LOG = "_log$" -- what wood's name ends with (minecraft:oak_log)
local LEAVES = "_leaves$"
local SETTLE = 1 -- seconds it takes to drop the last of the way onto its legs, its field let go

local robot = assert(devices.open("robotics0"))
local field = assert(devices.open("field0"))
local storage = assert(devices.open("crafting0"))

-- A call's answer, or the program ends with why it failed.
local function need(ok, why)
  if ok == nil or ok == false then
    io.stderr:write("golem-tree: ", tostring(why), "\n")
    os.exit(1)
  end
  return ok, why
end

-- Waits for a device's next event, and gives it.
local function event(device)
  local fd = devices.info(device).fd
  while true do
    local got = device.pull()
    if got then
      return got
    end
    sys.poll({[fd] = {PRI = true}})
  end
end

-- Says what it's doing (its output: a service's log).
local function say(text)
  print(text)
  io.stdout:flush()
end

-- Flies (its field moves it) by forward, up; ends the program if something's in the way.
local function fly(forward, up)
  need(field.move(forward, 0, up))
  local moved = event(field)
  need(moved.event == "arrived", "flying " .. forward .. " forward, " .. up .. " up: " .. moved.event .. " (" .. tostring(moved.why) .. ")")
end

-- Breaks the block at a face with what it holds, and says what it broke.
local function chop(face)
  need(robot.swing(face))
  local swung = event(robot)
  need(swung.event == "broke", "cutting " .. face .. ": " .. swung.event .. " (" .. tostring(swung.why) .. ")")
  return swung.block
end

-- What's at a face matches a pattern.
local function at(face, pattern)
  local name = robot.scan(face).name
  return name and name:match(pattern)
end

-- The storage slot holding something whose name matches 'pattern', or nil.
local function slot(pattern)
  for _, item in ipairs(storage.list()) do
    if item.name:match(pattern) then
      return item.slot
    end
  end
end

-- 1. The axe, from the chest.
say("taking the axe")
need(robot.take("front") > 0 or nil, "no axe in the chest")

-- 2. Up beside the trunk, in its own field, on its legs.
say("flying up")
need(field.capture("self"))
fly(0, UP)
fly(FORWARD - 1, 0)

-- 3. The trunk: its lowest log, then each above it, from the air (a fifth of its speed), clearing
-- the leaves over its head as its field lifts it.
local cut = 0
while at("front", LOG) do
  say("cutting a log")
  chop("front")
  cut = cut + 1
  if at("up", LEAVES) then
    chop("up")
  end
  fly(0, 1)
end

-- 4. Home: back down and over the chest's side, and onto its feet, its field let go.
say("flying home")
fly(0, -cut)
fly(-(FORWARD - 1), 0)
fly(0, -UP)
need(field.release())
sys.sleep(SETTLE)

-- 5. The wood, then the axe, into the chest.
say("putting it away")
while slot(LOG) do
  local stored, left = storage.store("front", slot(LOG))
  need(stored, "the chest is full: " .. tostring(left) .. " left")
end
need(robot.store("front"))
print("cut " .. cut .. " logs, and brought them home")
