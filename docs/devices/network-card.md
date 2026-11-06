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
cable are two hosts on it. Two cards in one block are on the same wire, 0 meters apart.

## Nothing is dropped

A frame sent to one card is taken, or the sender hears why not at once. The card never tries again;
that is software's job (mcos's TCP does).

- `{why = "limit", of = "frames", max = 32}`: the other card's queue is full, or no program on its
  computer is taking data.
- `{why = "network", reason = "unreachable", message = "no card in reach"}`: no card at that address in reach, or it
  is off.

A broadcast is best effort: it says how many cards took it.

## In mcos

mcos sends and receives through sockets (UDP and TCP), with Linux's names for interfaces (`eth0`,
`wlan0`). Ports belong to the kernel, in the frame's data: the card knows only addresses. See
[Networking](/mcos/networking/). A card's address is shown by [`lsdev -i`](/programs/lsdev/).

The card's file is `/dev/network0` (`network1` for the next card, wired or not). It is its
**traffic**, as `tcpdump` shows a card's: a line for each frame in or out. Each line is the
direction, the other card's address (or `broadcast`), how far it is in meters (`-` for a broadcast
sent), the frame's bytes, and how it went:

```
in 5f0c2a9e-41d7-4c4e-9b0a-2f3c4d5e6f70 0.0m 5 taken
out 5f0c2a9e-41d7-4c4e-9b0a-2f3c4d5e6f70 0.0m 8 taken
out 9a1b2c3d-0000-4000-8000-000000000001 - 12 network unreachable no card in reach
in 5f0c2a9e-41d7-4c4e-9b0a-2f3c4d5e6f70 0.0m 9 broadcast
out broadcast - 4 2 took it
```

How it went is `taken`; a refusal's why and its fields' values (`limit frames 32`, `network
unreachable no card in reach`); `broadcast` for a broadcast that came in and was taken; and `N took
it` for one sent. The card keeps only its last 8 lines, so a reader sees
what's current.

- **Reading** gives the next line; with none waiting, a read waits for one, as a keyboard's does.
  `cat /dev/network0` shows the traffic as it goes (Ctrl+C stops it).
- **Writing** is refused: opening it for writing fails with `EACCES`. Frames are sent through
  sockets, or the `send` and `broadcast` methods.
- **poll:** `IN` while a line waits to be read; `PRI` while the card has news (a frame came since the
  file was last read or asked).
- **ioctls** are the card's methods, by name, with their arguments, in any open mode:

```lua
local sys = require("sys")
local card = assert(sys.open("/dev/network0"))
print(sys.ioctl(card, "address"))  -- this card's address
sys.ioctl(card, "setWake", "letmein")
```

A method's failure is `nil`, its message and `EINVAL`; a card that has gone is `ENODEV`. The
kernel's own ioctl `setLabel` sets the card's label (see [/dev](/mcos/dev/)).

Once a program has used a socket, the kernel takes every frame the card gets, for its sockets, so
the `receive` method finds nothing: receive through sockets. Sending by `send` and `broadcast` works
alongside them.

## Methods

### address() → string

The card's address: its id, which other cards send to.

### send(address, data) → true, or false and why

Sends a frame of at most 8192 bytes to the card with that address (with or without `id:`). Gives
`true` when it was taken, else it fails with one of the two above (through its file, `nil`, the
why, `EINVAL` and the failure). A longer frame fails with
`{why = "limit", of = "data", max = 8192}`. Sending costs power. The card never tries again itself.

### broadcast(data) → count

Sends a frame to every card in reach and gives how many took it. A longer frame than 8192 bytes
fails with `{why = "limit", of = "data", max = 8192}`.

### receive() → frame or nil

The oldest frame that came, or nil. A frame is a table:

| Field | Value |
|---|---|
| `from` | the sender's address |
| `data` | the bytes |
| `distance` | meters; a wired frame's is the length of cable it went along |
| `broadcast` | `true`, only when it was a broadcast |

### pull() → entry or nil

