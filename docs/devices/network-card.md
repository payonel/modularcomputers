---
title: Network Card
section: Devices
order: 60
---

A network card sends frames over the cables to another card's address, or to every card it reaches.
It has the shape of a real NIC.

<div class="infobox" markdown="1">

### Facts
| | |
|---|---|
| Type | `network` |
| Address | An expansion slot path such as `exp:0` |
| Interrupt | Raised while frames wait; also a wake line |
| Power | 2 W at rest; a send costs a tenth of a tick of a running CPU |
| Frame size | At most 8192 bytes |
| Info | `kind` (`network`), `wireless` (false) |

</div>

The wireless cards, which send through the air, are on [WLAN Cards](/devices/wlan-card/); both kinds
have the same methods. A wired card hears only wired cards, and a WLAN card only WLAN cards.
Nothing routes between the two networks.

## Addresses

A card's address is its id (a MAC address burned into the card: its item's serial number), without
the `id:` prefix. `send` takes it with or without the prefix. A frame goes to the card with that
address, or to all cards in reach (a broadcast).

A wired frame reaches the cards the cables reach from the card's block, through hubs and screens
and across Bluetooth links, ending at a computer. A cable is a shared fabric: two cases on one
cable are two hosts on it. Two cards in one block are on the same wire.

## Nothing is dropped

A frame sent to one card is taken, or the sender hears why not at once. The card never tries again;
that is software's job (mcos's TCP does).

- `"busy"`: the other card's queue is full, or no program on its computer is taking data.
- `"unreachable"`: no card at that address in reach, or it is off.

A broadcast is best effort: it says how many cards took it.

## In mcos

A network card has no file in `/dev`. mcos uses it through sockets (UDP and TCP), with Linux's names
for interfaces (`eth0`, `wlan0`). Ports belong to the kernel, in the frame's data: the card knows
only addresses. The wake message is read and set with ethtool's `ETHTOOL_GWOL` and `ETHTOOL_SWOL`
ioctls. See [Networking](/mcos/networking/). A card's address is shown by `lsdev -i`.

## Methods

### address() → string

The card's address.

### send(address, data) → true, or false and why

Sends a frame of at most 8192 bytes to the card with that address. Gives `true` when it was taken,
else `false` and `"busy"` or `"unreachable"`. A longer frame fails with `frame too long`. Sending
costs power.

### broadcast(data) → count

Sends a frame to every card in reach and gives how many took it.

### receive() → frame or nil

The oldest frame that came, or nil. A frame is a table:

| Field | Value |
|---|---|
| `from` | the sender's address |
| `data` | the bytes |
| `distance` | meters; a wired frame's is the length of cable it went along |
| `broadcast` | `true`, only when it was a broadcast |

### getWake() → string or nil

The wake message, or nil for none.

### setWake(message) → old message or nil

Sets the message that turns the computer on while it is off. Nil or an empty string clears it. At
most 256 bytes (`wake message too long`). Returns the old message.

`getStrength` and `setStrength` fail on a wired card (`a wired card has no signal strength`).

## Events

Each frame that arrives is an event, taken with `receive`. At most 32 wait, which is the card's
ring; a frame that finds it full is refused as `"busy"`, not dropped. Frames wait only while the
card has power, and are dropped when no program runs.

**Wake-on-LAN.** With a wake message set, a card listens while its computer is off. A frame holding
the message anywhere in its data presses the computer's power button. The setting is kept across
power off. This is the mod's own wake message, not a real magic packet.

## Example

From code on the EEPROM, send to another card and wait for its answer:

```lua
local nic = "exp:0"
local _, mine = board.invoke(nic, "address")
local ok, taken, why = board.invoke(nic, "send", "5f0c0000-0000-0000-0000-000000000000", "hello")
if ok and not taken then
  board.invoke("console", "write", "not sent: " .. why .. "\r\n")
end
board.waitForInterrupt(5, nic)
local _, frame = board.invoke(nic, "receive")
if frame then
  board.invoke("console", "write", frame.from .. ": " .. frame.data .. "\r\n")
end
```

The first `true` from each call is `board.invoke`'s own. The address is a placeholder: use the other
card's id from `lsdev -i` there.
