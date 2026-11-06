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
`{why = "denied", reason = "disabled", value = ""}`.

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
the method, the URL, the status (or `-` and why it failed: its why and its fields' values), how long it
took and the bytes it brought. A request its program closed before its end is there too, as
`stopped`.

```
GET https://example.com/ 200 143ms 1256
POST https://example.com/paste 201 310ms 42
GET http://10.0.0.1/ - 2ms 0 denied private 10.0.0.1
```

- **Reading** gives the next line; with none waiting, a read waits for one. `cat /dev/internet0`
  shows requests as they end (Ctrl+C stops it). The card keeps the last 32, as a device keeps its events:
  when more come, the oldest go, and the entry that pushed one out says so (`lost`, a `lost n` line
  before it). They go when the computer stops.
- **Writing** is refused: opening it for writing fails with `EACCES`.
- **poll:** `IN` while a line of the log waits; `PRI` while a request has news (its response came, a
  piece of its body, its end or its failure), until the program next asks the card or reads its
  file. A program waiting for a request polls for `PRI`, then asks again.

An ioctl's answer is the method's (`true` for `close`, which answers nothing). A method's failure is
`nil`, its why, `EINVAL`, and the failure itself (below); a card that has gone is `ENODEV`.

## Failures

Every failure is a table, `{why, ...}`: a word, and its own fields, every one always there (`""`
when it has nothing to say). A method answers `false, failure` at the board, and `nil, why, errno,
failure` through its file. A log entry, a request's end, has its why's fields beside `why` (below).
A `message` is words for a person, never a string to parse: each reason has its own, and the
server's own error (Java's) never reaches a program; it goes to the server's log.

| why | fields | when |
|---|---|---|
| `bad_argument` | `argument` (its position), `value` (as given), `expected`: `url`, `http`, `host`, `method`, `header` or `handle` | a request can't be made of it, or a handle names no open request |
| `denied` | `reason`, `value` (what was refused) | not allowed: `disabled` (the card is off, `""`), `credentials` (the URL), `reserved_header` (the header), or the address judged (`"127.0.0.1"`): `admin` (the operator's deny list), `loopback`, `private`, `link_local`, `shared`, `reserved`, `multicast`, `unspecified`, `documentation`, `teredo` |
| `limit` | `of`, `max` | `open_requests` (the card's 4), `body` (1048576 bytes), `server_requests` (64, every card on the server) |
| `network` | `reason`, `message` (that reason's words) | in transit: `unknown_host` ("host not found"), `connection_refused` ("connection refused"), `timed_out` ("timed out"), `tls` ("secure connection failed"), `connection_lost` ("connection lost"); or the answer was bad: `not_http` ("not an HTTP response"), `bad_content_length` ("bad Content-Length"), `bad_chunk` ("bad chunked encoding"), `closed_early` ("connection closed early"), `headers_too_long` ("response headers too long") |
| `fault` | `condition`, `part`, `message` | the card itself failed: `internal_error`, part `internet` ("internal error") |

```lua
local full = {why = "limit", of = "open_requests", max = 4}
local tls = {why = "network", reason = "tls", message = "secure connection failed"}
```

[`wget`](/programs/wget/) and [`curl`](/programs/curl/) are built on these methods, through the
`http` library (`require("http")`, see [the sys library](/mcos/sys-api/)): `http.request(url,
options)` makes a request (`options.method`, `headers`, `body`, `follow` to follow redirects, up to
20, and `device` for a card's file other than the first), and gives the response
`{status, message, headers, url}` and a reader for its body (`reader:read(n)`, `""` at its end;
`reader:close()`), or `nil`, its failure as words (`network timed_out`) and the failure.

## Methods

### request(url[, method[, headers[, body]]]) → handle

Starts a request and gives its handle (a number) at once. The method defaults to `GET`, or `POST`
when there is a body. `headers` is a table of names and values. The body is a string. The URL must be
`http` or `https` with a host. It fails when the request cannot be made: `bad_argument` (a URL that
isn't one, isn't `http`, or has no host; a method or a header that isn't one), `denied`
(`disabled`; `credentials`: they're never sent; `reserved_header`: one the card writes itself), or
`limit` (`body`, `open_requests`, `server_requests`). Whether the address is allowed is judged as
the request runs, and a refusal is the request's failure, `denied` with the address. A request
costs power.

### response(handle) → {status, message, headers} or false

The status line and headers once they have come (`headers` by name, in lower case); `false` while
they have not. It fails with the request's failure, when it failed (`denied`, `network`).

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

The access log's oldest entry: a request's end, as a [command's end](/devices/robotics/#one-command-at-a-time)
is, what the file reads as a line. Its method and URL are always there; then how it went:

<!-- lua-fragment -->
```lua
-- done (a 404 is done too: the server answered)
{event = "request", ok = true, method = "GET", url = "https://example.com/",
 result = {status = 200, millis = 143, bytes = 1256}}

-- failed: its why's fields beside why, and its call
{event = "request", ok = false, method = "GET", url = "https://slow.example/big",
 why = "network", reason = "timed_out", message = "timed out",
 call = {args = {"https://slow.example/big"}, progress = {status = 200, millis = 30012, bytes = 65536, sent = 0}}}

-- closed by its program before its end
{event = "request", ok = false, method = "POST", url = "https://api.example.com/upload", why = "stopped",
 call = {args = {"https://api.example.com/upload", "POST", {}, '{"name":"backup-'},
         progress = {status = 0, millis = 410, bytes = 0, sent = 52311}}}
```

`result` and `progress` are how far it got: `status` (0 when no response came), `millis`, the
`bytes` its body brought, and, when it didn't end done, `sent`, its body's whole size. `call.args`
are its arguments as given, a body cut to its first 16 bytes: it says which request, for working out
what went wrong. Its `method` and `url` are what a program reads, always there.

`response` and `read` fail with `bad_argument` (`expected = "handle"`) for a handle that is not open.

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