The traffic log's oldest entry, or nil: what the card's file reads as a line. A frame it sent is
named after the method that sent it; a frame that came is the card's own news, `arrived`:

<!-- lua-fragment -->
```lua
{event = "send", address = "5f0c2a9e-...", distance = 3.0, bytes = 42, took = 1, ok = true}
{event = "send", address = "5f0c2a9e-...", distance = 3.0, bytes = 42, took = 0, ok = false,
 why = "limit", of = "frames", max = 32}
{event = "send", address = "9a1b2c3d-...", distance = -1, bytes = 42, took = 0, ok = false,
 why = "network", reason = "unreachable", message = "no card in reach"}
{event = "broadcast", bytes = 17, took = 2, ok = true}
{event = "arrived", address = "7e3d1f00-...", broadcast = true, distance = 12.5, bytes = 17, ok = true}
```

| Field | Value |
|---|---|
| `event` | `"send"`, `"broadcast"` (the method that sent it) or `"arrived"` (a frame that came) |
| `address` | the other card's (a send's and an arrived frame's) |
| `distance` | meters between the cards; `-1` for a send that reached no card |
| `broadcast` | an arrived frame's: whether it was a broadcast |
| `bytes` | the frame's size |
| `took` | a send's and a broadcast's: how many cards took it (a send's 1 or 0) |
| `ok` | how it went: `false` with the refusal's `why` and its fields beside it, as `send` answered (`limit`, `network`); a broadcast is always `ok` (best effort) |

### getWake() → string or nil

The wake message, or nil for none.

### setWake([message]) → old message or nil

Sets the message that turns the computer on while it is off. Nil or an empty string clears it. At
most 256 bytes (`{why = "limit", of = "wake_message", max = 256}`). Returns the old message.

### getStrength(), setStrength(blocks)

A WLAN card's signal strength (see [WLAN Cards](/devices/wlan-card/)). On a wired card both fail with
`{why = "denied", reason = "wired", value = ""}`.

## Events

Each frame that arrives is an event, taken with `receive`, and raises the card's interrupt; it stays
raised while frames wait. At most 32 wait, which is the card's ring; a frame that finds it full is
refused as a `limit`, not dropped. Frames wait only while the card has power and a program runs on
its computer; they are dropped when it stops. Each frame in or out also adds a line to the traffic
log (`pull`).

**Wake-on-LAN.** With a wake message set, a card listens while its computer is off. A frame holding
the message anywhere in its data presses the computer's power button, so a UDP datagram carrying it
does. The card takes such a frame (the sender hears `true`) whether it holds the message or not;
without a message set, an off computer's card is unreachable. The setting is kept across power
off and saves. This is the mod's own wake message, not a real magic packet.

## Examples

Each is a complete program: copy it to a computer with a network card and run it with `lua`. For why
they loop without yielding, see [Writing programs](/mcos/programs/). Two computers on one cable can
try the socket ones; [`nc`](/programs/nc/) is a handy other end.

### Log the traffic

Through the card's file: each line it reads goes into a log, with the time.

```lua
{% include network-traffic.lua %}
```
[Download network-traffic.lua](/examples/network-traffic.lua)

### A chat over UDP

Broadcasts what's typed, shows what others send. Run it on each computer: `lua network-chat.lua alice`.

```lua
{% include network-chat.lua %}
```
[Download network-chat.lua](/examples/network-chat.lua)

### A TCP echo server

Serves any number of clients at once with one `sys.poll`. Start it with `lua network-echo.lua &`,
then from another computer:

```sh
echo hello | nc <the server card's address> 7
```

```lua
{% include network-echo.lua %}
```
[Download network-echo.lua](/examples/network-echo.lua)

### Wake-on-LAN

The card's `setWake` and `getWake` methods as ioctls, and a datagram to wake another computer. On
the computer to be woken, `lua network-wol.lua enable letmein`, then `poweroff`; from another,
`lua network-wol.lua <its card's address> letmein`.

```lua
{% include network-wol.lua %}
```
[Download network-wol.lua](/examples/network-wol.lua)
