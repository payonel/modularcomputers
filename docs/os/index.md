---
title: Your own OS
section: Your own OS
order: 0
---

Every computer in Modular Computers can run [mcos](/mcos/), and most of this manual assumes it does.
But mcos is only a program on a disk. You can write your own operating system instead, in Lua, and
the computer will boot it the same way. This section shows you how, from nothing to a working
command shell, with every line of code included.

Your OS won't be anything like mcos, and that's the point: it's small enough to read in one sitting,
and you'll see exactly how a program talks to the computer's hardware.

## What you'll build

1. [Booting your own code](/os/boot/). The smallest OS there is: it says hello and turns the computer
   off. You learn how a computer finds and starts an OS, and how to put your files on a disk.
2. [Reading the keyboard](/os/keyboard/). An OS that reads a line you type and says it back. You
   learn the two calls everything else is built on: waiting for a device, and calling one.
3. [A command shell](/os/shell/). A prompt that runs commands: list the devices, call their methods,
   read and write files, run programs. About 250 lines, all of it on the page.
4. [Talking to devices](/os/devices/). Using your shell to drive a redstone card, take its events,
   and run programs you write for your OS.

Do them in order: each page's OS is the last one with more added.

## How a computer starts

There are three layers, as on a real PC:

- **The CPU's firmware** builds a safe place for Lua to run (a sandbox), and runs whatever the
  [EEPROM](/hardware/eeprom/) holds in it. You can't change this layer, and you don't need to.
- **The EEPROM's bootloader** is what a new EEPROM comes with, like a PC's BIOS. It looks through
  the [bus](/concepts/bus/), in order, for the first [disk](/hardware/disk/) holding a file named
  `/init.lua`, reads it, and runs it, passing it the disk's address.
- **Your OS** is that `/init.lua`. From the moment it starts, the computer is yours: nothing runs
  but your code.

Your code reaches the hardware through one global table, `board`: the motherboard's API. Its calls
are listed in [Devices](/devices/#from-the-eeprom-the-board-api). The ones this section uses:

| Call | What it does |
|---|---|
| `board.list()` | the devices on the bus: a table each with `address`, `type` and `id` |
| `board.invoke(address, method, ...)` | calls a device's method: `true` and its answers, or `false` and why it failed |
| `board.waitForInterrupt(seconds, sources...)` | sleeps until one of the devices named has news (any device, when none are named), or the time is up; gives the ones that do |
| `board.methods(address)`, `board.doc(address, method)` | what a device can do, and what one method does |
| `board.uptime()` | how long the computer has been running, in game ticks (20 a second) |
| `board.poweroff()`, `board.reboot()` | turns the computer off, or restarts it |

Every device page in [Devices](/devices/) lists its methods: those are what `board.invoke` calls.

## What you need

- A working computer: follow [Your first computer](/tutorials/first-computer/) if you don't have one.
  It boots mcos, which you'll use to write your OS's files.
- A second [hard disk](/hardware/disk/), for your OS.

No Lua experience beyond [Your first program](/tutorials/first-program/) is needed: every program is
complete on its page, and explained line by line.
