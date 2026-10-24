---
title: Disk Station
section: Devices
order: 30
---

A Disk Station is a dock for disks. It formats, wipes, clones and installs mcos on a disk without
crafting it alone. Each action is a method, a line written to its file, and a switch in its screen.

<div class="infobox" markdown="1">

### Facts
| | |
|---|---|
| Type | `disk_station` (the kernel names it `diskstation0`) |
| Address | Cabled: it is known by its `id:` name |
| Interrupt | None |
| Power | 1 W at rest; each action costs 2000 units (twenty ticks of a running CPU), paid from the bus's power while a running computer supplies it, else from the station's battery |
| Info | none beyond the common ones |

</div>

For the block and its slots, see [Disk Station](/hardware/disk-station/).

## Target and source

The station works on its **target** disk. Its **source** disk is optional and only read, as a
forensic dock's write blocker is: every write to it fails with `read-only disk`. The source is used
by `clone`.

Both disks, and the station itself, are on the bus of the computers wired to it, beside each other.
A disk behind the station is on the bus only while the station is. See [Bus](/concepts/bus/). Once
on the bus, each disk is a normal [hard disk](/devices/disk/).

## In mcos

`/dev/diskstation0` is a text file. Reading it gives a line a slot:

```
target 2 hfs 40960 2097152 backups
source -
```

For a disk the line is: the slot, its tier, its format, bytes used, capacity, and its label (the
label is empty when it has none). An empty slot is `slot -`.

A line written is an action, taking effect at once: `format`, `wipe`, `clone`, `install` or
`update`. Any other word fails the write (`EINVAL`), and so does an action that fails (`EIO`).

## Methods

Every action fails with `no disk` when the target slot is empty, and with `no power` when neither
the bus nor the battery can pay for it.

### format() → nothing

Puts an empty hfs filesystem on the target. Everything on it is gone.

### wipe() → nothing

Makes the target raw, with no filesystem. Everything on it is gone.

### clone() → nothing

Makes the target a copy of the source: its format and its contents, with the files' times kept.
Fails with `no source disk` when the source slot is empty, and with `doesn't fit` when what is on
the source is larger than the target (a raw source needs a target as big). Nothing changes when it
is refused.

### install() → nothing

Formats the target as hfs, then copies mcos onto it (the mod's latest).

### update() → nothing

Writes the mod's latest mcos files over the target's, `/etc` too. Everything else on the disk is
left, and `/home` is never written over. The target must be hfs.

### get() → {target = disk, source = disk}

The state of the slots. A disk is `{tier, mode, used, capacity, label}`; `label` is left out when
the disk has none, and an empty slot is left out of the table.

### set({action = name}) → nothing

Does an action by its name: `format`, `wipe`, `clone`, `install` or `update`. Any other name fails
with `no such action`.

## Events

None. Changes to the disks are reported by the disks themselves
([Hard disk](/devices/disk/)), so a program that watches a disk sees the work.

## Example

Install mcos on a blank disk in the target slot, then read the slot:

```sh
echo install > /dev/diskstation0
cat /dev/diskstation0
```

The first line of the output starts with `target`, then the disk's tier and `hfs`, and shows the bytes mcos now takes.
