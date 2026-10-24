---
title: Names
section: Concepts
order: 50
---

One device has several names, as on a real Linux system, where a disk is `/dev/sda`, and also
`/dev/disk/by-path/...` and `/dev/disk/by-id/...`.

## Slot path

A part in the case's connector has the connector's path: `eeprom`, `sata:0`, `exp:1`. The path is
the device's address on the [bus](/concepts/bus/), as a position on real hardware is. A part moved
to another connector has a new path.

## Id

Every device has an id, `id:<uuid>`, that stays the same however it is connected: in a slot, on a
cable, behind a hub. A part that has been in a computer carries its id on its item. Crafting it
alone makes it a new device and clears the id. External devices are addressed by their id.

## Virtual names

The motherboard provides devices that always exist. `console` is the one you meet: the board's
console, which merges every terminal on the bus into one. Writing to it writes to all of them, and
reading from it reads the keys typed at any of them. Its name is fixed.

## mcos names

[mcos](/mcos/) names a device by its type and its place among devices of that type in bus order:
`terminal0`, `storage0`, `storage1`, `redstone0`. The EEPROM is just `eeprom`, and the first sound
card is `dsp`, then `dsp1`, as OSS names them. These are the names under `/dev`
(see [/dev](/mcos/dev/)), and `lsdev` lists them with each one's slot path and, with `-i`, its id:
see [lsdev](/programs/lsdev/).

Inside, mcos keeps a device by its id: an open file holds an id, so if a part is moved, or another
device takes its name, the file still reaches the same device or fails; it never reaches another
one by accident.

## Why names meet in one place

Everything the host keeps for a program about a device (what it waits for, the streams it reads, its
rings) is kept by the name the program used for it. Interrupts come from the bus by address, and
each one is turned into the program's name before anything compares it. Comparing names as strings
in many places once let a card in a slot fail to wake a computer that waited for it by its id; now
there is one place that does it.
