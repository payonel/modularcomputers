---
title: Weather Sensor
section: Devices
order: 140
---

A weather sensor tells the weather where its block is, and raises an event when it changes.

<div class="infobox" markdown="1">

### Facts
| | |
|---|---|
| Type | `weather` |
| Address | An expansion slot path such as `exp:0`, or a hub's |
| Interrupt | Raised while events wait |
| Power | Nothing to speak of; it works while a running computer powers it |
| Info | `kind` (`weather_sensor`) |

</div>

The weather is one of `clear`, `rain`, `snow` or `thunder`. It is worked out for the sensor's block
from the world's weather and the biome there: a block in a desert reads `clear` even
while it rains elsewhere, and a cold biome reads `snow`. The sensor does not need to see the sky.

## In mcos

`/dev/weather0` (`weather1` for the next) is a read-only file, an event a line: the new weather.

```
rain
```

A read with nothing waiting waits for the next change, and `poll` reports the file readable (`IN`)
while one waits. When events were dropped, a line `lost n` comes before the next one. The first
look at the weather is not a change. Opening it to write fails with `EACCES`.

```sh
cat /dev/weather0   # waits, then prints each change
```

The file's ioctls are the sensor's methods, by name, in any open mode. The weather now, without
waiting for a change, is `getWeather`:

<!-- lua-fragment -->
```lua
local now = sys.ioctl(fd, "getWeather")  -- "clear", "rain", "snow" or "thunder"
```

A sensor that is gone answers `ENODEV`. `setLabel` labels it, as for any device (see
[The sys library](/mcos/sys-api/)). The file and `pull` take from the same buffer, in order (a change
read from one is gone from the other); polling loses nothing, so a program can wait with `sys.poll`
and then `pull`.

## Methods

### getWeather() → string

The weather now: `clear`, `rain`, `snow` or `thunder`. `clear` before its first look.

### pull() → event or nil

The oldest change waiting, as an event table (see Events), or nil when none waits. Each is taken
once.

## Events

| Field | Value |
|---|---|
| `name` | `"weather"` |
| `old` | the weather before |
| `new` | the weather now |
| `lost` | present when events were dropped: how many |

Each change raises the sensor's interrupt, which stays raised while any wait. At most 32 wait. Events are kept across a save, and the weather the sensor saw last is kept too, so
a load is not a change. Events are dropped while no program runs.

## Examples

Run these with `lua name.lua &`, from a computer with a weather sensor. For why they loop without
yielding, see [Writing programs](/mcos/programs/).

### Print each change

```lua
local f = io.open("/dev/weather0")
while true do
  print("the weather is now " .. f:read("l"))  -- waits for the next change
end
```

### A weather diary

```lua
{% include weather-log.lua %}
```
[Download weather-log.lua](/examples/weather-log.lua)

### A roof that closes in the rain

```lua
{% include weather-roof.lua %}
```
[Download weather-roof.lua](/examples/weather-roof.lua)

It needs a [redstone card](/devices/redstone-card/) in the same computer, and a sticky piston on
its top face. `getWeather` gives the weather at the start, so a program started in the rain closes
the roof at once.
