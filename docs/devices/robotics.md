---
title: Robotics device
section: Devices
order: 155
---

A robot's robotics device is its body's motors: soldered onto a [Bot Golem](/hardware/bot-golem/)'s
board, it's the only way the golem moves. It walks to a point relative to the robot, turns it, and
stops it, and says how each went.

<div class="infobox" markdown="1">

### Facts
| | |
|---|---|
| Type | `robotics` |
| Where | Soldered onto a bot golem's last expansion lane (`exp:3`) |
| `/dev` | `robotics0` |
| Power | A running CPU's tick for each block walked, and for each turn |

</div>

## In mcos

`/dev/robotics0` reads as what it's doing now, a word: `idle`, `walking` or `turning`. A line
written is an action, taking effect as it's written:

| Line | Does |
|---|---|
| `walk forward [right [up]]` | Walks to the point that many blocks from it, in its own frame |
| `turn left`, `turn right`, `turn around`, `turn degrees` | Turns in place (degrees: to its right; negative, its left) |
| `stop` | Stops, where it is |

A line that isn't one fails the write with `EINVAL`. How an action went comes as an event: the
file is ready for `sys.poll`'s `PRI` while one waits, and the `pull` ioctl takes it. The file's
ioctls are the device's methods, by name, in any open mode: `sys.ioctl(fd, "walk", 3)`.

```sh
echo "walk 3" > /dev/robotics0
cat /dev/robotics0
```

## Methods

### walk(forward[, right[, up]]) → true

Walks to the point `forward` blocks the way it faces, `right` to its right (negative: its left) and
`up` above, by the way it finds. It answers at once; its event says how it went: `arrived`, or
`blocked` and why (`no way there`). A walk or turn under way is given up. It fails with
`too far: at most 32 blocks each way`.

### turn(way) → true

Turns in place: `"left"`, `"right"`, `"around"`, or a number of degrees (to its right; negative, its
left). Its event: `turned`. It fails with `not a way to turn` for any other word.

### stop() → true

Stops walking or turning, where it is. Its event, when it was doing something: `stopped`.

### get() → {state}

What it's doing now: `idle`, `walking` or `turning`.

### set(action) → true

An action as a table: `{action = "walk", forward, right, up}`, `{action = "turn", way}`, or
`{action = "stop"}` (the file's lines are these).

### pull() → event or nil

Its oldest event, or nil.

## Events

| `event` | Other fields | When |
|---|---|---|
| `arrived` | | a walk got there |
| `blocked` | `why` | a walk can't go on: `no way there`; `interrupted` when its world was saved during it |
| `turned` | | a turn is done |
| `stopped` | | `stop` ended a walk or turn |

Its interrupt is raised when an event comes, and stays raised while one waits. With no program
running on its board it takes no data: its events go, and it stops.

## Examples

Walk to a point and say how it went, through the device's file (a write is the action):

```lua
{% include robotics-goto.lua %}
```

Walk round a square with the device's methods, waiting for each move:

```lua
{% include robotics-square.lua %}
```
