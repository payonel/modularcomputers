---
title: Services
section: mcos
order: 60
---

A service is a program that runs in the background, started at boot or by hand, such as
[`rshd`](/programs/rshd/), the remote shell's daemon. mcos manages them with [`rc`](/programs/rc/),
in OpenRC's shape, with FreeBSD's way of turning them on.

## The pieces

- **`/etc/rc`** is the boot script. The kernel runs it with `sh` before the console's shell starts
  (see [Boot](/mcos/boot/)). It ends with `rc boot`.
- **`/etc/rc.d/<name>`** defines a service, a Lua script in the system's own language (as
  `/etc/init.d` scripts are in the shell's; the shell here has no `if`).
- **`/etc/rc.conf`** says which start at boot, a line each: `name_enable=YES`.

## A service

A service's script sets a few settings, as OpenRC's do:

| Setting | Meaning |
|---|---|
| `command` | The program to run. |
| `command_args` | Its arguments: a list, or a string split on spaces. |
| `description` | What it is. |
| `output_log` | Where its output and errors go. `/dev/null` when not given. |

The script runs in its own environment, with `name` set to the service's name. This is all
`/etc/rc.d/rshd` is:

```lua
description = "remote shell daemon"
command = "/bin/rshd"
```

From the settings, `rc` gives it:

- **start** runs the command as a daemon: in a session of its own (so no terminal's Ctrl+C
  reaches it), with its input from `/dev/null`, and its process id in `/run/<name>.pid`;
- **stop** sends SIGTERM, then SIGKILL if it hasn't ended in five seconds;
- **restart**;
- **status**, which says whether it is running. The status is 0 when running and 3 when not (LSB's
  "program is not running").

A script may define a function named for one of those actions, `start`, `stop`, and so on, and it is
used instead of the default. Any other function it defines is an action too: `rc name reload` runs a
`reload()`.

## Using rc

```
$ rc                    # every service: enabled or not, running or not
$ rc rshd start
$ rc rshd status
rshd is running (pid N)
$ rc rshd enable        # starts at boot: writes rshd_enable=YES to /etc/rc.conf
$ rc rshd disable
```

`rc boot` starts every service whose `name_enable` is `YES` in `/etc/rc.conf`, in the file's order. It
is run by `/etc/rc`, and you can run it by hand.

`/run` is a filesystem in memory, made by `/etc/rc` at every boot, so a pid file never outlives
the computer. A service started by hand stops with the computer too; one in `rc.conf` starts
again at the next boot.

## Writing your own

The tutorial [Your own service](/tutorials/service/) has two complete ones, each with its program,
its `/etc/rc.d` definition and every command. In short: put a Lua script in `/etc/rc.d` named after
the service, with a `command`:

```lua
{% include service-ticker-rc.lua %}
```

And the program, a daemon that loops forever, sleeps between turns, and ends cleanly when stopped:

```lua
{% include service-ticker.lua %}
```

Then `rc ticker start` runs it, `rc ticker stop` stops it, and `rc ticker enable` starts it at boot.

- **A loop forever is right for a daemon.** mcos runs processes in turns, so it never freezes the
  computer.
- **Sleep, or wait on something** (`sys.sleep`, a device's file, `sys.poll`, a socket): a waiting
  process uses no CPU, so the computer draws its waiting power.
- **`rc stop` sends `SIGTERM`.** With no handler it ends the process wherever it is. With a handler
  (`sys.signal`, in [The sys library](/mcos/sys-api/)) the handler runs and a waiting call returns
  early with `EINTR`, so the loop can finish and clean up. Five seconds later, `SIGKILL` ends it
  regardless.

The kernel stays init: it runs the console's shell and starts it again when it ends. A user-space
PID 1 would stay in memory for nothing, so there is none.
