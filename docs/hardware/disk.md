---
title: Hard Disk
nav: Disk
section: Hardware
order: 80
---

A hard disk stores files, and holds the operating system a computer boots.

<div class="infobox" markdown="1">

### Facts
| | |
|---|---|
| Kind | Item |
| Tiers | 1 (1 MiB), 2 (4 MiB), 3 (16 MiB) |
| Stack | 1 |
| Slot | A SATA socket in the case |
| Power | Tier 1 8 W, tier 2 5 W, tier 3 2 W, while powered |

</div>

## Use

Put disks in the SATA sockets of a [case](/hardware/case/): a tier 1 case has two, tier 2 three,
and tier 3 four. Higher tiers hold more and draw less power. A disk crafted at the table comes
with mcos, the operating system, on it.

A disk has one of two formats, shown in its tooltip with Shift held:

- **hfs** is a filesystem. The disk serves files itself: they are real files in the world save, in
  a folder for the disk. This is what mcos installs on.
- **raw** has no filesystem: it is read and written by byte offset, like a block device.

The [Disk Station](/hardware/disk-station/) changes a disk's format. A program uses a disk through
the [Disk](/devices/disk/) device.

## Crafting

- **Tier 1:** a column of iron ingots on the left and on the right, and in the middle a redstone,
  a piston and a chip, from the top down.
- **Tier 2:** a tier 1 disk in the middle, gold ingots left and right of it, iron ingots in the four
  corners, redstone above and a chip below.
- **Tier 3:** a tier 2 disk in the middle, diamonds left and right of it, iron ingots in the four
  corners, redstone above and a chip below.

Craft a disk alone to wipe it: it comes out blank, and its files are gone. A disk used in another
recipe loses its files too.

## Notes

- A disk keeps its id when you move it to another socket. See [Names](/concepts/names/).
- Hold Shift over a disk in the game to see its format and its draw.
