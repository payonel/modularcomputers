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
`{event = "walk", ok = true}`, or `ok = false` and why
(`{event = "walk", ok = false, why = "blocked", name = "minecraft:stone", face = "front"}`).
[`devices.sync`](/mcos/devices-library/) waits until nothing is pending (before a field picks it up,
say), and its events say how each went. A failure ends that command only: the next one runs as any
would.

Four methods never wait: `get` (what it's doing now, and where it really is), `pull` (its events),
`stop` (it stops at once), and `sync` (whether its queue is empty: mcos's sync waits on it). Under the board's own calls, a command while another runs is refused at once
with `busy`, doing nothing (mcos turns that into the wait). A [crafting
card](/devices/crafting-card/)'s commands wait for the robot's moves too, but its craft is the
card's own: the robot is free while it crafts.

## Where each command goes from

Every command goes from where its last command left it, facing a right angle. `walk(1)` twice is
two blocks from where the first began, whatever happened between. Every move ends **squared**: on a
block's middle, whole blocks from its origin, a flight's height too (within a 4096th of a block).
A move that wouldn't is refused, saying where it would end: from a block's middle, `walk(4.4)`
fails with `not an integer: forward 4.4`.

What happens to it that no program asked for comes as one kind of event, a **notice**
(`{event = "notice", why = ...}` for a shove or a fall, `{event = "notice", what = ...}` for a
field's hold), so a program tells at once what it asked for (a command's end,
named after its method) from what came to it.

If something knocks it away from where its last command left it (a shove in the air, or a piston
on its legs), it **drifts**: it says so (`{event = "notice", why = "shove", drift}`, how far), once
the shove is over.
It doesn't go back by itself: it stays where it was put, and a program decides. `walk()` or
`fly()` takes it back to where its last command left it; `stop()` takes where it is as that point.
Its moves still measure from where its last command left it, never from where a shove put it, so a
series of moves ends the same however it was shoved: a flight goes straight from where it is to
its target, measured from that point, and so does a landing.

The commands that act where it is are **positional**, always: a turn, a swing, a scan and its
hand's actions act where its last command left it, never where a shove or a fall put it, and never
take it back there. Off that spot, a scan or a hand's action fails at once, `drifted`, doing
nothing; a turn or a swing starts, and its event ends at once, `drifted`, with its `drift`. On its
spot means in the same block as it (its middle's: half a block either way, up and down too),
facing the same right angle: a shove that keeps it there changes nothing a positional command does.
`position` gives where its last command left it; `get` gives where it really is, and its drift.
`setPosition()` makes where it is its new origin, there and then.

A fall it didn't ask for, on its legs (a block gone from under it, a trapdoor it opened, a piston
off a ledge), isn't a drift: it says so as it begins and as it lands, one notice each time,
`{event = "notice", why = "fall", drift, below}` (how far it is off where its last command left
it, and the block under it then, as it is), and while it falls it's
busy (`falling`), its commands waiting, but for its flights: a tier 2 catches itself on its
thrusters, its legs dropped to fall on without it, `fly()` taking it back to where its last command
left it. To catch it where it is, `stop()`, then `fly()`: the stop takes where it is as that point
(it goes on falling), and the flight holds it there. A caught fall has no landing to say: its
flight's end says it. Landed, its `drift` is the drop (`{forward = 0, right = 0, up = -5}`, five
down). A fall moves where its last command left it nowhere: to go on from where it landed, a robot
digging down calls `stop()` there.

`stop` stops it at once, where it is: no centering, no squaring up. The command running ends
`stopped`, and every thread waiting to give it a command is told so (`ECANCELED`): a stop stops
what a program's threads were feeding it. Where it is becomes where its last command left it, a
command running or not: the only thing that moves that point but a command. Its next command goes on from exactly where it stopped,
on its legs as in the air (in the air it hovers there), its heading the nearest right angle. Where
that is depends on when the stop came, so a move from there must still end squared: stopped at
forward 0.6, `walk(4.4)` reaches 5, and `walk(4)` is refused. `walkTo` and `flyTo` go to an exact
place from its origin whatever the stop did.

A command under way when its world is saved goes on after the load: given again as it was, from
where its last command left it, it ends where it would have (a break going on from as far as it
had got). Only one that can't go on (its legs gone meanwhile, say) ends
`{event = <method>, ok = false, why = "interrupted", call}`.

## Held in a field

Held in an [energy field](/devices/energy-field-card/), any field, its own too, a robot is in
stasis, as any creature is: its robotics freezes. What it was doing pauses (a block it was breaking
too: no tools in a field, and the break goes on, from as far as it got, once it's let go), a new command
that runs over time (a walk, a flight, a turn, a landing) is taken and waits in stasis, nothing
said, then goes once the field lets it go and it's down; one that answers at once (a scan, its
hand's actions, a swing) fails `held` (with the robot's id: its `thawed` notice ends the wait); and
`stop` still works, ending a command waiting. It says so: `frozen`, then `thawed` notices. Only its
robotics is held: the robot's body can't act, but its other devices go on (its computer runs, its
sensors sense, a [crafting card](/devices/crafting-card/)'s craft under way finishes); a command
whose actor is the robot waits, the crafting card's too, as the robot is what works it.

The field's carry is no move of its own, so its odometry never sees it: when it's down again, its
origin has moved with it, and `position` reads as before. Like a robot picked up and set down
elsewhere, it has to be told where it is (`setPosition`, from a fix) if that matters. Its own field
is how a tier 1 golem gets up a cliff: [the tree job](/hardware/bot-golem/) is carried up one, feeling its way
with the field's moves (it can't look while it's held), and set down on the ledge.

## In mcos

`/dev/robotics0` reads as what it's doing now, a word (`idle`, `walking`, `turning`, `breaking`,
`flying`, `hovering`, `landing`, `falling` or `held`), then where it really is, a
line: `position forward right up heading`, where its last command left it, `planned forward right up
heading`, and when it's away from there, `drift forward right up`. A line written is an action, as a command (a line
given while one runs waits for it, the writing thread only; a `stop` never waits):

