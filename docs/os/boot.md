---
title: Booting your own code
section: Your own OS
order: 1
---

The smallest operating system there is: it writes a line on the screen, waits five seconds, and
turns the computer off. Small as it is, the computer runs nothing else: no mcos, no shell.

## What you need

- [ ] A computer that boots mcos (see [Your first computer](/tutorials/first-computer/)), with a free
  SATA socket.
- [ ] A second [hard disk](/hardware/disk/), any tier.

## The whole thing

Your OS is one file, `/init.lua`, on a disk of its own:

```lua
{% include os-hello.lua %}
```

[Download os-hello.lua](/examples/os-hello.lua)

The steps:

1. Craft the second disk alone in the crafting grid: it comes out blank (no mcos, no files).
2. Put it in the computer's second SATA socket, and power on. mcos boots from the first disk and
   shows the new one at `/mnt/sata/1`.
3. Write the file: `edit /mnt/sata/1/init.lua`, type the program in, Ctrl+O to save, Ctrl+X to leave.
4. Tell the EEPROM to boot your disk, next time only, and reboot:
   `efibootmgr -c -d storage1 -L "my OS"`, `efibootmgr -O`, `efibootmgr -n 0`, `reboot`.
5. You see `Hello from my own OS, booted from sata:1`, and five seconds later the computer turns
   off. Power it on again and mcos starts, as before.

## Step by step

### 1. A blank disk

A disk crafted at the table comes with mcos on it. Crafted again *alone*, it comes out blank: an
empty filesystem (*hfs*), no files at all. That's what you want, so nothing of mcos is left to
confuse you. (A disk that still has mcos would work too: your `/init.lua` would replace its kernel,
and the rest of its files would just sit there.)

### 2. Write `/init.lua`

With your disk in the second SATA socket, mcos mounts it at `/mnt/sata/1`. Pick one way to put the
file there:

- **Type it in:** `edit /mnt/sata/1/init.lua`, then write it out with Ctrl+O (Enter takes the name offered) and leave with Ctrl+X.
- **Download it**, with an [internet card](/devices/internet-card/) in the computer:
  `wget -O /mnt/sata/1/init.lua https://payonel.github.io/modularcomputers/examples/os-hello.lua`
- **Copy it from your own computer.** Every hfs disk's files are real files in the world's save:
  `<world folder>/modularcomputers/disks/<disk id>/`. The disk's id is in its tooltip with Shift
  held. Put `init.lua` in that folder with any text editor you like. (Single player, or a server
  you run.)

Check it's there: `cat /mnt/sata/1/init.lua`.

### 3. Boot it

Both disks stay where they are. A real PC picks its boot disk from its firmware's boot entries, not
by which port a disk is on, and so does the EEPROM: it keeps boot entries in its NVRAM, each a disk
by its id, and boots `BootNext`'s once, then `BootOrder`'s, then (with none) the first disk with
`/init.lua`, mcos's. [`efibootmgr`](/programs/efibootmgr/) sets them, as on Linux. In mcos:

```
$ efibootmgr -c -d storage1 -L "my OS"
BootOrder: 0000
Boot0000  my OS
$ efibootmgr -O
BootOrder:
Boot0000  my OS
$ efibootmgr -n 0
BootNext: 0000
BootOrder:
Boot0000  my OS
$ reboot
```

- **`-c -d storage1 -L "my OS"`** makes boot entry `Boot0000` for your disk (`storage1`, the second
  disk), labeled `my OS`. A new entry goes first in `BootOrder`, which would make it the default.
- **`-O`** clears `BootOrder`, so mcos stays the default: with no order, the EEPROM falls back to the
  first disk with `/init.lua`.
- **`-n 0`** sets `BootNext`: entry 0, next time only.

The computer restarts, and the EEPROM boots your disk:

```
Hello from my own OS, booted from sata:1
```

Five seconds later, the computer turns off. `BootNext` was used up, so power it on and mcos starts.
Each time you change your OS: `efibootmgr -n 0`, then `reboot`. The computer's parts view marks the
disk the EEPROM will boot (`SATA 2, boots`).

To make your OS the default: `efibootmgr -o 0`. Then every power-on boots it; to go back, have your
OS clear the order (the next pages' shell can: `call eeprom setBootOrder {}`), or take the disk out
(an entry whose disk isn't there is skipped).

## What each line does

- **`local disk = ...`** The bootloader runs your file as a function and passes it one value: the
  address of the disk it came from (`sata:1`). Your OS uses it to read more files from that disk.
- **`board.invoke("console", "write", ...)`** calls the `write` method of the device at address
  `console`. The console is a device the motherboard always has: it stands for every
  [terminal](/devices/terminal/) on the bus at once, so whatever you write shows on all your
  screens. Terminals are VT100s: `\r` goes back to the start of the row and `\n` moves down one,
  so a new line is `\r\n`.
- **`board.waitForInterrupt(SECONDS, "nothing")`** waits until a device named `nothing` has news, or
  five seconds pass. No device has that name, so it's a sleep. While it waits, the computer uses
  the CPU's waiting power, not its running power.
- **`board.poweroff()`** turns the computer off, as its power button does.

## When it goes wrong

- **`boot: no bootable disk`:** no disk on the bus has `/init.lua`. Check the name (lower case,
  at the top of the disk, not in a folder) and that the disk is in the case.
- **`boot: sata:1: init.lua:7: ...`:** your file isn't valid Lua; the message says the line and why. Fix it
  from mcos, and try again.
- **The screen goes gray with `ABRT:` and a message:** your OS crashed while running (an error), or
  it ended. An OS must never return: when `init.lua` runs out of code, the CPU has nothing left to
  run, and aborts. This one ends with `board.poweroff()`, which never returns.
- **mcos boots instead:** `BootNext` isn't set: `efibootmgr -v` says what is, and `(not present)`
  for an entry whose disk isn't on the bus. Set it again with `efibootmgr -n 0`, then `reboot`.

Next: [Reading the keyboard](/os/keyboard/).
