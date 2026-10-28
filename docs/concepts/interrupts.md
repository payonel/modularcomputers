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
and raises its interrupt. For a redstone card, an event says the face and its level when the world
or another card changed it; the card's own changes are its `set`'s end, which says every face.

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

## Device calls never wait

Calling a device's method never waits for the device: it answers at once with what the device has
now. A terminal's `read` with no keys typed gives `nil`; a sensor's `pull` with nothing sensed gives
`nil`; an internet card's body not arrived yet says so. A method that starts something slow (a robot
walking, a craft, a download) answers as soon as it has started, and the device says how it went
later, with an event, raising its interrupt. Waiting is one call only: `board.waitForInterrupt`.

So a program that wants the next key waits for the device's interrupt, then reads, as real hardware
is used: reading a serial port's data register gives what's there, and the CPU sleeps on the port's
interrupt until there's more. Reading in a loop without waiting would spin, calling the device
thousands of times for nothing, at the CPU's running power.

In [mcos](/mcos/) a read does block: `io.read()` returns when there's a line. That's the kernel's
work, the same wait and read done for you: it puts your process to sleep until the device's
interrupt, and gives you what the device had.

## One command at a time

A device that acts over time is **buffered**, as a real robot's controller runs one motion or a
printer one job: a [robot](/devices/robotics/), an [energy field card](/devices/energy-field-card/),
a [redstone card](/devices/redstone-card/) (a change takes effect at its next tick), a
[robotic piston](/devices/robotic-piston/). It runs one command at a time, and a command that runs
over time ends in an event named after its method, `{event = "walk", ok = true}` (or `ok = false`
and why), which raises its interrupt: the device's "idle again".

- **At the board**, a command given while another runs is refused at once, `false, "busy"`, doing
  nothing: the call still never waits. Wait for the running command's end (its interrupt), then
  give it again. When another device's news ends the wait, the refusal names it too, by its id
  (`false, "busy", "id:..."`): a crafting card waiting for its robot, a robot for its card's craft.
- **Under mcos**, the kernel does that for you: a command to a busy device blocks the thread that
  gave it, and only that thread, until the device can take it, then starts it and returns. So a
  series of commands runs in order, and more work at once means more
  [threads](/mcos/threads/). Each device's page says which of its methods never wait (a robot's
  `get`, `pull` and `stop`; a field card's `release`); the others are commands, reads too (a
  robot's `position`, a redstone card's `get`), so they see the command before them done. A `stop`
  (a line `stop` too) also ends the threads waiting to give that device a command.

A failure ends its own command only: the next one runs as any would. There's no queue to drain
and no fault to clear.

## Why it matters

A waiting computer draws the CPU's waiting rate, much less than its running rate: see
[Power](/concepts/power/).
