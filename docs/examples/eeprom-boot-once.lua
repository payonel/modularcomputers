-- Boot another disk once, next time only, then this one again: an entry for it in the EEPROM's
-- NVRAM (made if it has none) and BootNext set to it, through /dev/eeprom's ioctls (the EEPROM's
-- own methods, which check every change), then a reboot. efibootmgr -c and -n do the same.
--
-- usage: lua eeprom-boot-once.lua disk     (its name in /dev: storage1)
local sys = require("sys")

local name = arg[1]
if not name then
  io.stderr:write("usage: lua eeprom-boot-once.lua disk\n")
  return 2
end

-- The disk's id, from /sys: an entry names a disk by it, the same wherever the disk sits.
local file = io.open("/sys/class/storage/" .. name .. "/id")
if not file then
  io.stderr:write(name, ": no such disk\n")
  return 1
end
local id = file:read("l")
file:close()

local eeprom = assert(sys.open("/dev/eeprom"))
local boot = assert(sys.ioctl(eeprom, "getBoot"))
-- The disk's entry, if it has one; else a new one, at the first free number.
local number
local used = {}
for _, entry in ipairs(boot.entries) do
  used[entry.number] = true
  if entry.disk == id then
    number = entry.number
  end
end
if not number then
  number = 0
  while used[number] do
    number = number + 1
  end
  assert(sys.ioctl(eeprom, "setBootEntry", number, id, "/init.lua", name))
end
assert(sys.ioctl(eeprom, "setBootNext", number))
sys.close(eeprom)
print(string.format("Boot%04X next: %s", number, name))
os.execute("reboot")
