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
| Interrupt | Raised while a request has news for the program |
| Power | 4 W at rest; a request costs a tick of a running CPU, and each piece of a body read a tenth of that |
| Limits | 4 requests open per card, 64 on the server; 16 pieces of a body wait unread per request; a request body of at most 1 MiB |
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
`disabled`.

Timeouts: 10 seconds to connect, 30 with nothing read, 10 minutes in all. The status line and
headers together are at most 64 KiB. The methods are `GET`, `HEAD`, `POST`, `PUT`, `DELETE`, `PATCH`
and `OPTIONS`.

## In mcos

The card is used as any device: through its file, `/dev/internet0` (`internet1` for the next card),
whose ioctls are its methods by name (below), in any open mode:

```lua
local sys = require("sys")
local card = assert(sys.open("/dev/internet0"))
local handle = assert(sys.ioctl(card, "request", "https://example.com/"))
```

Reading the file gives its **access log**, a line for each request as it ended, as a web server's:
the method, the URL, the status (or `-` and why it failed), how long it took and the bytes it
brought.

```
GET https://example.com/ 200 143ms 1256
POST https://example.com/paste 201 310ms 42
GET http://10.0.0.1/ - 2ms 0 refused: private
```

- **Reading** gives the next line; with none waiting, a read waits for one. `cat /dev/internet0`
  shows requests as they end (Ctrl+C stops it). The card keeps only the last 8 lines, so a reader
  sees what is current, and they go when the computer stops.
- **Writing** is refused: opening it for writing fails with `EACCES`.
- **poll:** `IN` while a line of the log waits; `PRI` while a request has news (its response came, a
  piece of its body, its end or its failure), until the program next asks the card or reads its
  file. A program waiting for a request polls for `PRI`, then asks again.

An ioctl's answer is the method's (`true` for `close`, which answers nothing). A method's failure is
`nil`, its message and `EINVAL`; a card that has gone is `ENODEV`.

[`wget`](/programs/wget/) and [`curl`](/programs/curl/) are built on these methods, through the
`http` library (`require("http")`, see [the sys library](/mcos/sys-api/)): `http.request(url,
options)` makes a request (`options.method`, `headers`, `body`, `follow` to follow redirects, up to
20, and `device` for a card's file other than the first), and gives the response
`{status, message, headers, url}` and a reader for its body (`reader:read(n)`, `""` at its end;
`reader:close()`), or `nil` and why.

## Methods

### request(url[, method[, headers[, body]]]) → handle

Starts a request and gives its handle (a number) at once. The method defaults to `GET`, or `POST`
when there is a body. `headers` is a table of names and values. The body is a string. The URL must be
`http` or `https` with a host. It fails with a message when the request cannot be made:
`not a URL: `, `not http: `, `no host: ` or `credentials: ` and the URL (credentials in a URL
aren't sent), `not a method: `, `not a header: ` or `reserved header: ` (one the card writes
itself) and what it was, `too long: 1048576` (a body over 1 MiB), `too many open: 4`, or
`server busy`. Whether the address is allowed is judged as the request runs, and a refusal is the
request's failure: `refused: ` and why (`loopback`, `private`, `link-local`, `shared`, `multicast`,
`reserved`, `unspecified`, `documentation`, `teredo`, `denied`, `disabled`, `unknown host`). A request costs power.

### response(handle) → {status, message, headers} or false

The status line and headers once they have come (`headers` by name, in lower case); `false` while
they have not. It fails with why, when the request did (`refused: ...`, `timed out`, `closed early`, `not http`,
`tls: ` and why).

### read(handle[, n]) → piece, "" or false

The next piece of the body, at most `n` bytes (a piece is at most 4096); `""` at its end; `false`
while nothing more has come. What a piece had past `n` is the next read's. A failed request fails
its reads with why, after what had come. Each piece costs power.

### close(handle) → nothing

Stops a request: its connection closes and what it had waiting goes. A program closes every request
it makes, finished or not: a card has only 4. In mcos, a request still open when the file it was
made through closes (its program ended, or crashed) is closed with it, as closing a socket closes
its connection.

### pull() → entry or nil

The access log's oldest entry, what the file reads as a line: `{method, url, status, millis, bytes,
why}` (`status` is 0 and `why` says why when it failed).

`response`, `read` and `close` fail with `no such request: N` for a handle that is not open.

## Waiting

The interrupt is raised when a request has news (its response came, a piece of its body, its end
or its failure) and stays raised until the program asks about it. In mcos, the card's file is
ready for `sys.poll`'s `PRI` while it has news, so a program asks again after each poll.

Each request holds at most 16 pieces of its body unread. A request whose next piece finds no room
waits for it, so nothing is dropped: a big download goes at the program's pace. Everything stops
when the computer loses power or no program runs: requests close.

## Examples

Fetch a page into a file from a shell:

```sh
curl -s https://example.com/ > /home/page.html
wget https://example.com/file.txt
```

Each program below is complete: run it with `lua` on a computer with an internet card.

### Download a file

The methods by ioctl, waiting for news with `sys.poll`:
`lua internet-fetch.lua https://example.com/ /home/page.html`.

```lua
{% include internet-fetch.lua %}
```
[Download internet-fetch.lua](/examples/internet-fetch.lua)

### Mirror several files at once

Up to 4 requests open, all waited on by one poll:
`lua internet-mirror.lua https://example.com/files /home/mirror a.txt b.txt c.txt`.

```lua
{% include internet-mirror.lua %}
```
[Download internet-mirror.lua](/examples/internet-mirror.lua)

### Post a file

With the `http` library, following redirects:
`lua internet-post.lua https://example.com/paste /home/note.txt`.

```lua
{% include internet-post.lua %}
```
[Download internet-post.lua](/examples/internet-post.lua)

### Keep the access log

Through the card's file, every request any program makes goes into a log:
`lua internet-log.lua &`.

```lua
{% include internet-log.lua %}
```
[Download internet-log.lua](/examples/internet-log.lua)
