-- A redstone card as OpenComputers' redstone component (a tier 1 card's: no bundled colors).
-- Sides are OpenComputers' numbers, relative to the block (0 down, 1 up, 2 back, 3 front, 4 right,
-- 5 left: the block's own, looking out of its front, so OpenComputers' right is mcos's left, which
-- is a viewer's facing it); a card in something without a front takes the world's (2 north, 3
-- south, 4 west, 5 east, as OpenComputers' sides aliases). A face's input is its level: the
-- strongest signal there, which the card can't tell from its own output. redstone_changed comes
-- when a face's level changes and not by this card's own output.
local devices = require("devices")

local RELATIVE = {[0] = "down", [1] = "up", [2] = "back", [3] = "front", [4] = "left", [5] = "right"}
local WORLD = {[0] = "down", [1] = "up", [2] = "north", [3] = "south", [4] = "west", [5] = "east"}

return function(name, info, ctx, address)
  local card = assert(devices.open(name))

  -- Each OpenComputers side's face, as the card names it (the world's), learned by asking.
  local faces, sides = {}, {}
  local relative = card.get({"front"}) ~= nil -- (a card in a block with a front)
  for side = 0, 5 do
    local asked = relative and RELATIVE[side] or WORLD[side]
    local answer = card.get({asked})
    local world = next(answer)
    faces[side], sides[world] = world, side
  end

  local function check(side)
    side = math.tointeger(side)
    if not side or not faces[side] then
      error("invalid side", 3)
    end
    return faces[side]
  end

  local levels, powers = {}, {}
  for world, state in pairs(card.get()) do
    levels[world], powers[world] = state.level, state.powered
  end

  -- A field of every face (as OpenComputers' tables: by side number), or one face's.
  local function read(side, field)
    local state = card.get()
    if side == nil then
      local all = {}
      for n = 0, 5 do
        all[n] = state[faces[n]][field]
      end
      return all
    end
    return state[check(side)][field]
  end

  local methods, docs = {}, {}

  docs.getInput = "function([side:number]):number or table -- Get the redstone input (all sides, or optionally on the specified side)"
  function methods.getInput(side)
    return read(side, "level")
  end

  docs.getOutput = "function([side:number]):number or table -- Get the redstone output (all sides, or optionally on the specified side)"
  function methods.getOutput(side)
    return read(side, "powered")
  end

  docs.setOutput = "function([side:number, ]value:number or table):number or table -- Set the redstone output (all sides, or optionally on the specified side). Returns previous values"
  function methods.setOutput(side, value)
    local old = read(nil, "powered")
    local settings = {}
    if type(side) == "table" then
      for n, v in pairs(side) do
        settings[check(n)] = {powered = math.tointeger(v) or 0}
      end
      card.set(settings)
      return old
    elseif value == nil then
      for n = 0, 5 do
        settings[faces[n]] = {powered = math.tointeger(side) or 0}
      end
      card.set(settings)
      return old
    end
    settings[check(side)] = {powered = math.tointeger(value) or 0}
    card.set(settings)
    return old[math.tointeger(side)]
  end

  for _, missing in ipairs({"getComparatorInput", "getWakeThreshold", "setWakeThreshold"}) do
    docs[missing] = "function(...) -- not supported"
    methods[missing] = function()
      error("openos: redstone." .. missing .. " is not supported", 2)
    end
  end

  -- Its events: redstone_changed for a face whose level changed, not by this card's own output.
  ctx.source(devices.info(card).fd, {PRI = true}, function()
    while true do
      local change = card.pull()
      if not change then
        break
      end
      local world, old = change.side, levels[change.side]
      local own = powers[world] ~= change.powered
      levels[world], powers[world] = change.level, change.powered
      if old ~= change.level and not own and sides[world] then
        ctx.push("redstone_changed", address, sides[world], old or 0, change.level)
      end
    end
  end)

  return methods, docs, function()
    ctx.source(devices.info(card).fd, nil)
    devices.close(card)
  end
end
