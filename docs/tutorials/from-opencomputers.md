---
title: Coming from OpenComputers
section: Tutorials
order: 9
---

Modular Computers is OpenComputers' idea built again, but its computers run mcos, a Linux-like OS,
so the way you do things is Linux's. This page maps what you knew in OpenComputers to how it's done
here. Where something has no equivalent, it says so.

## The bus, not components

OpenComputers called a computer's parts and attached blocks *components*. Here they're *devices*, on
the computer's *bus* (see [the bus](/concepts/bus/)), and mcos shows each as a file in
[`/dev`](/mcos/dev/), named by kind and number: `robotics0`, `storage1`, `field0`.

| OpenComputers | Modular Computers |
|---|---|
| `component.list("robot")` | `ls /sys/class/robotics`, or `require("devices").list("robotics")` |
| `component.proxy(address)`, `component.robot` | `require("devices").open("robotics0")` |
| `component.invoke(address, "move", 1)` | `sys.ioctl(fd, "walk", 1)` on `/dev/robotics0`'s descriptor |
| `component.doc(address, "move")` | `cat /sys/class/robotics/robotics0/methods/walk`, or `tostring(robot.walk)` |
| `component.type(address)` | `cat /sys/class/*/robotics0/type`, or `devices.info(robot).type` |
| a component's address | its id: `cat /sys/class/robotics/robotics0/id` (`id:...`), the same wherever it's plugged in |

With the [devices library](/mcos/devices-library/), a device works as a proxy did:

```lua
local devices = require("devices")
local robot = assert(devices.open("robotics0"))
robot.walk(3)
for name, method in pairs(robot) do
  print(name, method)       -- every method, and what it does
end
```

Every device's methods are listed in the game too: `ls /sys/class/robotics/robotics0/methods`, and
`cat` one for what it does.

## Signals are files you wait on

OpenComputers queued *signals* that a program pulled with `event.pull`. mcos has no signal queue:
a device's news is its `/dev` file's, and a program waits for files with
[`sys.poll`](/mcos/sys-api/), as Linux's `poll`. One wait can cover devices, the keyboard (standard
input), sockets and timers at once.

| OpenComputers | Modular Computers |
|---|---|
| `event.pull("redstone_changed")` | `sys.poll({[fd] = {PRI = true}})` on `/dev/redstone0`, then read it (or its `pull` method) |
| `event.pull(5)` | `sys.poll(fds, 5)`: what's ready, or an empty table after 5 seconds |
| `os.sleep(1)` | `sys.sleep(1)`, or `sleep 1` in the shell |
| `event.listen(...)` (callbacks) | one loop around `sys.poll`, or a [service](/mcos/services/) |
| `component_added` | inotify on `/dev`, or `devices.wait` (below) |
| `key_down` | read standard input: in raw mode (`stty raw`) each key comes as typed |

### Waiting for a device to arrive (component_added)

In OpenComputers you'd pull `component_added` until the right type came. Here a device joining the
bus makes its file appear in `/dev`, and inotify, Linux's file change notices, says so: `CREATE
/dev/charger0`. `devices.wait` does that for you, and a device already there counts, so there's no
race between looking and waiting:

```lua
local devices = require("devices")
print("waiting for a charging pad to be cabled up...")
local name = devices.wait("charger")
print("found " .. name)
```

Done by hand, with the same calls any file watcher uses:

```lua
local sys = require("sys")

local watch = sys.inotify()
sys.watch(watch, "/dev")
while true do
  sys.poll({[watch] = {IN = true}})
  local notice = sys.read(watch, 4096)        -- "CREATE /dev/charger0"
  local name = notice:match("CREATE /dev/(charger%d+)")
  if name then
    print("found " .. name)
    break
  end
end
```

A device leaving the bus is `DELETE /dev/charger0` (OpenComputers' `component_removed`).

## Screens are terminals

OpenComputers' GPU drew characters and colors with calls. A screen here is a VT102/xterm terminal:
a program writes text and escape sequences, as on any Linux terminal (see
[the terminal](/devices/terminal/)).

| OpenComputers | Modular Computers |
|---|---|
| `gpu.set(x, y, text)` | `io.write("\27[" .. y .. ";" .. x .. "H" .. text)` |
| `gpu.setForeground(0xFF0000)` | `io.write("\27[38;2;255;0;0m")` (or `\27[31m`) |
| `term.clear()` | `io.write("\27[2J\27[H")`, or `clear` |
| `gpu.getResolution()` | `sys.ioctl(1, "TIOCGWINSZ")`, or `stty size` |
| `gpu.setResolution(w, h)` | `resize` (xterm's `\27[8;rows;cols t`) |
| `computer.beep()` | `io.write("\a")`: the terminal's bell |

## Files and disks

There's no filesystem component: disks are mounted as directories (the boot disk at `/`, others at
`/mnt/sata/1`, `/mnt/id/...`), and files are Lua's `io` and the [`fs` and `sys`](/mcos/sys-api/)
libraries. `df` and `mount` show the disks. Labels are `label`.

## Networking

OpenComputers' modem sent messages on ports. Here [network cards](/devices/network-card/) carry UDP
and TCP sockets (`sys.socket`, `sys.sendto`, `sys.connect`; see [networking](/mcos/networking/)),
and [`rsh`](/programs/rsh/) and [`rcp`](/programs/rcp/) run commands and copy files between computers.
A wake message is a card's method, `setWake`, on its `/dev` file. The internet card is
`wget`, `curl`, or the `http` library.

## Robots

The bot golem's moves are its [robotics device](/devices/robotics/)'s methods. A move answers as it
starts and ends in an event; the device does one at a time, so the next command waits for it (that
thread only), and a program that wants to know how a move went waits for its end:

| OpenComputers | Modular Computers |
|---|---|
| `robot.forward()` | `robot.walk(1)`, then `devices.sync(robot)` to wait for it; how it went is its `walk` event (`devices.event`) |
| `robot.turnLeft()` | `robot.turn("left")` |
| `robot.detect()`, `robot.detectUp()`... | `robot.scan()`: all six faces at once, by name |
| `robot.swing()` | `robot.swing("front")`: its event says when the block's broken |
| `robot.use()`, `robot.place()` | `robot.use("front")` |
| `robot.suck()`, `robot.drop()` | `take`, `store` (a container), or a [crafting card](/devices/crafting-card/)'s `pickup` and `drop` |
| the crafting upgrade | the [crafting card](/devices/crafting-card/) |
| `computer.energy()` | the power meter in the computer's view (its battery and buffer) |

## Running programs

| OpenComputers | Modular Computers |
|---|---|
| `shell.execute("ls")` | `os.execute("ls")`, or `io.popen("ls")` to read what it prints |
| `computer.shutdown()`, `computer.shutdown(true)` | `poweroff`, `reboot` |
| `computer.energy()`, `computer.maxEnergy()` | `/sys/class/power_supply/case/energy_now` and `energy_full` (µWh), `capacity` (percent), and a battery a bay (`BAT0`) |
| `computer.setBootAddress(address)` | [`efibootmgr`](/programs/efibootmgr/) `-c -d storage1` (an entry, first in the boot order), or `-C` and `-n` for the next boot only |
| `/autorun.lua`, `/home/.shrc` | a [service](/mcos/services/) in `/etc/rc.d`, or `/home/.shrc` for login shells |
| `edit file.lua` | `edit file.lua` |
