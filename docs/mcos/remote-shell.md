---
title: Remote shell
section: mcos
order: 80
---

[`rsh`](/programs/rsh/) runs a command, or a shell, on another computer. [`rcp`](/programs/rcp/)
copies files between computers. [`rshd`](/programs/rshd/) is the daemon on the other computer. They
are named for Berkeley's r-commands because they have what those had: **no passwords and no
encryption**.

Anyone whose network card reaches a computer that runs `rshd` gets a shell on it. Leave it off
where that matters. It is off until you enable it.
{: .warn}

## Setting up

On the computer you want to log in to, which needs a [network card](/devices/network-card/) or a
[WLAN card](/devices/wlan-card/):

```
$ hostname lab
$ rc rshd enable
$ rc rshd start
```

`hostname lab` names the computer and keeps the name in `/etc/hostname` (the boot script reads it).
[`rc rshd enable`](/mcos/services/) starts the daemon at every boot.

On any computer whose cards reach it:

```
$ rsh lab
$ rsh lab ls /
$ cat notes | rsh lab 'cat > /notes'
```

For the tutorial, see [Two computers](/tutorials/two-computers/).

## Naming a host

A host is its name (`hostname`), or a card's id (shown by `lsdev -i`), whole or as much of its
start as names one computer. A name given in full wins over an id's start. If no name has been set,
the name is the first network card's address.

`rsh` finds a host by broadcasting a probe on UDP port 514. Each `rshd` answers with its name. It asks up to three
times, as ARP does, because a busy computer may miss one.

## A command

With a command, the other computer runs `sh -c` on it, on pipes. Its input is `rsh`'s standard input
(none with `-n`), its output and its errors come back apart, and `rsh` ends as the command did: with its
exit status, or 128 plus the signal's number if a signal ended it.

## A login

With no command, `rsh` logs in: a shell on a pty on the other computer, as big as your terminal. Your
terminal goes raw, so every key goes there as it is typed, Ctrl+C and the arrows included. A change
to your terminal's size goes there too. `~.` at the start of a line disconnects, and `~~` sends one `~`.

## rcp

`rcp` is `rsh` underneath, as BSD's was: on the other computer, `cat` reads or writes each file,
`mkdir -p` makes directories, and `find` lists a remote directory's tree.

```
$ rcp /notes lab:/notes
$ rcp lab:/log /tmp/
$ rcp -r /projects lab:/backup/
```

A name with `host:path` is remote; a name with no colon, or with a `/` before its colon, is local.
Both ends being remote isn't supported.

## How it works

One TCP connection to port 514, framed in both directions, so output, errors and the exit status stay
apart. (BSD's used a second connection for errors, and never sent the exit status.) A frame is a kind
byte, then its data with a 2-byte length.

`rshd` runs each connection as `rshd -s` with the connection as its standard input, as inetd ran
BSD's. A login runs on a pty (`sys.openpty`): the pty does the terminal's work (echo, line editing,
Ctrl+C) in the kernel, so `rshd` only relays. When `rsh` goes away, a login's line hangs up (SIGHUP to
its shell), and a command's input ends.

See also [Networking](/mcos/networking/) and [Services](/mcos/services/).
