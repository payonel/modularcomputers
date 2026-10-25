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
4. Turn the computer off, take the mcos disk out, and put your disk in the first SATA socket.
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

Turn the computer off. Take the mcos disk out, and move your disk to the first SATA socket. Power
on.

The EEPROM's bootloader looks at the disks in bus order, finds `/init.lua` on yours, and runs it.
You see:

```
Hello from my own OS, booted from sata:0
```

Five seconds later, the computer turns off.

To go back to mcos, put the mcos disk back in the first socket. To keep both, leave mcos first and
your disk second while you work on your OS: the bootloader boots the first disk that has
`/init.lua`, so the order of the sockets decides which OS starts.

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
  from mcos (swap the disks back) and try again.
- **The screen goes gray with `ABRT:` and a message:** your OS crashed while running (an error), or
  it ended. An OS must never return: when `init.lua` runs out of code, the CPU has nothing left to
  run, and aborts. This one ends with `board.poweroff()`, which never returns.
- **mcos boots instead:** the mcos disk is still before yours in the bus order.

Next: [Reading the keyboard](/os/keyboard/).
