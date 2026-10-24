---
title: Boot
section: mcos
order: 10
---

What happens between pressing the power button and seeing a prompt.

## 1. The CPU runs the EEPROM

A [CPU](/hardware/cpu/) only runs what the [EEPROM](/hardware/eeprom/) holds. The EEPROM is a fixed
slot of the board, like the reset vector of a real chip. If the EEPROM has nothing the CPU can run,
the CPU aborts, which is a panic.

A new EEPROM comes with a small boot program, like a BIOS. It looks through the
[bus](/concepts/bus/) in order for the first storage device that has a file `/init.lua`. It
reads the file, and runs it, giving it the disk's address. With no such disk it writes
`boot: no bootable disk` to the console and halts. If the file doesn't compile, it writes why and
halts.

So the order of the bus decides which disk starts the computer. A computer with several disks
boots the first one that has `/init.lua`.

## 2. The kernel starts

`/init.lua` is the kernel. It is told its boot disk's address and, optionally, a command line of
words. These words are `console=<address>` (the input device of its console, the board's console by
default), `init=<path>` (the program to start on the console, `/bin/sh` by default) and
`rc=<path>` (the script to run first, `/etc/rc` by default). The EEPROM that comes with the mod gives
none.

The kernel:

1. loads its own parts from `/lib/modules/` on the boot disk,
2. mounts the boot disk on `/`, the device files on `/dev` and a filesystem in memory on `/tmp`,
3. clears the screen and prints `mcos` and its version,
4. mounts every other disk on the bus (see [Filesystem](/mcos/filesystem/)).

## 3. /etc/rc

The kernel runs `/etc/rc` with `sh` on the console, to its end. This is the boot script, as BSD's
`/etc/rc` is. By default it:

1. mounts a filesystem in memory on `/run`,
2. sets the host's name from `/etc/hostname`, if there is one,
3. runs `rc boot`, which starts every service enabled in `/etc/rc.conf`.

See [Services](/mcos/services/).

## 4. The shell

When `/etc/rc` is done, the kernel starts `/bin/sh -l` on the console, a login shell. It runs
`/etc/profile`, goes to `$HOME` (`/home`) and runs `$HOME/.shrc` if there is one, then shows the
prompt: see [The shell](/mcos/shell/).

The kernel is init: when the shell ends (you pressed Ctrl+D on an empty line), the kernel starts it
again, as init respawns a getty. If it can't start it (the file is missing or broken), it says why
on the console and tries again after five seconds.

## Stopping

[`halt`](/programs/halt/), [`poweroff`](/programs/poweroff/) and [`reboot`](/programs/reboot/) end
every process first, closing their files so the disks get what was written, and then halt the CPU,
turn the computer off, or restart it.

## Watching a boot

`dmesg` shows the kernel's log: the devices that joined the bus, mounts, and what the kernel
saw. See [dmesg](/programs/dmesg/).

## The computer after a save

A saved, running computer does not boot again when its chunk loads: it goes on where it was. See
[Persistence](/concepts/persistence/).
