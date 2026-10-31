---
title: Three ways to reach a device
nav: Device layers
section: Concepts
order: 45
---

A program can reach a device in three ways, each built on the one below it, as on a real computer:
a driver talks to the hardware, the kernel gives programs files, and a library makes the files
easy. Which you use depends on where your code runs and how much you want done for you.

```
  Layer 3   the devices library    robot.walk(3)   devices.event(robot)
              │  (a program's help, in user space)
  Layer 2   mcos's sys             sys.open("/dev/robotics0")   sys.poll   sys.ioctl
              │  (the kernel's files: it owns the devices)
  Layer 1   the board              board.list   board.waitForInterrupt   board.invoke
              │  (no OS: the motherboard itself)
            the device             its methods, its events, its interrupt line
```


| | Layer 1: the board | Layer 2: `sys` | Layer 3: `devices` |
|---|---|---|---|
| Where it runs | Code with no OS under it: the EEPROM, [your own OS](/os/), mcos's kernel | Any mcos program | Any mcos program |
| A device is | An address (`exp:0`, `id:...`) | A file (`/dev/redstone0`) | A table of its methods |
| Calling a method | `board.invoke(address, "set", ...)` | `sys.ioctl(fd, "set", ...)` | `card.set(...)` |
| Waiting for news | `board.waitForInterrupt(seconds, address)` | `sys.poll({[fd] = {PRI = true}}, seconds)` | `devices.event(card, seconds)` |
| Taking an event | `board.invoke(address, "pull")` | `sys.ioctl(fd, "pull")` | (`devices.event` takes it) |
| Finding a device | `board.list()` | `/dev`, `/sys/class` | `devices.list`, `devices.wait` |
| Who owns it | You: nothing else runs | The kernel, for all programs | The kernel (the library is only a program's help) |

The tutorial [One device, three ways](/tutorials/device-layers/) does the same job at each layer,
every program complete.

## Three rules, at every layer

- **A device's methods never wait at the board.** A call answers at once with what the device has
  now: a redstone card's `pull` with no change gives `nil`, a robot's `walk` answers `true` as it
  starts, and a [buffered device](/concepts/interrupts/#one-command-at-a-time) given a command
  while it runs one answers `false, "busy", id` (the device to wait on). Under mcos (layers 2 and 3) that command waits
  instead, its thread only, until the device can take it. See
  [Interrupts](/concepts/interrupts/#device-calls-never-wait).
- **News is an event, and an interrupt says it's there.** A device keeps its events until they're
  taken, and its interrupt stays up while one waits (it's level-triggered), so a program never misses
  one by being busy: it waits, then takes them all.
- **Events are the device's own tables**, the same at every layer: `{event = "walk", ok = true}`,
  `{event = "walk", ok = false, why = "too low: 3"}`, `{side = "west", level = 15, powered = 0}`.
  Each device's page lists its events.

## Layer 1: the board

The board's API is the motherboard itself: a global table, `board`, that code with no operating
system under it calls. The [EEPROM](/devices/eeprom/)'s bootloader uses it, mcos's kernel uses it, and
[your own OS](/os/) does. Its calls are listed in [Devices](/devices/#from-the-eeprom-the-board-api).

At this layer your code is the only code: it finds a device in `board.list()`, sleeps on its interrupt
with `board.waitForInterrupt`, and calls its methods with `board.invoke`, which answers `true` and the
method's results, or `false` and why. Nothing is shared, nothing is buffered, nothing is in the way:
it's as close to the hardware as a program gets, and it's what every layer above is made of.

```lua
board.waitForInterrupt(nil, card)          -- sleep until the card has news
local ok, change = board.invoke(card, "pull")   -- take it (nil: nothing left)
```

## Layer 2: mcos's `sys`

Under [mcos](/mcos/) a program never calls the board: the kernel owns every device, and gives each a
file in [/dev](/mcos/dev/), as Linux does. Many programs can share a device this way, and the kernel
decides who waits for what. A program uses a device's file as any file:

- **`sys.open("/dev/robotics0")`** gives a file descriptor.
- **Its ioctls are the device's methods**, by name: `sys.ioctl(fd, "walk", 3)`, answering as the method
  does (nil and why when it fails). A command to a busy device blocks the calling thread until the
  device can take it.
- **`sys.poll({[fd] = {PRI = true}}, seconds)`** sleeps until the device has news: `PRI` is its
  interrupt, as the kernel sees it. Then `sys.ioctl(fd, "pull")` takes an event.
- **Its text is the device too**, for the shell: `cat /dev/redstone0` reads a line a face, and
  `echo "east 15" > /dev/redstone0` sets one. Each device's page says what its file reads and takes.

```lua
local fd = sys.open("/dev/redstone0")
sys.poll({[fd] = {PRI = true}})             -- sleep until the card has news
local change = sys.ioctl(fd, "pull")        -- take it (nil: nothing left)
```

See [The sys library](/mcos/sys-api/) for every call.

## Layer 3: the `devices` library

The [devices library](/mcos/devices-library/) is a program's help with layer 2, in user space, as
libudev is on Linux: it makes nothing new possible, only shorter to write and easier to learn.

- **`devices.open("robotics0")`** gives the device as a table of its methods: `robot.walk(3)` is the
  file's ioctl; `pairs(robot)` lists them, and `tostring(robot.walk)` is what the method does.
- **`devices.event(robot[, seconds])`** waits for the device's next event and takes it: the poll and
  the pull, the loop every program writes, once. **`devices.sync(robot)`** waits until nothing it was
  given is pending (its `sync`, as the kernel waits on it); how each command went is its end, among
  its events.
- **`devices.wait("charger")`** waits for a device to come onto the bus (inotify on `/dev`), and
  **`devices.list`** and **`devices.info`** say what's there.

```lua
local card = devices.open("redstone0")
local change = devices.event(card)          -- the next change, waited for
card.set({east = {powered = 15}})
```

## Which to use

- **Writing an OS, or code for the EEPROM:** layer 1 is all there is.
- **A program under mcos:** layer 3 for most things, layer 2 when you want to wait on several things
  at once (a device and a socket and the keyboard in one `sys.poll`), or to see exactly what happens.
- **At the shell:** a device's file (`cat`, `echo`), layer 2's text.
