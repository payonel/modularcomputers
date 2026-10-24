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
| Address | An expansion slot path such as `exp:0`, or a hub's |
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

The card is OSS's `/dev/dsp` (`/dev/dsp1` for the next card), as on Linux.

- **Write:** what you write plays. A write waits while the buffer is full, as a pipe's writer does,
  so a program never runs ahead of the sound. Each open starts at OSS's defaults (8000 a second,
  unsigned 8-bit, mono), so a raw file plays as written; what another program left queued in that
  same format plays on first.
- **Read:** the file is write-only. Opening it to read fails with `EACCES` (it plays; it does not
  record).
- **Poll:** it is always ready for `OUT` (a write waits by itself). It is ready for `PRI` while the
  card has news: its interrupt came since the file was last asked an ioctl (room after a refused
  `write`, or the queue played out after a `sync` that said not yet).

```sh
cat /clip.raw > /dev/dsp
aplay /clip.wav
```

[`aplay`](/programs/aplay/) plays WAV files and sets the format for them. The file's ioctls are OSS's:

| ioctl | Argument | Does |
|---|---|---|
| `SNDCTL_DSP_SPEED` | a rate | sets the rate; answers what it is |
| `SNDCTL_DSP_SETFMT` | `AFMT_U8`, `AFMT_S16_LE` or `AFMT_IMA_ADPCM` | sets the encoding; answers its name |
| `SNDCTL_DSP_CHANNELS` | 1 or 2 | sets the channels; answers them |
| `SNDCTL_DSP_GETODELAY` | none | gives the bytes queued |
| `SNDCTL_DSP_RESET` | none | drops what is queued |
| `SNDCTL_DSP_SYNC` | none | waits until what is queued has played |

A value the card does not play fails with `EINVAL` (an unknown format name too). After OSS's, the
file's ioctls are the card's own methods below, by name, with their arguments: an ADPCM block's
size, which OSS never had, is

<!-- lua-fragment -->
```lua
sys.ioctl(dsp, "setFormat", {block = 1024})
```

A method's ioctl answers what the method does (`true` for one that answers nothing); its failure is
`nil`, the card's message and `EINVAL`. `setLabel` sets the card's label, as on every device file.

## Methods

### setFormat({rate, encoding, channels, block}) → nothing

Sets what it plays. Any field left out stays. `encoding` is `"u8"`, `"s16le"` or `"ima_adpcm"`.
An unsupported value fails with a message such as
`unsupported rate: 9000 ([8000, 11025, 22050, 44100])`, `unsupported encoding: ...` or
`unsupported channels: 3 (1 or 2)`. `block` is for ADPCM only (WAV's block align: at most 8192, a
multiple of 4 times the channels, and larger than 4 bytes a channel, else
`bad ADPCM block size: ...`); switching to ADPCM without one gives 512 bytes a channel. What was
queued goes, unless the new format is the same as the old.

### getFormat() → {rate, encoding, channels, block}

The current format. `block` is 0 unless the encoding is ADPCM.

### write(data) → count

Queues bytes to play and gives how many it took: fewer, or 0, when the buffer is full. Then its
interrupt comes once half the buffer is free again. (Writing to the file does this for you, and
waits.)

### getDelay() → bytes

The bytes queued and not yet played (OSS's `SNDCTL_DSP_GETODELAY`).

### sync() → boolean

True once what is queued has played out. False: not yet, and the interrupt says when (OSS's
`SNDCTL_DSP_SYNC`, which waits for it).

### reset() → nothing

Drops what is queued, at once (OSS's `SNDCTL_DSP_RESET`).

## Events

None to pull. The interrupt line is the event: it is up while a write was refused and the buffer is
at least half empty, and while a `sync` waits and the queue has played out. Each `write` and
`sync` starts its wait afresh.

## Examples

A tune, played through the file at OSS's defaults (the program only makes samples and writes them):

```lua
{% include sound-melody.lua %}
```

A doorbell: a redstone card hears the button, and the program sets a 16-bit format with OSS's ioctls
and waits with `SNDCTL_DSP_SYNC` for each ring to end:

```lua
{% include sound-doorbell.lua %}
```

A stereo siren through the card's own methods: `write`'s handshake with `poll`'s `PRI`, `sync` to
finish, and `reset` to stop at once when `touch /tmp/siren.off` asks:

```lua
{% include sound-siren.lua %}
```

Unsigned 8-bit samples centre on 128 (silence), so a square wave is a run of bytes above it and a
run below. A 16-bit sample is `string.pack("<i2", value)`, from -32768 to 32767.
