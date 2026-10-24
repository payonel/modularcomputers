-- Save the EEPROM's image to a file, and check the copy against the chip.
--
-- usage: lua eeprom-backup.lua [file]     (default /home/eeprom.bak)
--
-- Reading /dev/eeprom gives the chip's whole image, as flashrom -r reads a board's flash. Restore it
-- later with eeprom-flash.lua, or with cp file /dev/eeprom.
local FILE = ... or "/home/eeprom.bak"

local function slurp(path)
  local file = assert(io.open(path, "rb"))
  local data = file:read("a")
  file:close()
  return data
end

local image = slurp("/dev/eeprom")
local out = assert(io.open(FILE, "wb"))
assert(out:write(image))
assert(out:close()) -- (a disk's file is written whole at close)

if slurp(FILE) ~= image then
  io.stderr:write("eeprom-backup: ", FILE, " doesn't match the chip\n")
  os.exit(1)
end
print("saved " .. #image .. " bytes of the EEPROM to " .. FILE)
