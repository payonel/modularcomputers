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
  tick of a running CPU.
- A `distance` in a received frame is the straight line between the two blocks, in meters, to one
  decimal place.

A card tells the world where it is as its block ticks. A card that has not for a couple of ticks
(its chunk unloaded, its block broken, the card taken out) is not on the network.

Everything else (addresses, `send`, `broadcast`, `receive`, the `"busy"` and `"unreachable"`
answers, nothing being dropped, and wake-on-LAN) works as it does for a wired card; read
[Network Card](/devices/network-card/) for it.

## In mcos

mcos reaches it through sockets as `wlan0`; see [Networking](/mcos/networking/). Its file in
`/dev` (`network0`, a network card's) is its traffic, as a wired card's, and its methods are the
file's ioctls: `sys.ioctl(fd, "setStrength", 16)` sets its range.

## Methods

The methods of a [network card](/devices/network-card/): `address`, `send`, `broadcast`, `receive`,
`getWake` and `setWake`. And these two.

### getStrength() → blocks

How far the signal reaches now.

### setStrength(blocks) → blocks

Sets how far the signal reaches, from 0 up to the tier's range (16 or 400). A larger number is
clamped to the range. Gives what it is set to. The setting is kept with the card across saves.

## Events

A frame that arrives is an event, taken with `receive`: `{from, data, distance, broadcast}`. See
[Network Card](/devices/network-card/).

## Example

Whisper to a neighbor, a short way, and cheaply (EEPROM code; the address is a placeholder):

```lua
local nic = "exp:0"
board.invoke(nic, "setStrength", 4)
local _, strength = board.invoke(nic, "getStrength")
local ok, taken, why = board.invoke(nic, "send", "5f0c0000-0000-0000-0000-000000000000", "psst")
board.invoke("console", "write", "strength " .. strength .. ", taken " .. tostring(taken) .. "\r\n")
```

With the neighbor 4 blocks or closer, `taken` is `true`; farther, it is `false` and `why` is
`"unreachable"`.
