-- A doorbell: when a button wired to the computer is pressed, play a two-tone chime ("ding-dong").
--
-- The button's signal comes in on a redstone card (its file, /dev/redstone0, a line a face:
-- "face level powered"). The chime is 16-bit, so the program sets the sound card with OSS's
-- ioctls (format, channels, rate), as any Linux program sets /dev/dsp, and makes the chime once.
-- After each ring, SNDCTL_DSP_SYNC waits until it has played out, so a second press while it
-- rings does not pile up rings. sys.poll waits for the redstone card's change (PRI): no busy wait.
local sys = require("sys")

local BUTTON = "north"   -- the face the button's signal comes in on
local RATE = 11025       -- samples a second
local DING, DONG = 660, 520  -- the two tones' pitches, in Hz
local TONE = 0.4         -- seconds each tone lasts
local VOLUME = 12000     -- the loudest sample (16-bit samples go to 32767)

-- A tone fading out, as a struck chime does: a sine wave whose loudness falls away.
local function tone(hz)
  local count = math.floor(TONE * RATE)
  local out = {}
  for i = 0, count - 1 do
    local fade = 1 - i / count
    out[#out + 1] = string.pack("<i2", math.floor(VOLUME * fade * fade * math.sin(2 * math.pi * hz * i / RATE)))
  end
  return table.concat(out)
end

local chime = tone(DING) .. tone(DONG)

local dsp = assert(sys.open("/dev/dsp", "w"))
assert(sys.ioctl(dsp, "SNDCTL_DSP_SETFMT", "AFMT_S16_LE"))
assert(sys.ioctl(dsp, "SNDCTL_DSP_CHANNELS", 1))
assert(sys.ioctl(dsp, "SNDCTL_DSP_SPEED", RATE))

local redstone = assert(io.open("/dev/redstone0"))
local watch = {[sys.fileno(redstone)] = {PRI = true}}

-- The button's level now: the file read again from its start.
local function pressed()
  redstone:seek("set", 0)
  return (tonumber(redstone:read("a"):match(BUTTON .. " (%d+)")) or 0) > 0
end

local was = pressed()
while true do
  sys.poll(watch)  -- block until the redstone card changes
  local now = pressed()
  if now and not was then
    sys.write(dsp, chime)            -- waits while the card's buffer is full
    sys.ioctl(dsp, "SNDCTL_DSP_SYNC") -- and until it has all played
  end
  was = now
end
