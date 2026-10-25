---
title: Your own service
section: Tutorials
order: 6
---

In this tutorial you write a *service*: a program that runs in the background, started by hand or
at every boot, while you use the computer for other things. Yours keeps a visitor log: every
creature a [motion sensor](/devices/motion-sensor/) sees, with the world's date and time. mcos runs
services with [`rc`](/programs/rc/), as Linux's OpenRC and FreeBSD's rc do; see
[Services](/mcos/services/) for all of it.

## What you need

- [ ] A working computer from [Your first computer](/tutorials/first-computer/), with mcos
- [ ] A [motion sensor card](/devices/motion-sensor/) in one of its expansion slots
- [ ] You've written a program before ([Your first program](/tutorials/first-program/))

Check the sensor is there: `ls /dev` lists `motion0`.

## 1. The program

A service is an ordinary program that doesn't end on its own. Write this as `/home/visitors.lua`
(`edit /home/visitors.lua`):

```lua
{% include service-visitors.lua %}
```

What makes it a good service:

- **It waits, not spins.** Reading the sensor's file waits for the next event, so it uses no time
  while nothing moves.
- **It ends cleanly when stopped.** `rc` stops a service with `SIGTERM`; the program catches it
  with `sys.signal`, writes its last line, and exits. (Without a handler, `SIGTERM` simply ends it.)
- **It closes what it writes.** A file on a disk shows what's written only once it's closed, so
  the log is opened and closed for each line, and you can read it while the service runs.

Try it by hand first: `lua /home/visitors.lua`, walk past the computer, then press Ctrl+C and
`cat /home/visitors.log`.

## 2. The service's definition

A service is defined by a small Lua script in `/etc/rc.d`, named after it. Write this as
`/etc/rc.d/visitors`:

```lua
{% include service-visitors-rc.lua %}
```

Its settings are all `rc` needs: the `command` to run, its `command_args`, a `description`, and
`output_log`, where what it prints goes (nowhere when it's not set).

## 3. Start, check, stop

```
$ rc visitors start
$ rc visitors status
visitors is running (pid 12)
$ cat /home/visitors.log
Day 3 14:02:11 minecraft:pig
Day 3 14:02:40 minecraft:player
$ rc visitors stop
$ rc visitors status
visitors is stopped
```

`rc` on its own lists every service: enabled or not, running or not. It starts a service in a
session of its own, so Ctrl+C at your shell never reaches it, and keeps its process id in
`/run/visitors.pid`. `ps` shows it too.

You can follow the log as it grows: `tail -f /home/visitors.log` (Ctrl+C stops `tail`, not the
service).

## 4. Start it at every boot

```
$ rc visitors enable
```

That adds `visitors_enable=YES` to `/etc/rc.conf`, the list of services started at boot. Type
`reboot`, and once the computer's up, `rc visitors status` says it's running again.
`rc visitors disable` takes it out.

## Going further

- A definition can replace an action with a function of its own, or add one: a `reload()` function
  in `/etc/rc.d/visitors` makes `rc visitors reload` run it. See [Services](/mcos/services/).
- [`rshd`](/programs/rshd/), the remote shell, is a service like yours: `cat /etc/rc.d/rshd`.
- A service that listens on the network uses [sockets](/mcos/networking/); one that waits for
  several things at once (a sensor and a socket, say) uses `sys.poll`.
