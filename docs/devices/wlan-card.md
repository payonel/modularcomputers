---
title: WLAN Cards
nav: WLAN Cards
section: Devices
order: 70
---

A WLAN card is a [network card](/devices/network-card/) that sends frames through the air instead of
over cables. It has the same methods, plus a signal strength.

<div class="infobox" markdown="1">

### Facts
| | |
|---|---|
| Type | `network` |
| Address | An expansion slot path such as `exp:0` |
| Tiers | 1 (reaches 16 blocks), 2 (reaches 400 blocks) |
| Interrupt | Raised while frames wait; also a wake line |
| Power | 5 W at rest; a send costs more the farther the signal reaches |
| Frame size | At most 8192 bytes |
| Info | `kind` (`tier1_wlan` or `tier2_wlan`), `wireless` (true) |

</div>

## Reach

A frame reaches every WLAN card within the **sender's** signal strength, in blocks, in the same
world. Any tier hears any other: a tier 1 card hears a tier 2 card's frame if the sender's signal
reaches it. A WLAN card hears only WLAN cards, never a wired one.

A card starts at its tier's strongest signal. A program lowers it to save power and raises it again,
up to the tier's:

- A send costs `ceil(strength * 100 / 16)` power units: a tier 1 card at full strength costs one
  tick of a running CPU, a tier 2 card at full strength 25 ticks' worth.
- A `distance` in a received frame is the straight line between the two blocks, in meters, to one
  decimal place. A card at strength 0 reaches only cards in its own block.

A card tells the world where it is as its block ticks. A card that has stopped
(its chunk unloaded, its block broken, the card taken out) is not on the network.

Everything else (addresses, `send`, `broadcast`, `receive`, the `"busy"` and `"unreachable"`
answers, nothing being dropped, the traffic log, and wake-on-LAN) works as it does for a wired card;
read [Network Card](/devices/network-card/) for it.

## In mcos

mcos reaches it through sockets as `wlan0` (`wlan1` for the next); see
[Networking](/mcos/networking/). Its file in `/dev` is a network card's (`network0`, counted with
the wired ones): its traffic, a line a frame, read only. The distance in each line is the card's
own measure, which no socket gives:

```
in 5f0c2a9e-41d7-4c4e-9b0a-2f3c4d5e6f70 3.0m 9 broadcast
out broadcast - 9 2 took it
```

Reading gives the next line and waits for one; opening it for writing fails with `EACCES`; `poll`
gives `IN` while a line waits and `PRI` while the card has news. Its methods are the file's ioctls,
in any open mode:

```lua
local sys = require("sys")
local card = assert(sys.open("/dev/network0"))
print(sys.ioctl(card, "setStrength", 16))  -- 16: how far it reaches now
print(sys.ioctl(card, "getStrength"))
```

## Methods

The methods of a [network card](/devices/network-card/): `address`, `send`, `broadcast`, `receive`,
`pull`, `getWake` and `setWake`. And these two.

### getStrength() → blocks

How far the signal reaches now, in blocks.

### setStrength(blocks) → blocks

Sets how far the signal reaches, from 0 up to the tier's range (16 or 400). A number outside that
is clamped to it, so asking for a huge one gives the tier's most. It must be an integer. Gives what
it is set to. The setting is kept with the card across saves.

## Events

A frame that arrives is an event, taken with `receive`: `{from, data, distance, broadcast}`, and
each frame in or out is a line of its traffic log. See [Network Card](/devices/network-card/).

## Examples

Each is a complete program for a computer with a WLAN card; run it with `lua`.

### Turn the signal down

Finds the weakest signal that still reaches another card, with `setStrength` and `send` as ioctls,
and leaves the card there: `lua wlan-tune.lua <the other card's address>`.

```lua
{% include wlan-tune.lua %}
```
[Download wlan-tune.lua](/examples/wlan-tune.lua)

### A beacon

Says the computer's name over the air every few seconds, only as far as it should reach:
`lua wlan-beacon.lua 20 &`. Hear it elsewhere with `nc -ul 4001`.

```lua
{% include wlan-beacon.lua %}
```
[Download wlan-beacon.lua](/examples/wlan-beacon.lua)

### A site survey

Listens for a while through the card's file, and writes down every card heard and how far, nearest
first: `lua wlan-survey.lua /home/survey.txt 30`.

```lua
{% include wlan-survey.lua %}
```
[Download wlan-survey.lua](/examples/wlan-survey.lua)
