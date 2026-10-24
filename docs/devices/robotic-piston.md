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
| Power | Nothing at rest; every push, extend or retract costs five ticks of a running CPU |
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

Its file is `/dev/actuator0` (`actuator1` for the next; its type is `actuator`, as
[`lsdev`](/programs/lsdev/) names it).

- **Read:** it has no data, so a read says what it is, a line each:

  ```
  type actuator
  id id:3f2a...
  path -
  label -
  methods extend push retract
  ```

- **Write:** refused: opening it to write fails with `EACCES`.
- **Poll:** always ready for `IN` (that text). It has no interrupt, so never `PRI`.
- **ioctls:** its methods below, by name, with the side as their argument. A move that is refused is
  `nil`, its message (`cannot push`, `not extended`, `no such face: ...`) and `EINVAL`; a piston that
  has left the bus (its cable cut, say) is `ENODEV`. `setLabel` sets its label, as on every
  device file.

<!-- lua-fragment -->
```lua
local done, why = sys.ioctl(piston, "push", "front")
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

Takes the arm on that side back in; a sticky piston pulls what is in front of it. Fails with
`not extended` when there is no arm out there.

## Events

None.

## Examples

A hidden door: a lever on a redstone card opens it (`retract`) and closes it (`extend`):

```lua
{% include piston-door.lua %}
```

A pusher, as a farm or a conveyor uses: a `push` every few seconds, waiting longer while it is
blocked:

```lua
{% include piston-pusher.lua %}
```

Every piston on the bus, pushed in turn, in bus order:

```lua
{% include piston-wave.lua %}
```
