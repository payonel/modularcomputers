---
title: Internet Card
section: Devices
order: 100
---

An internet card makes HTTP and HTTPS requests to the real internet. The card speaks HTTP itself,
because a program's Lua cannot do TLS.

<div class="infobox" markdown="1">

### Facts
| | |
|---|---|
| Type | `internet` |
| Address | An expansion slot path such as `exp:0` |
| Interrupt | Raised when events arrive for the program |
| Power | 4 W at rest; a request costs a tick of a running CPU, and each piece of a body a tenth of that |
| Limits | 4 requests open per card, 64 on the server; 32 events wait; a request body of at most 1 MiB |
| Info | `kind` (`internet`) |

</div>

## What it can reach

A card is a program running on the server's network, so it is kept to the **public internet**.
Requests to loopback, private, link-local (cloud metadata), CGNAT, multicast and reserved ranges are
refused, in IPv4 and IPv6. Every address a name resolves to is judged, and the connection opens to
the address that was judged (so a name cannot change its answer between the check and the
connection). Redirects are the program's to follow, so each hop is judged again.

A request cannot set the headers that frame it (`Host`, `Content-Length`, `Connection` and the like),
cannot carry line breaks in a header, and cannot put credentials in its URL. The card sends no
proxies, cookies or server details; it names itself `ModularComputers`.

A server's operator can turn it off (`internet_enabled`) and allow or deny hosts and ranges
(`internet_allow`, `internet_deny`). When it is off, `request` fails with
`the internet is turned off on this server`.

Timeouts: 10 seconds to connect, 30 with nothing read, 10 minutes in all. The status line and
headers together are at most 64 KiB. The methods are `GET`, `HEAD`, `POST`, `PUT`, `DELETE`, `PATCH`
and `OPTIONS`.

## In mcos

A request is a file. `sys.http(url, {method, headers, body})` starts one on the first internet card
and gives a file descriptor: reading it gives the body as it comes (a read with nothing yet waits),
the `HTTP_RESPONSE` ioctl gives `{status, message, headers}` (header names in lower case), and
closing stops the request. A failed request (refused, unknown host, timed out) fails its reads with
the reason (`EIO`). `wget` and `curl` are built on it; see [curl](/programs/curl/) and
[wget](/programs/wget/), and the [sys API](/mcos/sys-api/). A card's own `/dev` file has no use: the
kernel takes the card's events itself.

## Methods

### request(url[, method[, headers[, body]]]) → handle

Starts a request and gives its handle (a number) at once. The method defaults to `GET`, or `POST`
when there is a body. `headers` is a table of names and values. The body is a string. The URL must be
`http` or `https` with a host. It fails with a message when the request cannot be made:
`not an http or https URL`, `not a method it sends`, `a header the card writes itself`, a body
over 1 MiB, `too many requests open (4)`, or `the server's internet is busy: try again soon`.
Whether the address is allowed is judged as the request runs, and a refusal comes back as an `error`
event (`refused: ...`).

### pull() → event or nil

The oldest event, or nil. See Events.

### close(handle) → nothing

Stops a request: its connection closes and its events still waiting go.

## Events

What comes back are events, in order, each with the request's `handle` and an `event` kind:

| `event` | Other fields |
|---|---|
| `response` | `status` (a number), `message`, `headers` (a table by name) |
| `data` | `data`: a piece of the body (at most 4096 bytes) |
| `end` | none: the body is complete |
| `error` | `why`: the reason it failed |

The card holds at most 32 events. A request whose next piece finds the card full waits for room,
so nothing is dropped: a big download goes at the program's pace. The interrupt is raised once for
events that came, and again when the card has been emptied and more come. Everything stops when the
computer loses power or no program runs: requests close and their events go.

## Example

Fetch a page into a file from a shell:

```sh
curl -s http://example.com/ > /page.html
```

Or from a Lua program under mcos, reading the status and the first bytes:

```lua
local sys = require("sys")
local fd = assert(sys.http("http://example.com/"))
local response = assert(sys.ioctl(fd, "HTTP_RESPONSE"))
local body = sys.read(fd, 200)
sys.close(fd)
print(response.status, #body)
```
