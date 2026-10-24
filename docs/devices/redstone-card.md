---
title: Redstone Card
nav: Redstone Card
section: Devices
order: 50
---

A redstone card puts out a redstone signal on any face of the block it is installed in, and reads
the signal on each face.

<div class="infobox" markdown="1">

### Facts
| | |
|---|---|
| Type | `redstone` |
| Address | An expansion slot path such as `exp:0`, or a hub's |
| Interrupt | Raised while a change to a face waits |
| Power | Nothing at rest; each change of an output costs a tick of a running CPU |
| Info | `kind` (`redstone`) |

</div>

It works where it is installed: in a [case](/hardware/case/) it sets and reads
redstone at the case, in an [expansion hub](/hardware/expansion-hub/) at the hub. A block may hold
several cards. On each face it puts out the strongest signal of any of its cards. Without power it
puts out nothing.

## Faces

All six, by [face](/concepts/faces/) name: `north`, `south`, `east`, `west`, `up`, `down`, or
`front`, `back`, `left`, `right` relative to the block's front. Answers always use the world's
names. Each face has two numbers:

- `level`: the signal there, 0 to 15, coming in or put out by any card in the block.
- `powered`: the signal this card puts out there, 0 to 15.

So a second card beside one that puts out 15 reads `level = 15, powered = 0`.

## In mcos

`/dev/redstone0` (`redstone1` for the next card) is text, a line a face:

```
north 7 7
south 0 0
```

Each line is `face level powered`. A line written, `face powered`, sets that face at once, as a sysfs
attribute does: `north 11`. A line that is not a face and a number fails the write. The file is
always readable; a change since it was read from its start shows to `poll` as `PRI`, and seeking to
the start reads the new state. `tail -f` shows it again each time it changes.

## Methods

### get([sides]) → table

Every face as `{level, powered}`, by name: `{north = {level = 7, powered = 7}, ...}`. With a table,
only the faces it names, as keys or as a list of names.

### set(sides) → table

Takes a table in the same shape and sets each face it names: `{north = {powered = 11}}`.
`powered` is the signal to put out; it is clamped to 0 through 15 and must be an integer
(`north: powered: integer expected`). `level` is what is detected, so it is ignored, and a face the
table leaves out stays as it was. Returns every face, as `get()`. The arguments are all checked
before anything changes. An unknown face fails with `no such face`.

### pull() → event or nil

The oldest change, or nil.

## Events

Any change to a face's state raises an event, from wherever it came: a signal coming in, this
card's own `set`, or another card in the block. Its fields are the face as it is now:

| Field | Value |
|---|---|
| `name` | `"redstone"` |
| `side` | the face, by world name |
| `level` | the signal there, 0 to 15 |
| `powered` | this card's output there |

At most 32 wait. When more come, the oldest is dropped and the next event carries `lost`, the number
dropped. The events are kept across a save, and dropped when no program runs on the computer.

## Example

Pulse a redstone line north for half a second, as a Lua program under mcos:

```lua
local sys = require("sys")
local f = io.open("/dev/redstone0", "w")
f:write("north 15\n")
f:flush()
sys.sleep(0.5)
f:write("north 0\n")
f:close()
```

`flush` makes the line take effect now, instead of at close. From a shell, `echo "north 15" >
/dev/redstone0` sets it, and `cat /dev/redstone0` reads every face.
