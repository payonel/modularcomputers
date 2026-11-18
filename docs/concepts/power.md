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

The yardstick is a running computer: a tier 1 CPU running plus one terminal, at their default
draws, 110 W. It's fixed, whatever the draws are set to. [Batteries](/hardware/battery/) are rated
in days of it, so a battery holds a fixed amount and a hungrier computer runs it down sooner.
Costs are counted in ticks of its CPU, 100 W for a tick.

## What draws power

A [CPU](/hardware/cpu/) draws while it runs, and much less while it waits for an
[interrupt](/concepts/interrupts/); off, nothing. Every device draws while it has power (a
[terminal](/devices/terminal/), a card, a disk), and its work costs more on top (a write to a
terminal, a robot's step). What each draws and what each action costs are server settings: see
[Settings](/settings/#power-draw) and [Settings](/settings/#power-cost). A change reaches what's
already built.

By default a higher CPU tier draws more while it runs and less while it waits, as real chips with
deeper idle states do. A computer that is mostly waiting for a key, as mcos is at its prompt, costs the waiting
rate, not the running one.

## What stores it

[Batteries](/hardware/battery/) sit in the case's battery bays. How many Minecraft days a full one
runs a computer is a server setting for each tier (see [Settings](/settings/#power-battery)); a
tier 2 also recharges itself while the computer isn't running anything.

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
