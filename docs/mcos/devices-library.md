---
title: The devices library
section: mcos
order: 75
---

`devices` is a library for programs that work with the bus's devices: what's there, a device as a
table of its methods, and waiting for one to come. It's plain user space, built on what any program
can reach: `/dev` (a device's file, whose ioctls are its methods), `/sys/class` (what each device
is, and its methods' descriptions) and inotify (`/dev`'s names coming and going). [`sys`](/mcos/sys-api/)
stays the kernel's calls; `devices` only makes them easier, as libudev does on Linux.

```lua
local devices = require("devices")

for _, device in ipairs(devices.list()) do
  print(device.name, device.type)
end

local robot = assert(devices.open("robotics0"))
robot.walk(3)                                  -- the device's walk: an ioctl on /dev/robotics0
for name, method in pairs(robot) do
  print(name, method)                          -- each method's description, from /sys
end
devices.close(robot)
```

## Functions

### devices.list([type]) → list

The devices on the bus (only those of `type`, when given), each `{name, type}`, by name: `name` is
its `/dev` file's (`robotics0`), `type` its kind (`robotics`).

### devices.open(name) → device, or nil and why

The device (`robotics0`, or `/dev/robotics0`) as a table of its methods. Each is called as a
function, `device.method(args...)`, and answers as the method does (nil and why when it fails);
`tostring` of one is its description, its arguments first. `pairs` over the table lists its
methods, and nothing else is in it. Its file stays open until `devices.close`. Fails with `no such
device`.

### devices.info(device) → {name, type, id, path, label, methods, fd}, or nil and why

What a device is: one opened, or one by its name (`devices.info("operator0")`, or
`"/dev/operator0"`): its name, type, id (`id:...`, the same wherever it's connected), path (its
slot, `exp:1`; nil when it's cabled), label (nil when it has none), and its methods' names, a list.
An opened one's adds its file's descriptor, `fd` (for `sys.poll`).

```lua
local devices = require("devices")
for _, device in ipairs(devices.list()) do
  local info = devices.info(device.name)
  print(info.name, info.type, info.path or "cabled", table.concat(info.methods, " "))
end
```

### devices.event(device[, seconds]) → event, or nil and why

An opened device's next event (its `pull` method's), waiting for one if none is waiting: the poll on
its file for `PRI` and the pull, the loop every program waiting on a device writes. With `seconds`,
nil and `timed out` after that long. Fails with `not an opened device with events` for a device
with no `pull` (an EEPROM).

```lua
local devices = require("devices")
local robot = assert(devices.open("robotics0"))
robot.walk(3)
local done = devices.event(robot)          -- arrived, or blocked and why
print(done.event, done.why)
```

### devices.ended(device, method[, seconds]) → true, or false and why

For a [buffered device](#buffered-devices): waits for the next end of one of its commands, `method`'s,
and says how it went: `true`, or `false` and why it failed (`stopped` when a `stop` cut it short).
It takes the events before that one: a program that also wants those reads them with
`devices.event` instead. With `seconds`, nil and `timed out` after that long.

### devices.close(device) → true

Closes its file.

### devices.wait(what[, seconds]) → name, or nil and "timed out"

The name of the first device of type `what`, or named `what`, that's on the bus or comes onto it:
it watches `/dev` with inotify and polls it, so one already there counts, and one that comes
while it looks isn't missed. With `seconds`, it gives up after that long.

```lua
local devices = require("devices")
print("place a charging pad on my cable...")
local name = devices.wait("charger")
local pad = assert(devices.open(name))
print("charging pad here: " .. name)
```

## Buffered devices

A device that acts over time runs one command at a time: a robot, an energy field card, a redstone
card, a robotic piston. A command given while another runs **waits**: the call blocks its thread,
and only its thread, until the device is free, then starts and returns. So a series of commands runs
in order, each starting as the one before ends, and a program that wants to do something else
meanwhile does it in another thread:

```lua
local devices = require("devices")
local thread = require("thread")
local robot = assert(devices.open("robotics0"))
local walker = thread.create(function()
  robot.walk(3)        -- returns as it starts
  robot.turn("right")  -- waits for the walk to end, then starts
  robot.walk(2)
end)
-- ... this thread goes on meanwhile ...
walker:join()
```

A read is a command too, so it waits its turn and answers once the command before it has ended. A
device's live state (`get`), its events (`pull`) and its `stop` never wait. A `stop` also ends every
thread waiting to give that device a command: their calls fail with `stopped` (`ECANCELED`). Each
command that runs over time (a walk, a move) ends in an event named after its method, `ok` and, when
it failed, why (one done at once answers in its call):
`{event = "walk", ok = false, why = "blocked: minecraft:oak_log front"}`.

## Events

A device's events are its file's: `devices.event` waits for the next and takes it. Every file open
on a device gets each of its events, so a program and a `cat` of its file each see them all; threads
sharing one file share its events (whichever reads first). To wait for
several things at once (a device, a socket, the keyboard), poll them all yourself, its descriptor
(`devices.info(device).fd`) for `PRI`, and take its events with its `pull` method:

```lua
local sys = require("sys")
local devices = require("devices")

local robot = assert(devices.open("robotics0"))
local fd = devices.info(robot).fd
robot.walk(5)
local done
repeat
  local ready = sys.poll({[fd] = {PRI = true}, [0] = {IN = true}})
  if ready[0] then
    io.read() -- (a line typed meanwhile)
  end
  done = robot.pull()
until done
print(done.event)
```

See [Three ways to reach a device](/concepts/device-layers/) for how this builds on `sys` and the
board.
