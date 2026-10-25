---
title: Robotics device
section: Devices
order: 155
---

A robot's robotics device is its body's motors: soldered onto a [Bot Golem](/hardware/bot-golem/)'s
board, it's the only way the golem moves and acts. It walks to a point relative to the robot, turns
it, and stops it; it senses the six spaces round it; and it works with what's in the robot's hand:
uses it on a space (places a block, opens a door, presses a button), breaks blocks and hits mobs
with it, and moves items in and out of a container. It says how each went.

The robot knows nothing of where it is, not even which way is north: every face is named in its
own frame, `front`, `back`, `left`, `right`, `up` and `down`, its left and right its own (as it
turns and walks), and a block's direction it reads (a door's `facing`) is in that frame too.

<div class="infobox" markdown="1">

### Facts
| | |
|---|---|
| Type | `robotics` |
| Where | Soldered onto a bot golem's last expansion lane (`exp:3`) |
| `/dev` | `robotics0` |
| Power | A running CPU's tick for each block walked, each turn, and each action with its hand |

</div>

## In mcos

`/dev/robotics0` reads as what it's doing now, a word: `idle`, `walking`, `turning` or
`breaking`. A line written is an action, taking effect as it's written:

| Line | Does |
|---|---|
| `walk forward [right [up]]` | Walks to the point that many blocks from it, in its own frame |
| `turn left`, `turn right`, `turn around`, `turn degrees` | Turns in place (degrees: to its right; negative, its left) |
| `use face` | Uses what it holds on the space at a face |
| `swing face` | Swings what it holds at a face: hits a mob, or breaks the block |
| `stop` | Stops, where it is |

A line that isn't one fails the write with `EINVAL`. How an action went comes as an event: the
file is ready for `sys.poll`'s `PRI` while one waits, and the `pull` ioctl takes it. The file's
ioctls are the device's methods, by name, in any open mode: `sys.ioctl(fd, "walk", 3)`.

```sh
echo "walk 3" > /dev/robotics0
cat /dev/robotics0
echo "swing front" > /dev/robotics0
```

Its scan, its hand and containers are ioctls: `sys.ioctl(fd, "scan")`, `sys.ioctl(fd, "take", "front")`.

## Its hand

The golem holds one item in its hand, in the hand slot of [its view](/hardware/bot-golem/) (right
of its board, under its buttons), and has an off hand under it. What it does with it is what a player does: the game's own interactions,
through a player standing where it is and looking at the space. A block placed, a door opened, a
bucket filled, a lever flipped, wool sheared from a sheep: each as a player's right click. Breaking
a block takes a player's time with what it holds (a pickaxe on stone, an axe on wood; the wrong tool
is slow and may drop nothing), and the tool wears. A hit on a mob does the weapon's damage, as
strong as a player's swing at that pace: full strength once the weapon has recovered.

