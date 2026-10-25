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
| Slots | CPU, EEPROM, a battery, 2 RAM, 1 disk, a tier 1 screen (its face), 2 cards |
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

It runs on its battery, as a case does: swap the battery, or walk it onto a
[charging pad](/hardware/charging-pad/). It does nothing until a
program tells its robotics device to walk or turn. See [Robotics device](/devices/robotics/) for
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
