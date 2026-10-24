---
title: Bluetooth Card
section: Devices
order: 80
---

A Bluetooth card in a computer links that computer's bus to another block's board, with no cable. A
program reads and sets the link the card holds, as a player does by right-clicking a block with it.

<div class="infobox" markdown="1">

### Facts
| | |
|---|---|
| Type | `bluetooth` |
| Address | An expansion slot path such as `exp:0` |
| Interrupt | None |
| Info | `kind` (`bluetooth`) |

</div>

## Boards and links

A link names a **board**, not a card: the id of a computer, a hub or a screen, as a UUID. A card's
**board** is the one its own computer is: what another card links to, to reach it. The link lives on
the card's item, read and written there. The board need not be present when you link to it.

See [Bus](/concepts/bus/) for how linked blocks join a computer's bus.

## In mcos

`/dev/bluetooth0` is text. A read gives two lines:

```
link 5f0c0000-0000-0000-0000-000000000000
board 9a210000-0000-0000-0000-000000000000
```

`link -` means no link, and `board -` means the card has no board. The state is taken at the first
read after the file is opened (or sought back to its start). Writing a line relinks the card as soon
as the line is written (flushed): `link <id>`, or `link -` for none. A line that is not a link, or
an id that is not a board's, fails the write with `EINVAL`.

```sh
cat /dev/bluetooth0
echo link - > /dev/bluetooth0
```

The card has no events, so `poll` never reports it `PRI`; the file is always readable (`IN`) and
writable (`OUT`). Its ioctls are the card's methods, by name, in any open mode:

<!-- lua-fragment -->
```lua
sys.ioctl(fd, "setLink", "5f0c0000-0000-0000-0000-000000000000")
```

A method that fails answers `nil`, its message and `EINVAL`; a card that is gone, `ENODEV`.
`setLabel` labels the card, as for any device (see [The sys library](/mcos/sys-api/)).

## Methods

### getLink() → id or nil

The id of the board it links to, or nil.

### setLink(id) → nothing

Links it to the board with that id, as linking it by hand does. Nil, or no argument, links it to
none. The board need not be there now. An id that is not a UUID, or not a string, fails with
`not a board id`. Through the ioctl it answers `true`.

### getBoard() → id or nil

The id of the board it is in: its computer's, what another card links to, to reach it. Nil if it
has none.

### get() → {link, board}

Both, as a table. A missing one is left out.

### set({link = id}) → nothing

Links it to that board; `{link = false}` (or no `link`) links it to none. Otherwise as `setLink`.

## Events

None.

## Examples

Run these with `lua name.lua`, from a computer with a Bluetooth card.

### Link from a shell

```sh
echo link 5f0c0000-0000-0000-0000-000000000000 > /dev/bluetooth0
cat /dev/bluetooth0
```

The first line of the output is `link 5f0c0000-0000-0000-0000-000000000000`. Replace the id with a
real one: yours are shown by `cat /dev/bluetooth0` on the other computer, in its `board` line.

### Switch between boards

```lua
{% include bluetooth-switch.lua %}
```
[Download bluetooth-switch.lua](/examples/bluetooth-switch.lua)

An [alias](/mcos/shell/) makes it one word: `alias next='lua /home/bluetooth-switch.lua 5f0c... 9a21...'`.

### Remember a link

```lua
{% include bluetooth-remember.lua %}
```
[Download bluetooth-remember.lua](/examples/bluetooth-remember.lua)
