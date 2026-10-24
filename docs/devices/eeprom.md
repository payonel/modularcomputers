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

The chip a computer comes with boots the first disk, in bus order, that holds `/init.lua`, handing
it the disk's address, and says `boot: no bootable disk` and halts when none does. The code on the
chip runs before any OS, so it calls the board directly (`board.invoke`, `board.list`).

The chip keeps its contents on its item: reads and writes go straight there. A write takes effect at
the next power-on.

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

### Boot one disk only

Writes a new boot image that boots one disk, by its id, wherever it sits on the bus. The image it
writes is firmware-level code: a whole EEPROM program, calling the board directly.

```lua
{% include eeprom-pin-boot.lua %}
```
[Download eeprom-pin-boot.lua](/examples/eeprom-pin-boot.lua)

To undo it, flash the old image back: `lua eeprom-flash.lua /home/eeprom.old`.
