---
title: Two computers
section: Tutorials
order: 4
---

In this tutorial you join two computers with [network cards](/devices/network-card/) and a
[cable](/hardware/cable/), give each a name, and use [rsh](/programs/rsh/) and
[rcp](/programs/rcp/) to run commands on one from the other and to copy files between them.

## What you need

- [ ] Two working computers, each built as in [Your first computer](/tutorials/first-computer/):
  case, CPU, RAM, EEPROM, a mcos disk, battery and screen. A second disk is made the same way as
  the first, so each computer has its own copy of mcos and its own files.
- [ ] Two **Network Cards** (a wired card: its recipe uses a cable; see its
  [page](/devices/network-card/)). Wireless [WLAN cards](/devices/wlan-card/) work the same way in
  this tutorial and need no cable, within their range.
- [ ] Some [cable](/hardware/cable/) (a craft gives eight)

## The whole thing

Once both computers have a network card and a cable between them, this is every command, in order
(each step below explains its part):

```sh
# on the second computer
hostname beta
rc rshd start              # rc rshd enable: at every boot too

# on the first computer
hostname alpha
rsh beta hostname          # prints beta: it ran there
rsh beta 'ls /nothing'; echo $?    # beta's error, and its exit status, 1
echo "sent from alpha" > note.txt
rcp note.txt beta:/home/note.txt
rsh beta cat /home/note.txt        # sent from alpha
rcp beta:/home/note.txt ./back.txt
rsh beta                   # a shell on beta; exit (or ~.) to come back
```

## Steps

### 1. Fit a card in each

With both computers off, open each case view and put a Network Card in an expansion (EXP) socket.
A card in a slot is on its computer's [bus](/concepts/bus/), and each card has an address of its own
(an id, like a MAC address on a real network card).

### 2. Run a cable between them

Place the two cases a few blocks apart, then a line of cable from one to the other so a cable
touches the side of each case (not its front). Cables connect to each other by themselves. You
will see the cable blocks join into a line.

A cable is a shared wire, not a private one: the two cases are two hosts on the same cable, and each
case's other devices still belong to that case alone. A case ends a run of cable: a cable cannot
pass *through* a case to reach another. See [the bus](/concepts/bus/) and
[networking](/mcos/networking/).

### 3. Boot both and name them

Turn both computers on. On the first, type:

```
hostname alpha
```

On the second:

```
hostname beta
```

You will see nothing printed. `hostname` with no name prints the current one. Without a name set, a
computer's name is its first network card's address. The name is kept in `/etc/hostname`, so the
computer has it again after a reboot. See [hostname](/programs/hostname/).

To see the card itself, run `lsdev -i`: the network card appears with its `id:` address.

### 4. Start the remote shell service on beta

A computer can be reached by [rsh](/programs/rsh/) only while it runs `rshd`, the remote shell
service ([remote shell](/mcos/remote-shell/)). On `beta`, type:

```
rc rshd start
```

Give it a moment to start. Run `rc` with no arguments to see the services: `rshd` should be listed
as running. To start it at every boot, run `rc rshd enable`
([services](/mcos/services/), [rc](/programs/rc/)).

`rshd` has no passwords and no encryption: anyone whose card reaches the computer can run commands on
it. That is how the Berkeley r-commands it is named for worked, and why it is a service that is off
until you turn it on.
{: .warn}

### 5. Run a command on the other computer

On `alpha`:

```
rsh beta hostname
```

You will see `beta`: the command ran there and its output came back here. Try one that fails:

```
rsh beta 'ls /nothing'; echo $?
```

You will see beta's error message, then `1`: the command's exit status comes back as rsh's own.

For a shell on the other computer, give no command:

```
rsh beta
```

Your prompt is now beta's. Anything you run runs there. To come back, type `exit`, or at the start of
a line type `~.` to cut the connection.

### 6. Copy files

Make a file on `alpha`:

```
echo "sent from alpha" > note.txt
rcp note.txt beta:/home/note.txt
rsh beta cat /home/note.txt
```

You will see `sent from alpha`. The other direction works too:

```
rcp beta:/home/note.txt ./back.txt
```

A directory needs `-r`: `rcp -r mydir beta:/home/`. Without it, a directory is refused.

## When something goes wrong

| What you see | What it means | The fix |
|---|---|---|
| `rsh` exits with status 1 and cannot find the host | No computer answered to that name: its rshd is not running, its card is not on this cable, or you misspelled it. | Run `rc rshd start` there. Check the cable touches both cases and has no gap. `lsdev -i` there shows its card. |
| It worked, then stopped | A cable block was broken, or a computer turned off. | Replace the cable; run `rsh` again. |
| Nothing at all on either side with WLAN cards | Out of range. | A tier 1 card reaches 16 blocks, a tier 2 400 ([WLAN card](/devices/wlan-card/)). |
| A name finds the wrong computer, or none | Two computers have the same name, or none. | Give each a unique name with `hostname`. You can also use a card's id, or the start of it, in place of the name. |

## Congratulations

Your computers now talk. A computer that wakes when another sends it a message, sockets
programs can use, and more are on the [networking](/mcos/networking/) page. Last,
[manage disks with the Disk Station](/tutorials/disk-station/).
