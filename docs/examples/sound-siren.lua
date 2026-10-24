-- An alarm siren: lua sound-siren.lua [seconds]
--
-- A tone sweeps up and down and swings between the card's two speakers, for SECONDS, or until
-- someone runs `touch /tmp/siren.off`. It drives the sound card through its own methods, which are
-- its /dev/dsp file's ioctls: setFormat (stereo), getFormat, write (which takes only what fits in
-- the buffer and says how much: the program waits for the card's news, poll's PRI, before it
-- writes the rest), sync (has it all played?), getDelay (what is still queued) and reset (drop it,
-- so the siren stops at once rather than playing on for its last second).
local sys = require("sys")

local SECONDS = tonumber(arg[1]) or 10  -- how long it sounds, unless stopped
local RATE = 8000        -- frames a second (each frame a left and a right sample)
local CHUNK = 0.25       -- seconds of sound made at a time
local SWEEP = 1          -- seconds for the tone to rise and fall back, and the sound to cross over
local LOW, HIGH = 500, 1000  -- the sweep's pitches, in Hz
local SILENCE = 128      -- an unsigned 8-bit sample's middle
local VOLUME = 90        -- how far a wave swings from it
local STOP = "/tmp/siren.off"  -- the file that stops it

local dsp = assert(sys.open("/dev/dsp", "w"))
assert(sys.ioctl(dsp, "setFormat", {rate = RATE, encoding = "u8", channels = 2}))
local format = sys.ioctl(dsp, "getFormat")
print(("siren: %d Hz, %s, %d channels"):format(format.rate, format.encoding, format.channels))

-- Frames 'from' to 'from + count - 1' of the siren, as bytes: left, right, left, right...
local phase = 0
local function frames(from, count)
  local out = {}
  for i = from, from + count - 1 do
    local t = (i / RATE) % SWEEP / SWEEP           -- where in the sweep, 0 to 1
    local rise = t < 0.5 and t * 2 or 2 - t * 2    -- up, then down
    phase = phase + (LOW + (HIGH - LOW) * rise) / RATE
    local wave = phase % 1 < 0.5 and VOLUME or -VOLUME
    out[#out + 1] = string.char(SILENCE + math.floor(wave * (1 - rise)), SILENCE + math.floor(wave * rise))
  end
  return table.concat(out)
end

-- Queue all of 'data', waiting for room as the card plays: false if told to stop meanwhile.
local function play(data)
  while #data > 0 do
    local taken = assert(sys.ioctl(dsp, "write", data))
    data = data:sub(taken + 1)
    if #data > 0 then
      sys.poll({[dsp] = {PRI = true}})  -- the card says when half its buffer is free
    end
    if sys.stat(STOP) then
      return false
    end
  end
  return true
end

local total, step = math.floor(SECONDS * RATE), math.floor(CHUNK * RATE)
local stopped = false
for from = 0, total - 1, step do
  if not play(frames(from, math.min(step, total - from))) then
    stopped = true
    break
  end
end

if stopped then
  local queued = sys.ioctl(dsp, "getDelay")
  sys.ioctl(dsp, "reset")  -- silence, now
  sys.unlink(STOP)
  print(("siren: stopped (%d bytes not played)"):format(queued))
else
  while not sys.ioctl(dsp, "sync") do  -- false: not played out yet, and the card will say when
    sys.poll({[dsp] = {PRI = true}})
  end
  print("siren: done")
end
sys.close(dsp)
