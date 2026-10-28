---
title: /dev
section: mcos
order: 40
---

`/dev` holds the computer's devices as files, as Linux's does: every device on the bus has one, and
it is how a program uses the device. It is flat: no subfolders.

## What is there

- `console`: the board's console, which merges every terminal on the bus. See [Names](/concepts/names/).
- `tty`: the terminal of the process that opens it.
- `null` and `zero`: `null` takes what's written and reads as empty; `zero` reads as zero bytes.
- One file for each device on the [bus](/concepts/bus/), named by type and place: `eeprom`,
  `storage0`, `redstone0`, `network0`, `internet0`, `terminal0`, `dsp`...
  [`lsdev`](/programs/lsdev/) lists the names.

An open file holds the device's **id**, not its name: it reaches that device or fails with
`ENODEV`, even if the names are rearranged while it is open.

## A device's methods are its ioctls

Every device file's ioctls are its device's [methods](/concepts/bus/), by name, with the
method's arguments, in any open mode, as a Linux device's ioctls are its driver's API. The answer is
the method's (`true` for a method that answers nothing); a method's failure is `EINVAL` with its
message, and a device gone is `ENODEV`.

```lua
local sys = require("sys")
local card = assert(sys.open("/dev/network0"))
sys.ioctl(card, "setStrength", 16)       -- a WLAN card's range
print(sys.ioctl(card, "getStrength"))
sys.close(card)
```

A device's file is ready for `sys.poll`'s `PRI` while the device has news: its interrupt came
since the file was last read, or an event waits in the file. Each file open on a device gets every
event the device has (a program and a `cat` each see them all; at most 32 wait in a file, the
oldest dropped past that, and the next says how many went, `lost`), and the file's `pull` ioctl
takes from its own. The methods each device has are on its page.

A device that does [one command at a time](/concepts/interrupts/#one-command-at-a-time) (a robot,
a field card, a redstone card, a robotic piston) makes an ioctl or a write given while it's busy
wait, the calling thread only, until the device takes it: so commands given in a row happen in
order. A `stop` of the device (a line `stop` too) ends those waits, `ECANCELED`, and a device gone
ends them, `ENODEV`. A last line written with no end goes as the file closes: its close waits for a
busy device as a write would (a process ending drops it).

## /sys: what a device is and can do

So a device can be learned in the game, without this manual, `/sys/class` has a directory for each
kind of device on the bus, and in it a directory for each device, by its `/dev` name, as Linux's
`/sys/class` has (`/sys/class/net/eth0`). In a device's directory, its attributes are small
read-only files (`type`, `id`, `path`: its slot, or `-` when it's cabled; `label`), and `methods`
has a file for each of its methods (its file's ioctls): the method's arguments and what it does,
from the device's own descriptions. Linux has a `/sys/devices` tree too, by where each device is
attached; mcos's bus is flat, so there's only the class tree.

```sh
ls /sys/class
ls /sys/class/robotics
cat /sys/class/robotics/robotics0/id
ls /sys/class/robotics/robotics0/methods
cat /sys/class/robotics/robotics0/methods/walk
```

```
walk(forward[, right[, up]])
    walks to the point that many blocks from it, in its own frame (forward the
    way it faces, right to its right, up), by the way it finds; ...
```

Programs read them as any files; the [devices library](/mcos/devices-library/) does it for you.

`/sys/class/power_supply` has the computer's power, as Linux's: a directory for each battery in a bay
(`BAT0`, `BAT1`, type `Battery`) and one for the case's own store, the one it runs on and its batteries
fill (`case`, type `UPS`), each with `status` (`Full`, `Charging`, `Discharging`, `Not charging`),
`capacity` (percent), `energy_now` and `energy_full` (µWh), and the case's `power_now` (µW: what the
computer draws now). A power unit a tick is a watt. An infinite battery has only its `capacity`, 100.

```sh
cat /sys/class/power_supply/case/capacity
cat /sys/class/power_supply/BAT0/status
```

`/sys/firmware/efi/efivars` has the EEPROM's boot variables, a file each (`Boot0001`, `BootOrder`,
`BootNext`, `BootCurrent`, `RestoreOnPowerLoss`), as Linux's efivarfs; see [EEPROM](/devices/eeprom/) and
[`efibootmgr`](/programs/efibootmgr/). `/sys/firmware/last_shutdown` has why the computer last
stopped (`cause`, `message`, `time`), as its board recorded it; see [aborts](/hardware/cpu/#aborts).

## Devices coming and going

A device joining the bus makes its file appear in `/dev`, and one leaving takes it away, so
inotify reports them as any directory's changes: `CREATE /dev/charger0`, `DELETE /dev/charger0`
(a name passing to another device, as `storage1` becomes `storage0` when `storage0` goes, is both).
A program waits for a device with a watch on `/dev`, or with `devices.wait`: see
[the devices library](/mcos/devices-library/).

## How a file behaves

Reading gives what the device's `get` returns, taken at the first read. What you write goes to the
device's `set`, all of it in one call, when the file is closed; the device decides what that
means (replace, append...). A device with nothing to read gives what it is: its type, id, label
and methods, a line each. A write to a device that can't be set, or a read from one that can't
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

**Disk Station.** A read gives a line for each slot: `target 2 hfs 40960 4194304 backups`
(its disk's tier, mode, bytes used, capacity and label) or `source -` for an empty slot. A line
written names an action: `format`, `wipe`, `clone`, `install` or `update`.

**Operator card.** A line written runs a server command, as a command block does (a leading `/` is
allowed); a read gives what the last command said.

**Network card, WLAN card.** Read-only: its traffic, a line a frame, in or out: the other card's
address (or `broadcast`), how far it is, the frame's size and how it went (`taken`, `busy`,
`unreachable`; a broadcast's: how many took it). The card keeps its last 8 frames. Its data is read
through [sockets](/mcos/networking/).

```
out 5f0c2a9e-... 3.0m 42 taken
in 5f0c2a9e-... 3.0m 17 taken
out broadcast - 4 2 took it
```

**Internet card.** Read-only: its access log, a line a request as it ended (method, URL, status,
time, bytes). See the [internet card](/devices/internet-card/).

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

An ADPCM block's size (WAV's block align), which OSS never had, is the card's own method:
`sys.ioctl(fd, "setFormat", {block = 1024})`. [`aplay`](/programs/aplay/) plays WAV files.

## Events and polling

A device file can be polled with `sys.poll` (see [The sys library](/mcos/sys-api/)). A redstone file
is always readable and reports a change as `PRI`, as a GPIO's sysfs value does; any other device's
file reports its device's news as `PRI`. An event file is
readable while an event waits. When a program blocks on a device and nothing else has work, the
kernel sleeps the computer until the device's [interrupt](/concepts/interrupts/).
