-- Fell the tree in front, and bring its wood home: a bot golem with a crafting card (its storage) and
-- an energy field card, a chest behind it holding an axe, and a tree somewhere ahead of it, perhaps
-- up on a ledge.
--   lua golem-tree.lua [reach [climb]]       (looks 8 blocks ahead, climbs a cliff up to 12)
-- It takes the axe from the chest (whatever slot it's in), holds itself in its field, legs and all,
-- and flies forward looking for a log, climbing any cliff it meets; it cuts the trunk from the air
-- (off the ground it works its axe at a fifth of its speed, as a player in the air), comes home the
-- way it went, and lands on its legs. Home, it leaves things ready to run again: the axe in the
-- chest's first slot, the wood and anything else it carries in the chest, its hands and storage
-- empty. If anything goes wrong it comes home all the same, and says what happened.
local sys = require("sys")
local devices = require("devices")

local REACH = tonumber(arg[1] or "8")  -- how far ahead it looks for a tree, in blocks
local CLIMB = tonumber(arg[2] or "12") -- how high a cliff it will climb, in blocks
local CHEST = "back"                   -- where the chest is, its own face
local AXE = "_axe$"                    -- what an axe's name ends with (minecraft:iron_axe)
local LOG = "_log$"                    -- what wood's name ends with (minecraft:oak_log)
local LEAVES = "_leaves$"
local AXE_SLOT = 1                     -- where the axe goes back, in the chest
local SETTLE = 1                       -- seconds it takes to drop onto its legs once its field lets go

local robot = assert(devices.open("robotics0"), "no robotics device: run it on a bot golem")
local field = assert(devices.open("field0"), "no energy field card in the golem")
local storage = assert(devices.open("crafting0"), "no crafting card in the golem (its storage)")

-- Says what it's doing (its output: a service's log, or the screen).
local function say(text)
  print(text)
  io.stdout:flush()
end

-- A problem: the job stops, and it comes home (the message says why, as a person would).
local function problem(text)
  error({problem = text}, 0)
end

-- What's at a face: its name, or nil for air.
local function at(face)
  return robot.scan(face).name
end

local function is(face, pattern)
  local name = at(face)
  return name ~= nil and name:match(pattern) ~= nil
end

-- Its field's moves, in order: what it retraces, backwards, to come home exactly the way it went.
local moves = {}

