---
title: /dev
section: mcos
order: 40
---

`/dev` holds the computer's devices as files, as Linux's does. It holds only data, and it is flat:
no subfolders.

## What is there

- `console`: the board's console, which merges every terminal on the bus. See [Names](/concepts/names/).
- `tty`: the terminal of the process that opens it.
- `null` and `zero`: `null` takes what's written and reads as empty; `zero` reads as zero bytes.
- One file for each device on the [bus](/concepts/bus/) that has data to read or write, named by
  type and place: `eeprom`, `storage0`, `redstone0`, `motion0`, `dsp`... [`lsdev`](/programs/lsdev/)
  lists the names. A device with no data of this kind has no file: a
  [network card](/devices/network-card/), for one, is used through [sockets](/mcos/networking/).

An open file holds the device's **id**, not its name: it reaches that device or fails with
`ENODEV`, even if the names are rearranged while it is open.

## How a file behaves

Reading gives what the device's `get` returns, taken at the first read. What you write goes to the
device's `set`, all of it in one call, when the file is closed; the device decides what that
means (replace, append...). A write to a device that can't be set, or a read from one that can't
be read, fails with `EACCES`. The kernel does not let a read-only open write, or the other way round.

The [EEPROM](/devices/eeprom/)'s file is its image: write it to reflash the chip. Everything
written is kept until the file is closed, and then applied all or nothing, as a firmware load
commits. (A program that ends closes its files, so this happens at the latest then.)

A device whose data is a table, not text, has a **driver**: a small file in `/lib/modules/drivers/`
named for the device's type, which turns it into text. mcos looks for the driver the first time a file
of that type is opened, so a type costs no memory until it is used. A driver's text is always text, as
a sysfs file is: lines and words, never Lua syntax.

## Drivers

**Redstone card.** A read gives a line a face, `face level powered`: the signal now on that face
(coming in or put out) and how much of it this card puts out. A line written sets a face and takes
effect at once, `face powered`. Faces are named as in [Faces](/concepts/faces/).

```
$ cat /dev/redstone0
north 7 7
south 0 0
...
$ echo "front 15" > /dev/redstone0
```

**Bluetooth card.** A read gives two lines, `link <id>` (the board it links to, `-` for none) and
`board <id>` (the board it is in). A line written, `link <id>` or `link -`, relinks it.

**Disk Station.** A read gives a line for each slot: `target 2 hfs 40960 2097152 backups`
(its disk's tier, mode, bytes used, capacity and label) or `source -` for an empty slot. A line
written names an action: `format`, `wipe`, `clone`, `install` or `update`.

**Operator card.** A line written runs a server command, as a command block does (a leading `/` is
allowed); a read gives what the last command said.

**Motion sensor, audio sensor, weather sensor.** Read-only; an event a line, and a read with none
waiting waits for one, as a keyboard does.

- motion: `mob x y z`, e.g. `minecraft:pig 1.5 0.0 -3.0`
- audio: `sound x y z`, e.g. `minecraft:entity.pig.ambient 1.5 0.0 -3.0`
- weather: the weather now: `clear`, `rain`, `snow` or `thunder`

Positions are relative to the sensor's block. If the sensor's buffer filled and events were
dropped, a line `lost n` comes before the next event, as an input device's `SYN_DROPPED` does.

See the device pages for the details of each: [redstone card](/devices/redstone-card/),
[Bluetooth card](/devices/bluetooth-card/), [Disk Station](/devices/disk-station/),
[operator card](/devices/operator-card/), [motion sensor](/devices/motion-sensor/),
[audio sensor](/devices/audio-sensor/), [weather sensor](/devices/weather-sensor/).

## Disks

A disk's file (`storage0`) is its bytes, as a block device is: read, written and sought by byte
offset. A raw disk can be used this way. An *hfs* disk, which has a filesystem, refuses it with
`EMEDIUMTYPE` (Linux would let a mounted disk's bytes be read; this is mcos's choice). Past the
disk's end, a read gives nothing and a write fails with `ENOSPC`. A read-only disk refuses writes
with `EROFS`.

## Sound

A [sound card](/devices/sound-card/)'s file is OSS's `/dev/dsp` (`dsp`, then `dsp1`). What you write
plays, and a write waits while the card's buffer (one second) is full. An open starts at OSS's
defaults (8000 samples a second, unsigned 8-bit, mono), so `cat file.raw > /dev/dsp` plays a raw
file. The card is not opened for reading. Its `ioctl`s are OSS's names:

| ioctl | Argument | Does |
|---|---|---|
| `SNDCTL_DSP_SPEED` | a rate | sets and answers the rate |
| `SNDCTL_DSP_SETFMT` | `AFMT_U8`, `AFMT_S16_LE` or `AFMT_IMA_ADPCM` | sets and answers the format |
| `SNDCTL_DSP_CHANNELS` | 1 or 2 | sets and answers the channels |
| `SNDCTL_DSP_GETODELAY` | | the bytes queued |
| `SNDCTL_DSP_RESET` | | drops what is queued |
| `SNDCTL_DSP_SYNC` | | waits until what is queued has played |
| `MCOS_DSP_BLOCK` | bytes | an ADPCM block's size (WAV's block align), mcos's own |

[`aplay`](/programs/aplay/) plays WAV files.

## Internet card

An [internet card](/devices/internet-card/)'s request is a file of its own, opened with
`sys.http`. [`wget`](/programs/wget/) and [`curl`](/programs/curl/) are built on it.

## Events and polling

A device file can be polled with `sys.poll` (see [The sys library](/mcos/sys-api/)). A redstone file
is always readable and reports a change as `PRI`, as a GPIO's sysfs value does. An event file is
readable while an event waits. When a program blocks on a device and nothing else has work, the
kernel sleeps the computer until the device's [interrupt](/concepts/interrupts/).
