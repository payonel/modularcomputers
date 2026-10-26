---
title: Bot Golem
section: Hardware
order: 150
---

The Bot Golem is a robot a block high, built like an iron golem: a computer you can walk anywhere.
It holds a computer's parts (a CPU, RAM, an EEPROM, a battery, a disk and two cards: what you put
in), and a robotics device soldered onto its board is the only way it moves. It comes in two
tiers: a **tier 1** walks; a **tier 2**, in dark steel, walks and flies on its thrusters, and has
room for a second battery and four RAM sticks. Its face is a screen
if you put a tier 1 screen in its gold slot; without one it has an empty bezel and runs all the
same. It never acts on its own: a program drives it.

<div class="infobox" markdown="1">

### Facts
| | |
|---|---|
| Kind | Robot (an entity) |
| Stack | 1 (it keeps its parts) |
| Size | A block high, through a one-block gap |
| Speed | A player's walking pace |
| Slots | CPU, EEPROM, 1 disk, a tier 1 screen (its face, optional), 2 cards, its hands; tier 1: a battery, 2 RAM; tier 2: 2 batteries, 4 RAM |
| Flies | Tier 2, on its thrusters (either tier, held in its own energy field) |
| Soldered | Its robotics device, on its last expansion lane |
| Getting one | Creative only, for now |

</div>

![A tier 2 golem in dark steel, its screen lit, and a tier 1 in iron on a charging pad](/assets/img/bot-golems.png)
*A tier 2 golem, its tier 1 screen lit in its gold slot, and a tier 1 charging on a pad.*

## Use

