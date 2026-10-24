-- Play a tune on the sound card: lua sound-melody.lua [notes]
--
-- The notes are names with an octave, a beat each (C4 D4 E4...), and "-" is a rest. Each note is a
-- square wave, made as samples at the card's defaults (OSS's: 8000 a second, unsigned 8-bit, mono),
-- so the program just writes them to /dev/dsp, as `cat file.raw > /dev/dsp` would. The card's
-- buffer holds a second; a write waits while it is full, so the program never runs ahead of the
-- music and needs no timer of its own.
local RATE = 8000        -- samples a second: the default every open of /dev/dsp starts at
local BEAT = 0.25        -- seconds a note lasts
local GAP = 0.02         -- seconds of silence at a note's end, so repeated notes are heard apart
local SILENCE = 128      -- an unsigned 8-bit sample's middle: no sound
local VOLUME = 60        -- how far the wave swings from the middle (at most 127)
local TUNE = "E4 D4 C4 D4 E4 E4 E4 - D4 D4 D4 - E4 G4 G4 -"

local SEMITONES = {C = -9, D = -7, E = -5, F = -4, G = -2, A = 0, B = 2}

-- A note's pitch in Hz, from its name ("A4" is 440; a "#" after the letter is a semitone up).
local function pitch(name)
  local letter, sharp, octave = name:match("^(%u)(#?)(%d)$")
  assert(letter and SEMITONES[letter], "not a note: " .. name)
  local steps = SEMITONES[letter] + (sharp == "#" and 1 or 0) + (tonumber(octave) - 4) * 12
  return 440 * 2 ^ (steps / 12)
end

-- A beat of samples: the note's square wave, then the gap (a rest is all silence).
local function samples(name)
  local count = math.floor(BEAT * RATE)
  local sounding = name == "-" and 0 or count - math.floor(GAP * RATE)
  local period = name ~= "-" and RATE / pitch(name)
  local high, low = string.char(SILENCE + VOLUME), string.char(SILENCE - VOLUME)
  local out = {}
  for i = 0, count - 1 do
    if i < sounding then
      out[#out + 1] = (i % period) < period / 2 and high or low
    else
      out[#out + 1] = string.char(SILENCE)
    end
  end
  return table.concat(out)
end

local dsp = assert(io.open("/dev/dsp", "w"))
for name in (arg[1] or TUNE):gmatch("%S+") do
  dsp:write(samples(name))  -- waits while the card's buffer is full
  dsp:flush()
end
dsp:close()  -- what is queued plays on after the program ends
