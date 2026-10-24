---
title: Writing programs
section: mcos
order: 35
---
Every mcos program is a Lua script. You write one with [`edit`](/programs/edit/), run it by its
path, and stop it with Ctrl+C. This page is about the one thing that differs most from how Lua
programs are written for other Minecraft computers: **you do not yield, and you do not need an event
loop.** Your program is a plain program. When it has nothing to do, it waits.

## Waiting is how a program idles

A call that has to wait blocks your program until it can finish. The computer runs other things
meanwhile and wakes yours when it is time. There are three ways to wait, and they cover everything:

| To wait for | Use | Example |
|---|---|---|
| Some time | `sys.sleep(seconds)` | `sys.sleep(0.5)` |
| One device or file | a read that blocks | `sensor:read("l")` gives the next line, when there is one |
| Several things at once, or a change | `sys.poll(fds, seconds)` | wait for a card to change, a keypress and a timeout together |

A device's file is the way into it, and an event is a line you read from it, so waiting for an event
is just reading. See [/dev](/mcos/dev/) for the files, [the sys library](/mcos/sys-api/) for
`sys.sleep` and `sys.poll`, and [each device's page](/devices/) for what its file says.

## A loop that never ends is fine

This is a whole program. It blinks a lamp forever:

```lua
{% include redstone-blink.lua %}
```
[Download redstone-blink.lua](/examples/redstone-blink.lua)

Put it in a file, and with a [redstone card](/devices/redstone-card/) in your computer and a lamp
against that face of the case, run it:

```sh
edit /home/blink.lua
/home/blink.lua
```

What to notice:

- The loop is `while true do`. Nothing is passed back to a scheduler and nothing calls `yield`.
- `sys.sleep` is the only place the program waits. Between two sleeps it runs at once.
- It never ends on its own. Press **Ctrl+C** in the terminal to stop it.

Even a loop that never waits is not an error. The computer gives your program a fixed slice of work
at a time, [freezes](/concepts/persistence/) it where it is when the slice is spent, and carries on
with the server; your program is thawed in its next slice, exactly where it stopped. It does mean that a loop that does not wait uses all the time it is given, so wait when
there is nothing to do.

## Waiting for a change

A redstone card's file also tells you when something changed. `sys.poll` waits for that, so this
program uses no time at all while the signal is steady:

```lua
{% include redstone-mirror.lua %}
```
[Download redstone-mirror.lua](/examples/redstone-mirror.lua)

Power the west face of the case and the east face follows it. The `PRI` event means "the device's
state changed", which is how a redstone file says an input moved.

## Waiting for an event

A sensor's file has no state to read, only events: one line each. Reading it blocks until there is a
line. This program sounds an alarm for each thing the [motion sensor](/devices/motion-sensor/) sees:

```lua
{% include motion-alarm.lua %}
```
[Download motion-alarm.lua](/examples/motion-alarm.lua)

`motion:read("l")` is the program's whole event loop. Events that arrive while the alarm sleeps wait
in the sensor for the next read; a sensor that fills up reports how many it dropped, which is the
`lost` line the program skips.

## Doing two things at once

Two programs can run at once. End a command with `&` to start it in the background, list the
running ones with [`ps`](/programs/ps/), and stop one with [`kill`](/programs/kill/):

```sh
/home/blink.lua &
ps
```

Inside one program, `sys.poll` waits on several descriptors at once, and says which of them is
ready, so one loop can serve a sensor and the keyboard together.

## If you know OpenComputers

| In OpenComputers | In mcos |
|---|---|
| `event.pull()` in a loop | A blocking read on the device's file, or `sys.poll` |
| `os.sleep(t)` | `sys.sleep(t)` |
| A program that does not yield for a while is killed | No such limit: a busy program is frozen and thawed |
| `component.redstone.setOutput(sides.north, 15)` | `redstone:write("north 15\n")` on `/dev/redstone0` |
| `sides.north` | the string `"north"`: faces are strings |
| Components found by address | Devices found by [their names](/concepts/names/) |

The [tutorials](/tutorials/) use these programs step by step.
