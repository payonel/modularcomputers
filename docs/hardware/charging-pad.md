---
title: Charging Pad
section: Hardware
order: 155
---

The Charging Pad charges a [Bot Golem](/hardware/bot-golem/) standing on it: little green
glowing particles rise round the golem while it does.

<div class="infobox" markdown="1">

### Facts
| | |
|---|---|
| Kind | Block (a plate, three pixels high) |
| Stack | 64 |
| Slots | A battery, a Bluetooth card |
| Power | A running computer's, by cable; else its own battery |
| Rate | 1,000 power units a tick: ten running CPUs' worth |
| Faces | Its front (its battery hatch) faces you when you place it |
| Recipe | Gold ingot, glowstone dust, gold ingot; iron ingot, redstone block, iron ingot; three iron ingots |

</div>

![A bot golem charging on a pad, green particles round it](/assets/img/bot-golem-charging.png)
*A golem on a charging pad, charging.*

## Use

Place the pad, and walk a golem onto it (its [robotics device](/devices/robotics/) does the
walking). While a golem stands on it, the pad charges the golem's battery, or its board's own power
buffer when it has no battery. Swapping the golem's battery works too.

The pad's power is a device's, as a [Disk Station](/hardware/disk-station/)'s is: while a running
computer has the pad on its bus (by [cable](/hardware/cable/), or right against it), the computer
pays; otherwise the pad charges from its own buffer, which its battery fills. With neither, it
charges nothing.

Right-click it to open it: its battery bay, its radio slot (a [Bluetooth card](/hardware/expansion-hub/)
pairs it with one computer), its buffer's meter, and its lamp, lit while it charges.

Broken, it keeps its battery and card, as other blocks on the network do.

## Notes

- The robot must stand on it, not beside it.
- It's on the bus of a computer it's wired to: see [Charging pad](/devices/charger/) for its
  `/dev` file and events.