-- Flies (its field moves it) forward and up; true when it got there, else false and why.
local function fly(forward, up)
  local ok, why = field.move(forward, 0, up)
  if not ok then
    return false, why
  end
  local moved = devices.event(field)
  if moved.event ~= "arrived" then
    return false, moved.why or moved.event
  end
  moves[#moves + 1] = {forward, up}
  return true
end

-- Breaks the block at a face with what it holds; problem if it can't.
local function chop(face, what)
  local ok, why = robot.swing(face)
  if not ok then
    problem("cutting " .. what .. ", it wouldn't: " .. tostring(why))
  end
  local swung = devices.event(robot)
  if swung.event ~= "broke" then
    problem("cutting " .. what .. ", it stopped: " .. tostring(swung.why or swung.event))
  end
end

-- Puts what's in its hand into the chest (into its slot 'to', or where it fits); false and why.
local function putAway(to)
  if not robot.held() then
    return true
  end
  local done, left = robot.store(CHEST, nil, to)
  if done == nil then
    return false, left -- (no chest)
  elseif not done then
    return false, "the chest is full (" .. left .. " left in my hand)"
  end
  return true
end

-- Empties its hands and its storage into the chest: ready to run again. What it couldn't, it says.
local function tidy()
  local ok, why = putAway()
  robot.swap() -- (its off hand: what it caught with nowhere else to put it)
  local ok2, why2 = putAway()
  robot.swap()
  local stored, left = storage.store(CHEST) -- (everything in its storage)
  if not ok or not ok2 then
    say("I couldn't empty my hands: " .. tostring(why or why2))
  elseif stored == false then
    say("I couldn't empty my storage: the chest is full (" .. tostring(left) .. " left)")
  end
end

-- 1. The axe: its hands emptied into the chest first, then the axe from whatever slot it's in.
local function takeAxe()
  local ok, why = putAway()
  if not ok then
    problem("before taking the axe, I couldn't put down what I held: " .. tostring(why))
  end
  local slots, nochest = robot.contents(CHEST)
  if not slots then
    problem("I need a chest behind me, with an axe in it (" .. tostring(nochest) .. ")")
  end
  for _, slot in ipairs(slots) do
    if slot.name:match(AXE) then
      if (robot.take(CHEST, slot.slot) or 0) > 0 then
        return
      end
    end
  end
  problem("there's no axe in the chest behind me")
end

-- 2. A tree ahead: forward a block at a time, climbing what's in the way, until a log is in front.
local function findTree()
  local ahead, climbed = 0, 0
  while not is("front", LOG) do
    if at("front") == nil then
      if ahead >= REACH then
        problem("while looking for a tree, I found none within " .. REACH .. " blocks")
      end
      local ok, why = fly(1, 0)
      if not ok then
        problem("while looking for a tree, something stopped me going on: " .. tostring(why))
      end
      ahead = ahead + 1
    else
      if climbed >= CLIMB then
        problem("while looking for a tree, I found a cliff (" .. tostring(at("front")) .. ") higher than " .. CLIMB .. " blocks")
      end
      local ok, why = fly(0, 1)
      if not ok then
        problem("while looking for a tree, I found a cliff but got blocked climbing it: " .. tostring(why))
      end
      climbed = climbed + 1
    end
  end
  say("found a tree " .. ahead .. " blocks ahead" .. (climbed > 0 and ", " .. climbed .. " up" or ""))
end

-- 3. The trunk: its lowest log in front, then each above it, the leaves over its head cleared.
local function cutTrunk()
  local logs = 0
  while is("front", LOG) do
    chop("front", "the trunk")
    logs = logs + 1
    -- Up a block, to the next log (what's over its head cleared first): the one it cut is gone.
    if is("up", LEAVES) or is("up", LOG) then
      chop("up", "what's over my head")
    end
    if at("up") ~= nil then
      break -- (something it can't cut over its head: as high as it goes)
    end
    local ok, why = fly(0, 1)
    if not ok then
      problem("going up the trunk, I got blocked: " .. tostring(why))
    end
  end
  return logs
end

-- 4. Home: its moves retraced, last first; then its field lets go, and it stands on its legs.
local function comeHome()
  for i = #moves, 1, -1 do
    local back = moves[i]
    local ok, why = field.move(-back[1], 0, -back[2])
    if ok then
      local moved = devices.event(field)
      if moved.event ~= "arrived" then
        say("coming home, I got blocked: " .. tostring(moved.why or moved.event) .. ". I'm stuck in the air: help me down")
        return false
      end
    end
  end
  moves = {}
  if field.get().state ~= "idle" then
    field.release()
    sys.sleep(SETTLE)
  end
  return true
end

-- The job: anything that goes wrong comes home, and says why.
local logs = 0
local ok, err = pcall(function()
  say("taking the axe")
  takeAxe()
  say("looking for a tree")
  if field.get().state == "idle" then
    local held, why = field.capture("self")
    if not held then
      problem("I couldn't hold myself in my field: " .. tostring(why))
    end
  end
  findTree()
  logs = cutTrunk()
end)
if not ok then
  local text = type(err) == "table" and err.problem or tostring(err)
  say(text .. ", so I came home")
end
local home = comeHome()
if home then
  -- 5. Ready to run again: the axe in the chest's first slot, the rest in the chest too.
  if robot.held() then
    local back, why = putAway(AXE_SLOT)
    if not back then
      putAway() -- (anywhere it fits)
    end
  end
  tidy()
end
if ok then
  say("cut " .. logs .. " logs, and brought them home")
end
return ok and 0 or 1