What a broken block drops is caught: into the robot's storage (a [crafting card](/devices/crafting-card/)'s)
first, else its off hand catches it, in the background (when it's empty, or holds the same). What it
can't catch stays in the world, and the `broke` event says so (`kept`, `dropped`), so a program knows
from the one event. Anything the hand is given besides (a filled bucket from a stack of empty ones)
is caught the same way. `swap` puts what the off hand caught in its hand, to store or use.

On its legs it presses pressure plates and trips tripwires, as anything walking does.

## Methods

### walk(forward[, right[, up]]) → true

Walks to the point `forward` blocks the way it faces, `right` to its right (negative: its left) and
`up` above, by the way it finds. It answers at once; its event says how it went: `arrived`, or
`blocked` and why (`no way there`). A walk or turn under way is given up. It fails with
`too far: at most 64 blocks each way`.

### turn(way) → true

Turns in place: `"left"`, `"right"`, `"around"`, or a number of degrees (to its right; negative, its
left). Its event: `turned`. It fails with `not a way to turn` for any other word.

### stop() → true

Stops walking or turning, where it is. Its event, when it was doing something: `stopped`.

### scan([face]) → space, or {face = space, ...}

What's in the space at a face, or with none, in all six at once, by face. Each space is a table:

| `kind` | Other fields |
|---|---|
| `air` | |
| `block` | `name` (`minecraft:oak_log`), `state` (its properties: `{axis = "y"}`, a door's `{facing = "back", open = false, ...}`, directions in its frame), `hardness` (-1: unbreakable), `solid` (it can't walk through it), `container` (its slots, when it's one), `fluid` (when waterlogged) |
| `fluid` | `name` (`minecraft:water`), `amount` (1 to 8), `source` |
| `entity` | `name` (the first's: `minecraft:pig`; an item's, its item's), `entities` (every one's name), `block` (the block there) |

### held([hand]) → item or nil

What its hand holds (`"off"`: its off hand): `{name, count, damage, maxDamage, label}` (`damage`
and `maxDamage` for what wears; `label` when it's named), or nil.

### swap() → true

Swaps what its hands hold, as a player's swap key: its main hand works, its off hand catches.

### use(face) → what, or nil and why

Uses what it holds on the space at a face, as a player's right click. A mob there is used on first
(shears on a sheep); then a block there is clicked on its face toward the robot (a door, a button,
a lever, a log stripped with an axe); an empty space gets what it holds placed into it, against a
neighbouring block, or with nothing to place against, what it holds is used the way it looks (a
bucket into water). Answers `"placed"` or `"used"`; fails with `nothing happened`, `nothing to
place against` or `nothing there`.

### swing(face) → what, or nil and why

Swings what it holds at a face, as a player's left click. A mob there is hit: `"attacked"`. Else
the block there starts breaking: `"breaking"`, and its event says how it went: `broke` (with the
block's name, and `kept`: true when it caught all the block dropped, else false and how many it
`dropped` into the world), or `failed` and why (`the block changed`, `what it held changed`). A walk or turn
under way is given up. It fails with `nothing there` or `unbreakable`.

### contents([face]) → list, or nil and why

What the container at a face (in front, when none is given) holds (a chest, a barrel, a furnace, a chest minecart): a list of its
slots with something in, each `{slot, name, count, ...}` (slots from 1). It fails with
`no container there`.

### store([face][, count[, to]]) → true, 0 or false, remaining; or nil and why

Puts what it holds (or `count` of it) into the container at a face (in front, when none is
given), into its slot `to` or where it fits, as a hopper
would (a furnace takes fuel from its side, what it smelts from above). One answer says it all: `true,
0` when none of it is left in its hand (an empty hand too), else `false` and how many are left (the
container is full), so a program never has to look again to know. It fails (nil and why) with `no
container there`.

### take([face][, slot[, count]]) → number, or nil and why

Takes items from the container at a face (in front, when none is given) into its hand: from `slot`, or the first it can take (the
same as it holds, when it holds something); all its hand can hold, or `count`. How many came: 0
when there were none it could take. It fails with `no container there` or `no slot`.

### get() → {state}

What it's doing now: `idle`, `walking`, `turning` or `breaking`.

### set(action) → true

An action as a table: `{action = "walk", forward, right, up}`, `{action = "turn", way}`,
`{action = "use", face}`, `{action = "swing", face}`, or `{action = "stop"}` (the file's lines are
these).

### pull() → event or nil

Its oldest event, or nil.

## Events

| `event` | Other fields | When |
|---|---|---|
| `arrived` | | a walk got there |
| `blocked` | `why` | a walk can't go on: `no way there`; `interrupted` when its world was saved during it |
| `turned` | | a turn is done |
| `stopped` | | `stop` ended a walk, turn or swing |
| `broke` | `block`, `kept`, `dropped` | a swing broke the block (its name); whether it caught all it dropped, and how many it didn't |
| `failed` | `why` | a swing couldn't go on |

Its interrupt is raised when an event comes, and stays raised while one waits. With no program
running on its board it takes no data: its events go, and it stops.

## Examples

Walk to a point and say how it went, through the device's file (a write is the action):

```lua
{% include robotics-goto.lua %}
```

Walk round a square with the device's methods, waiting for each move:

```lua
{% include robotics-square.lua %}
```

Look round, from one scan:

```lua
{% include robotics-look.lua %}
```

Dig a tunnel with the pickaxe it holds, breaking and walking in:

```lua
{% include robotics-dig.lua %}
```
