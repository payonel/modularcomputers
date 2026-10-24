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

The chip keeps its contents on its item: reads and writes go straight there. A write takes effect at
the next power-on.

## In mcos

`/dev/eeprom` is the chip's image. Reading it gives the contents. What is written is kept until the
file is closed, and then replaces the whole image in one call, all or nothing, as a firmware flash
commits.

```sh
cp /dev/eeprom /backup.lua
cp /new.lua /dev/eeprom
```

Both take effect at the next power-on, so `reboot` to boot the new image.

## Methods

### get() → string

The chip's contents.

### set(data) → nothing

Replaces the chip's contents with `data`.

## Events

None.

## Example

Back up the chip to the first disk, from code running off the chip itself (this assumes an hfs disk
at `sata:0`):

```lua
local _, image = board.invoke("eeprom", "get")
local _, file = board.invoke("sata:0", "open", "/eeprom.bak", "w")
board.invoke(file, "write", image)
board.invoke(file, "close")
```
