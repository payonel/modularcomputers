-- /init.lua: the smallest operating system. The EEPROM's bootloader found this file on a disk,
-- ran it, and passed it the disk's address. It writes to the screen, waits a few seconds, and
-- turns the computer off.
local disk = ...  -- the boot disk's address (sata:0, say): what the bootloader passes

local SECONDS = 5 -- how long the message stays before the computer turns off

-- "console" is every screen on the bus at once. A terminal needs \r\n to start a new line.
board.invoke("console", "write", "Hello from my own OS, booted from " .. disk .. "\r\n")

-- Waiting for an interrupt from no device is a sleep: nothing wakes it until the time is up.
board.waitForInterrupt(SECONDS, "nothing")
board.poweroff()
