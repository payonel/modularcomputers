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

## One command at a time

The device does one thing at a time, as a real robot's controller runs one motion: its methods are
**commands**, and a command given while another runs waits for it. In mcos only the thread that gave
it waits (a blocking write's way); the program's other threads and processes go on. So a program
gives its moves in order and they happen in order, and more work at once means
[threads](/mcos/threads/): one walks the robot while another watches a sensor.

A command answers as soon as it has started: `true` (a read answers what it read). One that runs
over time (a walk, a flight, a turn, a landing, a block breaking) ends in an event named after it,
`{event = "walk", ok = true}`, or `ok = false` and why (`blocked: minecraft:stone front`), and
[`devices.ended`](/mcos/devices-library/)
waits for it. A failure ends that command only: the next one runs as any would.

Four methods never wait: `get` (what it's doing now, and where it really is), `pull` (its events),
`stop` (it stops at once), and `set` (it does an action as its method would, waiting as it would,
but a stop at once). Under the board's own calls, a command while another runs is refused at once
with `busy`, doing nothing (mcos turns that into the wait); while a [crafting
card](/devices/crafting-card/)'s craft keeps it busy, the refusal names that card too
(`false, "busy", "id:..."`), as its end is the one to wait for.

## Its plan

Every command runs from its **plan**: where its last command left it, on a block's middle, facing a
right angle. `walk(1)` twice is two blocks from where the first began, whatever happened between.
If something knocks it off its plan (a shove, in the air), it **drifts**: it says so (`shoved`, with
how far), and the next command that depends on where it is (a move, a turn, a scan, its hand's
actions) first takes it back to its plan (`returning`), then runs. Hovering on its thrusters it
holds its plan as a drone does: shoved, it flies back by itself. If it can't get back (no path), it
says so once (`return`, not `ok`, and why), holds where it is, and a positional command given then
fails with why. `position` gives its plan; `get` gives where it really is, and its drift.

A fall it didn't ask for, on its legs (a block gone from under it, a trapdoor it opened), isn't a
drift: while it falls it's busy (`falling`), and once down its plan follows it down, and it says so
(`fell`, and how far), so a robot digging down goes on from where it landed.

`stop` stops it at once, where it is: no centering, no squaring up. The command running ends
`stopped` (a crafting card's craft too), and every thread waiting to give it a command is told so
(`ECANCELED`): a stop stops the plan a program's threads were feeding it. Its plan becomes where it
stopped: in the air the point it's at (it hovers there, holding it), on its legs the block it's
nearest, so the next positional command steps onto that block's middle first, never back to where
the stopped command began. A command cut short by a world's save and load (`interrupted`) plans the
same way.

## Held in a field

Held in an [energy field](/devices/energy-field-card/), any field, its own too, a robot is in
stasis, as any creature is: its robotics freezes. What it was doing pauses (a block it was breaking
is left: no tools in a field), new commands wait (`held`, under the board's calls) until the field
lets it go and it's down, and `stop` still works. It says so: `frozen`, then `thawed`.

The field's carry is no move of its own, so its odometry never sees it: when it's down again, its
origin has moved with it, and `position` reads as before. Like a robot picked up and set down
elsewhere, it has to be told where it is (`setPosition`, from a fix) if that matters. Its own field
is how a tier 1 golem gets up a cliff: [the tree job](/hardware/bot-golem/) is carried up one, feeling its way
with the field's moves (it can't look while it's held), and set down on the ledge.

## In mcos

`/dev/robotics0` reads as what it's doing now, a word (`idle`, `walking`, `turning`, `breaking`,
`flying`, `hovering`, `landing`, `returning`, `crafting`, `falling` or `held`), then where it really is, a
line: `position forward right up heading`, its plan, `planned forward right up heading`, and when
it's off its plan, `drift forward right up`. A line written is an action, as a command (a line
given while one runs waits for it, the writing thread only; a `stop` never waits):

| Line | Does |
|---|---|
| `walk forward [right [up]]` | Walks whole blocks from its plan, in its own frame |
| `walk` | Walks back onto its plan |
| `walkto forward [right [up]]` | Walks to whole blocks from its origin, in the origin's frame |
| `fly forward [right [up]]` | Flies on its thrusters that far from its plan, its legs left standing |
| `fly` | Takes off where it stands and holds there; in the air, back to its plan |
| `flyto forward [right [up]]` | Flies to a point from its origin |
| `land` | Comes down onto its legs, below it |
| `turn left`, `turn right`, `turn around` | Turns a right angle in place (or two) |
| `use face` | Uses what it holds on the space at a face |
| `swing face` | Swings what it holds at a face: hits a mob, or breaks the block |
| `stop` | Stops at once, where it is |
| `position [forward [right [up [heading]]]]` | Says where it is, its origin moved to make it so (none: its origin here) |
| `pathfinding on`, `pathfinding off` | Whether its moves find their way round things |

A line that isn't one fails the write with `EINVAL`. How an action went comes as an event: the
file is ready for `sys.poll`'s `PRI` while one waits, and the `pull` ioctl takes it. Each file open
on the device gets every event (a script and a `cat` each see them all). The file's ioctls are the
device's methods, by name, in any open mode: `sys.ioctl(fd, "walk", 3)`.

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
can't catch stays in the world, and the swing's event says so (its `result`: `kept`, `dropped`), so
a program knows from the one event. Anything the hand is given besides (a filled bucket from a stack
of empty ones) is caught the same way. `swap` puts what the off hand caught in its hand, to store or
use.

It uses its tools only standing on its legs: in the air (on its thrusters) it has no footing, and
in a field none at all. On its legs it presses pressure plates and trips tripwires, as anything
walking does.

## Methods

### walk(forward[, right[, up]]) → true

Walks to the block `forward` whole blocks the way it faces, `right` to its right (negative: its
left), its elevation changing by `up`, from its plan, onto the block's middle, exactly, facing as it
was. How it gets there is below ([Walking](#walking)). With no arguments it walks back onto its
plan. A command; its event, `walk`: `ok`, or not and why, on the middle of the last block it
reached (its plan then). It fails with `not whole`, `too far: 64` (blocks each way at most), or
`no legs`.

### walkTo(forward[, right[, up]]) → true

Walks, as `walk` does, to the block that many whole blocks from its origin, in the origin's
frame: the numbers `position` gives. `walkTo(0, 0, 0)` goes home. Its event: `walkTo`.

### turn(way) → true

Turns a right angle in place: `"left"` or `"right"`, or two, `"around"`, from its plan's heading.
Its event: `turn`. It fails with `not a way: ` and what it was given: its heading is always a right
angle from its origin's.

### stop() → {forward, right, up, heading}

Stops what it's doing at once, where it is, with no centering (flying, it hovers there; a block it
was breaking is left as it is; a crafting card's craft ends too): the command running ends,
`stopped`, and in mcos so does every thread's wait to give it a command (`ECANCELED`). Never waits.
Answers where it really stopped, from its origin. Its plan becomes where it stopped (on its legs, the
block it's nearest: its next positional command steps onto its middle first).

### position() → {forward, right, up, heading}

Its plan, from its origin, in the origin's frame: blocks forward, to the right and up, and its
heading in degrees to the origin's right (0, 90, 180 or 270). A command: it waits behind the one
running, so it gives where that one left it. Where it really is, a shove included, is `get`'s. See
[where it is](#where-it-is-its-position).

### setPosition([forward[, right[, up[, heading]]]]) → true

Says where it is now: whole blocks forward, right and up from its origin, in the origin's frame,
facing `heading` degrees to the origin's right (0, 90, 180 or 270). Its origin, in this dimension,
moves to make it so, and `position` then gives exactly these numbers. Each is 0 when not given, so
`setPosition()` makes where it stands the origin. It moves onto its block's middle and turns to the
nearest right angle first. From a place you know in another frame (a map's, a GPS's fix), it gives
`position` that frame: `setPosition(12, -3, 65, 90)` says "I'm 12 forward, 3 left and 65 up from
the origin, facing its right". Fails with `not whole` or `not a right angle: ` and the heading.

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
place against` or `nothing there`. In the air a tool in its hand works as an empty hand (buttons,
doors and levers yes).

### swing(face) → what, or nil and why

Swings what it holds at a face, as a player's left click, standing on its legs. A mob there is hit:
`"attacked"`. Else the block there starts breaking: `"breaking"`, a command running till it's
broken. Its event, `swing`: `ok`, and its `result`, `{block, kept, dropped}` (the block's name;
`kept`: true when it caught all the block dropped, else false and how many it `dropped` into the
world), or not and why (`block changed`, `hand changed`, `not broken`; `held` when a field took hold
of it). It fails with `nothing there`, `unbreakable` or `no legs` (on its thrusters: no footing).

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
when there were none it could take. It fails with `no container there` or `no slot: ` and the slot.

### fly(forward[, right[, up]]) → true

A tier 2 golem's: its thrusters' calls (`fly`, `flyTo`, `land`) fail on a tier 1 with `no
thrusters` (it walks; its own [energy field](/devices/energy-field-card/) can still carry it).

Flies on its thrusters to the point that many blocks from its plan, in its own frame, straight there
and never through blocks, at 25 blocks a second (a field carries it at 10; a player sprints at under
6). If it stands on its legs, they're left standing where it was: the thrusters fire red beams from
their sockets. With no arguments, on its legs, it takes off where it stands and holds there; in
the air, it flies back onto its plan. On its thrusters it walks nowhere (`no legs`), swings nothing
(`no legs`), presses no pressure plates, and can be shoved (it flies back to its plan, saying
`shoved`); it still uses what's round it (buttons, doors, levers: a tool in its hand works as an
empty hand), places what it holds, and its other cards work. Its event, `fly`: `ok` (then it holds
there), or not and why (`blocked: minecraft:stone up`). Its thrusters cost five running CPUs' power a
tick. Fails with `too far: 64`.

If its program ends while its thrusters hold it up, or its power runs out, it falls and powers off:
its torso lies on the ground, off, to be picked up as a Tier 2 Bot Golem Torso.

### flyTo(forward[, right[, up]]) → true

Flies, as `fly` does, to the point that many blocks from its origin, in the origin's frame. Its
event: `flyTo`.

### land() → true

Comes down onto its legs, standing below it (within three blocks to its side), and onto them. Its
event, `land`: `ok`, or not and why (`blocked: ...`, `no legs below` when they've gone: it hovers
there). Fails with `no legs below` (it goes on hovering) or `on legs`. A right click pops legs up
into an inventory, its own `use` too.

### get() → {state, legs, pathfinding, position, planned, drift}

Live, never waiting: what it's doing now (`idle`, `walking`, `turning`, `breaking`, `flying`,
`hovering`, `landing`, `returning`, `crafting`, `falling` or `held`), whether it stands on its legs, whether it
finds its way round things, where it really is and its plan (each as `position` gives it), and how
far it's off its plan (`{forward, right, up}`; none when it's on it). It answers while a field
holds it too: a program can follow the carry.

### set(action) → true

An action as a table: `{action = "walk"}` (or `"walkTo"`, `"fly"`, `"flyTo"`, with `forward`,
`right`, `up`), `{action = "turn", way}`, `{action = "use", face}`, `{action = "swing", face}`,
`{action = "land"}`, `{action = "stop"}`, `{action = "pathfinding", on}`, or
`{action = "setPosition", forward, right, up, heading}` (the file's lines are these). It does what
the method does, waiting as it would; a stop never waits. Fails with `not a table` or
`not an action: ` and what it was.

### pull() → event or nil

Its oldest event, or nil.

## Where it is: its position

A robot always knows exactly how far it is from its origin, and which way it faces from the
origin's way, as a real robot's odometry does: what it does itself (walking, flying, turning) moves
its position. Not where it is in the world: no compass, no coordinates.

- **Its origin** is where it stood, on its block's middle, and the way it faced, a right angle. It's
  set the first time the golem runs (powered on, or set down), by `setPosition`, and in each
  dimension it first comes to: each dimension keeps its own, so back in the overworld it has its old
  origin again.
- **Its plan** is where its last command left it; `position` gives it. A shove moves it off its plan
  for a while (`get`'s `drift`), never the plan itself.
- **It lasts**: through reboots, power cuts and shoves. A field's carry takes its origin with it
  ([held in a field](#held-in-a-field)). It's lost when the golem is picked up (its item keeps no
  position): set down again, it has a new origin there.
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

It ends at exactly the elevation `up` asked, or its walk ends not `ok`, and why: `too high: ` (a
step up it wasn't to make, or more than a jump: how many blocks), `too low: ` (a drop it wasn't to
make, or more than 3: how many), `too low` (no floor within its drop), `blocked: ` and the block in
the way and its face (a wall, a ceiling, lava, fire, deep water: `blocked: minecraft:lava front`),
or `height: ` and how far from the height asked it ended.

| From | To | `up = 0` | `up = 1` | `up = -3` |
|---|---|---|---|---|
| ground | a slab | walks | walks | walks |
| a slab | the full block beside it | `too high: 1` | jumps | `too high: 1` |
| ground | a block up | `too high: 1` | jumps | `too high: 1` |
| ground | the flat beyond | walks; ends `height: -1` if `up` wasn't met | | |
| ground | 3 down | `too low: 3` | `too low: 3` | drops |
| ground | 4 down | `too low` | `too low` | `too low` |

**With pathfinding** it finds its way, as a mob does: round walls, up stairs and slabs, jumping a
block and dropping up to 3, turning as it goes. It still ends on the asked block's middle, at the
asked elevation, facing as it began, or it's not `ok` (`no path`). A flight with pathfinding flies
round things too.

## Events

| `event` | Other fields | When |
|---|---|---|
| `walk`, `walkTo`, `fly`, `flyTo`, `turn`, `land`, `swing` | `ok`, `why`, `result` | that command ended: `ok`, or not and why (`stopped` when a `stop` cut it short; `interrupted` when its world was saved during it); a swing's `result` is `{block, kept, dropped}` |
| `return` | `ok`, `why` | its way back to its plan ended (after a shove, or before a positional command), or couldn't start (`no path`) |
| `shoved` | `drift` (`{forward, right, up}`) | something knocked it off its plan |
| `fell` | `by` (blocks) | it came down on its legs from a fall it didn't ask for: its plan followed it |
| `frozen` | | a field took hold of it: its robotics paused |
| `thawed` | | the field let go and it's down: its robotics back, its origin carried |

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

A robot's moves in a thread, while the main thread goes on with other work:

```lua
{% include threads-robot.lua %}
```

Walk round a square through the device's ioctls, each move waiting for the one before:

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
