---
title: The bus
section: Concepts
order: 10
---

The bus is the list of devices a computer can talk to. A program never reaches a block in the
world directly: it reaches whatever is on its computer's bus.

## What is on a bus

- **The case's own parts.** The EEPROM, the disks in the SATA bays and the cards in the expansion
  slots each sit on a fixed connector of the board: `eeprom`, `sata:0` to `sata:3`, `exp:0` to
  `exp:3`. A case wires up the connectors it has; a [tier 1 case](/hardware/case/) has fewer than a
  tier 3 one. A connector always has the same place on the bus, whatever the case.
- **External devices.** A [screen](/hardware/screen/) or a [Disk Station](/hardware/disk-station/)
  joins the bus by [cable](/hardware/cable/) or through an
  [expansion hub](/hardware/expansion-hub/). A block right against a computer, with neither's front
  between them, is on its bus without a cable, as a drive in a dock is. Screens and hubs carry the
  connection on to the next block; other devices end it. A [Bluetooth card](/devices/bluetooth-card/)
  can also bring a device onto the bus.
- **Virtual devices.** Some always exist. The `console` is one: it needs no hardware, so even the
  [EEPROM](/hardware/eeprom/)'s tiny program can write to a screen. See [Names](/concepts/names/).

## The CPU decides how big the bus is

How many devices fit comes entirely from the [CPU](/hardware/cpu/): a tier 1 bus holds 4, tier 2
holds 16 and tier 3 holds 64. The case's own devices count against it as much as external ones, so
a tier 1 bus holds the essentials: an EEPROM, a battery, a disk and one card or external device.

When a bus is full, the devices that don't fit wait, shown as such, and are not on the bus. A
program can ask how full the bus is.

## Bus order

The order of the bus is like a PC's UEFI boot order. The case's own connectors come first, in
their fixed order. External devices follow in a saved list that you, the player, can reorder. The list is kept by device id, so a device in a chunk that loads late, or one that is
unplugged for a while, keeps its place and shows as offline meanwhile. A device seen for the first
time goes to the end of the list. You can disconnect an external device: it stays in the list, in
its place, but is not on the bus.

The [boot EEPROM](/mcos/boot/) tries the disks in bus order, so the order decides which disk
starts the computer.

## A device behind another

Some devices sit behind a host, as a USB dock's drives sit behind the dock. The disks in a
[Disk Station](/hardware/disk-station/) are on the bus only while the station is. If the station
is disconnected, missing, or held by another computer, its disks show offline too, and if the bus
has no room for the station, they stay off. This is one rule in the bus order and holds for every
such host.

## One bus at a time

A device is on one computer's bus at a time. If two computers could reach the same
[screen](/hardware/screen/) or cabled disk, whichever computer has it first holds it; the other
sees it as absent until the first lets go (the device is unplugged, or that computer unloads or
loses its CPU). Then the others lay out their buses again and the first to do so takes it. Holds
are worked out afresh as computers load and are never saved.

Two computers on one cable are therefore not sharing a disk; they are two hosts on the same cable.
What they can share is the network: see [Network card](/devices/network-card/) and
[Networking in mcos](/mcos/networking/).

## Hot-plug

External devices come and go while a computer runs. A device that isn't there isn't on the bus, and
calls to it fail at once instead of stopping the machine. The bus raises its own
[interrupt](/concepts/interrupts/) when it changes, and mcos reads it again and mounts or unmounts
disks as they come and go (see [Filesystem](/mcos/filesystem/)).
