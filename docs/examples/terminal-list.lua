-- List the terminals on the bus, with their size now and the largest each can be made:
-- lua terminal-list.lua
--
-- sys.devices() gives every device the kernel knows, and each terminal has a file in /dev
-- (terminal0, terminal1...). The file has no data of its own (a read says what the device is), but
-- its ioctls are the terminal's methods, so getSize is one call on it.
local sys = require("sys")

print(("%-12s %-9s %-9s %s"):format("NAME", "SIZE", "LARGEST", "LABEL"))
for _, device in ipairs(sys.devices()) do
  if device.type == "terminal" then
    local fd = assert(sys.open("/dev/" .. device.name))
    local columns, rows, mostColumns, mostRows = sys.ioctl(fd, "getSize")
    sys.close(fd)
    if columns then
      print(("%-12s %-9s %-9s %s"):format(device.name, columns .. "x" .. rows,
        mostColumns .. "x" .. mostRows, device.label or "-"))
    else
      io.stderr:write(device.name, ": ", tostring(rows), "\n")  -- (rows holds why it failed)
    end
  end
end
