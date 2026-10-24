---
title: Robotic Piston
section: Devices
order: 150
---

A robotic piston pushes, as a vanilla piston does, on any side a program names. It is how a computer
moves blocks.

<div class="infobox" markdown="1">

### Facts
| | |
|---|---|
| Type | `actuator` |
| Address | Cabled: it is known by its `id:` name |
| Faces | Every side but the one with its cable port |
| Interrupt | None |
| Power | Nothing at rest; every push, extend or retract that moves costs five ticks of a running CPU |
| Info | none beyond the common ones |

</div>

For the block, the plain and sticky kinds, and how to place it, see
[Robotic Piston](/hardware/robotic-piston/).

## How it moves

A push moves the blocks in front of it with vanilla piston mechanics, slide and all: the arm comes
out, and goes back by itself after 6 ticks. A sticky piston pulls back what is in front of its arm
when the arm retracts. An arm cannot be pushed by other pistons.

A piston has a **front**: the side opposite its cable port. A program can name a side by the world's
names or relative to that front, so `front` is the side away from the cable. See
[Faces](/concepts/faces/). It can take a Bluetooth card in its card slot instead of a cable.

Every move costs power, whether or not it moved anything. Extending an arm that is already out is
free.

## In mcos

Its file in `/dev` (`actuator0`, its type `actuator`, as `lsdev` names it) has no data: a read says what it is
(its type, id and methods). Its methods are the file's ioctls:

```lua
local sys = require("sys")
local piston = assert(sys.open("/dev/actuator0"))
sys.ioctl(piston, "push", "front")
sys.close(piston)
```

## Methods

A side is a face name: `north`, `south`, `east`, `west`, `up`, `down`, or `front`, `back`, `left`,
`right`. A name that is no face fails with `no such face`; a relative name fails when the piston has
no front it knows, or `left` and `right` when its front is up or down.

### push(side) → true

Pushes on that side. The arm goes out and comes back on its own. Fails with `cannot push` on the
cable's side, when an arm is out there already, or when something in front will not move.

### extend(side) → true

Pushes and leaves the arm out until `retract`. Gives `true` also when the arm is out already
(and then it stays out, free). Fails with `cannot push` otherwise as `push` does.

### retract(side) → true

Takes the arm on that side back in. Fails with `not extended` when there is no arm out there.

## Events

None.

## Example

Find the piston on the bus, push east, and report a refusal (EEPROM code):

```lua
local piston
for _, device in ipairs(board.list()) do
  if device.type == "actuator" then
    piston = device.address
  end
end

local ok, why = board.invoke(piston, "push", "east")
if not ok then
  board.invoke("console", "write", "cannot push: " .. why .. "\r\n")
end
```

`board.invoke` gives `true` and the method's results, or `false` and the failure's message, so a
refused push is `false, "cannot push"`.
