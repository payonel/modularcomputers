---
title: EEPROM
section: Hardware
order: 70
---

The EEPROM holds the program a CPU starts when the computer powers on.

<div class="infobox" markdown="1">

### Facts
| | |
|---|---|
| Kind | Item |
| Stack | 1 |
| Slot | The case's EEPROM socket |

</div>

## Use

Put an EEPROM in the EEPROM socket of a [case](/hardware/case/). The [CPU](/hardware/cpu/) runs
what is on it at power-on, and stops with an error if it cannot. A new EEPROM comes from the
factory with a bootloader on it, which starts the operating system from a [disk](/hardware/disk/).
The EEPROM is also a device on the bus, so a program can read it or flash it; a new image takes
effect the next time the computer starts. See [EEPROM](/devices/eeprom/).

Its tooltip shows what it holds, and its label if it has one.

## Crafting

A chip above a row of paper, redstone and paper, above a row of three gold nuggets.

Craft an EEPROM alone in the crafting grid to wipe it: it comes out blank, with no id.

## Notes

- The CPU runs what the EEPROM holds as Lua: text that isn't Lua stops it with Lua's own error
  message, and a blank EEPROM stops it too.
