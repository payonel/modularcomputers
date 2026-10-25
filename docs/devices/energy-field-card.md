---
title: Energy field card
section: Devices
order: 158
---

An energy field card suspends a creature or an item in a sphere of translucent blue energy. What it
holds falls in place and can't act: it doesn't move, attack or react (a creeper doesn't blow, a
player can't hit or use anything). The field moves, and what it holds with it: lift a friend to a
higher floor, or hold a mob out of the way. It never moves blocks. In a [Bot Golem](/hardware/bot-golem/)
holding itself, it's how the golem flies.

<div class="infobox" markdown="1">

### Facts
| | |
|---|---|
| Type | `field` |
| Where | Any expansion slot of a computer, an expansion hub, or a golem |
| `/dev` | `field0` (`field1` the next) |
| Reach | 16 blocks from its computer (a robot holding itself carries it along) |
| Speed | 10 blocks a second |
| Power | Two running CPUs' a tick while it holds something, and a CPU's tick a block moved |
| Recipe | Ender pearl, chip, amethyst shard over three copper nuggets over three gold nuggets |

</div>

![A pig held up in an energy field: a translucent blue sphere round it](/assets/img/energy-field.png)
*A pig held in a field, two blocks up.*

## Where things are

Positions are in its computer's frame: `forward` out of its front, `right`, and `up`. On a block,
right is as someone facing its front sees it (as every face is named); a robot's is its own. So a
program written for one computer works however the computer is turned.

The field holds one thing at a time. It lets go when told to, when what it holds is gone (it dies,
it's picked up), when it's moved out of reach, when its computer has no power or stops its
program, and when its world is saved and loaded (a field isn't saved). What it held then falls.
A move that meets a block stops there: its event says it was blocked.

## In mcos

Reading `/dev/field0` gives what it's doing, and what it holds and where:

```
holding minecraft:pig 2.0 0.0 3.0
idle
```

A line written is an action:

| Line | Does |
|---|---|
| `capture self` | Holds its own robot |
| `capture ID` | Holds what has that id (from `scan`) |
| `capture forward [right [up]]` | Holds what's nearest that point, within a block and a half |
| `move forward [right [up]]` | Moves the field by that much |
| `release` | Lets go |

```sh
echo "capture 2" > /dev/field0
echo "move 0 0 4" > /dev/field0
echo release > /dev/field0
```

Its ioctls are its methods, by name. Its events come as `sys.poll`'s `PRI`, and `pull` takes them.
`cat /sys/field0` lists its methods.

## Methods

### scan([range]) → list

What's within `range` (16 at most) of its computer, nearest first: creatures (players too) and items,
each `{id, name, forward, right, up, distance}`.

### capture(target) → name, or nil and why

Holds a creature or an item: an `id` from `scan`, `"self"` (a robot holding itself), or a point
`forward[, right[, up]]` (the nearest thing within a block and a half of it). Answers its name.
Fails with `already holding something`, `nothing there`, `nothing with that id here`, `too far`,
`another field holds it`, or in a block, `no robot to hold`.

### move(forward[, right[, up]]) → true, or nil and why

Moves the field by that much, at 10 blocks a second, never through blocks. It answers at once; its
event says how it went: `arrived`, or `blocked`. Fails with `holding nothing`, or `too far` (out of
its reach, unless it holds its own robot).

### release() → true

Lets what it holds go: it falls from where it is.

### get() → {state, name, forward, right, up}

What it's doing (`idle`, `holding` or `moving`), and what it holds and where.

### set(action) → true

An action as a table: `{action = "capture", target}`, `{action = "move", forward, right, up}`,
`{action = "release"}` (the file's lines are these).

### pull() → event or nil

Its oldest event, or nil.

## Events

| `event` | Other fields | When |
|---|---|---|
| `arrived` | | a move got there |
| `blocked` | `why` | a move met a block |
| `lost` | `why` | it let go on its own: `gone`, `out of range`, `no power`, `interrupted` (its world saved and loaded) |

## Example

Lift the nearest player a floor or two, and set them down on the edge above:

```lua
{% include field-lift.lua %}
```
