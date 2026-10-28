---
title: Audio Sensor
section: Devices
order: 130
---

An audio sensor hears what a player standing at its block would hear, and reports each sound: which
one, and where it came from.

<div class="infobox" markdown="1">

### Facts
| | |
|---|---|
| Type | `hearing` |
| Address | An expansion slot path such as `exp:0`, or a hub's |
| Range | The game's own range for each sound |
| Interrupt | Raised while events wait |
| Power | 9 W |
| Info | `kind` (`audio_sensor`) |

</div>

It reports the sound the game plays, by its id, such as `minecraft:entity.pig.ambient`, and nothing
else about it (no category, no mob). It is exactly what the game plays: a skeleton's step is
`minecraft:entity.skeleton.step`, and the stone under it is silent. It hears every sound the server
sends to players, and the sounds players make of world events (a block broken plays its own break
sound). A sound counts if a player at the sensor's block would hear it. It works while a running
computer powers it.

## In mcos

`/dev/hearing0` (`hearing1` for the next) is a read-only file, an event a line:

```
minecraft:entity.pig.ambient 1.5 -3.0 0.0
```

That is `sound forward right up`: the sound's id, and where it came from, in blocks, from its
computer's middle (1.5 ahead and 3 to its left). A read with nothing waiting waits for the next event, and `poll` reports the
file readable (`IN`) while one waits. When events were dropped, a line `lost n` comes before the
next one. Opening it to write fails with `EACCES`.

```sh
tail -f /dev/hearing0
```

The file's ioctls are the sensor's methods, by name, in any open mode:
`sys.ioctl(fd, "pull")` answers the oldest event as a table, with numbers for its coordinates. A
sensor that is gone answers `ENODEV`. `setLabel` labels it, as for any device (see
[The sys library](/mcos/sys-api/)).

Each file open on the sensor gets every event (a program and a `tail -f` each see them all).
Within one file, its lines and `pull` take from the same events, in order: an event read as a line
is gone from that file's `pull`, and the other way round. Polling loses nothing: an event a poll
found waiting is the next one `pull` answers (or the next line read), so a program can wait with
`sys.poll` and then take events with `pull` until it answers nil.

## Methods

### pull() → event or nil

The oldest sound heard and waiting, as an event table (see Events), or nil when none waits. Each is
taken once.

<!-- lua-fragment -->
```lua
local event = sys.ioctl(fd, "pull")  -- {sound = "minecraft:entity.pig.ambient", forward = 1.5, right = -3.0, up = 0.0}
```

## Events

| Field | Value |
|---|---|
| `sound` | the sound's id |
| `forward`, `right`, `up` | where it came from, from its computer's middle, in its frame, in blocks, to one decimal place |
| `lost` | present when events were dropped: how many |

Each sound heard raises the sensor's interrupt, which stays raised while any wait. At most 32 wait. When more come, the oldest goes and the next event carries `lost`. Events are kept
across a save, and dropped while no program runs. A [sound card](/devices/sound-card/) starting to
play is heard once at its start.

Where is in its computer's frame, as an [energy field card](/devices/energy-field-card/)'s
positions are: `forward` out of its front, `right` as someone facing the front sees it (as every
[face](/concepts/faces/) is named), and `up`; in a [Bot Golem](/hardware/bot-golem/), its own
heading and its own right. So a program written for one computer works however it's turned.

## Examples

Run these with `lua name.lua &`, from a computer with an audio sensor. For why they loop without
yielding, see [Writing programs](/mcos/programs/).

### Count the first five sounds

```lua
local f = io.open("/dev/hearing0")
for i = 1, 5 do
  print(i, f:read("l"))  -- waits for each sound
end
```

### Log a base's doors and chests

```lua
{% include audio-log.lua %}
```
[Download audio-log.lua](/examples/audio-log.lua)

The patterns are Lua's: `%.` is a dot. `tail -f /home/sounds.log` follows the log.

### Point toward the last sound

```lua
{% include audio-pointer.lua %}
```
[Download audio-pointer.lua](/examples/audio-pointer.lua)

It needs a [redstone card](/devices/redstone-card/) in the same computer. The directions are the
world's, so the faces it lights are too, however the computer is turned.
