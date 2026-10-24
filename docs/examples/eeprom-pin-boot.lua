-- Make the computer boot one disk only, whatever else is on the bus: write a new boot image for the
-- EEPROM that looks for /init.lua on that disk alone, by its id.
--
-- usage: lua eeprom-pin-boot.lua [disk]     (the disk's name in /dev; default storage0, the one now)
--
-- The EEPROM a computer comes with boots the first disk, in bus order, holding /init.lua; a disk put
-- in an earlier slot would then boot instead. A disk's id never changes when it moves, so an image
-- naming it boots that disk wherever it sits. The image is built from IMAGE below and flashed with
-- the "set" ioctl (the EEPROM's own method: the whole image, at once), after "get" saves the old one.
local sys = require("sys")

local BACKUP = "/home/eeprom.old" -- the image there before

-- The new image: firmware-level Lua (it runs before any OS, so it calls the board directly).
-- DISK is filled in with the disk's id.
local IMAGE = [=[
-- pinned boot: boots /init.lua from one disk, by its id, or halts saying why.
local DISK = %q
local function say(text)
  board.invoke("console", "write", text)
end
local opened, file = board.invoke(DISK, "open", "/init.lua")
if not opened then
  say("boot: " .. DISK .. ": " .. tostring(file) .. "\r\n")
  board.halt()
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
local init, why = load(table.concat(parts), "=init.lua")
if not init then
  say("boot: " .. DISK .. ": " .. why .. "\r\n")
  board.halt()
end
return init(DISK)
]=]

local diskname = ... or "storage0"
local disk
for _, device in ipairs(sys.devices()) do
  if device.name == diskname and device.type == "storage" then
    disk = device
  end
end
if not disk then
  io.stderr:write("eeprom-pin-boot: ", diskname, ": no such disk\n")
  os.exit(1)
end

local image = string.format(IMAGE, disk.id)
assert(load(image, "=pinned boot")) -- (it must compile: the CPU runs it at power-on)

local chip = assert(io.open("/dev/eeprom"))
local fd = sys.fileno(chip)
local old = assert(sys.ioctl(fd, "get"))
local saved = assert(io.open(BACKUP, "wb"))
saved:write(old)
assert(saved:close())

assert(sys.ioctl(fd, "set", image)) -- (true: done)
chip:close()
print("the EEPROM now boots " .. disk.id .. " only; the old image is in " .. BACKUP)
