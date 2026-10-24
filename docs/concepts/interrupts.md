---
title: Interrupts
section: Concepts
order: 40
---

A computer that is waiting for a key does not ask "has a key been pressed?" a thousand times a
second. It sleeps, and the device wakes it. That is an interrupt.

## Level-triggered

Every device with something to say has an interrupt line. The line is **level-triggered**, as on
real hardware: it is on while the device holds data the program hasn't taken, and off once the
program has read it. The data stays in the device until there is room for it somewhere, so nothing
is lost between the device and the program. The line only says "look now".

When a line goes up while a computer is waiting, the computer is woken at once, as a real interrupt
pulls a CPU out of its halt instruction, instead of at its next tick.

## What has an interrupt

Every kind of device with news: a [terminal](/devices/terminal/) (a key), a
[network card](/devices/network-card/) (a frame), the sensors ([motion](/devices/motion-sensor/),
[audio](/devices/audio-sensor/), [weather](/devices/weather-sensor/)), a
[redstone card](/devices/redstone-card/) (a face changed), a
[disk](/devices/disk/) (a file changed), the [sound card](/devices/sound-card/) (room in its
buffer, or what was queued has played out), the [internet card](/devices/internet-card/) (part of
a response) and so on. The bus itself has one, raised when a device joins or leaves.

A device reports every change to its state, from wherever it came, as an event in its own terms,
and raises its interrupt. For a redstone card, an event says the face, the level, and whether the
change came in from outside, was the card's own, or was another card's.

## How a program sees them

A program waits for an interrupt on some sources (devices, or `bus`), with a time limit or none,
and is answered with the sources that are pending. In [mcos](/mcos/), you rarely do that yourself:
the kernel waits when no process can run, and wakes the process that was blocked reading the device.
A program just reads a device's file and the read returns when there is something
(see [/dev](/mcos/dev/)), or polls many files at once with `sys.poll`
(see [The sys library](/mcos/sys-api/)).

The firmware also pushes device data into a ring in the program's memory by credit, a little like
DMA: the program gives a ring of some size, and the firmware fills it with what the device sends,
as long as there is room. This means a key needs no call to arrive, so the latency of a key is one
round trip. When a device has more data than the ring holds, the device keeps the rest: a full
ring is a pause, never a silent drop.

## Why it matters

A waiting computer draws the CPU's waiting rate, much less than its running rate: see
[Power](/concepts/power/).
