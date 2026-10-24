---
title: Motion Sensor
section: Devices
order: 120
---

A motion sensor reports living things that move near the block it is installed in: which kind, and
where.

<div class="infobox" markdown="1">

### Facts
| | |
|---|---|
| Type | `motion` |
| Address | An expansion slot path such as `exp:0`, or a hub's |
| Range | 8 blocks around its block |
| Interrupt | Raised while events wait |
| Power | 9 W |
| Info | `kind` (`motion_sensor`) |

</div>

It has no settings. It looks every 5 ticks (four times a second) at every living entity in a box
8 blocks around its block (players too). An entity that has moved 0.1 of a block or more in any
direction since the last look, or that it did not see last time, is an event. It works while a
running computer powers it.

## In mcos

`/dev/motion0` is a read-only file, an event a line:

```
minecraft:pig 1.5 0.0 -3.0
```

That is `mob x y z`: the entity's type and where it is, in blocks, relative to the sensor's block
(its center). A read with nothing waiting waits for the next event, as a keyboard does, and `poll`
reports the file readable (`IN`) while one waits. When events were dropped, a line `lost n` comes
before the next one.

```sh
head -n 3 /dev/motion0
tail -f /dev/motion0
```

## Methods

### pull() → event or nil

The oldest motion, or nil.

## Events

| Field | Value |
|---|---|
| `mob` | the entity's type, such as `minecraft:pig` |
| `x`, `y`, `z` | its position relative to the block's center, in blocks, to one decimal place (floats) |
| `lost` | present when events were dropped: how many |

The sensor keeps at most 32 events. When it is full the oldest goes, and the next event says how many
were lost. Events are kept across a save, and dropped while no program runs.

The coordinates are the world's (not relative to the block's front). `x` grows east, `y` up and `z`
south.

## Example

Wait for something to come near, from a Lua program under mcos:

```lua
local f = io.open("/dev/motion0")
local line = f:read("l")
print("something moved: " .. line)
```

`read("l")` waits until the sensor has an event. Run it with `lua /watch.lua`.
