-- Cut down a tree up on a ledge and bring its wood home: a bot golem with a crafting card (its
-- storage) and an energy field card, a chest in front of it holding an axe, and a tree on a ledge
-- ahead of it: its trunk FORWARD blocks ahead and its lowest log UP blocks up, standing on the
-- ledge, the golem's way there clear.
--   lua golem-tree.lua [up [forward]]       (10 and 5)
-- It takes the axe, holds itself in its field and leaves its legs (picking them up: no tools while
-- it flies), flies up to the ledge beside the trunk, sets its legs down and stands on them, cuts
-- the trunk (its field lifting it, legs and all, to reach the logs above), flies home, stands on
-- its legs at the chest again, and puts the wood and the axe in it (what else the tree dropped, its
-- saplings and sticks, it keeps).
local sys = require("sys")
local devices = require("devices")

local UP = tonumber(arg[1] or "10")
local FORWARD = tonumber(arg[2] or "5")
local LOG = "_log$" -- what wood's name ends with (minecraft:oak_log)
local SETTLE = 1 -- seconds legs dropped mid-air take to land

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

-- The storage slot holding something whose name matches 'pattern', or nil.
local function slot(pattern)
  for _, item in ipairs(storage.list()) do
    if item.name:match(pattern) then
      return item.slot
    end
  end
end

-- Off its legs, in its own field, holding them (it flies; a block up, they're below it to pop up).
local function takeOff()
  if not robot.get().legs then
    return
  end
  if field.get().state == "idle" then
    need(field.capture("self"))
  end
  need(robot.detachLegs())
  sys.sleep(SETTLE) -- (left mid-air, they fall to whatever's below)
  fly(0, 1)
  need(robot.use("down")) -- popped up into its storage
end

-- Onto its legs, set down below it.
local function land()
  local held = robot.held()
  need(storage.equip(need(slot("bot_golem_legs$"), "its legs aren't in its storage")))
  need(robot.use("down"))
  need(robot.attachLegs())
  local on = event(robot)
  need(on.event == "attached", "landing: " .. on.event .. " (" .. tostring(on.why) .. ")")
  repeat until not field.pull() -- (its field let go as it landed: that's said, and done with)
  if held then
    need(storage.equip(need(slot(held.name), "what it held is gone"))) -- back in its hand
  end
end

-- Breaks the block in front with what it holds, and says what it broke.
local function chop()
  need(robot.swing("front"))
  local swung = event(robot)
  need(swung.event == "broke", "cutting: " .. swung.event .. " (" .. tostring(swung.why) .. ")")
  return swung.block
end

say("taking the axe")
-- 1. The axe, from the chest.
need(robot.take("front") > 0 or nil, "no axe in the chest")

-- 2. Up to the ledge, beside the trunk, and onto its legs there.
say("taking off")
takeOff()
fly(0, UP)
fly(FORWARD - 1, 0)
say("landing by the tree")
land()

-- 3. The trunk: its lowest log, then each above it, its field lifting it (on its legs: it can cut),
-- clearing the leaves over its head as it goes up.
local LEAVES = "_leaves$"
local function ahead(pattern)
  local name = robot.scan("front").name
  return name and name:match(pattern)
end
local cut = 0
need(field.capture("self"))
while ahead(LOG) do
  say("cutting a log")
  chop()
  cut = cut + 1
  local above = robot.scan("up").name
  if above and above:match(LEAVES) then
    need(robot.swing("up"))
    local swung = event(robot)
    need(swung.event == "broke", "clearing the leaves: " .. swung.event .. " (" .. tostring(swung.why) .. ")")
  end
  fly(0, 1)
end
fly(0, -cut)

-- 4. Home: off its legs again, back over the chest's side, down, and onto them.
say("flying home")
takeOff()
fly(-(FORWARD - 1), 0)
fly(0, -UP)
land()

-- 5. The wood, then the axe, into the chest.
say("putting it away")
while slot(LOG) do
  local stored, left = storage.store("front", slot(LOG))
  need(stored, "the chest is full: " .. tostring(left) .. " left")
end
need(robot.store("front"))
print("cut " .. cut .. " logs, and brought them home")
