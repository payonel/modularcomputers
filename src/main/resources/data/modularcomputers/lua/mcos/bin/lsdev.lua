-- lsdev: the devices on the bus, in bus order.
for _, device in ipairs(board.list()) do
  mcos.term.write(device.address .. "  " .. device.type .. "\n")
end
