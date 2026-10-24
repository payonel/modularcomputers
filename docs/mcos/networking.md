---
title: Networking
section: mcos
order: 50
---

mcos talks over the network cards with sockets, UDP and TCP, in Linux's shape. A
[network card](/devices/network-card/) sends frames over cables, a [WLAN card](/devices/wlan-card/)
through the air; mcos puts ports, datagrams and connections on top of them.

## Addresses and interfaces

There is no IP. An address is a card's id (its MAC, in a real card), without the `id:` in front;
[`lsdev -i`](/programs/lsdev/) shows it. The special address `broadcast` means every card the
sender's cards reach. Interfaces are named as Linux names them: `eth0` for the first wired card,
`wlan0` for the first WLAN card. There are no commands for them (no `ifconfig`): the sockets' ioctls
below do the job.

A frame to one of this computer's own cards, or a broadcast, reaches this computer's own sockets
too, as Linux's loopback does.

The card never drops a frame: it takes it, or refuses it at once and says why. The sender gets
`ENOBUFS` when the card on the other end is busy, and `EHOSTUNREACH` when no card there takes it.
The card never tries again: that is software's job, and TCP does it. "Taken" is the card's, not the
program's.

## UDP

A UDP socket is a datagram socket, the default.

```lua
local sys = require("sys")
local s = assert(sys.socket())            -- "dgram" (UDP) is the default
assert(sys.bind(s, 7000))                 -- take datagrams for port 7000
local data, from, port = sys.recvfrom(s)  -- waits for one
sys.sendto(s, data, from, port)           -- answer it
```

- `sys.socket([kind])`: a file descriptor.
- `sys.bind(fd, port)`: the port to take datagrams on; `0` is any free one. A socket that sends
  without being bound is given a port from 32768 to 60999.
- `sys.sendto(fd, data, address, port)`: the bytes sent. At most 8187 bytes go in one datagram (a
  frame holds 8192, and the header takes 5); more fails with `EMSGSIZE`.
- `sys.recvfrom(fd)`: the oldest datagram's data, the sender's address and port; it waits for one. A
  plain `sys.read` takes one datagram's data.

**UDP has no remote dependency.** A datagram's answer is the card's alone. The other computer's
kernel never answers (there is no ICMP), and it drops what has no socket or no room, as UDP does. A
socket holds up to 64 datagrams; one more is dropped.

## TCP

A stream socket gives a byte stream both ways, in order, none lost.

```lua
local sys = require("sys")

-- on one computer: the server
local l = assert(sys.socket("stream"))
assert(sys.bind(l, 8080))
assert(sys.listen(l))
local conn, address, port = assert(sys.accept(l))   -- waits for a connection
print(sys.read(conn))                                -- hello

-- on another: the client, given the server card's address (lsdev -i)
local c = assert(sys.socket("stream"))
assert(sys.connect(c, "<the server card's id>", 8080)) -- waits until it is open
sys.write(c, "hello\n")
sys.shutdown(c)                                      -- no more to send; reading goes on
```

- `sys.listen(fd[, backlog])`: take connections on the port (a port is chosen if none was bound).
  The backlog is 8 by default.
- `sys.accept(fd)`: a new socket, and the peer's address and port. It waits.
- `sys.connect(fd, address, port)`: waits until the connection is open, refused
  (`ECONNREFUSED`), unreachable or timed out.
- `sys.shutdown(fd)`: sends the end of the stream; reading goes on.
- `sys.read` and `sys.write` work as on any file; a closed peer reads as the end (an empty string),
  and a write to one fails with `EPIPE`.

TCP is TCP's shape, kept small. Segments carry sequence numbers and acknowledgements, and a receiver
says how much room it has (its *window*, 8192 bytes); a sender never sends more. A segment no card took
is tried again in a tenth of a second; one taken and not acknowledged goes again in a second (go back
N). A connection with no progress for eight seconds is reset (`ETIMEDOUT`). A SYN that found no
card for three seconds fails with `EHOSTUNREACH`, as an ARP that is never answered does. A connection
opens with SYN, SYN+ACK, ACK, and closes with a FIN each way. A segment for nothing gets RST. There is
no congestion control or `TIME_WAIT`.

Ports are the kernel's, in the frame's data (a frame starts with IP's protocol number, 17 for UDP
and 6 for TCP), so any kernel can put its own protocol on a card. The cards know only addresses.

## ioctls

`sys.ioctl(fd, op, arg)` on any socket:

| op | Argument | Answers |
|---|---|---|
| `SIOCGIFCONF` | | a list of the interfaces, `{name, address, wireless}` each |
| `ETHTOOL_GWOL` | interface name | the interface's wake message, or `nil` |
| `ETHTOOL_SWOL` | `{name, wake}` | `true`; sets the wake message (`nil` or empty: none) |
| `SIOCGIWTXPOW` | WLAN interface name | its transmit power, in blocks |
| `SIOCSIWTXPOW` | `{name, txpower}` | what it is set to, up to its card's tier |

The wake message is **Wake-on-LAN**: a card with a wake message listens while its computer is
off, and a frame whose data holds the message presses the power button. See the
[network card](/devices/network-card/) page. A frame reaches as far as its sender's strength
(`txpower`), and a send costs more power the farther it reaches: see the
[WLAN card](/devices/wlan-card/).

## Programs

- [`nc`](/programs/nc/): netcat, over TCP or UDP, to send and receive by hand.
- [`rsh`](/programs/rsh/), [`rshd`](/programs/rshd/) and [`rcp`](/programs/rcp/): the remote shell,
  built on TCP: see [Remote shell](/mcos/remote-shell/).
- [`hostname`](/programs/hostname/): a computer's name, which `rsh` finds it by.

## Example: nc

On one computer, listen; on the other, connect to its card's address:

```
$ nc -l 7000
$ echo hi | nc <the first computer's card id> 7000
```

## The internet

The [internet card](/devices/internet-card/) reaches the real internet, over HTTP and HTTPS only. It
is not a socket; a request is a file: `sys.http(url, {method, headers, body})` returns a file
descriptor whose reads give the body. See [The sys library](/mcos/sys-api/);
[`wget`](/programs/wget/) and [`curl`](/programs/curl/) use it.
