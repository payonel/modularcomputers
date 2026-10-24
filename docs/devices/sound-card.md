---
title: Sound Card
section: Devices
order: 90
---

A sound card plays the samples a program writes to it, as a real card's DAC does. The players near
its block hear them.

<div class="infobox" markdown="1">

### Facts
| | |
|---|---|
| Type | `sound` (the kernel names it `dsp`, then `dsp1`) |
| Address | An expansion slot path such as `exp:0` |
| Formats | Rates 8000, 11025, 22050 or 44100 a second; unsigned 8-bit (`u8`), signed 16-bit little-endian (`s16le`) or IMA ADPCM (`ima_adpcm`); 1 or 2 channels |
| Buffer | One second of the current format |
| Interrupt | Raised when a refused write has room, or when what is queued has played out |
| Power | 6 W |
| Info | `kind` (`sound`) |

</div>

## How it plays

A write takes what fits in the buffer and says how much (nothing is dropped silently). A program
that is refused waits for the card's interrupt, which comes when at least half the buffer is free.
The card plays by game time: each tick its block sends what is due, still encoded, to the players
within 40 blocks, and their games decode and play it. It decodes IMA ADPCM itself.

Mono comes from the card's block. Stereo is two speakers half a block either side of the block, its
left and right as seen facing its front, because the game does not place a stereo sound in the world.
See [Faces](/concepts/faces/).

The card powers on at OSS's defaults: 8000 a second, unsigned 8-bit, mono. Changing the format drops
what is queued. A new format that is the same as the old one drops nothing.

## In mcos

The card is OSS's `/dev/dsp` (`/dev/dsp1` for the next card). It is write-only (`EACCES` to read).
Each open starts at 8000 a second, unsigned 8-bit, mono, so a raw file plays as written. A write
waits while the buffer is full.

```sh
cat /clip.raw > /dev/dsp
aplay /clip.wav
```

`aplay` plays WAV files and sets the format for them. The file's ioctls are OSS's:

| ioctl | Argument | Does |
|---|---|---|
| `SNDCTL_DSP_SPEED` | a rate | sets the rate; answers what it is |
| `SNDCTL_DSP_SETFMT` | `AFMT_U8`, `AFMT_S16_LE` or `AFMT_IMA_ADPCM` | sets the encoding; answers its name |
| `SNDCTL_DSP_CHANNELS` | 1 or 2 | sets the channels; answers them |
| `SNDCTL_DSP_GETODELAY` | none | gives the bytes queued |
| `SNDCTL_DSP_RESET` | none | drops what is queued |
| `SNDCTL_DSP_SYNC` | none | waits until what is queued has played |

Its methods below are ioctls too, by name: an ADPCM block's size, which OSS never had, is
`sys.ioctl(fd, "setFormat", {block = 1024})`.

## Methods

### setFormat({rate, encoding, channels, block}) → nothing

Sets what it plays. Any field left out stays. An unsupported value fails with a message such as
`unsupported rate: 9000 ([8000, 11025, 22050, 44100])`. `block` is for ADPCM only (WAV's block align,
at most 8192, a multiple of 4 times the channels, and larger than 4 bytes a channel); it defaults
to 512 a channel. What was buffered goes.

### getFormat() → {rate, encoding, channels, block}

The current format. `block` is 0 unless the encoding is ADPCM.

### write(data) → count

Queues bytes to play and gives how many it took: fewer, or 0, when the buffer is full.

### getDelay() → bytes

The bytes queued and not yet played.

### sync() → boolean

True once what is queued has played out. False: not yet, and the interrupt says when.

### reset() → nothing

Drops what is queued, at once.

## Events

None to pull. The interrupt line is the event: it is up while a write was refused and the buffer is
at least half empty, and while a `sync` waits for the queue to drain.

## Example

Play a short square wave, from a Lua program under mcos (8-bit, 8000 a second, so a note of 440 Hz
is about 18 samples a cycle):

```lua
local f = io.open("/dev/dsp", "w")
local cycle = string.rep("\200", 9) .. string.rep("\56", 9)
f:write(string.rep(cycle, 220))
f:close()
```

That is half a second of sound: 220 cycles of 18 bytes at 8000 bytes a second is 0.495 s. Unsigned
8-bit samples centre on 128, so 200 and 56 are the high and low of the wave.