Use the golem item on a block: the golem stands there, facing the nearest right angle to you, on its
block's middle: that's its pose's origin, from which it always knows exactly how far it is (see
[its pose](/devices/robotics/#where-it-is-its-pose)). Its walks are whole blocks, straight, and end
exactly where asked, unless you turn its pathfinding on. Right-click it to see its
screen (when its face has one), or crouch and right-click for its parts. Hit it to pick it up: it
drops as its item, its parts and its board's id kept in it, as a broken case keeps them. Holding
that item (a golem's or a torso's), crouch and right-click, in the air or at a block, to open its
parts right there: change its parts, what it holds and its crafting cards' slots without setting it
down (it's off while it's an item: no bus till it's set down). Nothing else harms it. It's solid: you walk into it, not through it, and can stand on it. While it
stands on its legs nothing shoves it: not a player walking into it, a mob, or a blast.

Its parts view is a case's, seen through its iron chest: the same board, the same slots, those its
size has. Its gold slot takes only a [tier 1 screen](/hardware/screen/), which is its face. Its
last expansion lane has its robotics device soldered on, so it has no slot there. Its parts are its
own to change: none is soldered but the robotics device.

![The golem's parts view: the board through its iron chest, its robotics chip soldered at the bottom right](/assets/img/bot-golem-parts.png)
*Its parts view: the bus order panel lists its robotics device, soldered on EXP 4.*

Right of its board, under its buttons, are its hands: what it holds (a tool, blocks to place, a
bucket), shown in its hand in the world, and under it its off hand, where it catches what it breaks
when it has no storage. A program uses it as a player would: places blocks, opens
doors and presses buttons, breaks blocks with a tool in a player's time, hits mobs with a weapon,
and moves items to and from a chest in front of it. When you pick the golem up, what it holds goes
with it. On its legs it presses pressure plates, as anything walking does.

A [crafting card](/devices/crafting-card/) gives it storage (a tiny chest appears on its back) and a
crafting grid its programs craft with; what it breaks goes there first. Each card has a tab under
its hands.

## Flight

A golem flies two ways, each with its price:

- **Its thrusters, legs off.** A tier 2 golem has them (a tier 1's thruster calls answer `no
  thrusters: a tier 2 golem's`): its nozzles are the collars round its legs' sockets. `fly` leaves its legs standing where it was and
  fires red beams from their empty sockets: it flies at 25 blocks a second, much faster than it
  walks or its field flies it, needs no card, and costs five running CPUs' power a tick. On its
  thrusters it has no footing: it uses no tools (buttons, doors and levers yes: a tool in its hand
  works as an empty hand), presses no pressure plates, and can be shoved. `hover` holds it where it
  is; `land` brings it down onto its legs, below it, and won't with no legs there. Chickens and cows
  run from it. If its program ends or its power runs out while its thrusters hold it up, it falls
  and powers off.
- **Its own field, legs on.** An [energy field card](/devices/energy-field-card/) holding the golem
  itself (`capture("self")`) carries it, legs and all, at 10 blocks a second, faster than a player
  runs. It keeps its tools, but off the ground it works them as a player in the air does: it digs
  at a fifth of its speed. Nothing shoves it (the field holds it), and its field is busy holding it.
  Mobs don't mind it.

![A golem on its thrusters, red beams from its leg sockets, its legs left on the ground](/assets/img/bot-golem-thrusters.png)
*A golem on its thrusters, its legs left on the ground below.*

Its legs, emerald and redstone clamps on top, plug into sockets under its torso.

![A golem's torso on the ground beside its legs](/assets/img/bot-golem-legs.png)
*A golem's torso, beside its legs.*

A right click on legs pops them up into your inventory (the golem's `use` pops them into its own),
and their item, used on a block, stands them there. A fallen torso lies on the ground, off, as its
legs do: hit it to pick it up as a Tier 2 Bot Golem Torso (its parts in it), or right-click it for its
view. A torso crafted over its legs, in any crafting grid (2x2 is
enough), is the golem again, with all it kept.

It runs on its battery, as a case does: swap the battery, or walk it onto a
[charging pad](/hardware/charging-pad/). It does nothing until a
program tells its robotics device to act. See [Robotics device](/devices/robotics/) for
what a program can do with it, and its examples.

## A whole job: fell a tree

An advanced example, using all of it: the robotics device, a [crafting card](/devices/crafting-card/)
for storage, and an [energy field card](/devices/energy-field-card/) to fly. In its own field the
golem keeps its legs and its tools; in the air it works its axe at a fifth of its speed, as a player
in the air does. Set it up:

1. A golem with a CPU, RAM, an EEPROM, a battery, a disk with mcos, a crafting card and an energy
   field card (a screen in its gold slot, to watch it).
2. A chest right behind it, with an axe in it (any slot).
3. A tree ahead of it, within 8 blocks: on flat ground, or up on a cliff or a ledge (it climbs up to
   12 blocks).

Save the program as `/home/golem-tree.lua` and run `lua /home/golem-tree.lua` (or `lua
/home/golem-tree.lua 5 4` to look 5 blocks ahead and climb at most 4). The golem empties its hands
into the chest, takes the axe, holds itself in its field, and flies forward a block at a time until a
log is in front of it, climbing whatever's in its way. It cuts the trunk from the air, clearing the
leaves over its head as its field lifts it, then comes home exactly the way it went and lands on its
legs.

Home, it leaves everything ready to run again: the axe back in the chest's first slot, the wood and
whatever else it carries (saplings, sticks) in the chest, its hands and storage empty. If anything
goes wrong, it comes home just the same and says what happened: `there's no axe in the chest behind
me, so I came home`, or `while looking for a tree, I found a cliff (minecraft:stone) higher than 12
blocks, so I came home`.

```lua
{% include golem-tree.lua %}
```

## Mobs

Spiders keep away from golems, as they keep away from armadillos, and a skeleton riding one is
carried off with it. A flying golem scares chickens and cows (mooshrooms too) off as it comes near.
Sheep don't care.

## Notes

- A golem and its parts are each one thing, by their ids: placing one you picked up moves it
  into the world, in creative too (it leaves your hand). A copy of one that's in the world (creative's
  Ctrl and pick block makes them) can't be placed, and a copied part, a disk say, in another computer
  stays off its bus until the one in the world is gone. Nothing is given a new id.
- It knows nothing of where it is: a walk is given relative to itself (forward, right, up). A GPS
  card is planned.
- A walk under way when its world is saved doesn't go on after a load: it ends then, blocked
  (`interrupted`), so a program waiting on it hears.
- When its chunk unloads its program freezes, as a case's does, and goes on when it loads.
