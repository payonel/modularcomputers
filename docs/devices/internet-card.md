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

The card is used as any device: through its file, `/dev/internet0`, whose ioctls are its methods
by name (below). Reading the file gives its access log, a line for each request as it ended, as a
web server's: the method, the URL, the status (or `-` and why it failed), how long it took and the
bytes it brought.

```
GET https://example.com/ 200 143ms 1256
GET http://10.0.0.1/ - 2ms 0 refused: a private address (the server's network)
```

The card keeps only the last 8 lines, so a reader sees what is current. The log is read only.
`wget` and `curl` are built on the methods; see [curl](/programs/curl/) and
[wget](/programs/wget/).

## Methods

### request(url[, method[, headers[, body]]]) → handle

Starts a request and gives its handle (a number) at once. The method defaults to `GET`, or `POST`
when there is a body. `headers` is a table of names and values. The body is a string. The URL must be
`http` or `https` with a host. It fails with a message when the request cannot be made:
`not an http or https URL`, `not a method it sends`, `a header the card writes itself`, a body
over 1 MiB, `too many requests open (4)`, or `the server's internet is busy: try again soon`.
Whether the address is allowed is judged as the request runs, and a refusal is the request's
failure (`refused: ...`).

### response(handle) → {status, message, headers} or false

The status line and headers once they have come (header names in lower case); `false` while they
have not. It fails with why, when the request did (refused, unknown host, timed out).

### read(handle[, n]) → piece, "" or false

The next piece of the body, at most `n` bytes; `""` at its end; `false` while nothing more has
come. A failed request fails its reads with why, after what had come.

### close(handle) → nothing

Stops a request: its connection closes and what it had waiting goes.

### pull() → entry or nil

The access log's oldest entry: `{method, url, status, millis, bytes, why}` (`why` when it failed).

## Waiting

The interrupt is raised when a request has news (its response came, a piece of its body, its end
or its failure) and stays raised until the program asks about it. In mcos, the card's file is
ready for `sys.poll`'s `PRI` while it has news, so a program asks again after each poll.

Each request holds at most 16 pieces of its body unread. A request whose next piece finds no room
waits for it, so nothing is dropped: a big download goes at the program's pace. Everything stops
when the computer loses power or no program runs: requests close.

## Example

Fetch a page into a file from a shell:

```sh
curl -s http://example.com/ > /page.html
```

Or from a Lua program under mcos, reading the status and the body:

```lua
local sys = require("sys")
local card = assert(sys.open("/dev/internet0"))

-- Asks again, after waiting for the card's news, while the answer is "not yet".
local function await(method, ...)
  while true do
    local answer, why = sys.ioctl(card, method, ...)
    if answer ~= false then
      return answer, why
    end
    sys.poll({[card] = {PRI = true}})
  end
end

local handle = assert(sys.ioctl(card, "request", "http://example.com/"))
local response = assert(await("response", handle))
local size = 0
while true do
  local piece = assert(await("read", handle))
  if piece == "" then
    break
  end
  size = size + #piece
end
sys.ioctl(card, "close", handle)
print(response.status, size)
```
