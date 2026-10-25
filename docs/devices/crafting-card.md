---
title: Crafting card
section: Devices
order: 157
---

A crafting card is storage and a 3x3 crafting grid on a card. In any computer you craft with it
by hand, in its own tab of the computer's view. In a robot, programs craft with it too, and move
its items to and from the robot's hand, containers round it, and the world. What the robot breaks
goes into its storage.

<div class="infobox" markdown="1">

### Facts
| | |
|---|---|
| Type | `crafting` |
| Where | Any expansion slot of a computer or a [Bot Golem](/hardware/bot-golem/) |
| `/dev` | `crafting0` (`crafting1` the next) |
| Slots | 1 to 9 its grid (rows left to right, top to bottom), 10 to 25 its storage |
| A craft | Half a second (10 ticks) |
| Power | A running CPU's tick for each craft, and each move |
| Recipe | Crafting table, chip, chest over three copper nuggets over three gold nuggets |

</div>

Its slots are on the card itself: take the card out and its items go with it, into another
computer or robot. Several cards craft at once: two crafting side by side make twice as much.

## In its computer's view

Each crafting card in a computer gets a tab under the view's buttons (under a golem's hands), after
the bus order's tab, and in its screen view too. Click it to open the card, left of the board in the
bus order panel's place (the buttons stay right; the bus order's tab brings the bus panel back): its grid, what the grid makes, and
its storage, as a crafting table's. Take what it makes as from a crafting table (Shift-click makes
as many as it can). A tab in the screen view opens the parts view with that card open.

![A golem's view with its crafting card's tab open: planks in its grid, four sticks as its result, logs in its storage](/assets/img/crafting-card.png)
*A golem's crafting card, its tab open: two planks make four sticks.*

A golem with a crafting card carries a tiny chest on its back.

## Robotics needed

Programs work the card only in a robot: crafting, moving its items and its file need a robot's
robotics to do the work. In a case every method fails with `no robotics available` (the card's
details, with Shift held, say so); its tab still works by hand.

## In mcos

Reading `/dev/crafting0` lists what its slots hold, a line each, its item and how many:

```
minecraft:oak_log 12
minecraft:stick 4
```

A line written drops everything in its storage (not its grid) at a face of the robot: an empty
line below it, or a face's name. A container there is filled first; the rest go into the world.
So a golem back from mining empties itself into a hopper or a chest under it in one write:

```sh
echo > /dev/crafting0
echo front > /dev/crafting0
```

Its file's ioctls are its methods, by name. Its events come as `sys.poll`'s `PRI`, and the `pull`
ioctl takes them.

## Methods

Faces are the robot's own: `front`, `back`, `left`, `right`, `up`, `down`. Where a face is
optional, a container is in front of it and a drop goes below it.

### list() → list

What its slots hold: those with something in, each `{slot, name, count, ...}` (as the robotics
device's `held`: `damage`, `maxDamage`, `label` where they apply).

### craft([count]) → true

Crafts what its grid makes, `count` times (1), each taking half a second, what it makes going into
its storage. It answers at once; its event says how it went: `crafted` (with `name` and `count`, how
many it made), or `failed` and why (`no recipe`, `nothing in its grid`, `no room`: its storage
can't take what it made, so the craft isn't made and the grid is as it was). Fails with `already
crafting`.

### move(from, to[, count]) → number

Moves items from one of its slots to another (all, or `count`): how many went. Fails with `slot N
holds something else`.

### equip(slot) → true

Swaps one of its slots with what the robot holds.

### pickup() → number

Picks up the items lying round the robot (within a block and a half) into its storage: how many.

### drop([face][, slot[, count]]) → number

Drops items at a face (below, when none is given): all of a slot, or `count` of it, or with no slot
everything in its storage. A container there is filled first, the rest go into the world. It
answers how many went into the world: what the container took isn't counted (a hopper below that
takes them later isn't the robot's concern).

### store([face][, from[, count[, to]]]) → true, 0 or false, remaining; or nil and why

Puts items into the container at a face (in front, when none is given): from slot `from` (all, or
`count`), or with none everything in its storage; into the container's slot `to`, or where they fit.
One answer says it all: `true, 0` when none of it is left (nothing to store too), else `false` and
how many are left (the container is full). Fails with `no container there`.

### take([face][, from[, count[, to]]]) → number

Takes items from the container at a face (in front, when none is given): from its slot `from`, or the
first it can; all, or `count`; into the card's slot `to` (its grid's too, to lay out a recipe), or
where they fit in its storage. How many came.

### get() → list

What `list` gives: its file's read.

### set([face]) → true

Drops everything in its storage at a face (below): its file's write.

### pull() → event or nil

Its oldest event, or nil.

## Events

| `event` | Other fields | When |
|---|---|---|
| `crafted` | `name`, `count` | the crafts asked for are made |
| `failed` | `why`, `count` (made before it stopped), `name` | a craft couldn't be made; `interrupted` when its world was saved during it |

## Example

Turn the logs in a chest in front of the robot into planks, and put them back:

```lua
{% include crafting-planks.lua %}
```
