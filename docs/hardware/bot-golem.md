---
title: Bot Golem
section: Hardware
order: 150
---

The Bot Golem is a robot a block high, built like an iron golem: a computer you can walk anywhere.
It holds a computer's parts, its face is a tier 1 screen, and a robotics device soldered onto its
board is the only way it moves. It never acts on its own: a program drives it.

<div class="infobox" markdown="1">

### Facts
| | |
|---|---|
| Kind | Robot (an entity) |
| Stack | 1 (it keeps its parts) |
| Size | A block high, through a one-block gap |
| Speed | A player's walking pace |
| Slots | CPU, EEPROM, a battery, 2 RAM, 1 disk, a tier 1 screen (its face), 2 cards, its hands |
| Soldered | Its robotics device, on its last expansion lane |
| Getting one | Creative only, for now |

</div>

![Two bot golems: one with a screen in, its face lit; one without, an empty bezel](/assets/img/bot-golems.png)
*A golem with a tier 1 screen in its gold slot, and one without: its face an empty bezel.*

## Use

Use the golem item on a block: the golem stands there, facing you. Right-click it to see its
screen (when its face has one), or crouch and right-click for its parts. Hit it to pick it up: it
drops as its item, its parts and its board's id kept in it, as a broken case keeps them. Nothing
else harms it. It's solid: you walk into it, not through it, and can stand on it. While it
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

## Its legs, and flight

A program can detach the golem's legs (`detachLegs`). With an [energy field card](/devices/energy-field-card/)
holding the golem itself first, it flies on, much faster than it walks, its program moving the field,
its legs left standing below; in flight it still runs, crafts and works buttons and doors, but uses
no tools (it has no footing) and presses no pressure plates. With nothing holding it up, it falls
over and powers off. Its legs, emerald and redstone clamps on top, plug into sockets under its
torso: `attachLegs`, within three blocks of them, takes it to them and onto them.

![A golem's torso on the ground beside its legs](/assets/img/bot-golem-legs.png)
*A golem's torso, beside its legs.*

A right click on legs pops them up into your inventory (the golem's `use` pops them into its own),
and their item, used on a block, stands them there. If its power runs out or it's turned off in
flight, its field lets go and its torso falls: hit it to pick it up as a Bot Golem Torso (its parts
in it), or right-click it for its view. A torso crafted over its legs, in any crafting grid (2x2 is
enough), is the golem again, with all it kept.

It runs on its battery, as a case does: swap the battery, or walk it onto a
[charging pad](/hardware/charging-pad/). It does nothing until a
program tells its robotics device to act. See [Robotics device](/devices/robotics/) for
what a program can do with it, and its examples.

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
