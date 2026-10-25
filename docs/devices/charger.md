---
title: Charging pad
section: Devices
order: 160
---

A [charging pad](/hardware/charging-pad/) on the bus says what it's doing, and when a robot comes
onto it and leaves.

<div class="infobox" markdown="1">

### Facts
| | |
|---|---|
| Type | `charger` |
| Where | Cabled, or right against a computer |
| `/dev` | `charger0` |
| Power | What it gives a robot, while a running computer supplies it |

</div>

## In mcos

`/dev/charger0` is read only: a line saying what it's doing, the robot on it (its board's id, or
`-`), and its buffer:

```
charging 4f0c2a9e-... 1200/26400
idle - 26400/26400
```

The state is taken at the first read after opening or seeking to the start. Opening it to write
fails with `EACCES`. Its events (a robot docked or undocked) make the file ready for `sys.poll`'s
`PRI`, and the `pull` ioctl takes them. The file's ioctls are the device's methods, by name, in any
open mode.

## Methods

### get() → {charging, robot, buffer, capacity}

Whether it's charging a robot now, the robot's board id (nil: none on it), and its buffer's level
and capacity, in power units.

### pull() → event or nil

Its oldest event, or nil.

## Events

| `event` | Other fields | When |
|---|---|---|
| `docked` | `robot` (its board's id) | a robot stood on it |
| `undocked` | `robot` | it left |

Its interrupt is raised when an event comes, and stays raised while one waits. With no program
running on the board that has it, it takes no data: its events go.

## Example

Log every robot that comes to the pad and leaves it:

```lua
{% include charger-log.lua %}
```
