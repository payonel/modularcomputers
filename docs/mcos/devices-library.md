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

## Events

A device's events are its file's: wait with `sys.poll` on its descriptor (`devices.info(device).fd`)
for `PRI`, and take them with its `pull` method.

```lua
local sys = require("sys")
local devices = require("devices")

local robot = assert(devices.open("robotics0"))
robot.walk(5)
local fd = devices.info(robot).fd
repeat
  sys.poll({[fd] = {PRI = true}})
  local event = robot.pull()
until event and event.event ~= nil
```
