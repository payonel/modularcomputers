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
once a second, from the world's weather and the biome there: a block in a desert reads `clear` even
while it rains elsewhere, and a cold biome reads `snow`. The sensor does not need to see the sky.

## In mcos

`/dev/weather0` is a read-only file, an event a line: the new weather.

```
rain
```

A read with nothing waiting waits for the next change. When events were dropped, a line `lost n`
comes before the next one. The first look at the weather is not a change.

## Methods

### getWeather() → string

The weather now: `clear`, `rain`, `snow` or `thunder`. `clear` before its first look.

### pull() → event or nil

The oldest change, or nil.

## Events

| Field | Value |
|---|---|
| `name` | `"weather"` |
| `old` | the weather before |
| `new` | the weather now |
| `lost` | present when events were dropped: how many |

At most 32 wait. Events are kept across a save, and the weather the sensor saw last is kept too, so
a load is not a change. Events are dropped while no program runs.

## Example

Print each change in the weather, under mcos:

```lua
local f = io.open("/dev/weather0")
while true do
  print("the weather is now " .. f:read("l"))
end
```
