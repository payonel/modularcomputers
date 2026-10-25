---
title: CPU
section: Hardware
order: 50
---

The CPU runs the program on the [EEPROM](/hardware/eeprom/), and decides how many devices a
computer's [bus](/concepts/bus/) can hold.

<div class="infobox" markdown="1">

### Facts
| | |
|---|---|
| Kind | Item |
| Tiers | 1, 2 and 3 |
| Stack | 64 |
| Slot | The case's CPU socket |

</div>

## Use

Put a CPU in the CPU socket of a [case](/hardware/case/). Without one the computer cannot start.
Each tier holds more devices on the bus, and idles more cheaply, but draws more while it works.
The bus counts every device, the case's own parts included, so a tier 1 CPU's four are used up by
an EEPROM, a battery, a disk and one card or external device.

| | Tier 1 | Tier 2 | Tier 3 |
|---|---|---|---|
| Bus devices | 4 | 16 | 64 |
| Draw while running | 100 W | 150 W | 200 W |
| Draw while waiting for an interrupt | 50 W | 20 W | 5 W |

A halted CPU draws nothing. See [Power](/concepts/power/).

## Crafting

- **Tier 1:** a row of three iron ingots, then chip, amethyst shard, chip, then gold nugget,
  redstone, gold nugget.
- **Tier 2:** a tier 1 CPU in the middle, a row of three gold ingots above it, chips left and right,
  and below it gold nugget, redstone, gold nugget.
- **Tier 3:** a tier 2 CPU in the middle, a row of three diamonds above it, chips left and right,
  and below it gold nugget, redstone, gold nugget.

## Notes

- A CPU only runs what the EEPROM holds, and stops with an error when it cannot.
- Hold Shift over a CPU in the game to see its draw.
