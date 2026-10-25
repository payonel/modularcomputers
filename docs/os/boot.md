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
4. Turn the computer off, and swap the two disks' sockets: yours to the first SATA socket, mcos to
   the second. Both stay in the case.
5. Power on. You see `Hello from my own OS, booted from sata:0`, and five seconds later the computer
   turns off.

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

Turn the computer off. Swap the two disks: take each out of its socket and put your disk in the
first SATA socket and the mcos disk in the second. Both stay in the case. Power on.

Why swap sockets, and not the bus order? A case's own sockets are fixed connectors, like the SATA
ports on a real motherboard: SATA 1 always comes before SATA 2, and the bus order's arrows move only
devices on cables. The bootloader boots the first disk, in that order, holding `/init.lua`, so
the socket decides.

The EEPROM's bootloader looks at the disks in bus order, finds `/init.lua` on yours, and runs it.
You see:

```
Hello from my own OS, booted from sata:0
```

Five seconds later, the computer turns off.

To go back to mcos, turn the computer off and swap the disks again. Your OS sees the mcos disk
while it runs (at `sata:1`), so the two can stay together for as long as you work on your OS.

To choose without swapping, do what a PC's boot menu does: flash the EEPROM with a bootloader that
boots one disk by its id, wherever it sits. [EEPROM](/devices/eeprom/) has one,
`eeprom-pin-boot.lua`: run it from mcos with your disk's name (`lua eeprom-pin-boot.lua storage1`),
and that disk boots from then on, wherever it sits. It boots only that disk, so mcos won't start
again from that EEPROM: to go back, put in a new EEPROM, which comes with the usual bootloader.

## What each line does

- **`local disk = ...`** The bootloader runs your file as a function and passes it one value: the
  address of the disk it came from (`sata:0`). Your OS uses it to read more files from that disk.
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
- **`boot: sata:0: init.lua:7: ...`:** your file isn't valid Lua; the message says the line and why. Fix it
  from mcos (swap the disks back, the computer off) and try again.
- **The screen goes gray with `ABRT:` and a message:** your OS crashed while running (an error), or
  it ended. An OS must never return: when `init.lua` runs out of code, the CPU has nothing left to
  run, and aborts. This one ends with `board.poweroff()`, which never returns.
- **mcos boots instead:** the mcos disk is still in the first SATA socket. Swap them, the computer
  off.

Next: [Reading the keyboard](/os/keyboard/).
