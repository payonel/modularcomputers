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

The robot knows nothing of where it is in the world, not even which way is north: every face is
named in its own frame, `front`, `back`, `left`, `right`, `up` and `down`, its left and right its own
(as it turns and walks), and a block's direction it reads (a door's `facing`) is in that frame too.
But it always knows exactly how far it is from where it started, as a real robot's odometry does:
its [position](#where-it-is-its-position).

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

`/dev/robotics0` reads as what it's doing now, a word (`idle`, `walking`, `turning`, `breaking`,
`flying`, `hovering` or `landing`), then where it is, a line: `position forward right up heading`. A line
written is an action, taking effect as it's written:

| Line | Does |
|---|---|
| `walk forward [right [up]]` | Walks whole blocks from where it is, in its own frame |
| `walkto forward [right [up]]` | Walks to whole blocks from its origin, in the origin's frame |
| `fly forward [right [up]]` | Flies on its thrusters that far from where it is, its legs left standing |
| `flyto forward [right [up]]` | Flies to a point from its origin |
| `hover` | Holds itself where it is on its thrusters |
| `land` | Comes down onto its legs, below it |
| `turn left`, `turn right`, `turn around` | Turns a right angle in place (or two) |
| `use face` | Uses what it holds on the space at a face |
| `swing face` | Swings what it holds at a face: hits a mob, or breaks the block |
| `stop` | Stops, on the last block's middle it reached (flying: it hovers there) |
| `origin [forward [right [up [heading]]]]` | Sets its origin, where it is being that far from it (none: here) |
| `pathfinding on`, `pathfinding off` | Whether its moves find their way round things |

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

