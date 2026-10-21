-- The weather sensor's driver, for its /dev file: its events are tables ({name, old, new}), one a
-- line, as the motion sensor's are: the same driver.
local k = ...
return k.driver("motion")
