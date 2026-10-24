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

`/dev/hearing0` is a read-only file, an event a line:

```
minecraft:entity.pig.ambient 1.5 0.0 -3.0
```

That is `sound x y z`. A read with nothing waiting waits for the next event. When events were
dropped, a line `lost n` comes before the next one.

```sh
tail -f /dev/hearing0
```

## Methods

### pull() → event or nil

The oldest sound heard, or nil.

## Events

| Field | Value |
|---|---|
| `sound` | the sound's id |
| `x`, `y`, `z` | where it came from, relative to the block's center, in blocks, to one decimal place |
| `lost` | present when events were dropped: how many |

At most 32 wait. When more come, the oldest goes and the next event carries `lost`. Events are kept
across a save, and dropped while no program runs. The directions are the world's. A
[sound card](/devices/sound-card/) starting to play is heard once at its start.

## Example

Count the first five sounds, under mcos:

```lua
local f = io.open("/dev/hearing0")
for i = 1, 5 do
  print(i, f:read("l"))
end
```