| Line | Does |
|---|---|
| `walk forward [right [up]]` | Walks that far from where its last command left it, in its own frame, ending squared |
| `walk` | Walks back to where its last command left it |
| `walkto forward [right [up]]` | Walks to whole blocks from its origin, in the origin's frame |
| `fly forward [right [up]]` | Flies on its thrusters that far from where its last command left it, ending squared, its legs left standing |
| `fly` | Takes off where it stands and holds there; in the air, back to where its last command left it |
| `flyto forward [right [up]]` | Flies to whole blocks from its origin |
| `land` | Comes down onto its legs, below it |
| `land force` | With no legs there, comes straight down onto the ground without them |
| `turn left`, `turn right`, `turn around` | Turns a right angle in place (or two) |
| `turn north` (`south`, `east`, `west`) | Turns to face the world's way |
| `use face` | Uses what it holds on the space at a face |
| `swing face` | Swings what it holds at a face: hits a mob, or breaks the block |
| `stop` | Stops at once, where it is, and takes it as where its last command left it |
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
bucket filled, a lever flipped, wool sheared from a sheep: each as a player's right click. It breaks
a block as a player does with what it holds (a pickaxe for stone, an axe for wood; the wrong tool
may drop nothing), and the tool wears. A hit on a mob does the weapon's damage, as strong as a
player's swing: full strength once the weapon has recovered.

