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

`/dev/redstone0` (`redstone1` for the next card) is text, a line a face, in this order:

```
north 7 7
south 0 0
east 0 0
west 9 0
up 0 0
down 0 0
```

Each line is `face level powered`. The state is taken at the first read after the file is opened
or sought back to its start, so a read from the start always gives the faces as they are then.

A line written, `face powered`, sets that face at the card's next tick after the line is written
(flushed), as a sysfs attribute does: `north 11`. Relative names work too (`front 15`). A line that is not a face and a
number fails the write with `EINVAL`, and so does an unknown face (`no such face`).

The file is always readable (`poll`'s `IN`) and writable (`OUT`). A change to any face since the
file was last sought to its start shows as `PRI`, and stays until the file is sought there again
(`f:seek("set", 0)`, `sys.lseek(fd, 0, "set")`), as a GPIO's value file does. `tail -f` shows the
faces again each time they change.

```sh
cat /dev/redstone0
echo "north 15" > /dev/redstone0
tail -f /dev/redstone0
```

The file's ioctls are the card's methods, by name, in any open mode:
`sys.ioctl(fd, "set", {north = {powered = 15}})`. A method that fails answers `nil`, its message
and `EINVAL`; a card that is gone, `ENODEV`. `setLabel` labels the card, as for any device (see
[The sys library](/mcos/sys-api/)).

The file's `PRI` comes from the card's events, and polling loses none: the events a poll found are
the next ones `pull` answers, and once a program has pulled them all, `PRI` is off until the next
change (as reading the file from its start turns it off). So a program can wait with `sys.poll`
and then take every change with `pull` until it answers nil.

The card is a [buffered device](/mcos/devices-library/#buffered-devices): a change in the world takes
a tick, so a `set` is applied at the card's next tick, and the card takes one command at a time. A
`set` or a `get` given while a `set` waits is held, in mcos its thread only, until it's applied: a
`get` after a `set` always sees it. A line written to the file waits the same way.

## Methods

### get([sides]) → table

Every face as `{level, powered}`, by world name: `{north = {level = 7, powered = 7}, ...}`. With a
table, only the faces it names, as keys or as a list of names, relative names too (`{"front"}`
answers the face that is the block's front, by its world name). An unknown face fails with
`no such face`.

<!-- lua-fragment -->
```lua
local west = sys.ioctl(fd, "get", {"west"}).west.level
```

### set(sides) → true

Takes a table in the same shape and sets each face it names, at the card's next tick:
`{north = {powered = 11}}`.
`powered` is the signal to put out; it is clamped to 0 through 15 and must be an integer
(`north: powered: integer expected`). `level` is what is detected, so it is ignored, and a face the
table leaves out stays as it was. Answers `true` at once; once applied, its event, `set`, carries
every face as `get()` gives them (`result`). The arguments are all checked
before anything changes, so a table with one bad face changes nothing. An unknown face fails with
`no such face`; a face that is not a table, with `north: table expected`. Each output that changes
costs a tick of a running CPU's power.

<!-- lua-fragment -->
```lua
sys.ioctl(fd, "set", {north = {powered = 15}, up = {powered = 0}})
```

### pull() → event or nil

The oldest event waiting (see Events), or nil when none waits. Each is taken once.

<!-- lua-fragment -->
```lua
local event = sys.ioctl(fd, "pull")  -- {name = "redstone", side = "west", level = 9, powered = 0}
```

## Events

A change to a face's state from the world raises an event: a signal coming in, or another card in
the block. This card's own changes don't: its `set`'s event says them. A face's event is the face as
it is now:

| Field | Value |
|---|---|
| `name` | `"redstone"` |
| `side` | the face, by world name |
| `level` | the signal there, 0 to 15 |
| `powered` | this card's output there |

A `set`'s end is `{event = "set", ok = true, result = {north = {level = 15, powered = 15}, ...}}`.

At most 32 wait. When more come, the oldest is dropped and the next event carries `lost`, the number
dropped. The events are kept across a save, and dropped when no program runs on the computer.

## Examples

A program drives the card by writing lines to its file and reading it back, or through its methods
as ioctls. Run these with `lua name.lua`, from a computer with a redstone card and something on its
faces (add `&` to keep the shell). Each is a complete program. For why they loop without yielding,
see [Writing programs](/mcos/programs/).

### Set an output

```lua
local f = io.open("/dev/redstone0", "w")
f:write("north 15\n")  -- "face strength": 0 to 15
f:close()
```

A line written takes effect when it is flushed (`f:flush()`, or closing the file). From a shell,
`echo "north 15" > /dev/redstone0` does the same.

### Read an input

```lua
local f = io.open("/dev/redstone0")
local text = f:read("a")  -- one line a face: "west 9 0"
local level = tonumber(text:match("west (%d+)"))
print("west is " .. level)
```

Each line is `face level powered`: the signal on that face, and how much of it is this card's own.

### Blink a lamp forever

```lua
{% include redstone-blink.lua %}
```
[Download redstone-blink.lua](/examples/redstone-blink.lua)

### Follow an input

```lua
{% include redstone-mirror.lua %}
```
[Download redstone-mirror.lua](/examples/redstone-mirror.lua)

`sys.poll` blocks until the card reports a change (`PRI`), so the program uses no time while the
signal is steady. The [redstone tutorial](/tutorials/redstone/) walks through both programs.

### A door that closes by itself

```lua
{% include redstone-door.lua %}
```
[Download redstone-door.lua](/examples/redstone-door.lua)

The door's own change (its face powered) wakes `poll` too; that only starts the wait again.

### Count pulses

```lua
{% include redstone-counter.lua %}
```
[Download redstone-counter.lua](/examples/redstone-counter.lua)

The card keeps at most 32 changes: a pulse is two, so with more than 16 pulses a period some are
lost (the log says so). Shorten `PERIOD` for a faster clock.
