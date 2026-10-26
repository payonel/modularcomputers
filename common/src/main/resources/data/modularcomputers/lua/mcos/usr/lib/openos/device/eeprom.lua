-- The EEPROM as OpenComputers' eeprom component: its code and its label. OpenComputers' EEPROM
-- data (a boot disk's address) is the NVRAM's boot variables here, efibootmgr's: not a free store.
local devices = require("devices")

local SIZE = 4096 -- what OpenComputers' EEPROM held, as getSize says

-- CRC-32 (IEEE), as OpenComputers' checksum, in hex.
local function crc32(data)
  local crc = 0xFFFFFFFF
  for i = 1, #data do
    crc = crc ~ data:byte(i)
    for _ = 1, 8 do
      crc = crc & 1 ~= 0 and (crc >> 1) ~ 0xEDB88320 or crc >> 1
    end
  end
  return ("%08x"):format(crc ~ 0xFFFFFFFF)
end

return function(name, info)
  local chip = assert(devices.open(name))
  local methods, docs = {}, {}

  docs.get = "function():string -- Get the currently stored byte array."
  function methods.get()
    return chip.get()
  end

  docs.set = "function(data:string) -- Overwrite the currently stored byte array."
  function methods.set(data)
    local ok, why = chip.set(data)
    if not ok then
      error(why, 2)
    end
  end

  docs.getLabel = "function():string -- Get the label of the EEPROM."
  function methods.getLabel()
    return devices.info(name).label or "EEPROM"
  end

  docs.setLabel = "function(data:string):string -- Set the label of the EEPROM."
  function methods.setLabel(label)
    local ok, why = chip.setLabel(label)
    if not ok then
      return nil, why
    end
    return label
  end

  docs.getSize = "function():number -- Get the storage capacity of this EEPROM."
  function methods.getSize()
    return SIZE
  end

  docs.getChecksum = "function():string -- Get the checksum of the data on this EEPROM."
  function methods.getChecksum()
    return crc32(chip.get() or "")
  end

  for _, missing in ipairs({"getData", "setData", "getDataSize", "makeReadonly"}) do
    docs[missing] = "function(...) -- not supported"
    methods[missing] = function()
      error("openos: eeprom." .. missing .. " is not supported (the boot variables are efibootmgr's)", 2)
    end
  end

  return methods, docs, function()
    devices.close(chip)
  end
end
