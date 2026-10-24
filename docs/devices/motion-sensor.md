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

`/dev/motion0` (`motion1` for the next) is a read-only file, an event a line:

```
minecraft:pig 1.5 0.0 -3.0
```

That is `mob x y z`: the entity's type and where it is, in blocks, relative to the sensor's block
(its center). A read with nothing waiting waits for the next event, as a keyboard does, and `poll`
reports the file readable (`IN`) while one waits. When events were dropped, a line `lost n` comes
before the next one. Opening it to write fails with `EACCES`.

```sh
head -n 3 /dev/motion0
tail -f /dev/motion0
```

The file's ioctls are the sensor's methods, by name, in any open mode:
`sys.ioctl(fd, "pull")` answers the oldest event as a table, with numbers for its coordinates. A
sensor that is gone answers `ENODEV`. `setLabel` labels it, as for any device (see
[The sys library](/mcos/sys-api/)).

The file and `pull` take from the same buffer, in order: an event read from the file is gone from
`pull`, and the other way round. Polling loses nothing: an event a poll found waiting is the next
one `pull` answers (or the next line read), so a program can wait with `sys.poll` and then take
events with `pull` until it answers nil.

## Methods

### pull() → event or nil

The oldest motion waiting, as an event table (see Events), or nil when none waits. Each is taken
once.

<!-- lua-fragment -->
```lua
local event = sys.ioctl(fd, "pull")  -- {mob = "minecraft:pig", x = 1.5, y = 0.0, z = -3.0}
```

## Events

| Field | Value |
|---|---|
| `mob` | the entity's type, such as `minecraft:pig` |
| `x`, `y`, `z` | its position relative to the block's center, in blocks, to one decimal place (floats) |
| `lost` | present when events were dropped: how many |

Each event raises the sensor's interrupt, which stays raised while any wait. The sensor keeps at most
32 events. When it is full the oldest goes, and the next event says how many
were lost. Events are kept across a save, and dropped while no program runs.

The coordinates are the world's (not relative to the block's front). `x` grows east, `y` up and `z`
south.

## Examples

### Wait for something to come near

```lua
local f = io.open("/dev/motion0")
local line = f:read("l")  -- blocks until the sensor has an event
print("something moved: " .. line)
```

`read("l")` waits until the sensor has an event, then returns it as `mob x y z`. Run it with
`lua /watch.lua`.

### An alarm that never stops watching

```lua
{% include motion-alarm.lua %}
```
[Download motion-alarm.lua](/examples/motion-alarm.lua)

The `while true do` loop is the program's whole event loop: it sleeps in `read` until something
moves. It needs a [redstone card](/devices/redstone-card/) as well, for the alarm's output. See
[Writing programs](/mcos/programs/).

### Count what walks past

```lua
{% include motion-census.lua %}
```
[Download motion-census.lua](/examples/motion-census.lua)

### A light that brightens as something comes near

```lua
{% include motion-nearlight.lua %}
```
[Download motion-nearlight.lua](/examples/motion-nearlight.lua)

Put the [redstone card](/devices/redstone-card/) in the same computer, and a lamp on top of it.
`pull` gives the coordinates as numbers, so the distance is plain arithmetic.
