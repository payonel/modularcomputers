---
title: EEPROM
section: Devices
order: 40
---

The EEPROM is the chip a computer boots from. Its contents are the program the CPU runs at power-on,
and a program can read or reflash them.

<div class="infobox" markdown="1">

### Facts
| | |
|---|---|
| Type | `eeprom` |
| In `/dev` | `eeprom` (there is only ever one) |
| Address | `eeprom` |
| Interrupt | None |
| Info | `used`: the size of its contents in bytes |

</div>

For the chip you craft and place, see [EEPROM](/hardware/eeprom/).

## How it works

At power-on the CPU loads its program from the EEPROM's fixed slot, as a reset vector, and runs it.
A CPU that cannot (the chip is empty or its code does not load) panics. The EEPROM's contents are
plain text, one character a byte, and for the Lua CPU they are Lua code. The mod has no size limit
for it yet.

The chip a computer comes with is a boot manager, as a UEFI board's. It tries the boot entries in
the chip's NVRAM (below): `BootNext`'s once, then `BootOrder`'s in turn, skipping a disk that isn't
on the bus. Then, as UEFI's fallback, it boots the first disk in bus order that holds `/init.lua`.
It hands the file it runs the disk's address. A file that doesn't compile is reported and skipped;
with nothing left, it says `boot: no bootable disk` and halts. The code on the chip runs before any
OS, so it calls the board directly (`board.invoke`, `board.list`).

The chip keeps its contents on its item: reads and writes go straight there. A write takes effect at
the next power-on.

## NVRAM: the boot entries

Besides its image, the chip has NVRAM, a small store of boot variables apart from the image (a new
image keeps them), as a UEFI board keeps `Boot####`, `BootOrder` and `BootNext`:

| Variable | What it is |
|---|---|
| `Boot0000` to `Boot0007` | A boot entry: a disk by its id (`id:<uuid>`), the file on it to boot (`/init.lua` if none), and a label |
| `BootOrder` | The entries' numbers, in the order they're tried |
| `BootNext` | An entry to try first, next time only: the bootloader clears it as it boots |
| `BootCurrent` | The entry that booted the computer this time; none when it booted by the fallback. Not kept in NVRAM: it's gone when the power goes, as UEFI's |

They're a closed set of typed values, never free text: every change is checked by the chip, and a
change that isn't valid is refused with why (an id that isn't one, a path that doesn't start at `/`
or has spaces, an entry that doesn't exist). An entry is checked for its form, not for its disk
being on the bus, so you can make one for a disk you haven't put in yet, as a UEFI board keeps a USB
stick's entry while it's unplugged. At boot, an entry whose disk isn't there is skipped.

The NVRAM is on the chip's item, so it goes where the chip goes: take the EEPROM out and its boot
entries go with it. A computer's parts view marks the disk the chip boots (`SATA 3, boots`): the
first disk on the bus that its entries name. Crafting the chip alone wipes its NVRAM too.

## In mcos

### /dev/eeprom

`/dev/eeprom` is the chip's image, as `flashrom -r` and `-w` read and write a board's flash.

- **Read** gives the whole image, as it was when the file was first read. Seeking back to the start
  reads it afresh.
- **Write** keeps what is written until the file is closed, and then replaces the whole image in
  one call, all or nothing, as a firmware flash commits. Until then the chip is as it was.
- For `sys.poll`, the file is always ready to read and to write, and never has `PRI` (the chip has
  no interrupt).

```sh
cp /dev/eeprom /home/eeprom.bak   # back it up
cp /home/new.lua /dev/eeprom      # flash a new image
reboot                            # run it
```

### Its boot variables: /sys/firmware/efi/efivars

The NVRAM's variables are files to read, as Linux's efivarfs shows a UEFI board's, a line each:

```sh
$ ls /sys/firmware/efi/efivars
Boot0000  Boot0001  BootCurrent  BootOrder
$ cat /sys/firmware/efi/efivars/BootCurrent
0001
$ cat /sys/firmware/efi/efivars/Boot0001
id:9a02c3e1-5b7d-4f0e-8c21-3d6e7f1a2b4c /init.lua tiny OS
$ cat /sys/firmware/efi/efivars/BootOrder
0001
0000
```

An entry whose disk isn't on the bus says `not present` on a second line. Linux's files are binary
and named with UEFI's vendor GUID (`BootOrder-8be4df61-...`); mcos's are text, with plain names (a
closed set: no vendors). To change them, use [`efibootmgr`](/programs/efibootmgr/), or the methods
below.

### Its ioctls are its methods

The file's ioctls are the methods below, by name, in any open mode: `get` gives the image and
`set` flashes one at once, with no close to wait for. `setLabel` (the kernel's, on every device
file) puts a label on the chip's item; a chip with no item to keep it on refuses with `EPERM`.

<!-- lua-fragment -->
```lua
local image = sys.ioctl(sys.fileno(io.open("/dev/eeprom")), "get")
```

## Methods

### get() → string

The chip's contents.

### set(data) → nothing

Replaces the chip's contents with `data`. It takes effect at the next power-on.

### getBoot() → table

The boot variables: `entries`, a list of `{number, disk, path, label}` by number; `order`, a list of
numbers; `next`, a number, or nil when there's no BootNext; and `current`, the entry that booted, or
nil.

### setBootEntry(number, disk[, path[, label]]) → nothing

Makes boot entry `number` (0 to 7), or replaces it: `disk` its id (`id:<uuid>`, on the bus or not),
`path` the file it boots (`/init.lua` if none: starting at `/`, printable, no spaces, at most 128
characters), and `label` (at most 32 printable characters). Fails, with why, for anything else.

### removeBootEntry(number) → nothing

Removes an entry, from `BootOrder` and as `BootNext` too. Fails for an entry that isn't there.

### setBootOrder(numbers) → nothing

Sets the order entries are tried in: a list of entries' numbers, each one there, none twice. An
empty list clears it.

### setBootCurrent([number]) → nothing

The bootloader's: the entry it's booting, or none when it boots by its fallback. It's kept until the
computer's off. Fails for an entry that isn't there.

### setBootNext([number]) → nothing

Sets the entry to boot next time only, or clears it with none. The bootloader clears it as it boots
it.

## Events

None.

## Examples

### Back up the chip

```lua
{% include eeprom-backup.lua %}
```
[Download eeprom-backup.lua](/examples/eeprom-backup.lua)

### Flash a new image, safely

Refuses a file that does not compile (it would leave the computer unable to boot), keeps the old
image, and reads the chip back to check.

```lua
{% include eeprom-flash.lua %}
```
[Download eeprom-flash.lua](/examples/eeprom-flash.lua)

```sh
lua eeprom-flash.lua /home/boot.lua
reboot
```

### Boot another disk once

Boots another disk next time only, then this one again: it makes the disk a boot entry (if it hasn't
one) and sets `BootNext`, through the file's ioctls, then reboots. `efibootmgr -c` and `-n` do the
same as commands.

```lua
{% include eeprom-boot-once.lua %}
```
[Download eeprom-boot-once.lua](/examples/eeprom-boot-once.lua)

```sh
lua eeprom-boot-once.lua storage1
```
