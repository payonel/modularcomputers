-- boot: what a new EEPROM comes flashed with. Like a BIOS, it boots the first disk, in bus order,
-- that has /init.lua, handing it the disk's address; with none, it says so and halts.
local function say(text)
  board.invoke("console", "write", text)
end

for _, device in ipairs(board.list()) do
  if device.type == "storage" then
    local opened, file = board.invoke(device.address, "open", "/init.lua")
    if opened then
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
        say("boot: " .. device.address .. ": " .. why .. "\n")
        board.halt()
      end
      return init(device.address)
    end
  end
end
say("boot: no bootable disk\n")
board.halt()
