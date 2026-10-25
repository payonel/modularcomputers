-- boot: what a new EEPROM comes flashed with, a boot manager as UEFI's. It tries the NVRAM's boot
-- entries (the EEPROM's getBoot: a disk by its id and a file on it): BootNext's once (cleared
-- before it boots, as UEFI does), then BootOrder's in turn, skipping a disk that isn't on the bus;
-- then, UEFI's fallback, each disk in bus order holding /init.lua. It runs the file found, handing
-- it the disk's address, and tells the EEPROM which entry that was (BootCurrent: none for the
-- fallback). A file that doesn't compile is said and skipped; with none left, it says
-- so and halts.
local function say(text)
  board.invoke("console", "write", text)
end

-- The disks on the bus: their addresses, by id.
local disks, order = {}, {}
for _, device in ipairs(board.list()) do
  if device.type == "storage" then
    disks[device.id] = device.address
    order[#order + 1] = device.address
  end
end

-- The OS in a file on a disk, compiled; or nil when there's none there (or it doesn't compile: said).
local function find(disk, path)
  local opened, file = board.invoke(disk, "open", path)
  if not opened then
    return nil
  end
  local parts = {}
  while true do
    local ok, chunk = board.invoke(file, "read", 4096)
    if not ok or chunk == nil then
      break
    end
    parts[#parts + 1] = chunk
  end
  board.invoke(file, "close")
  local at = 0
  local init, why = load(function() -- (straight from the pieces read: never joined into one string)
    at = at + 1
    local piece = parts[at]
    parts[at] = nil
    return piece
  end, "=" .. path:match("[^/]*$"))
  if not init then
    say("boot: " .. disk .. ": " .. why .. "\r\n")
  end
  return init
end

local ok, vars = board.invoke("eeprom", "getBoot")
if ok and vars then
  local entries, tries = {}, {}
  for _, entry in ipairs(vars.entries) do
    entries[entry.number] = entry
  end
  if vars.next then
    tries[1] = vars.next
    board.invoke("eeprom", "setBootNext") -- once only
  end
  for _, number in ipairs(vars.order) do
    tries[#tries + 1] = number
  end
  for _, number in ipairs(tries) do
    local entry = entries[number]
    local disk = entry and disks[entry.disk]
    local init = disk and find(disk, entry.path)
    if init then
      board.invoke("eeprom", "setBootCurrent", number)
      return init(disk)
    end
  end
end
board.invoke("eeprom", "setBootCurrent")
for _, disk in ipairs(order) do
  local init = find(disk, "/init.lua")
  if init then
    return init(disk)
  end
end
say("boot: no bootable disk\r\n")
board.halt()