What a broken block drops is caught: into the robot's storage (a [crafting card](/devices/crafting-card/)'s)
first, else its off hand catches it, in the background (when it's empty, or holds the same). What it
can't catch stays in the world, and the swing's event says so (its `result`: `kept`, `dropped`), so
a program knows from the one event. Anything the hand is given besides (a filled bucket from a stack
of empty ones) is caught the same way. `swap` puts what the off hand caught in its hand, to store or
use.

It uses its tools on its legs and in the air: on its thrusters it works as a player off the ground
does (the game's rules for that: a block breaks more slowly). In a field it uses none at all. On its
legs it presses pressure plates and trips tripwires, as anything walking does.

## Methods

### walk(forward[, right[, up]]) → true

Walks `forward` the way it faces, `right` to its right (negative: its left), its elevation changing
by `up` whole blocks, from where its last command left it, facing as it was. It must end squared,
on a block's middle: from one, whole numbers do; after a stop between blocks, the rest of the way
does. How it gets there is below ([Walking](#walking)). With no arguments it walks back to where
its last command left it. A command; its event, `walk`: `ok`, or not and why, on the middle of the
last block it reached. It fails with `not an integer: ` and where it would end (`forward 4.4`),
`too far: 64` (blocks each way at most), or `no legs`.

### walkTo(forward[, right[, up]]) → true

Walks, as `walk` does, to the block that many whole blocks from its origin, in the origin's
frame: the numbers `position` gives. `walkTo(0, 0, 0)` goes home. Its event: `walkTo`. Fails with
`not an integer: ` and the number (`forward 2.5`).

### turn(way) → true

Turns a right angle in place: `"left"` or `"right"`, or two, `"around"`, from the heading its last
command left it; or to face the world's way, `"north"`, `"south"`, `"east"` or `"west"` (facing it
already, it doesn't turn, and its event comes all the same).
Its event: `turn`. It fails with `not a way: ` and what it was given: its heading is always a right
angle from its origin's.

### stop() → {forward, right, up, heading}

Stops what it's doing at once, where it is, with no centering (flying, it hovers there; a block it
was breaking is left as it is): the command running ends, `stopped` (see [Stopped](#stopped)), and
in mcos so does every thread's wait to give it a command (`ECANCELED`). With nothing running, no
event. A fall isn't something it's doing, so `stop()` lets a fall go on. Where it is becomes where
its last command left it, either way: its next command goes on from exactly there (its heading the
nearest right angle), no squaring first; falling, a `fly()` then catches it there. Never waits.
Answers where it really stopped, from its origin.

### position() → {forward, right, up, heading}

Where its last command left it, from its origin, in the origin's frame: blocks forward, to the
right and up, and its heading in degrees to the origin's right (0, 90, 180 or 270). A command: it waits behind the one
running, so it gives where that one left it. Where it really is, a shove included, is `get`'s. See
[where it is](#where-it-is-its-position).

### setPosition([forward[, right[, up[, heading]]]]) → true

Says where it is now: whole blocks forward, right and up from its origin, in the origin's frame,
facing `heading` degrees to the origin's right (0, 90, 180 or 270). Its origin, in this dimension,
moves to make it so, and `position` then gives exactly these numbers. Each is 0 when not given, so
`setPosition()` makes where it stands the origin. It moves onto its block's middle and turns to the
nearest right angle first. From a place you know in another frame (a map's, a GPS's fix), it gives
`position` that frame: `setPosition(12, -3, 65, 90)` says "I'm 12 forward, 3 left and 65 up from
the origin, facing its right". Fails with `not an integer: ` and the number, or `not a right
angle: ` and the heading.

### setPathfinding(on) → true

Whether its walks and flights find their way round things (the game's pathfinder: it turns as it
goes, jumps and drops as it finds, and turns back as it arrives) or go straight, the default. It's
kept through reboots, and off again when the golem is picked up. `get` says which.

### scan([face]) → things, or {face = things, ...}

Every thing in the space at a face, a list, or with none, the six spaces at once by face, a list
each, the same shape as the [energy field card](/devices/energy-field-card/)'s `scan` gives. The
space's creatures and items come first, then its own block, always one (air is one too). Every
thing has a `kind`, its `name` (what it is: its registry id), its `id` (its own: `""` for a block,
which has none) and `at` (where it is, `{forward, right, up}` in its own frame), and its kind's own
fields:

| `kind` | Its own fields |
|---|---|
| `entity` | none more: `name` is a creature's (`minecraft:pig`) or an item's (its item's) |
| `block` | `state` (its properties: `{axis = "y"}`, a door's `{facing = "back", open = false, ...}`, directions in its frame), `hardness` (-1: unbreakable), `solid` (it can't walk through it), `container` (its slots, when it's one), `fluid` (when waterlogged) |
| `fluid` | `amount` (1 to 8), `source` (`name` the fluid's: `minecraft:water`) |
| `air` | none more (`name = "minecraft:air"`) |

```lua
local things = {
  {kind = "entity", name = "minecraft:bat", id = "5f0c…", at = {forward = 1, right = 0.2, up = 0.4}},
  {kind = "air", name = "minecraft:air", id = "", at = {forward = 1, right = 0, up = 0}},
}
```

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

Swings what it holds at a face, as a player's left click (in the air, as a player off the ground). A mob there is hit:
`"attacked"`. Else the block there starts breaking: `"breaking"`, a command running till it's
broken. It breaks a place, as a player's swing does: another block pushed into it, the break goes
on into that block at its own pace; another item put in its hand, the break starts again with it;
held in a field, it pauses, and goes on once let go. Its event, `swing`: `ok`, and its `result`,
`{name, kept, dropped}` (the block's name; `kept`: true when it caught all the block dropped, else
false and how many it `dropped` into the world), or not and why: `lost` (air there now: what it
was breaking gone, `{why = "lost", name, id = "", at}`, its name, no id as a block has none, and
where it was, `{forward, right, up}` from where its last command left it), `not broken`. It fails with `nothing there` or `unbreakable`.

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

Flies on its thrusters that far from where its last command left it, in its own frame, straight
there and never through blocks. It must end squared, as a walk does, its feet's height whole blocks
from its origin too (from a slab, `fly(0, 0, 1.5)`). If it stands on its legs, they're left standing where it was: the thrusters fire red beams from
their sockets. With no arguments, on its legs, it takes off where it stands and holds there; in
the air, it flies back to where its last command left it. On its thrusters it walks nowhere (`no
legs`), presses no pressure plates, and can be shoved (a `shove` notice; it stays where it was put); everything else it does as
on its legs, as a player off the ground does it (its tools, a block breaking more slowly), and its
other cards work. Its event, `fly`: `ok` (then it holds
there), or not and why (`blocked`, a block over it: `name = "minecraft:stone", face = "up"`). Its thrusters cost five running CPUs' power a
tick. Fails with `not an integer: ` and where it would end, or `too far: 64`.

If its program ends while its thrusters hold it up, or its power runs out, it falls and powers off:
its torso lies on the ground, off, to be picked up as a Tier 2 Bot Golem Torso.

### flyTo(forward[, right[, up]]) → true

Flies, as `fly` does, to the point that many whole blocks from its origin, in the origin's frame.
Its event: `flyTo`. Fails with `not an integer: ` and the number.

### land([force]) → true

Comes down onto its legs, standing below where its last command left it however far down (within
three blocks to its side), and onto them: a move, so from that spot, getting there from wherever
it really is. With
`force` (`land(true)`) and no legs there, it comes straight down onto the ground beneath it without
them, and stays running, its thrusters off. That's what a crash does, but safely and still on.
Legless, it walks nowhere; it flies again to fetch its legs and lands onto them. Its event, `land`:
`ok`, or not and why (`blocked`: with no legs below, or its legs gone as it came down, `face = "down"` and `name` what it found below instead of its legs; it hovers there). With
`force` it comes down onto whatever ground is beneath it, however far; with nothing under it down
to the world's bottom (the End's void, a hole through the world), its event says so at once, blocked by the air below
(`name = "minecraft:air", face = "down"`), and it goes on hovering. Fails with `on legs`. A right click pops legs up
into an inventory, its own `use` too.

### get() → {state, legs, pathfinding, position, planned, drift}

Live, never waiting: what it's doing now (`idle`, `walking`, `turning`, `breaking`, `flying`,
`hovering`, `landing`, `falling` or `held`), whether it stands on its legs, whether it
finds its way round things, where it really is and where its last command left it (`planned`; each
as `position` gives it), and how far it's away from there (`{forward, right, up}`; none when it's
there). It answers while a field
holds it too: a program can follow the carry.

### sync() → boolean

Whether its queue is empty: `true` when no command of its own is under way, else `false`, its
interrupt saying when. It never waits; in mcos its ioctl (and `devices.sync`) waits until the queue
is empty, and answers `true`. How its commands went is in its events.

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
- **Where its last command left it** is what its next command goes from; `position` gives it. A
  shove moves it away for a while (`get`'s `drift`), never that.
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

It ends at exactly the elevation `up` asked, or its walk ends not `ok`, `blocked`
(see [Blocked](#blocked)), by what stopped it:

- **a step up** it wasn't to make, or more than a jump: the step's block, on the face it stepped toward;
- **a drop** it wasn't to make, more than 3, or with no floor within its drop: the air it would step
  into (`minecraft:air`), on that face;
- **a wall, a ceiling, lava, fire, deep water**: that block, on the face that met it;
- **there, but at another elevation**: above where `up` asked, the block under it, on its `down`;
  below, the air over it it couldn't rise into, on its `up`.

| From | To | `up = 0` | `up = 1` | `up = -3` |
|---|---|---|---|---|
| ground | a slab | walks | walks | walks |
| a slab | the full block beside it | blocked, `front` | jumps | blocked, `front` |
| ground | a block up | blocked, `front` | jumps | blocked, `front` |
| ground | the flat beyond | walks; ends blocked, `minecraft:air` on its `up`, if `up` wasn't met | | |
| ground | 3 down | blocked, `minecraft:air` | blocked, `minecraft:air` | drops |
| ground | 4 down | blocked, `minecraft:air` | blocked, `minecraft:air` | blocked, `minecraft:air` |

**With pathfinding** it finds its way, as a mob does: round walls, up stairs and slabs, jumping a
block and dropping up to 3, turning as it goes. It still ends on the asked block's middle, at the
asked elevation, facing as it began, or it's `blocked`: with no way there, or its way running out
short of it, `name = ""` (no one block to name) and `face` its face toward where it was going; a
flight with no way there is blocked at once, before it takes off. A flight with pathfinding flies
round things too.

## Events

### Blocked

A move a block stopped (`walk`, `walkTo`, `fly`, `flyTo`, `land`) ends with `why = "blocked"`, and
three fields beside it, the same for every one:

| Field | What it is |
|---|---|
| `name` | the block in its way, its name (its registry id: `minecraft:stone`, `minecraft:lava`); `""` when no one block can be named (never `nil`) |
| `face` | the face of the robot that met it, its own: `front`, `back`, `left`, `right`, `up` or `down`. It's the side that was hit, not the way the move was mostly going: a flight sliding along a wall on its right met it on its `right` |
| `call` | the command cut short: see below |

```lua
local event = {event = "walk", ok = false, why = "blocked", name = "minecraft:lava", face = "front",
  call = {args = {3}, progress = {1, 0, 0}}}
```

### Stopped

A command a `stop()` cut short ends `{event = <method>, ok = false, why = "stopped", call}`, and
nothing else: always a command's end, never a notice.

### Drifted

A turn or a swing given off its spot (see above) ends at once
`{event = <method>, ok = false, why = "drifted", drift = {forward, right, up}, call}`: its `drift`
how far it is off where its last command left it, in its origin's frame.

### Call

A stopped, blocked or drifted end says the command it cut short: `call = {args, progress}`.
`args` are the command's arguments as the program gave them (`walk(20)` is `{20}`); `progress` is
how far it got, in their terms:

| Command | `progress` |
|---|---|
| `walk`, `fly` | `{forward, right, up}` done, from where its last command left it, in its own frame as it began |
| `walkTo`, `flyTo` | where it got to, `{forward, right, up}` from its origin |
| `turn` | degrees turned |
| `land` | blocks come down |
| `swing` | how far the block's break got, 0 to 1 |

A walk's `args` less its `progress` is the rest of the way: `walk` given that goes on to where it
was going.

### Every event

| `event` | Other fields | When |
|---|---|---|
| `walk`, `walkTo`, `fly`, `flyTo`, `turn`, `land`, `swing` | `ok`, `why` (`blocked`: and `name`, `face`; `lost`: and `name`, `id`, `at`; `drifted`: and `drift`; with those, `stopped` and `interrupted`, `call`), `result` | that command ended: `ok`, or not and why (`stopped` when a `stop` cut it short; `interrupted` when a world's save cut it short and it couldn't go on after the load); a swing's `result` is `{block, kept, dropped}` |
| `notice` | `why` (or `what`), and its fields | something happened to it that no program asked for (below) |

| Notice | Other fields | When |
|---|---|---|
| `why = "shove"` | `drift` (`{forward, right, up}`) | something knocked it away from where its last command left it, once the shove's over: it stays where it was put |
| `why = "fall"` | `drift`, `below` (a block's id) | a fall it didn't ask for, on its legs, as it begins and as it lands (a caught fall: its flight's end says it); its `drift` then, and the block under it then, as it is |
| `what = "frozen"` | | a field took hold of it: its robotics paused |
| `what = "thawed"` | | the field let go and it's down: its robotics back, its origin carried |

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
