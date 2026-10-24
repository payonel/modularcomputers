-- Flash a Lua file onto the EEPROM, safely: refuse one that doesn't compile, keep the old image, and
-- check the chip holds the new one.
--
-- usage: lua eeprom-flash.lua file [backup]     (backup: default /home/eeprom.old)
--
-- What's written to /dev/eeprom is kept until the file is closed, then replaces the whole image in
-- one go (all or nothing, as a firmware flash commits). The CPU runs the image as Lua at the next
-- power-on, so an image that doesn't compile would leave the computer unable to boot: this checks
-- first. Then it reads the chip back with the "get" ioctl (its method) to be sure.
local sys = require("sys")

local file, backup = ...
backup = backup or "/home/eeprom.old"
if not file then
  io.stderr:write("usage: eeprom-flash.lua file [backup]\n")
  os.exit(2)
end

local function slurp(path)
  local f = assert(io.open(path, "rb"))
  local data = f:read("a")
  f:close()
  return data
end

local image = slurp(file)
local compiled, why = load(image, "=" .. file)
if not compiled then
  io.stderr:write("eeprom-flash: not flashed: ", why, "\n")
  os.exit(1)
end

-- Keep what's there now.
local old = assert(io.open(backup, "wb"))
old:write(slurp("/dev/eeprom"))
assert(old:close())

-- Flash: written at close.
local chip = assert(io.open("/dev/eeprom", "wb"))
chip:write(image)
assert(chip:close())

-- Read it back from the chip itself.
local check = assert(io.open("/dev/eeprom"))
local now = assert(sys.ioctl(sys.fileno(check), "get"))
check:close()
if now ~= image then
  io.stderr:write("eeprom-flash: the chip doesn't hold what was written; the old image is in ", backup, "\n")
  os.exit(1)
end
print("flashed " .. #image .. " bytes; the old image is in " .. backup .. "; reboot to run it")
