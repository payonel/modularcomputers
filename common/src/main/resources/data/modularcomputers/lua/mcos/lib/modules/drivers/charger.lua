-- A charging pad's driver, for its /dev file (as a sysfs attribute: text, read whole). A read
-- gives what it's doing and its buffer, e.g.
--   charging 4f0c2a9e-... 26400/26400   (a robot on it, by its board's id)
--   idle - 1200/26400                   (none)
-- Its events (a robot docked or undocked) come as poll's PRI and the pull ioctl.
return {
  get = function(now)
    return (now.charging and "charging" or "idle") .. " " .. (now.robot or "-") .. " " .. now.buffer .. "/" .. now.capacity .. "\n"
  end,
}
