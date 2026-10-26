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

An off CPU draws nothing. See [Power](/concepts/power/).

## Aborts

A CPU runs only what its EEPROM holds, and the only clean ways out are the board's own: a program
calling `board.poweroff()` or `board.reboot()` (mcos's `poweroff` and `reboot`), or the power
button. Anything else is an **abort**: the computer turns off, and its power light blinks amber
until it's next powered on. It aborts when:

| Cause | What happened |
|---|---|
| `no program` | No RAM, no EEPROM, a blank one, or code that doesn't compile, at power-on. |
| `returned` | The program ran out of code: an OS must never return. |
| `crashed` | The program raised an error out of its EEPROM. |
| `power lost` | Its power ran out: the case's store empty, no battery to carry it. |
| `cpu changed` | Its CPU was taken out, or swapped, while it ran. |
| `ram changed` | RAM was put in, taken out or swapped while it ran. |
| `unsaved` | It was on when the world was saved, but its program couldn't be saved with it. |

Disks, cards, the EEPROM and batteries can be swapped while it runs without an abort.

The board records why it last stopped, and the next boot can read it: `board.lastShutdown()` gives
`{cause, message, time}` (`cause` one of the above, or `clean`, `button`, `none`), and mcos shows it
as `/sys/firmware/last_shutdown/` (`cause`, `message`, `time`) and in its log at boot (`dmesg`). An
EEPROM can also bring a computer back on by itself when power returns after a power loss: see
[restore on power loss](/devices/eeprom/#restore-on-power-loss).

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
