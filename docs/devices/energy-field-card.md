---
title: Energy field card
section: Devices
order: 158
---

An energy field card suspends a creature or an item in a sphere of translucent blue energy. What it
holds falls in place and can't act: it doesn't move, attack or react (a creeper doesn't blow, a
player can't hit or use anything). The field moves, and what it holds with it: lift a friend to a
higher floor, or hold a mob out of the way. It never moves blocks. It hums up as it takes hold,
hums softly round what it holds, and powers down as it lets go. A [Bot Golem](/hardware/bot-golem/)
held, even by its own field, is in stasis too: its [robotics freezes](/devices/robotics/#held-in-a-field)
until it's let go and down, and its origin is carried with it. Holding itself, it's how a golem
goes up with its legs on (a tier 1's way up a cliff); its thrusters fly it too, but with its legs
off.

<div class="infobox" markdown="1">

### Facts
| | |
|---|---|
| Type | `field` |
| Where | Any expansion slot of a computer, an expansion hub, or a golem |
| `/dev` | `field0` (`field1` the next) |
| Reach | 16 blocks from its computer (a robot holding itself carries it along) |
| Power | Two running CPUs' a tick while it holds something, and a CPU's tick a block moved |
| Recipe | Ender pearl, chip, amethyst shard over three copper nuggets over three gold nuggets |

</div>

![A pig held up in an energy field: a translucent blue sphere round it](/assets/img/energy-field.png)
*A pig held in a field, two blocks up.*

## Where things are

Positions are in its computer's frame: `forward` out of its front, `right`, and `up`. On a block,
right is its own (as every face is named), as a robot's is. So a
program written for one computer works however the computer is turned.

The field holds one thing at a time. It lets go when told to, when what it holds is gone (it dies,
it's picked up), when it's moved out of reach, or when its computer has no power or stops its
program. What it held then falls.

A field outlasts its chunk: saved and loaded with the world, or unloaded and loaded again, it holds
on, and a move under way goes on. Loaded before what it holds, it takes hold of it as soon as that
loads too. Meanwhile its card still holds; a move it's given then ends `blocked` (with `name = ""`:
no one block to name, and the face it was to move toward). Its card's `release` always works, its
field loaded or not: loaded later, the field lets go. While its card's chunk isn't loaded, the field
holds what it holds where it is, a move waiting, till its card's back.
A move that meets a block stops there: its event says what was in the way.

The card is a [buffered device](/mcos/devices-library/#buffered-devices): it does one command at a
time. A `capture`, `move` or `release` given while a move runs waits (in mcos, its thread only) until
the move ends, and so does a line written doing one. `scan`, `get`, `pull` and `stop` never wait:
a `stop` ends a move under way where it is, and the field holds on. To let go at once, `stop`, then
`release`.

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
| `release` | Lets go (after a move under way) |
| `stop` | Ends a move under way where it is, holding on |

```sh
echo "capture 2" > /dev/field0
echo "move 0 0 4" > /dev/field0
echo stop > /dev/field0
echo release > /dev/field0
```

Its ioctls are its methods, by name. Its events come as `sys.poll`'s `PRI`, and `pull` takes them.
`ls /sys/class/field/field0/methods` lists its methods.

## Methods

### scan([range]) → list

What's within `range` (16 at most) of its computer, nearest first: creatures (players too) and items,
a list of things in the same shape as a [robot's `scan`](/devices/robotics/) gives them, each
`{kind = "entity", name, id, at}`: what it is (`name`, its registry id), its own `id` (for
`capture`), and where it is (`at`, `{forward, right, up}` in its computer's frame).

### capture(target) → name, or nil and why

Holds a creature or an item: an `id` from `scan`, `"self"` (a robot holding itself, legs and all), or a point
`forward[, right[, up]]` (the nearest thing within a block and a half of it). Answers its name.
Fails with `{why = "bad_state", of = "hold", value, expected = "empty"}` (it holds something
already, `value` its id), `{why = "not_found", kind = "entity", value}`
(nothing near the point, `value` the point; or no such creature, `value` its id), `bad_argument`
(`expected = "id"`: not an id), `{why = "limit", of = "reach", max = 16}` (out of its reach: 16 blocks), `{why = "bad_state", of = "entity",
value, expected = "free"}` (another field holds it, `value` its id), or in a block, `{why = "denied", reason = "not_a_robot", value = "self"}`.

### move(forward[, right[, up]]) → true, or nil and why

Moves the field by that much, never through blocks. It answers at once; its
event, `move`, says how it went: `ok`, or not and why (`blocked`; `stopped`; or why it let go, as
its notice says: `lost`, `blocked`, `fault`). Blocked, two fields say by what: `name`, the block
in its way, its registry id (`""` when no one block can be named: past its reach, or what it holds
not loaded), and `face`, the side of what it holds that met it, in its
computer's frame (`front`, `back`, `left`, `right`, `up` or `down`; a case's front, or a robot's
heading), the side that was hit, not the way the move was mostly going:
`{event = "move", ok = false, why = "blocked", name = "minecraft:stone", face = "up", call = {args = {0, 0, 5}, progress = {0, 0, 2}}}`:
its `call` the move's arguments as given and `{forward, right, up}` it moved, as a stopped move's
has. A move that would take what it holds past its reach (16 blocks from its computer, unless it
holds its own robot) is blocked at once, nothing moved, `name = ""` and `face` the side of what it
holds that meets the reach's edge (the way out from its computer there). Fails at once with `{why = "bad_state", of = "hold", value = "",
expected = "holding"}`.

### release() → true

Lets what it holds go: it falls from where it is. It's a command: given during a move, it waits
for the move to end. To let go at once, `stop` first.

### stop() → true

Ends a move under way at once, where the field is: its event, `move`, ends `ok = false`,
`why = "stopped"`, with its `call` (`{args, progress}`: the move's arguments as given, and how far
it moved). The field holds on to what it holds. It never waits.

### get() → {state, name, forward, right, up}

What it's doing (`idle`, `holding` or `moving`), and what it holds and where.

### sync() → boolean

Whether its queue is empty: `true` when no command of its own is under way, else `false`, its
interrupt saying when. It never waits; in mcos its ioctl (and `devices.sync`) waits until the queue
is empty, and answers `true`. How its commands went is in its events.

### pull() → event or nil

Its oldest event, or nil.

## Events

| `event` | Other fields | When |
|---|---|---|
| `move` | `ok`, `why` (and `name`, `face` when it's `blocked`; `name`, `id`, `at` when it's `lost`; `call` unless it's `ok`) | a move ended: `ok` true when it got there; else why (`blocked`, `stopped`, or why it let go of what it moved, as its notice says: `lost`, `blocked`, `fault`) |
| `notice` | `why = "lost"`, `name`, `id`, `at` | what it held is gone (it died, it was picked up, it went through a portal): what it was, its own id, where it last was in its computer's frame; a move under way ends `lost` first |
| `notice` | `why = "blocked"`, `name = ""`, `face` | what it held went past its reach, and it let go: the side of it past the edge; a move under way ends `blocked` first |
| `notice` | `why = "fault"`, `condition = "missing"`, `part = "power"`, `message` | its power ran out, and it let go; a move under way ends the same `fault` first, with its `call` |

## Example

Lift the nearest player a floor or two, and set them down on the edge above:

```lua
{% include field-lift.lua %}
```
