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
| Type | `disk_station` |
| In `/dev` | `diskstation0`, `diskstation1`... in bus order |
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
on the bus, each disk is a normal [hard disk](/devices/disk/), with its own `/dev/storage` file.

## In mcos

### /dev/diskstation0

The station's file is text, as a sysfs attribute is.

- **Read** gives a line a slot, the target first:

  ```
  target 2 hfs 40960 4194304 backups
  source -
  ```

  For a disk the line is: the slot, its tier, its format, bytes used, capacity, and its label (a
  disk with no label ends after its capacity). An empty slot is `slot -`. The file holds the slots
  as they were when it was first read; seek back to the start to read them afresh.
- **Write**: each line written is an action, done at once: `format`, `wipe`, `clone`, `install` or
  `update`. Any other line fails the write with `EINVAL`, and so does an action the station refuses
  (no disk, no power, it doesn't fit). Through `io`, a write is buffered: the line goes when the
  file is flushed or closed, and that call reports the failure.
- For `sys.poll`, the file is always ready to read and to write, and never has `PRI` (the station has
  no interrupt).

```sh
cat /dev/diskstation0
echo install > /dev/diskstation0
```

### Its ioctls are its methods

The file's ioctls are the methods below, by name, in any open mode. They say why an action was
refused, as a write cannot: the ioctl gives `nil`, the station's message (`no power`, `doesn't
fit: ...`) and `EINVAL`. An action that worked gives `true`.

<!-- lua-fragment -->
```lua
local ok, why = sys.ioctl(sys.fileno(io.open("/dev/diskstation0")), "install")
```

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
the source is larger than the target: an hfs source's used bytes, or a raw source's whole capacity
(a raw source needs a target as big). Nothing changes when it is refused.

### install() → nothing

Formats the target as hfs, then copies mcos onto it (the mod's latest).

### update() → nothing

Writes the mod's latest mcos files over the target's, `/etc` too. Everything else on the disk is
left, and `/home` is never written over. The target must be hfs (a raw one fails with
`raw disk: no filesystem`).

### get() → {target = disk, source = disk}

The state of the slots. A disk is `{tier, mode, used, capacity, label}`; `label` is left out when
the disk has none, and an empty slot is left out of the table.

### set({action = name}) → nothing

Does an action by its name: `format`, `wipe`, `clone`, `install` or `update`. Any other name fails
with `no such action`.

## Events

None. Changes to the disks are reported by the disks themselves
([Hard disk](/devices/disk/)), so a program that watches a disk sees the work.

## Examples

### Do one action, checking first

Through the station's file: reads the slots, checks the action can be done, writes it, and reads
the result.

```lua
{% include disk-station-do.lua %}
```
[Download disk-station-do.lua](/examples/disk-station-do.lua)

```sh
lua disk-station-do.lua install
```

### Clone a disk

Through the station's methods, which say why when they refuse.

```lua
{% include disk-station-clone.lua %}
```
[Download disk-station-clone.lua](/examples/disk-station-clone.lua)

### A duplicator

Left running, it makes each disk put in the target slot a copy of the source, or a fresh mcos
install when there is no source, and logs each one.

```lua
{% include disk-station-duplicator.lua %}
```
[Download disk-station-duplicator.lua](/examples/disk-station-duplicator.lua)
