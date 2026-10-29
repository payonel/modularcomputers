-- Fell the tree in front, and bring its wood home: a bot golem with a crafting card (its storage)
-- and an energy field card (its lift), a chest behind it holding an axe, and a tree somewhere ahead
-- of it, perhaps up on a ledge.
--   lua golem-tree.lua [reach [climb]]       (looks 8 blocks ahead, climbs a cliff up to 12)
-- It takes the axe from the chest (whatever slot it's in), and walks forward looking for a log. A
-- cliff in its way, it holds itself in its field, legs and all, and is carried up it a block at a
-- time, trying forward at each height: held, its robotics are frozen (it can't look, nor use its
-- hands), so the field's moves feel the way (a move blocked says what by, and it's carried back to
-- where it was). Over the top, the field lets it go, and it stands there on its legs and walks on.
-- At the tree it cuts the trunk from the ground, as far as it reaches (it uses no tools in the air,
-- nor in a field): the log in front, then, standing where that was, the one over its head. It
-- comes home the way it went (walked back, carried down), and is set down where it started. Each
-- command waits for the one before (a device does one at a time), and its end says how it went.
-- Home, it leaves things ready to run again: the axe in the chest's first slot, the wood and
-- anything else it carries in the chest, its hands and storage empty. If anything goes wrong it
-- comes home all the same, and says what happened.
local devices = require("devices")

local REACH = tonumber(arg[1] or "8")  -- how far ahead it looks for a tree, in blocks
local CLIMB = tonumber(arg[2] or "12") -- how high a cliff it will climb, in blocks
local CHEST = "back"                   -- where the chest is, its own face
local AXE = "_axe$"                    -- what an axe's name ends with (minecraft:iron_axe)
local LOG = "_log$"                    -- what wood's name ends with (minecraft:oak_log)
local AXE_SLOT = 1                     -- where the axe goes back, in the chest

local robot = assert(devices.open("robotics0"), "no robotics device: run it on a bot golem")
local storage = assert(devices.open("crafting0"), "no crafting card in the golem (its storage)")
local field = assert(devices.open("field0"), "no energy field card in the golem (its lift)")

-- How its last command went, once it's done: sync waits until the device's queue is empty, and the
-- command's end is among its events (ok, or false and why).
local function ended(device, method)
  devices.sync(device)
  local ok, why = true, nil
  for event in function() return devices.event(device, 0) end do
    if event.event == method and event.ok ~= nil then
      ok, why = event.ok, event.why
    end
  end
  return ok, why
end

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

-- Its moves, in order, each {how, forward, up} (how: "walk", or "carry", its field moving it): what
-- it retraces, backwards, to come home exactly the way it went.
local moves = {}

-- Whether its own field holds it.
local held = false

-- Where it really is, from its origin: forward, right, up (get answers at once, even while it's held).
local function where()
  local now = robot.get().position
  return now.forward, now.right, now.up
end

-- Carried in its own field by forward and up (held first, legs and all): true when it got there.
-- Blocked, it's carried back to where it began (a move all or nothing), and it's false and why.
local function carry(forward, up)
  if not held then
    local ok, why = field.capture("self")
    if not ok then
      return false, why
    end
    held = true
  end
  local f0, r0, u0 = where()
  local ok, why = field.move(forward, 0, up)
  if ok then
    ok, why = ended(field, "move") -- (its end: there, or blocked: and by what)
  end
  if not ok then
    local f1, r1, u1 = where()
    local got = math.sqrt((f1 - f0) ^ 2 + (r1 - r0) ^ 2) -- (how far forward it got: a field doesn't turn it)
    field.move(forward < 0 and got or -got, 0, u0 - u1)
    devices.sync(field) -- (done: back where it began)
  end
  return ok, why
end

-- Its field lets it go: it stands where it is, on its legs (its robotics back once it's down).
local function setDown()
  if held then
    field.release()
    held = false
  end
end

-- Walks forward and up on its legs (set down first: a command waits while it's held); true when
-- it got there, else false and why.
local function walk(forward, up)
  setDown()
  local ok, why = robot.walk(forward, 0, up)
  if ok then
    ok, why = ended(robot, "walk") -- (its end: there, or why not)
  end
  return ok, why
end

-- A move of the job's ("walk" or "carry"), kept to retrace when it got there.
local function go(how, forward, up)
  local ok, why
  if how == "walk" then
    ok, why = walk(forward, up)
  else
    ok, why = carry(forward, up)
  end
  if ok then
    moves[#moves + 1] = {how, forward, up}
  end
  return ok, why
end

-- Breaks the block at a face with what it holds; problem if it can't.
local function chop(face, what)
  local swung, why = robot.swing(face)
  if swung == "breaking" then
    swung, why = ended(robot, "swing") -- (its end: broken, or why not)
  end
  if not swung then
    problem("cutting " .. what .. ", it stopped: " .. tostring(why))
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

-- Up the cliff in front, carried a block at a time, trying forward at each height until it's over
-- the top; then set down there. How high it climbed.
local function climb()
  local cliff, climbed = at("front"), 0
  repeat
    if climbed >= CLIMB then
      problem("while looking for a tree, I found a cliff (" .. cliff .. ") higher than " .. CLIMB .. " blocks")
    end
    local ok, why = go("carry", 0, 1)
    if not ok then
      problem("while looking for a tree, I found a cliff but got blocked climbing it: " .. tostring(why))
    end
    climbed = climbed + 1
  until go("carry", 1, 0) -- (over the top: else blocked, and back where it was)
  setDown()
  return climbed
end

-- 2. A tree ahead: forward a block at a time until a log is in front, carried up any cliff it meets.
local function findTree()
  local ahead, climbed = 0, 0
  while not is("front", LOG) do
    if ahead >= REACH then
      problem("while looking for a tree, I found none within " .. REACH .. " blocks")
    end
    if at("front") == nil then
      local ok, why = go("walk", 1, 0)
      if not ok then
        problem("while looking for a tree, something stopped me going on: " .. tostring(why))
      end
    else
      climbed = climbed + climb()
    end
    ahead = ahead + 1
  end
  say("found a tree " .. ahead .. " blocks ahead" .. (climbed > 0 and ", " .. climbed .. " up" or ""))
end

-- 3. The trunk, as far as it reaches from the ground: the log in front, then, standing where that
-- was, the one over its head.
local function cutTrunk()
  chop("front", "the trunk")
  local logs = 1
  local ok, why = go("walk", 1, 0) -- (into its place)
  if not ok then
    problem("stepping into the trunk's place, I got blocked: " .. tostring(why))
  end
  if is("up", LOG) then
    chop("up", "the trunk over my head")
    logs = logs + 1
  end
  return logs
end

-- 4. Home: its moves retraced, last first (walked back, carried back), then set down where it began.
local function comeHome()
  for i = #moves, 1, -1 do
    local how, forward, up = table.unpack(moves[i])
    local ok, why
    if how == "walk" then
      ok, why = walk(-forward, -up)
    else
      ok, why = carry(-forward, -up)
    end
    if not ok then
      say("coming home, I got blocked: " .. tostring(why) .. ". I'm stuck: help me home")
      return false
    end
  end
  moves = {}
  setDown()
  return true
end

-- The job: anything that goes wrong comes home, and says why.
local logs = 0
local ok, err = pcall(function()
  say("taking the axe")
  takeAxe()
  say("looking for a tree")
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
    local back = putAway(AXE_SLOT)
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
