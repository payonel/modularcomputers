---
title: Persistence
section: Concepts
order: 60
---

You can stop the server, or walk away so that the chunk holding your computer unloads, and when you
come back the computer **goes on exactly where it was**: in the middle of a loop, in the middle of
a wait, with its variables, its open files and its screen. It does not reboot. This page explains
how, and what it costs.

## The Lua engine

The computers run Lua 5.5 on **LungoLua** and **StyxLua**, a Java port of a Lua 5.5 fork with three
things stock Lua doesn't have:

- **Budgets.** A program is allowed so many steps before it is stopped, and a limit on its memory.
- **Freezing.** A stopped program is not killed: it is frozen where it is, and thawed later to go
  on.
- **Persistence.** A frozen program, with all of its state, can be written out as bytes and made
  again after a restart.

It is written entirely in Java. There is no native binary to install, so the same jar runs on any
machine that runs Minecraft, on both Fabric and NeoForge.

## Three layers

A computer runs three layers, as a real one runs firmware under an operating system.

1. **The host** is the Java side. A runner on the computer's own thread (a low-priority thread of
   its own) runs the Lua virtual machine. Anything that touches the world, such as a device or a
   block, is done for it on the server thread, and the computer's thread waits for the answer.
2. **The firmware** (`machine.lua`) is the CPU's layer. It is unbudgeted, and user code cannot reach
   it. It builds the sandbox, loads the EEPROM's program, budgets and freezes it, and answers its
   syscalls and its waits for [interrupts](/concepts/interrupts/).
3. **User code** is everything budgeted: the EEPROM's program, and with [mcos](/mcos/) the kernel
   and every program. The program that runs first is the root of all user threads.

User code never sees the firmware's variables, the real globals or `debug`.

## What a budget means

There are three limits.

- **Steps.** The firmware lets user code run for a *slice* of 100,000 steps, then freezes it. The
  firmware gets control and the host gets a *safe point*: a moment when the machine can be saved or
  can answer a call. Then the program is thawed at once and goes on. A program that loops forever
  cannot hang the computer or the server; it is just cut into slices.
- **Server time.** Devices live on the server thread, so a program's calls to them (a **board
  call**) get 1 millisecond of the server's time each tick for each computer. A program that makes
  too many calls waits for the next tick. It costs the server a bounded amount, however many
  computers there are. Lua itself runs on the computer's own thread and costs the server nothing.
- **Memory.** A program's memory is the RAM installed, above a measured baseline, so a
  [memory](/hardware/memory/) stick means the same to a program whatever the firmware itself costs.
  A program that fills it gets Lua's out-of-memory error, as it would on a real machine without
  swap. Changing the RAM while a program runs aborts it, as a real machine would crash.

## Freezing, not yielding

A syscall does not yield. Every board call, wait or power-off records its request and cuts the program's
budget to one step; the budget callback then freezes the **whole chain** of the program's
coroutines, wherever it is: inside coroutines, inside a `table.sort` comparison, inside a `gsub`
callback. When the host has answered, the program thaws and the call returns the answer.

So no coroutine in your program ever sees a yield, and `pcall` cannot catch a freeze. A program
needs no special care to be saved, or to be stopped by its budget.

[mcos](/mcos/) uses this itself. Its kernel runs every program as a coroutine, stops one with a
*trap* (the syscall instruction) and resumes the next: see [The sys library](/mcos/sys-api/).

## Saving a running computer

When the world saves, or a chunk unloads, the server asks each running computer for a snapshot.
The computer's thread honours it at its next safe point, with no call in flight. A save never
splits a program from its devices: a call that was queued but not yet made is a safe point itself,
so the program and the devices it talks to are saved in agreement.

Then StyxLua writes the entire state of the Lua machine out as bytes, and they are stored with the
block in the chunk.

There are no timeouts and no kills. If a program is deep in a long step, the save waits until it
reaches the next slice. The server logs a computer that took long to save, naming it.

## What survives

- All the program's variables, its tables and coroutines, wherever they are in a call.
- The kernel's state: the processes, the files they have open, the contents of `/tmp` and `/run`
  (they live in the computer's memory, see [Filesystem](/mcos/filesystem/)), and the screen.
- An open file of the computer, with what was written to it so far.
- What its devices were holding for it: keys typed, sensor events, frames. A key typed just before
  a save arrives after the restore.
- A wait: the program finishes the wait it was saved in. A wait with a time limit ends when it
  would have, on the computer's own clock, which stands still while the computer is unloaded.

After a restore, the computer is simply running.

## What does not

- **Power loss.** An unloaded chunk only freezes a computer, but a broken case, or a battery that
  runs dry, is a power cut. A power cut is not a save: the computer is off, and what a program had
  written to a file but not closed is lost. On a disk, a file's writes are committed at close.
- **A state that can't be saved.** If a running computer can't be saved (its state holds something
  that cannot be persisted, or it could not be saved at all), it comes back
  [aborted](/hardware/cpu/#aborts) (`unsaved`): off, its light blinking, and you start it again.
  The server logs why.
- **A computer whose saved state fails to load** also comes back aborted (`crashed`), with a warning
  in the log.
- **Old saves.** The mod's saved formats change freely between versions, without shims. Do not
  expect a computer saved by one version to resume in another.

## Why

A world where every chunk load reboots every computer would be a world where long-running
programs are impossible: a sorter, a farm controller, a server. So computers keep their state, as a
real machine in suspend-to-disk does, and the engine had to be able to write a program out
whole. That is what LungoLua and StyxLua are for.
