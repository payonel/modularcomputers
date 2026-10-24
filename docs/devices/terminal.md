---
title: Terminal
section: Devices
order: 10
---

A terminal is a screen and a keyboard in one device, like a VT100 on a serial line. A program writes
text to it, with escape sequences to move the cursor and set colors, and reads back the keys typed.

<div class="infobox" markdown="1">

### Facts
| | |
|---|---|
| Type | `terminal` |
| Tiers | 1 (50 by 16, monochrome), 2 (80 by 25, 16 colors), 3 (160 by 50, every color and sixel pictures) |
| Interrupt | Raised while keys wait to be read |
| Power | 10 W while powered, and a little per write |
| Info | `tier`, `width`, `height`, `colors` |

</div>

![A tier 3 screen block showing text and a small magenta sixel picture](/assets/img/screen-tier3.png)
*A tier 3 terminal showing text and a sixel picture.*

For the block you place and look at, see [Screen](/hardware/screen/).

## In mcos

A terminal has no file of its own in `/dev`. Terminals are grouped into the **console**: mcos reads
the keys typed at any of them and writes to all of them through `/dev/console` (and `/dev/tty` for a
program's own terminal). The console's size is the largest every terminal in the group can show.
See [/dev](/mcos/dev/).

## Methods

### write(text) → nothing

Sends `text` to the terminal. Escape sequences in it are decoded by the terminal itself. Writes are
raw: a line feed only moves the cursor down, unless the program turns on new line mode (`ESC[20h`),
so write `\r\n` for a new line. Each write costs a little power.

### read([max]) → string or nil

Takes the bytes waiting, at most `max` of them, as a serial line gives them: the keys typed at this
terminal (an arrow key is its escape sequence's bytes among the others) and the terminal's answers to
reports (such as the cursor position). Keys are never split. With nothing waiting, it gives nil.

### getSize() → columns, rows, maxColumns, maxRows

The size now, then the largest it can be made. A program resizes it with xterm's sequence
`ESC[8;rows;cols t`, up to the largest. `ESC[18t` reports the size and `ESC[19t` the largest.

## Events

A terminal raises its interrupt while it has bytes waiting. The queue holds at most 256 keys
and reports; past that, new keys are dropped. Keys typed while no program runs on the computer are
dropped too.

Typing comes from the screen view: only the terminal a player has open gets the keys.

## What a terminal understands

All tiers use the same VT decoder, and differ in colors:

| Tier | Model | Colors | Answers Device Attributes (`ESC[c`) with |
|---|---|---|---|
| 1 | VT102 | one phosphor, shown as intensities (bold, normal, reverse) | `ESC[?6c` |
| 2 | VT525 | the 16 basic colors (others show as the nearest) | `ESC[?65;22c` |
| 3 | VT340 | every 24-bit color, and sixel pictures (`DCS q`) | `ESC[?63;4;22c` |

It understands the common xterm subset: cursor movement, erasing and editing, scroll regions, SGR
(bold, reverse, 16, 256 and 24-bit colors), the alternate screen, the cursor's shape, device status
and cursor position reports, and the kitty keyboard protocol (flags 1, 2 and 8). Anything else is
parsed and ignored. A BEL (`\a`) rings the terminal's bell at its block.

Lines that scroll off the top of the main screen are kept, 200 of them, and the screen view scrolls
back over them. They are lost when the block unloads.

## Example

Draw a colored word on every terminal in the console, and ask for the size:

```lua
local ok, columns, rows = board.invoke("console", "getSize")
board.invoke("console", "write", "\27[2J\27[1;1H\27[31mhello\27[0m, " .. columns .. "x" .. rows .. "\r\n")
```

`\27[2J` clears the screen, `\27[1;1H` homes the cursor and `\27[31m` selects red. The first call
gives `true, columns, rows, maxColumns, maxRows`; the code keeps the first two numbers.
