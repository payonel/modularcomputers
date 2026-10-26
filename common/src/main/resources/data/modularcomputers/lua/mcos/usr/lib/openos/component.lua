-- OpenComputers' component library, for openos's programs (a machine's own there; here the bus
-- as mcos sees it, each device by an adapter: openos/device/<type>.lua, OpenComputers' component of
-- that kind over the device's /dev file). A component's address is its device's id (without id:);
-- the gpu, the screen and the keyboard are the program's terminal's, made for the run, and the
-- computer is this machine. A device with no adapter isn't a component. OpenOS's own additions
-- (primaries, component.gpu, get, isAvailable) are its boot's, as OpenOS's were.
local component = {}
local machine = {}

local entries = {} -- by address: {type, slot, methods = {name = {f, doc}}}

-- machine.add(address, type, methods[, docs]): a component on the bus; methods a function each.
function machine.add(address, kind, methods, docs)
  local entry = {type = kind, slot = -1, methods = {}}
  for name, f in pairs(methods) do
    entry.methods[name] = {f = f, doc = docs and docs[name] or "function(...)"}
  end
  entries[address] = entry
end

function machine.remove(address)
  local entry = entries[address]
  entries[address] = nil
  return entry and entry.type
end

function machine.entries()
  return entries
end

local function entry(address)
  if type(address) ~= "string" then
    error(("bad argument #1 (string expected, got %s)"):format(type(address)), 3)
  end
  return entries[address]
end

-- component.list([filter[, exact]]): the components (of a type containing filter, or exactly
-- it), address to type, a table that's its own iterator, as OpenComputers'.
function component.list(filter, exact)
  local list = {}
  for address, e in pairs(entries) do
    if not filter or (exact and e.type == filter) or (not exact and e.type:find(filter, 1, true)) then
      list[address] = e.type
    end
  end
  local key
  return setmetatable(list, {__call = function()
    key = next(list, key)
    if key then
      return key, list[key]
    end
  end})
end

function component.type(address)
  local e = entry(address)
  if not e then
    return nil, "no such component"
  end
  return e.type
end

function component.slot(address)
  local e = entry(address)
  if not e then
    return nil, "no such component"
  end
  return e.slot
end

function component.methods(address)
  local e = entry(address)
  if not e then
    return nil, "no such component"
  end
  local methods = {}
  for name in pairs(e.methods) do
    methods[name] = true -- (direct: every call answers at once, or waits as the program would)
  end
  return methods
end

function component.fields(address)
  local e = entry(address)
  if not e then
    return nil, "no such component"
  end
  return {}
end

function component.doc(address, method)
  local e = entry(address)
  if not e then
    return nil, "no such component"
  end
  local m = e.methods[method]
  return m and m.doc or nil
end

function component.invoke(address, method, ...)
  local e = entry(address)
  if not e then
    error("no such component", 2)
  end
  local m = e.methods[method]
  if not m then
    error("no such method", 2)
  end
  return m.f(...)
end

-- A proxy's method: called, it invokes; as text, its doc (as OpenComputers' callbacks).
local Callback = {}
Callback.__call = function(self, ...)
  return component.invoke(self.address, self.name, ...)
end
Callback.__tostring = function(self)
  return component.doc(self.address, self.name) or "function"
end

function component.proxy(address)
  local e = entry(address)
  if not e then
    return nil, "no such component"
  end
  local proxy = {address = address, type = e.type, slot = e.slot, fields = {}}
  for name in pairs(e.methods) do
    proxy[name] = setmetatable({address = address, name = name}, Callback)
  end
  return proxy
end

component.machine = machine
return component
