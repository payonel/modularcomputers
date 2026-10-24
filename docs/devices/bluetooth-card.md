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
link 5f0c...
board 9a21...
```

`link -` means no link, and `board -` means the card has no board. Writing a line relinks the card:
`link <id>`, or `link -` for none. A line that is not a link fails the write.

```sh
cat /dev/bluetooth0
echo link - > /dev/bluetooth0
```

## Methods

### getLink() → id or nil

The id of the board it links to, or nil.

### setLink(id) → nothing

Links it to the board with that id. Nil, or no argument, links it to none. An id that is not a UUID
fails with `not a board id`.

### getBoard() → id or nil

The id of the board it is in: its computer's.

### get() → {link, board}

Both, as a table. A missing one is left out.

### set({link = id}) → nothing

Links it to that board; `{link = false}` links it to none. Same as `setLink`.

## Events

None.

## Example

Link to another computer, whose board id you copied from its card, and show the result:

```sh
echo link 5f0c0000-0000-0000-0000-000000000000 > /dev/bluetooth0
cat /dev/bluetooth0
```

The first line of the output is `link 5f0c0000-0000-0000-0000-000000000000`. Replace the id with a
real one: yours are shown by `cat /dev/bluetooth0` on the other computer, in its `board` line.