Walks to the block `forward` whole blocks the way it faces, `right` to its right (negative: its
left), its elevation changing by `up`, onto the block's middle, exactly, facing as it was. How it
gets there is below ([Walking](#walking)). It answers at once; its event says how it went:
`arrived`, or `blocked` and why, on the middle of the last block it reached. A walk or turn under
way is given up. It fails with `whole blocks expected` or `too far: at most 64 blocks each way`.

### walkTo(forward[, right[, up]]) → true

Walks, as `walk` does, to the block that many whole blocks from its origin, in the origin's
frame: the numbers `position` gives. `walkTo(0, 0, 0)` goes home.

### turn(way) → true

Turns a right angle in place: `"left"` or `"right"`, or two, `"around"`. Its event: `turned`. It
fails with `not a way to turn` for anything else: its heading is always a right angle from its
origin's.

### stop() → true

Stops walking, turning, flying or landing: a walk stops on the middle of the last block it reached,
facing as it was; a flight hovers where it is. Its event, when it was doing something: `stopped`.

### position() → {forward, right, up, heading}

Where it is from its origin, in the origin's frame: blocks forward, to the right and up, and its
heading in degrees to the origin's right (0, 90, 180 or 270). Exact: after a walk the numbers are
whole; a shove (on its thrusters) shows as a fraction. See [where it is](#where-it-is-its-position).

### setOrigin([forward[, right[, up[, heading]]]]) → true

Sets its origin, in this dimension, so that where it is now is that far from it: whole blocks
forward, right and up in the origin's frame, and facing `heading` degrees to the origin's right (0,
90, 180 or 270). Each is 0 when not given, so `setOrigin()` makes where it is the origin, and its
position all zeros. It moves onto its block's middle and turns to the nearest right angle first.
From a place whose position you know in another frame (a map's, a GPS's), it gives `position` that
frame: `setOrigin(12, -3, 0, 90)` says "I'm 12 forward, 3 left of its origin, facing its right".
Fails with `whole blocks expected` or `a right angle expected`.

### setPathfinding(on) → true

Whether its walks and flights find their way round things (the game's pathfinder: it turns as it
goes, jumps and drops as it finds, and turns back as it arrives) or go straight, the default. It's
kept through reboots, and off again when the golem is picked up. `get` says which.

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

### fly(forward[, right[, up]]) → true, or nil and why

A tier 2 golem's: its thrusters' calls (`fly`, `flyTo`, `hover`, `land`) fail on a tier 1 with `no
thrusters` (it walks; its own [energy field](/devices/energy-field-card/) can
still carry it).

Flies on its thrusters to the point that many blocks from it, in its own frame, straight there and
never through blocks, at 25 blocks a second (its field flies it at 10; a player sprints at under
6). If it stands on its legs, they're left standing where it was: the thrusters fire red beams from
their sockets. On its thrusters it walks nowhere (`no legs`), swings nothing (no tools: `no legs:
it can't swing without its footing`), presses no pressure plates, and can be shoved; it still uses
what's round it (buttons, doors, levers: a tool in its hand works as an empty hand), places what it
holds, and its other cards work. Its event: `arrived` (then it hovers there), or `blocked`, `something
in the way`. Its thrusters cost five running CPUs' power a tick. Fails with `held in a field: its
field moves it`, or `too far` beyond 64 blocks each way. Up to 64 blocks each way.

### hover() → true, or nil and why

Holds itself where it is on its thrusters (its legs, if it stands on them, left standing there),
until it flies or lands. A shove moves it, and it drifts to a stop. If its program ends while its
thrusters hold it up, or its power runs out, it falls and powers off: its torso lies on the ground,
off, to be picked up as a Tier 2 Bot Golem Torso.

### land() → true, or nil and why

Comes down onto its legs, standing below it (within three blocks to its side), and onto them; a
field holding it lets go. Its event: `landed`, or `blocked` and why (`something in the way`, `its
legs are gone`: it hovers there). Fails with `no legs there to land on` (it goes on hovering) or
`already on its legs`. A right click pops legs up into an inventory, its own `use` too.

### get() → {state, legs, pathfinding, position}

What it's doing now (`idle`, `walking`, `turning`, `breaking`, `flying`, `hovering` or `landing`),
whether it stands on its legs, whether it finds its way round things, and where it is.

### set(action) → true

An action as a table: `{action = "walk", forward, right, up}`, `{action = "fly", forward, right,
up}`, `{action = "turn", way}`, `{action = "use", face}`, `{action = "swing", face}`,
`{action = "hover"}`, `{action = "land"}`, or `{action = "stop"}` (the file's lines are these).

### pull() → event or nil

Its oldest event, or nil.

## Where it is: its position

A robot always knows exactly how far it is from its origin, and which way it faces from the
origin's way, as a real robot's odometry does: what moves it (walking, flying, a field carrying
it, a shove) moves its position. Not where it is in the world: no compass, no coordinates.

- **Its origin** is where it stood, on its block's middle, and the way it faced, a right angle. It's
  set the first time the golem runs (powered on, or set down), by `setOrigin`, and in each
  dimension it first comes to: each dimension keeps its own, so back in the overworld it has its old
  origin again.
- **It lasts**: through reboots, power cuts, shoves and field flight. It's lost only when the golem
  is picked up (its item keeps no position): set down again, it has a new origin there.
- **Its heading is always a right angle** from the origin's: a move begins by squaring it up, and
  ends by giving it back, so pathfinding's turns never leave it facing a new way.

So a program can always say where to go from home: `walkTo(3, 0, 5)`, or `walk` and check `position`.

## Walking

**Without pathfinding** (the default) a walk goes straight: forward, then right, block by block, onto
each block's middle, without turning (it walks backwards and sideways). At each block it compares the
floor there with the floor it stands on. Its *elevation* is the block its feet are in, so a slab or
a stair's lower half is the ground's elevation:

- **Within its elevation it steps freely**: onto a slab, along slabs, over carpet.
- **Up an elevation** only while `up` is above where it is: a jump, at most one block at a time.
- **Down** only while `up` is below: at most 3 blocks at a time, as far as a player falls unhurt.
- **Never past `up`**, and never into lava, fire or deep water.

It ends at exactly the elevation `up` asked, or it's `blocked`. The reasons: `a step up`, `a drop`,
`too high to jump`, `too far down`, `a wall`, `no room to jump`, `lava`, `fire`, `deep water`, `not at
the height asked`.

| From | To | `up = 0` | `up = 1` | `up = -3` |
|---|---|---|---|---|
| ground | a slab | walks | walks | walks |
| a slab | the full block beside it | blocked | jumps | blocked |
| ground | a block up | blocked | jumps | blocked |
| ground | the flat beyond | walks; ends `not at the height asked` if `up` wasn't met | | |
| ground | 3 down | blocked | blocked | drops |
| ground | 4 down | blocked | blocked | blocked (`too far down`) |

**With pathfinding** it finds its way, as a mob does: round walls, up stairs and slabs, jumping a
block and dropping up to 3, turning as it goes. It still ends on the asked block's middle, at the
asked elevation, facing as it began, or it's `blocked`. A flight with pathfinding flies round things
too.

## Events

| `event` | Other fields | When |
|---|---|---|
| `arrived` | | a walk or a flight got there |
| `blocked` | `why` | a walk can't go on: `no way there`; a flight or a landing: `something in the way`; `interrupted` when its world was saved during it |
| `turned` | | a turn is done |
| `stopped` | | `stop` ended a walk, turn, swing, flight or landing |
| `broke` | `block`, `kept`, `dropped` | a swing broke the block (its name); whether it caught all it dropped, and how many it didn't |
| `failed` | `why` | a swing couldn't go on |
| `landed` | | it's down on its legs again |

Its interrupt is raised when an event comes, and stays raised while one waits. With no program
running on its board it takes no data: its events go, and it stops.

## Examples

Walk to a point and say how it went, through the device's file (a write is the action):

```lua
{% include robotics-goto.lua %}
```

Fly up on its thrusters, look round, and land back on its legs:

```lua
{% include robotics-fly.lua %}
```

Walk somewhere and back home with `walkTo`, from its origin:

```lua
{% include robotics-home.lua %}
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
