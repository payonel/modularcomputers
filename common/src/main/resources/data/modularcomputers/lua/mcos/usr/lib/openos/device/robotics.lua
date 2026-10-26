-- A golem's robotics device as OpenComputers' robot component. OpenComputers' robot calls wait
-- until the robot has done what it was asked (a move answers once it's moved); the golem's answer
-- at once and say how it went in an event, so each call here starts the action and waits for its
-- event. Sides are OpenComputers' numbers in the robot's own frame (0 down, 1 up, 2 back, 3 front,
-- 4 right, 5 left: its own right). Moving up or down is walking a block up or down (a golem walks;
-- it doesn't hover as OpenComputers' robots did). Its inventory is its hand: one slot.
local devices = require("devices")

local FACES = {[0] = "down", [1] = "up", [2] = "back", [3] = "front", [4] = "right", [5] = "left"}
local MOVES = {[0] = {0, 0, -1}, [1] = {0, 0, 1}, [2] = {-1, 0, 0}, [3] = {1, 0, 0}}
local ENDS = {arrived = true, blocked = true, stopped = true, turned = true, broke = true, failed = true, landed = true}
local STACK = 64 -- what a slot holds, for space()

return function(name, info, ctx)
  local robot = assert(devices.open(name))

  local function face(side, allowed)
    side = math.tointeger(side)
    local f = side and FACES[side]
    if not f or allowed and not allowed[side] then
      error("invalid side", 3)
    end
    return f
  end

  -- Starts an action and waits for how it ended: its event.
  local function run(start, ...)
    while robot.pull() do
      -- (what's left of an earlier action's events: not this one's)
    end
    local ok, why = start(...)
    if not ok then
      return nil, why
    end
    ctx.settle()
    while true do
      local event = devices.event(robot)
      if event and ENDS[event.event] then
        return event
      end
    end
  end

  local methods = {}
  local docs = {}

  docs.move = "function(side:number):boolean -- Moves the robot in the specified direction."
  function methods.move(side)
    face(side)
    local move = MOVES[math.tointeger(side)]
    if not move then
      return nil, "invalid side"
    end
    local event, why = run(robot.walk, move[1], move[2], move[3])
    if not event then
      return nil, why
    elseif event.event ~= "arrived" then
      return nil, event.why or event.event
    end
    return true
  end

  docs.turn = "function(clockwise:boolean):boolean -- Rotates the robot by 90 degrees."
  function methods.turn(clockwise)
    local event, why = run(robot.turn, clockwise and "right" or "left")
    if not event then
      return nil, why
    end
    return event.event == "turned"
  end

  docs.detect = "function(side:number):boolean, string -- Checks the contents of the block on the specified side."
  function methods.detect(side)
    local what = robot.scan(face(side))
    if what.kind == "entity" then
      return true, "entity"
    elseif what.kind == "fluid" then
      return false, "liquid"
    elseif what.kind == "block" then
      return what.solid and true or false, what.solid and "solid" or "passable"
    end
    return false, "air"
  end

  docs.swing = "function(side:number):boolean, string -- Swings the equipped tool at the specified side."
  function methods.swing(side)
    local did, why = robot.swing(face(side))
    if did == "attacked" then
      return true, "entity"
    elseif did ~= "breaking" then
      return false, why or "air"
    end
    local event = run(function() return true end)
    if event.event == "broke" then
      return true, "block"
    end
    return false, event.why or event.event
  end

  docs.use = "function(side:number):boolean, string -- Uses the equipped item on the specified side."
  function methods.use(side)
    local did, why = robot.use(face(side))
    if not did then
      return false, why
    end
    return true, did == "placed" and "block_activated" or "item_used"
  end

  docs.place = "function(side:number):boolean -- Places a block from the selected slot on the specified side."
  function methods.place(side)
    local did, why = robot.use(face(side))
    if did == "placed" then
      return true
    end
    return false, why or "nothing placed"
  end

  docs.name = "function():string -- The robot's name."
  function methods.name()
    return info.label or name
  end

  docs.inventorySize = "function():number -- The size of the robot's inventory: its hand."
  function methods.inventorySize()
    return 1
  end

  local function slot(n)
    n = math.tointeger(n or 1)
    if n ~= 1 then
      error("openos: the golem's inventory is its hand: slot 1 only", 3)
    end
  end

  docs.select = "function([slot:number]):number -- The selected slot: its hand."
  function methods.select(n)
    slot(n)
    return 1
  end

  docs.count = "function([slot:number]):number -- How many items are in its hand."
  function methods.count(n)
    slot(n)
    local held = robot.held()
    return held and held.count or 0
  end

  docs.space = "function([slot:number]):number -- How many more items its hand takes."
  function methods.space(n)
    slot(n)
    local held = robot.held()
    return held and STACK - held.count or STACK
  end

  docs.durability = "function():number, number, number -- The durability of what it holds."
  function methods.durability()
    local held = robot.held()
    if not held then
      return nil, "no tool equipped"
    elseif not held.maxDamage or held.maxDamage == 0 then
      return nil, "tool cannot be damaged"
    end
    return (held.maxDamage - (held.damage or 0)) / held.maxDamage, held.damage or 0, held.maxDamage
  end

  docs.drop = "function(side:number[, count:number]):boolean -- Puts what it holds into the container at a side."
  function methods.drop(side, count)
    local done, left = robot.store(face(side), count)
    if done then
      return true
    end
    return false, math.type(left) and "inventory full" or left
  end

  docs.suck = "function(side:number[, count:number]):boolean -- Takes items from the container at a side into its hand."
  function methods.suck(side, count)
    local got, why = robot.take(face(side), nil, count)
    if not got then
      return false, why
    end
    return got > 0
  end

  for _, missing in ipairs({"compare", "compareTo", "transferTo", "getLightColor", "setLightColor", "tankCount",
    "selectTank", "tankLevel", "tankSpace", "compareFluidTo", "transferFluidTo", "drain", "fill", "compareFluid"}) do
    docs[missing] = "function(...) -- not supported"
    methods[missing] = function()
      error("openos: robot." .. missing .. " is not supported", 2)
    end
  end

  return methods, docs, function()
    devices.close(robot)
  end
end
