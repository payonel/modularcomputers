---
title: Talking to devices
section: Your own OS
order: 4
---

Your shell from [A command shell](/os/shell/) can already reach every device on the bus. This page
uses it on a [redstone card](/devices/redstone-card/): setting its output, taking its events, and
running a program you write for your OS. The same steps work for any device: its page in
[Devices](/devices/) lists its methods.

## What you need

- [ ] Your OS disk with the shell's `/init.lua`, booted with `efibootmgr -n 0` and `reboot`.
- [ ] A [redstone card](/devices/redstone-card/) in the computer, and a redstone lamp against the
  case's north side (or any side: change `north` below to it).

## The whole thing

A program for your OS, `/blink.lua` on your OS disk (beside `/init.lua`):

```lua
{% include os-blink.lua %}
```

[Download os-blink.lua](/examples/os-blink.lua)

At your OS's prompt:

```
# methods redstone
get([sides]): every face as {level ..., powered ...} by name ...
pull(): the oldest change to a face, from wherever it came, as {name, side, level, powered} ...
set(sides): sets each face named: powered, the signal it puts out (0 to 15) ...
# call redstone set {north = {powered = 15}}
{down = {level = 0, powered = 0}, east = ..., north = {level = 15, powered = 15}, ...}
# call redstone set {north = {powered = 0}}
...
# wait 1
exp:0
# call redstone pull
{level = 15, name = "redstone", powered = 15, side = "north"}
# run /blink.lua 3
blink 1
blink 2
blink 3
```

The lamp lights at the first `call`, goes dark at the second, and blinks three times with `run`.

## Step by step

### 1. Find out what it can do

`methods redstone` asks the board for the card's methods (`board.methods`) and each one's line of
documentation (`board.doc`). Every device describes itself this way, so `methods` works on a device
you've never used: try it on `sata:0` or `eeprom`.

### 2. Call a method

`call redstone set {north = {powered = 15}}` is the same as this line in a program:

```lua
board.invoke("exp:0", "set", {north = {powered = 15}})
```

`set` takes a table of faces, each with the signal to put out (`powered`, 0 to 15), and answers with
every face as it is now: `level` is the signal there, `powered` this card's own. Faces are `north`,
`south`, `east`, `west`, `up` and `down`, or `front`, `back`, `left` and `right` from the case's front
(see [Faces](/concepts/faces/)).

### 3. Take its events

A device with news raises its [interrupt](/concepts/interrupts/), and keeps the news until a
program takes it. The redstone card keeps an event for every change to a face, whoever made it: your
`set`, a lever, another card.

- `wait 1` waits up to a second for any device's interrupt (but the keyboard's) and names the ones
  raised: `exp:0`, the card.
- `call redstone pull` takes its oldest event: the face, its level, and this card's own signal.
- Call `pull` until it answers `nil`, and the card has nothing left: its interrupt drops, and
  `wait` waits again.

That's the pattern for every device: wait for its interrupt, then call the method its page names to
take what it has (`pull` for a redstone card or a sensor, `read` for a terminal, `changes` for a
disk). Interrupts are *level-triggered*: a device's stays raised while it has news, so nothing is
missed between the wait and the take.

Flip a lever beside the case, then `wait 1` and `call redstone pull`: the event's `level` is the
lever's signal, and `powered` stays your card's own (0), so you can tell a signal coming in from
one your card puts out.

A redstone card does [one command at a time](/concepts/interrupts/#one-command-at-a-time): a `set`
takes effect at its next tick and ends in a `set` event, and a command given before then answers
`false, "busy"`. Typed at your shell, a tick has always gone by; a program giving commands in a row
waits for the end event (`wait`, then `pull`) before the next, as every OS does for a busy device.

### 4. Run a program

`run /blink.lua 3` loads `/blink.lua` from the boot disk and runs it with `3` as its argument
(`arg[1]`). The program:

- finds the first redstone card on the bus with `board.list()`, by its type;
- sets the face on, sleeps half a second (`board.waitForInterrupt` on a device that isn't there:
  nothing can wake it early), sets it off, sleeps again, as many times as asked;
- prints with your OS's `print`, which `run` gives it;
- returns, and your shell's prompt comes back.

This is all a program is to your OS: a Lua file it loads and calls. In mcos, a program is far more
(a process of its own, files, signals), but every one of those is code like your shell's, built on
the same `board` calls.

## Where to go from here

- **More commands.** A `sleep`, a `blink` of your own, a `label` that calls `board.setLabel`: each
  is a function in the `commands` table.
- **Other devices.** A [network card](/devices/network-card/)'s `send` and `pull` let two of your
  computers talk; a [sound card](/devices/sound-card/) plays samples. Their pages list every method.
- **Running two things at once.** Your shell runs one command at a time. An OS that runs several
  programs uses Lua's coroutines, one a program, and the board's `trap` and `addstop` to switch
  between them, as [mcos](/mcos/) does. The [firmware's comments](https://github.com/payonel/modularcomputers/blob/master/common/src/main/resources/data/modularcomputers/lua/cpu/machine.lua)
  explain them, and mcos's kernel (`/init.lua` on any mcos disk) is a whole OS built on them.

Back to [Your own OS](/os/).
