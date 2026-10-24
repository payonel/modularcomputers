---
title: Your first computer
nav: First computer
section: Tutorials
order: 1
---

In this tutorial you build a working computer from parts and boot [mcos](/mcos/boot/), the
operating system that comes on a Modular Computers disk. It ends at a shell prompt, ready for the
[next tutorial](/tutorials/first-program/).

## What you need

Collect these first. Every recipe is on the part's page, linked here.

- [ ] A [Tier 1 Case](/hardware/case/)
- [ ] A [Tier 1 CPU](/hardware/cpu/)
- [ ] A [Tier 1 RAM](/hardware/memory/) stick
- [ ] An [EEPROM](/hardware/eeprom/)
- [ ] A [Tier 1 Hard Disk](/hardware/disk/)
- [ ] A [Tier 1 Battery](/hardware/battery/), and something glowing to charge it with (a sea lantern is enough)
- [ ] A [Tier 1 Screen](/hardware/screen/)

Most of these recipes use a **Chip**. Chips are printed from **Silicon** by a
[Photomask](/hardware/photomask/): smelt nether quartz in a blast furnace to get Silicon, put it in
the photomask, and turn the photomask's gear until chips come out. Its page has the details.
{: .note}

## Steps

### 1. Charge the battery

A crafted battery starts empty, and a computer will not start without power. Put the battery and a
sea lantern anywhere in a crafting grid. The result is the same battery, full. Several lesser
glowing items work too; the battery's tooltip lists them (hold Shift). See [power](/concepts/power/).

You will see the battery's charge bar fill in the result slot.

### 2. Place the case

Place the Tier 1 Case on the ground. Its front faces you, as you place it. The front is the side
with the case's own look; the back has a power button and a window into the case.

### 3. Open the case and fit the parts

Right-click the case. A tier 1 case has no screen of its own, so the case view opens (except on the
power button on the back, which presses power). The window into the case on the back opens it too.

The case view is the computer's motherboard: a green board with sockets, labeled in silkscreen.
Move each part into its socket, by dragging or Shift-clicking:

| Part | Socket |
|---|---|
| CPU | the CPU socket |
| EEPROM | the ROM socket |
| RAM | a RAM socket |
| Hard Disk | a SATA socket |
| Battery | a UPS socket |

A socket takes only its own kind of part, so a part that does not go in a slot is in the wrong one.
A tier 1 case has one battery bay, two RAM sockets, two SATA sockets and two expansion (EXP)
sockets; sockets it does not have are greyed out. A higher tier case has more
([Case](/hardware/case/)).

The disk you crafted already holds [mcos](/mcos/filesystem/). Only a disk crafted *alone*, with no
other ingredient, comes out blank.
{: .note}

You will see a click as each part seats. The case view's bus line at the bottom counts the devices
on the [bus](/concepts/bus/).

### 4. Place the screen

A tier 1 case has no screen slot, so a screen is a block of its own. Place the Tier 1 Screen
against a side of the case, but not the case's front, with its glass facing you. A screen touching
a case's side, or joined to it by a [cable](/hardware/cable/), is on its [bus](/concepts/bus/).
Screens and cables never connect on the glass side.

Higher tier cases have a gold-trimmed expansion socket that takes a screen directly; then the
case itself is the screen.
{: .tip}

### 5. Press power

Press the power button on the back of the case (or sneak and right-click it). You will hear a click,
and the light turns green.

Now right-click the screen to open its view. After a moment you will see mcos start, and then a
prompt:

```
~ $
```

That `~` is your home directory, and `$` says you are an ordinary shell. Type a command and press
Enter:

```
ls /
```

You will see the top of mcos's filesystem (`bin`, `etc`, `home`, `lib`, `usr`, and a few more).

## When something is missing

A real PC refuses to start in specific ways, and so does this one.

| What you see | What it means | The fix |
|---|---|---|
| Pressing power clicks, but the light never comes on and nothing starts | The case has no charge. | Charge a battery (step 1) and fit it in a UPS socket. |
| An error beep, and the light flashes green for a moment | There is no CPU. | Fit a CPU. |
| The screen shows `ABRT: no RAM installed` on a gray background | There is no RAM. | Fit a RAM stick. |
| `ABRT: no EEPROM installed` | There is no EEPROM. | Fit an EEPROM in the ROM socket. |
| `ABRT: EEPROM is blank` | The EEPROM was wiped (crafting one alone wipes it). | Use a new EEPROM, which comes with the boot program. |
| `boot: no bootable disk`, and the light turns amber | The EEPROM looked for a disk with `/init.lua` and found none. | Fit a mcos disk in a SATA socket. |
| The light is green but the screen is blank | The screen is not on the bus. | Move it against a side of the case that is not its front, with the glass out. |

The amber light always means the computer is on but its CPU has stopped. Press power twice (off,
then on) to start it again. Taking RAM out or putting it in while the computer runs also stops it
with `ABRT: RAM changed while running`.

## Turning it off

At the prompt, type `poweroff`: mcos closes its files and turns the computer off. Pressing the power
button does the same without the courtesy. See [poweroff](/programs/poweroff/) and
[halt](/programs/halt/).

## Congratulations

You have a computer. Next, [write a program for it](/tutorials/first-program/).
