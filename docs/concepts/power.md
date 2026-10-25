---
title: Power
section: Concepts
order: 30
---

A computer runs on stored power. Nothing in the mod ticks once a second per device to count it, so
a big build costs the server almost nothing: power is settled lazily, when someone looks.

## Units

Power is counted in whole units, and one unit a tick is shown as one watt. What is stored is shown
in watt-hours. Every draw, load and charge you see in a screen or tooltip goes through the same two
conversions, so the unit's name is one change in the mod. (The names may change; the
numbers will stay as they are.)

The yardstick is a running computer: a tier 1 CPU running plus one terminal,
110 W. [Batteries](/hardware/battery/) are rated in days of it.

## What draws power

| Thing | Draw |
|---|---|
| [CPU](/hardware/cpu/) tier 1, running | 100 W |
| CPU tier 2, running | 150 W |
| CPU tier 3, running | 200 W |
| CPU tier 1, waiting for an [interrupt](/concepts/interrupts/) | 50 W |
| CPU tier 2, waiting | 20 W |
| CPU tier 3, waiting | 5 W |
| CPU, halted | nothing |
| A [terminal](/devices/terminal/), while it has power | 10 W |
| One write to a terminal | 1 unit, once |

A higher CPU tier draws more while it runs and less while it waits, as real chips with deeper idle
states do. A computer that is mostly waiting for a key, as mcos is at its prompt, costs the waiting
rate, not the running one.

## What stores it

[Batteries](/hardware/battery/) sit in the case's battery bays. By default, a full tier 1 runs a
computer for ten Minecraft days, a full tier 2 for twenty and also recharges itself while the
computer isn't running anything (two seconds of rest charge one second of running), and a tier 3
never runs out. These are defaults that a server's configuration can change.

## Bus-powered devices

Cards and cabled devices are powered like a USB device is: a device has power while at least one
running computer has it on its [bus](/concepts/bus/), and goes dark when the last one lets go. What
it used is a debt, settled lazily: every 20 ticks, or at an event such as a power-on or a hot-plug.
The first computer to look pays the whole debt, and any other computer owes only what's new. Which
computer pays a shared device is a race, and doesn't matter.

Work costs power too. A [Disk Station](/devices/disk-station/)'s format, clone or install is paid
from the bus's power while a running computer supplies it, and from its battery
otherwise.

## What you see

The case's view has meters for the load and the stored power.
A broken case is a power cut. An unloaded chunk only freezes a computer: see
[Persistence](/concepts/persistence/).
