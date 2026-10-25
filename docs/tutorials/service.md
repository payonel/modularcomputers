---
title: Your own service
section: Tutorials
order: 6
---

A *service* is a program that runs in the background, started by hand or at every boot, while you
use the computer for other things. mcos runs services with [`rc`](/programs/rc/). Here is a whole
one, a visitor log: every creature a [motion sensor](/devices/motion-sensor/) sees, with the world's
date and time. All of its files come first; the rest of the page explains them.

You need a computer with mcos and a [motion sensor card](/devices/motion-sensor/) (`ls /dev` lists
`motion0`).

## The whole thing

**1. The program,** `/home/visitors.lua`:

```lua
{% include service-visitors.lua %}
```

**2. The service,** `/etc/rc.d/visitors` (named for the service: `visitors`):

```lua
{% include service-visitors-rc.lua %}
```

**3. Start it at every boot,** in `/etc/rc.conf` (a line a service; `rc visitors enable` writes it):

```sh
# /etc/rc.conf: the services started at boot, a line each.
visitors_enable=YES
```

**4. Use it:**

```
$ rc visitors start
$ rc visitors status
visitors is running (pid 12)
$ cat /home/visitors.log
Day 3 14:02:11 minecraft:pig
Day 3 14:02:40 minecraft:player
$ rc visitors restart
$ rc visitors stop
$ rc visitors status
visitors is stopped
$ rc                        # every service: enabled or not, running or not
```

That's all a service needs. The settings in `/etc/rc.d/visitors` are enough for `rc` to start, stop,
restart and check it.

## Every action written out

When the definition sets only `command`, `rc` does each action itself. A definition can do any of
them its own way instead, by defining a function with the action's name, and can add actions of its
own: any other function is one (`rc visitors count`). This is the same service with all of them
written out, doing what `rc` does by default, plus a `count`. Copy it, and change what you need:

```lua
{% include service-visitors-full-rc.lua %}
```

```
$ rc visitors start
$ rc visitors count
2 visitors
$ rc visitors stop
```

A function returns the action's exit status (0: it went well). A function the file doesn't define,
`rc` does itself, so you can write just the one you want to change (a `stop` that saves something
first, say) and leave the rest out.

## A daemon that loops forever

The visitor log waits on a sensor, so a read is its loop. Most daemons you write will instead do
something, sleep, and do it again, forever, until they're stopped. Here is one whole: a ticker that
writes the world's time to a log every few seconds, and ends cleanly on `rc ticker stop`.

**1. The program,** `/home/ticker.lua`:

```lua
{% include service-ticker.lua %}
```

**2. The service,** `/etc/rc.d/ticker`:

```lua
{% include service-ticker-rc.lua %}
```

**3. Use it:**

```
$ rc ticker start
$ tail -f /home/ticker.log         # Ctrl+C to stop watching (the service goes on)
Day 3 14:02:11 started, a line every 2 seconds
Day 3 14:02:11 tick 1
Day 3 14:02:13 tick 2
^C
$ rc ticker stop
$ tail -1 /home/ticker.log
Day 3 14:02:16 stopped after 3 ticks
```

### Can it loop forever?

Yes. A service is meant to: `while not stopping do ... end` (or `while true do ... end`) is the
daemon's whole life. mcos runs every process in turns, so a loop never freezes the computer or your
shell, and `rc stop` reaches it wherever it is.

### Can it sleep?

Yes, and it should. `sys.sleep(seconds)` waits that long (the world's seconds: 20 ticks each) without
using the CPU, so the computer draws its waiting power, not its running power (see
[Power](/concepts/power/)). Any call that waits does the same: reading a device's file (as the
visitor log does), `sys.poll`, reading a socket. A loop that never waits (`while true do end`)
still works and still stops, but keeps the CPU busy and the computer at its running power the whole
time, for nothing.

### How does `rc stop` end the loop?

`rc ticker stop` sends the process `SIGTERM`, then waits up to five seconds for it to end, then sends
`SIGKILL`, which nothing can catch. What `SIGTERM` does is up to the program:

- **No handler:** it ends the process at once, wherever it is: mid-sleep, mid-read, mid-loop. Fine
  when there's nothing to finish.
- **A handler** (`sys.signal("SIGTERM", function() ... end)`): the handler runs instead, in your
  program, and a call that was waiting (the `sys.sleep`) returns early, with `nil` and `EINTR`. So
  the ticker's handler only sets `stopping = true`; the sleep ends, the loop checks `stopping`, and
  the program writes its last line and returns. Keep a handler this small, and do the cleanup after
  the loop, where you know what state you're in.
- **Exit from the handler**, as the visitor log does (`os.exit(0)` after its last line), when the
  program has no loop of its own to finish.

A handler that doesn't end the program (one that ignores the signal) gets `SIGKILL` after five
seconds anyway. `rc ticker status` then says it's stopped, its status 3.

## What each part does

### The program

A service is an ordinary program that doesn't end on its own.

- **It waits, not spins.** Reading the sensor's file waits for the next event, so it uses no time
  while nothing moves.
- **It ends cleanly when stopped.** `rc stop` sends `SIGTERM`; the program catches it with
  `sys.signal`, writes a last line, and exits. Without a handler, `SIGTERM` simply ends it.
- **It closes what it writes.** A file on a disk shows what's written only once it's closed, so the
  log is opened and closed for each line, and you can read it while the service runs
  (`tail -f /home/visitors.log`).

Try it by hand first: `lua /home/visitors.lua`, walk past the computer, press Ctrl+C, then
`cat /home/visitors.log`.

### The definition

`/etc/rc.d/<name>` is a Lua script, run by `rc` with `name` set to the service's name. Its settings:

| Setting | What it is |
|---|---|
| `command` | The program to run (`/bin/lua` for a Lua script) |
| `command_args` | Its arguments: a list, or a string split on spaces |
| `description` | What it is (`rc` lists it) |
| `output_log` | Where what it prints, and its errors, go; nowhere when it isn't set |

`rc start` runs the command in a session of its own (so Ctrl+C at your shell never reaches it), its
input from `/dev/null`, and keeps its process id in `/run/<name>.pid` (`/run` is made fresh at every
boot). `rc stop` sends `SIGTERM`, then `SIGKILL` if it hasn't ended in five seconds. `rc status` is 0
while it runs, 3 when it doesn't (LSB's "not running").

### Starting at boot

At boot, `/etc/rc` runs `rc boot`, which starts every service with `name_enable=YES` in
`/etc/rc.conf`, in the file's order. `rc visitors enable` and `rc visitors disable` edit that line for
you. Type `reboot`, and once the computer's up, `rc visitors status` says it's running again.

## See also

- [Services](/mcos/services/): all of `rc`.
- [`rshd`](/programs/rshd/), the remote shell, is a service like yours: `cat /etc/rc.d/rshd`.
- A service that waits for several things at once (a sensor and a socket, say) uses `sys.poll`; one
  that listens on the network uses [sockets](/mcos/networking/).
