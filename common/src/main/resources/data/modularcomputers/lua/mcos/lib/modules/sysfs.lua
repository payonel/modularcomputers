-- sysfs: /sys, what each device on the bus is and can do, so a player can learn a device without
-- the manual (the user's). As Linux's /sys/class: a directory a kind of device (its type), in it a
-- directory a device, by the kernel's name for it (as /dev's), and in that its attributes, a small
-- read-only file each, and its methods (its /dev file's ioctls), a file each, saying its arguments
-- and what it does, from the device's own descriptions:
--   /sys/class/robotics/robotics0/type, id, path, label     (path: its slot, or - when it's cabled)
--   /sys/class/robotics/robotics0/methods/walk
-- Linux has a /sys/devices tree too, by where each device is; mcos's bus is flat, so there's only
-- the class tree. And the firmware's: the EEPROM's NVRAM, its boot variables, a file each, as
-- Linux's efivarfs, as text (Linux's are binary), and without UEFI's vendor GUID in the names (a
-- closed set, no vendors); efibootmgr changes them, through /dev/eeprom's ioctls:
--   /sys/firmware/efi/efivars/Boot0001      id:<uuid> /init.lua label (and "not present", off the bus)
--   /sys/firmware/efi/efivars/BootOrder     an entry's number a line, in order
--   /sys/firmware/efi/efivars/BootNext      (while it's set)
--   /sys/firmware/efi/efivars/BootCurrent   the entry booted (when one was: not by the fallback)
-- And the computer's power, as Linux's power_supply class: a battery a bay (BAT0, BAT1...) and the
-- case's own store, the one it runs on (case, a UPS: its batteries fill it), a small file each:
--   /sys/class/power_supply/BAT0/type, status, capacity, energy_now, energy_full
--   /sys/class/power_supply/case/type, status, capacity, energy_now, energy_full, power_now
-- in Linux's units (percent, µWh, µW: a power unit a tick is a watt, 72000 stored units a watt-hour);
-- status Full, Charging, Discharging or Not charging (by its last reading); an infinite battery
-- has no energy, only its capacity, 100.
-- Programs list and read it as any files.
local k, vfs = ...

local fail = vfs.fail

local fs = {type = "sysfs"}

local CLASS = "class"
local POWER = "power_supply"
local MICRO = 1000000
local UNITS_PER_WATT_HOUR = 20 * 60 * 60 -- stored power units in a watt-hour (a watt a tick, for an hour)
local SUPPLY_FILES = {"type", "status", "capacity", "energy_now", "energy_full"}
local TREND_TICKS = 20 -- how far apart readings are that say a store's charging or discharging (a second)
local FIRMWARE = {"firmware", "efi", "efivars"} -- the boot variables' directory, a level each
local METHODS = "methods"
local ATTRIBUTES = {"type", "id", "path", "label"} -- a device's, from the kernel's view of the bus
local WIDTH = 78 -- a description's lines, at most, indented
local INDENT = "    "

-- Text wrapped to WIDTH, each line indented.
local function wrap(text)
  local lines, line = {}, INDENT
  for word in text:gmatch("%S+") do
    if #line > #INDENT and #line + 1 + #word > WIDTH then
      lines[#lines + 1] = line
      line = INDENT .. word
    else
      line = line .. (#line > #INDENT and " " or "") .. word
    end
  end
  lines[#lines + 1] = line
  return table.concat(lines, "\n")
end

-- A method's text: its signature (how a doc begins: "walk(forward[, right]): ..."), then what it
-- does, wrapped and indented.
local function about(device, name)
  local doc = board.doc(device.id, name) or (name .. "()")
  local signature, says = doc:match("^([%w_]+%b())[^:]*:%s*(.*)$")
  return (signature or doc) .. "\n" .. (signature and wrap(says) .. "\n" or "")
end

local function methods(device)
  local list = board.methods(device.id) or {}
  table.sort(list)
  return list
end

local function kind(device)
  return tostring(device.type or "device")
end

-- The EEPROM's boot variables, by file name ("Boot0001", "BootOrder", "BootNext"), each its text.
local function efivars()
  local vars = {}
  for _, device in ipairs(k.devices.list) do
    if device.type == "eeprom" then
      local ok, boot = board.invoke(device.id, "getBoot")
      if ok and boot then
        local present = {}
        for _, other in ipairs(k.devices.list) do
          present[other.id] = true
        end
        local order = {}
        for _, entry in ipairs(boot.entries) do
          vars[string.format("Boot%04X", entry.number)] = entry.disk .. " " .. entry.path .. (entry.label ~= "" and " " .. entry.label or "")
            .. "\n" .. (present[entry.disk] and "" or "not present\n")
        end
        for i, number in ipairs(boot.order) do
          order[i] = string.format("%04X\n", number)
        end
        vars.BootOrder = table.concat(order)
        if boot.next then
          vars.BootNext = string.format("%04X\n", boot.next)
        end
        if boot.current then
          vars.BootCurrent = string.format("%04X\n", boot.current)
        end
      end
      break
    end
  end
  return vars
end

-- What a path in the firmware's tree names (parts[1] is "firmware"), or nil.
local function firmware(parts)
  for depth = 1, math.min(#parts, #FIRMWARE) do
    if parts[depth] ~= FIRMWARE[depth] then
      return nil
    end
  end
  if #parts < #FIRMWARE then
    local below = FIRMWARE[#parts + 1]
    return {dir = function() return {{name = below, type = "directory"}} end}
  elseif #parts == #FIRMWARE then
    return {dir = function()
      local names = {}
      for name in pairs(efivars()) do
        names[#names + 1] = name
      end
      table.sort(names)
      for i, name in ipairs(names) do
        names[i] = {name = name, type = "file"}
      end
      return names
    end}
  elseif #parts == #FIRMWARE + 1 then
    local text = efivars()[parts[#parts]]
    return text and {file = function() return text end} or nil
  end
  return nil
end

-- Each power supply's last reading ({stored, at}), by name: whether it's charging or discharging.
local readings = {}

-- The computer's power supplies now, by name: {file = text} each, in Linux's units.
local function supplies()
  local power = board.power()
  local found = {}
  local function supply(name, kind, store, load)
    local files = {type = kind}
    local stored, capacity = store.stored, store.capacity
    if store.infinite then
      files.capacity, files.status = 100, "Full"
    elseif capacity > 0 then
      files.capacity = math.floor(stored * 100 / capacity + 0.5)
      files.energy_now = math.floor(stored * MICRO / UNITS_PER_WATT_HOUR + 0.5)
      files.energy_full = math.floor(capacity * MICRO / UNITS_PER_WATT_HOUR + 0.5)
      -- Its trend: against a reading at least TREND_TICKS old (a look takes several reads: a stat, an
      -- open), the last trend seen while nothing's changed since.
      local now = k.now()
      local last = readings[name]
      if not last then
        last = {stored = stored, at = now, trend = 0}
        readings[name] = last
      end
      local trend = stored ~= last.stored and stored - last.stored or last.trend
      if now - last.at >= TREND_TICKS then
        readings[name] = {stored = stored, at = now, trend = trend}
      end
      if stored >= capacity then
        files.status = "Full"
      elseif trend > 0 then
        files.status = "Charging"
      elseif trend < 0 or load and load > 0 then
        files.status = "Discharging"
      else
        files.status = "Not charging"
      end
    end
    if load then
      files.power_now = load * MICRO
    end
    found[name] = files
  end
  supply("case", "UPS", power.store, power.load)
  for _, battery in ipairs(power.batteries or {}) do
    supply("BAT" .. battery.bay, "Battery", battery.charge)
  end
  return found
end

-- What a path in the power supplies names (parts[2] is "power_supply"), or nil.
local function power(parts)
  if #parts == 2 then
    return {dir = function()
      local names = {}
      for name in pairs(supplies()) do
        names[#names + 1] = name
      end
      table.sort(names)
      for i, name in ipairs(names) do
        names[i] = {name = name, type = "directory"}
      end
      return names
    end}
  end
  local files = supplies()[parts[3]]
  if not files then
    return nil
  elseif #parts == 3 then
    return {dir = function()
      local entries = {}
      for _, name in ipairs(SUPPLY_FILES) do
        if files[name] ~= nil then
          entries[#entries + 1] = {name = name, type = "file"}
        end
      end
      if files.power_now then
        entries[#entries + 1] = {name = "power_now", type = "file"}
      end
      return entries
    end}
  elseif #parts == 4 and files[parts[4]] ~= nil then
    return {file = function() return tostring(files[parts[4]]) .. "\n" end}
  end
  return nil
end

-- What a path names: {dir = entries()} or {file = text()}, or nil.
local function find(path)
  local parts = {}
  for part in path:gmatch("[^/]+") do
    parts[#parts + 1] = part
  end
  if #parts == 0 then
    return {dir = function() return {{name = CLASS, type = "directory"}, {name = FIRMWARE[1], type = "directory"}} end}
  elseif parts[1] == FIRMWARE[1] then
    return firmware(parts)
  elseif parts[1] ~= CLASS then
    return nil
  elseif parts[2] == POWER then
    return power(parts)
  elseif #parts == 1 then
    return {dir = function()
      local seen, entries = {[POWER] = true}, {{name = POWER, type = "directory"}}
      for _, device in ipairs(k.devices.list) do
        if not seen[kind(device)] then
          seen[kind(device)] = true
          entries[#entries + 1] = {name = kind(device), type = "directory"}
        end
      end
      return entries
    end}
  end
  if #parts == 2 then
    local entries = {}
    for _, device in ipairs(k.devices.list) do
      if kind(device) == parts[2] then
        entries[#entries + 1] = {name = device.name, type = "directory"}
      end
    end
    return #entries > 0 and {dir = function() return entries end} or nil
  end
  local device = k.devices.byname[parts[3]]
  if not device or kind(device) ~= parts[2] then
    return nil
  elseif #parts == 3 then
    return {dir = function()
      local entries = {}
      for i, attribute in ipairs(ATTRIBUTES) do
        entries[i] = {name = attribute, type = "file"}
      end
      entries[#entries + 1] = {name = METHODS, type = "directory"}
      return entries
    end}
  elseif #parts == 4 and parts[4] == METHODS then
    return {dir = function()
      local entries = {}
      for i, name in ipairs(methods(device)) do
        entries[i] = {name = name, type = "file"}
      end
      return entries
    end}
  elseif #parts == 4 then
    for _, attribute in ipairs(ATTRIBUTES) do
      if attribute == parts[4] then
        return {file = function() return tostring(device[attribute] or "-") .. "\n" end}
      end
    end
  elseif #parts == 5 and parts[4] == METHODS then
    for _, name in ipairs(methods(device)) do
      if name == parts[5] then
        return {file = function() return about(device, name) end}
      end
    end
  end
  return nil
end

function fs:open(path, mode)
  local node = find(path)
  if not node then
    return fail("ENOENT")
  elseif node.dir then
    return fail("EISDIR")
  elseif mode:find("[wa+]") then
    return fail("EACCES") -- (read only)
  end
  local text, at = node.file(), 0
  return {
    type = "file",
    events = function(self)
      return {IN = true}
    end,
    read = function(self, n)
      local chunk = text:sub(at + 1, at + n)
      at = at + #chunk
      return chunk
    end,
    seek = function(self, whence, offset)
      if whence ~= "set" or offset ~= 0 then
        return fail("ESPIPE")
      end
      at = 0
      return 0
    end,
  }
end

function fs:stat(path)
  local node = find(path)
  if not node then
    return fail("ENOENT")
  elseif node.dir then
    return {type = "directory", size = 0, modified = 0}
  end
  return {type = "file", size = #node.file(), modified = 0}
end

function fs:list(path)
  local node = find(path)
  if not node then
    return fail("ENOENT")
  elseif not node.dir then
    return fail("ENOTDIR")
  end
  return node.dir()
end

function fs:mkdir()
  return fail("EPERM")
end
fs.remove, fs.rename = fs.mkdir, fs.mkdir

function fs:statfs()
  return {type = "sysfs", size = 0, used = 0}
end

return fs
