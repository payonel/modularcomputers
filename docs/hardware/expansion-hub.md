---
title: Expansion Hub
section: Hardware
order: 110
---

An expansion hub is a box of card slots on the cable network, like an external expansion chassis.

<div class="infobox" markdown="1">

### Facts
| | |
|---|---|
| Kind | Block |
| Stack | 64 |
| Slots | 4 cards, and 1 for a Bluetooth card |
| Faces | Its card bays (front) face you when you place it |

</div>

## Use

Place a hub and join it to a computer with [cable](/hardware/cable/). Right-click to open its slots,
and put cards in them. Every computer wired to the hub has the hub's cards on its bus, unless a
[Bluetooth card](/devices/bluetooth-card/) in the hub's radio slot pairs the whole hub with one
computer.

A card works where it is installed. A [redstone card](/devices/redstone-card/) in a hub emits and
detects redstone at the hub, not at the computer, and the hub gives off the redstone its cards
put out. Faces such as `front` and `left` are named from the hub's front. See
[Faces](/concepts/faces/).

A hub passes the cable connection on, like a cable, and takes a cable on every side. It holds any
card: [network](/devices/network-card/), [WLAN](/devices/wlan-card/), [sound](/devices/sound-card/),
[internet](/devices/internet-card/), the sensors and the rest.

## Crafting

A chip in the middle, a comparator above, below, left and right of it, copper ingots in the top
corners and gold ingots in the bottom corners.

## Notes

- Hubs let you use more slots than a case has. Mind the CPU's bus size: each card is a device.
- Breaking a hub drops it with its cards.
