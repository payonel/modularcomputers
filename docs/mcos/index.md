---
title: mcos
section: mcos
order: 0
---

**mcos** is the operating system of Modular Computers. It is written in Lua, it lives on a
[disk](/devices/disk/) you craft, and it is modeled on Linux: a kernel that owns every resource,
programs that reach it only through system calls, a shell, files, pipes, sockets and services.

If you know Linux, most of it will look familiar; where mcos differs from Linux, the pages and
the [programs' pages](/programs/) say so. If you don't, it is a good way to learn how a real
operating system works, because the parts are small enough to read.

## The pieces

- [Boot](/mcos/boot/): from the power button to a shell prompt.
- [The filesystem](/mcos/filesystem/): disks, mounts, `/dev`, `/tmp` and links.
- [The shell](/mcos/shell/): `sh`, its syntax and its line editing.
- [/dev](/mcos/dev/): devices as files.
- [Writing programs](/mcos/programs/): blocking instead of yielding, with complete examples.
- [Networking](/mcos/networking/): UDP and TCP sockets over the network cards.
- [Services](/mcos/services/): `/etc/rc`, `/etc/rc.d` and `rc.conf`.
- [Remote shell](/mcos/remote-shell/): `rsh`, `rshd` and `rcp`.
- [The sys library](/mcos/sys-api/): the system calls a Lua program uses.
- [The devices library](/mcos/devices-library/): the bus's devices as tables of their methods, and waiting for one to come.
- [Programs](/programs/): every command, one page each.

## How it is built

- **The kernel** runs on its own coroutine and owns open files, pipes, terminals, mounts and
  processes. A program is a process that runs its own code and asks the kernel for anything else
  through `sys`, the table of system calls. A system call stops the process where it is (this is
  what the CPU's *trap* does: see [Persistence](/concepts/persistence/)) and the kernel serves it on
  its own stack. A process is also stopped when input arrives, and when its time slice is spent, so
  a busy program never keeps the others, or the kernel, from running.
- **Every program is Lua.** Every process starts in `/lib/ld.lua`, which plays the part of Linux's
  `ld.so` and `crt1`: it gives the program its standard library (`io`, `os`, `print`, `require`)
  over the process's system calls, calls it, and ends the process with what it returns. `/bin/lua`
  is just Lua's interpreter, for typing Lua at a prompt; it is not needed to run a program.
- **Commands are programs** in `/bin`. The shell contains only shell behavior (`cd`, `export`,
  redirections, pipelines); line editing is a library, `/lib/readline.lua`.
- **Interfaces are Linux-shaped.** Devices speak tables; mcos turns them into text, as Linux does.
  A [redstone card](/devices/redstone-card/)'s file is a line a face, not Lua syntax.
- **No lazy loading, no old compatibility.** A program loads the libraries it asks for, and nothing
  is kept for the sake of an older way.

## What it needs

A [tier 1 memory](/hardware/memory/) stick is enough to run mcos to its shell. Every disk tier
comes with mcos: its files are made when the disk is first installed. A disk crafted alone is blank.
The [Disk Station](/hardware/disk-station/) can format a disk, clone it, and install or update
mcos on it: see the [Disk Station tutorial](/tutorials/disk-station/).

## Getting started

[Your first computer](/tutorials/first-computer/) builds one and gets it to a prompt. After that,
[your first program](/tutorials/first-program/) writes a Lua program, and
[Redstone](/tutorials/redstone/) controls the world with it.
