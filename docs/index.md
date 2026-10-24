---
title: Modular Computers
description: Build computers in Minecraft and program them in Lua.
section: Start
nav: Home
order: 0
---
# Modular Computers

Modular Computers is a Minecraft mod for Fabric and NeoForge that adds computers you assemble from
parts and program in [Lua](https://www.lua.org). Put a CPU, memory and an EEPROM on a motherboard
in a case, add a disk and a screen, and boot **mcos**, a Linux-like operating system that comes on
a disk you craft. Then automate your world: read and drive redstone, sense players and sounds,
move pistons, talk to other computers, or just learn how a computer really works.

New here? Start with [Installing](/install/), then build your [first computer](/tutorials/first-computer/).

## What makes it different

<div class="cards" markdown="1">
<div markdown="1">

### A Lua that can be frozen and saved
The Lua runs on a Java port of Lua 5.5 with no native code. A computer is
[budgeted and preempted](/concepts/persistence/), and a running program is saved with the world and
resumes mid-run when it comes back.

</div>
<div markdown="1">

### Real parts
A [case](/hardware/case/), a [CPU](/hardware/cpu/), [memory](/hardware/memory/), an
[EEPROM](/hardware/eeprom/), [disks](/hardware/disk/), cards in slots, and
[cables](/hardware/cable/) to everything else.

</div>
<div markdown="1">

### An operating system
[mcos](/mcos/) has a shell, a filesystem, processes, sockets, services and
[53 programs](/programs/), with man pages for each. Its files are real files in your world save.

</div>
<div markdown="1">

### Devices on a bus
Everything a program can touch is a [device](/devices/) on one [bus](/concepts/bus/), found by
[several names](/concepts/names/) and woken by [interrupts](/concepts/interrupts/).

</div>
<div markdown="1">

### Computers that talk
Wired and wireless [network cards](/devices/network-card/), sockets in mcos, and
[remote shells](/mcos/remote-shell/) between computers. A card never silently drops a frame.

</div>
<div markdown="1">

### Real terminals
Screens are VT102 and xterm [terminals](/devices/terminal/): color, resizing, scrollback and, on
the best tier, sixel graphics.

</div>
</div>

## The manual

<div class="cards" markdown="1">
<div markdown="1">

### [Tutorials](/tutorials/)
From an empty case to two computers talking.

</div>
<div markdown="1">

### [Hardware](/hardware/)
Every block and item, how to craft it and what it does.

</div>
<div markdown="1">

### [Devices](/devices/)
Each device's methods, events and faces.

</div>
<div markdown="1">

### [mcos](/mcos/)
The operating system, and its `sys` API.

</div>
<div markdown="1">

### [Programs](/programs/)
The man page of every program.

</div>
<div markdown="1">

### [Concepts](/concepts/)
The ideas behind the mod: the bus, faces, power.

</div>
</div>

Modular Computers is MIT licensed, and its source is on
[GitHub](https://github.com/payonel/modularcomputers).
