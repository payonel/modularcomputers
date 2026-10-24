---
title: Redstone
section: Tutorials
order: 3
---

In this tutorial you put a [redstone card](/devices/redstone-card/) in your computer, read a lever
from a program, and switch a lamp on from the shell. It also shows how a computer names its sides.

## What you need

- [ ] A working computer from [Your first computer](/tutorials/first-computer/), switched off
- [ ] A **Redstone Card** (its recipe is on its [page](/devices/redstone-card/))
- [ ] A redstone lamp and a lever
- [ ] Some redstone dust, if you want the lamp a little way from the case

## Steps

### 1. Fit the card

Turn the computer off, and open the case view. Put the Redstone Card in an expansion (EXP) socket.
Every socket under EXP takes any card. A tier 1 case has two; on higher tiers the first is trimmed
in gold because it also takes a screen, but a card fits there as well.

You will see the bus line at the bottom of the case view count one more device. The card is on the
[bus](/concepts/bus/), a card in a slot is `exp:0` or `exp:1`, and a card works only while the
computer it is in is running.

### 2. Boot and find the card

Turn the computer on and wait for the prompt. Then list its devices:

```
lsdev
```

You will see a table that includes a row named `redstone0`, type `redstone`, and the slot it is in
(`exp:0`, say). Devices with data have a file in `/dev`, so there is now `/dev/redstone0`
([devices in mcos](/mcos/dev/)).

### 3. Read the card

```
cat /dev/redstone0
```

You will see one line per face, each `face level powered`:

```
north 0 0
south 0 0
east 0 0
west 0 0
up 0 0
down 0 0
```

`level` is the redstone signal on that face of the case, 0 to 15, from outside or from any card in
this block. `powered` is the signal this card puts out there. The names are the world's own
[faces](/concepts/faces/): `north`, `south`, `east`, `west`, `up`, `down`.

### 4. Read a lever

Place a lever against the case (on its east face, say), and flip it. Run `cat /dev/redstone0`
again: you will see the `east` line change, `east 15 0` (a signal coming in, none of it from the
card). Flip it back and the line returns to zero.

To wait for a change instead of asking again, run `tail -f /dev/redstone0`: it shows a line each time
a face changes, until you press `Ctrl+C`. See [tail](/programs/tail/).

### 5. Drive a lamp

Put a redstone lamp on the west side of the case, touching it. Write a line, a face and a strength,
to the card's file:

```
echo "west 15" > /dev/redstone0
```

You will see the lamp light at once. A line written takes effect immediately.

```
echo "west 0" > /dev/redstone0
```

and it goes dark. If you write a line that is not a face and a number, the write fails and the shell
tells you.

### 6. A blinking lamp, forever

A short program is a loop. Run `edit blink.lua` and type this (or copy it, see the download below):

```lua
{% include redstone-blink.lua %}
```
[Download redstone-blink.lua](/examples/redstone-blink.lua)

Your lamp is on the west side, so change the line `local FACE = "north"` to `local FACE = "west"`.
Save with `Ctrl+O` and Enter, leave with `Ctrl+X`, and run it:

```
lua blink.lua
```

The lamp blinks, and keeps blinking. Press `Ctrl+C` to stop it.

Notice what the program does not have: no event loop, no `yield`, no scheduler to hand control back
to. It is a plain `while true do` loop, and `sys.sleep` is where it waits. Programs in mcos block
instead of yielding, and the computer runs other things while one waits. See
[Writing programs](/mcos/programs/).

### 7. Wait for the lever

A program should not look at the card over and over. It can wait until the card says something
changed. This one copies the lever's signal on the west face to a lamp on the east face, and uses no
time at all while the lever stays where it is:

```lua
{% include redstone-mirror.lua %}
```
[Download redstone-mirror.lua](/examples/redstone-mirror.lua)

Put a lamp on the east side, a lever on the west, and run it with `lua mirror.lua`. Flip the lever:
the lamp follows. The call that waits is `sys.poll`; `PRI` is the event for "this device's state
changed", which is how a redstone file reports that an input moved.

### 8. Relative faces

A face named `west` is west wherever the case is turned. If you would rather say "the side to my
right", the card also takes `front`, `back`, `left` and `right`, measured from the case's front, as
someone standing facing the case sees them. A program written that way works however the case is
placed. The card's file always reports the world's names. See [faces](/concepts/faces/).

## When something is missing

| What you see | What it means | The fix |
|---|---|---|
| No `redstone0` in `lsdev` | The card is not in a socket, or the computer was started before it was fitted. | Turn the computer off, fit the card, start again. |
| `/dev/redstone0: No such file or directory` | Same as above. | The same. |
| The lamp does not light | The card has no power, or the lamp is not touching that face. | Check the computer is on and that the face you wrote is the one the lamp touches. |
| A write fails | The line is not `face number`. | Write `west 15`, not `west on`. |

## Congratulations

Your computer now reads and drives redstone. The full device is on the
[redstone card](/devices/redstone-card/) page, with its methods and events. Next,
[connect two computers](/tutorials/two-computers/).
