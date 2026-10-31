---
title: One device, three ways
section: Tutorials
order: 7
---

One job, done at each of the three layers a program can reach a device from (see
[Three ways to reach a device](/concepts/device-layers/)): **when the lever on the computer's west
side flips, the lamp on its east side follows**, and the screen says so. The same redstone card, the
same events; only the code around them changes. Then the same three ways for a robot's events.

## What you need

- [ ] A computer with mcos (see [Your first computer](/tutorials/first-computer/)), with a
  [redstone card](/devices/redstone-card/) in it (`ls /dev` lists `redstone0`).
- [ ] A lever on the case's west side and a redstone lamp on its east side. (Other sides: change
  `LEVER` and `LAMP` at the top of each program.)
- [ ] For layer 1, a second disk for an OS of its own (see [Booting your own code](/os/boot/)).

## The whole thing

**Layer 1, the board:** `/init.lua` on its own disk, an OS of its own:

```lua
{% include layers-board.lua %}
```

**Layer 2, mcos's `sys`:** `/home/lamp-sys.lua`:

```lua
{% include layers-sys.lua %}
```

**Layer 3, the `devices` library:** `/home/lamp-devices.lua`:

```lua
{% include layers-devices.lua %}
```

Run them:

```
$ lua /home/lamp-sys.lua            # layer 2: flip the lever, watch the lamp; Ctrl+C to stop
$ lua /home/lamp-devices.lua        # layer 3: the same
$ efibootmgr -C -d storage1 -L lamp # layer 1: its own disk (storage1), booted once
$ efibootmgr -n 0
$ reboot
```

Each prints `watching the lever`, then `lever on` and `lever off` as you flip it.

## Layer 1: the board, step by step

Your OS is the only code running, so it does everything a driver would.

1. **Find the card.** `board.list()` gives every device on the bus, each `{address, type, id}`; the
   program keeps the first `redstone` one's address (`exp:0`, its slot).
2. **Sleep until it has news.** `board.waitForInterrupt(nil, card)` stops the computer, drawing only
   its waiting power, until the card's interrupt goes up: a face changed. `nil` is no time limit.
3. **Take everything it has.** `board.invoke(card, "pull")` answers `true` and the card's oldest
   change, `{side, level, powered}`, or `true, nil` when it has none left. Device methods never
   wait, so the inner loop takes changes until `nil`: then the card's interrupt is down, and the next
   wait sleeps. (Interrupts are level-triggered: a change that comes while the OS is busy keeps the
   line up, so the next wait returns at once. Nothing is lost.)
4. **Act.** `board.invoke(card, "set", {east = {powered = 15}})` lights the lamp. Its end
   comes as an event too (`{event = "set", ok = true, ...}`, no side); the program
   ignores any event but the lever's face. (A second `set` before the first's end answers `false, "busy"` and the card's id:
   the card takes one command at a time.)

## Layer 2: `sys`, step by step

Under mcos the kernel owns the card; the program asks for it by its file.

1. **Open its file.** `sys.open("/dev/redstone0")` gives a file descriptor. The kernel named the card
   `redstone0`: the first redstone card on the bus.
2. **Sleep until it has news.** `sys.poll({[fd] = {PRI = true}})` blocks the program (other programs
   run meanwhile) until the file has `PRI`: the kernel's word for the card's interrupt. With several
   files in the table, one poll waits for whichever comes first.
3. **Take its changes.** `sys.ioctl(fd, "pull")` is the card's own `pull` method, called through its
   file: the change, or `nil`. A file's ioctls are its device's methods, by name, with the arguments
   given.
4. **Act.** `sys.ioctl(fd, "set", {east = {powered = 15}})`.

The file's text is the card too, which is handy at the shell:

```
$ cat /dev/redstone0
north 0 0
south 0 0
east 15 15
west 15 0
up 0 0
down 0 0
$ echo "east 0" > /dev/redstone0
```

(A line a face: its level, then this card's own signal. A line written sets a face.)

## Layer 3: `devices`, step by step

The same file and the same calls, written shorter.

1. **Open the card by name**, as a table of its methods: `devices.open(devices.wait("redstone"))`.
   `devices.wait` gives the first redstone card's name, waiting for one to come onto the bus if none's
   there yet.
2. **Take its next event**: `devices.event(card)` is the poll and the pull together. With seconds,
   `devices.event(card, 5)`, it gives up after that long with `nil, "timed out"`.
3. **Act**: `card.set({east = {powered = 15}})`, the file's ioctl.

Learn a device from its table: `for name, method in pairs(card) do print(method) end` prints each
method's description, its arguments first (from `/sys/class/redstone/redstone0/methods`).

## The same for a robot

A [bot golem](/hardware/bot-golem/)'s robotics device works the same way: start a move, then take its
event (`walk`: `ok`, or not and why). Its moves answer at once; how they went is the event. It does
[one command at a time](/concepts/interrupts/#one-command-at-a-time): at layer 1 a move given while
one runs answers `false, "busy"` and the id of the device to wait on (wait for its end, then give
it again); at layers 2 and 3 it waits,
its thread only, so moves given in a row happen in order.

```lua
-- Layer 1 (an OS of its own): its address is "exp:3", where it's soldered.
board.invoke("exp:3", "walk", 3)
local ok, done
repeat
  board.waitForInterrupt(nil, "exp:3")
  ok, done = board.invoke("exp:3", "pull")
until done
```

```lua
-- Layer 2 (mcos's sys):
local sys = require("sys")
local fd = assert(sys.open("/dev/robotics0"))
sys.ioctl(fd, "walk", 3)
local done
repeat
  sys.poll({[fd] = {PRI = true}})
  done = sys.ioctl(fd, "pull")
until done
```

```lua
-- Layer 3 (devices):
local devices = require("devices")
local robot = assert(devices.open("robotics0"))
robot.walk(3)
robot.turn("right")             -- (waits for the walk: the robot does one at a time)
devices.sync(robot)             -- waits for the turn too: nothing pending
print(devices.event(robot).ok)  -- the walk's end: true, or false (its why says why)
```

## When it goes wrong

- **Nothing happens when you flip the lever:** check the sides (`cat /dev/redstone0` shows the west
  face's level change), and that the program's still running (`ps`).
- **`no redstone card`** (layer 1) or **`no such device`** (layers 2 and 3): the card isn't in the
  computer, or it's not the computer you think (`lsdev`).
- **The lamp stays lit after the program ends:** a card's signal stays as it was set. `echo "east 0" >
  /dev/redstone0` turns it off.

Next: [Coming from OpenComputers](/tutorials/from-opencomputers/).
