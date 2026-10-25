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

A program uses a terminal through its **tty**, as on Linux: its standard input, output and error,
`/dev/tty` (the terminal of the process that opens it) and `/dev/console`. The kernel's line
discipline sits in between: it echoes what you type, edits the line, turns Ctrl+C into a signal and
`\n` into `\r\n` on the way out. A program draws by writing escape sequences to its tty (see
[What a terminal understands](#what-a-terminal-understands)), and reads keys from it, a line at a
time or, with the line discipline's raw mode (`stty`, `TCSETS`), as they come. The tty's ioctls
(`TCGETS`, `TCSETS`, `TIOCGWINSZ` for its columns and rows) are in [the sys library](/mcos/sys-api/).

Terminals are grouped into the **console**: mcos reads the keys typed at any of them and writes to
all of them through `/dev/console`. The console's size is the largest every terminal in the group
can show. See [/dev](/mcos/dev/).

Each terminal also has a file of its own, `/dev/terminal0` (`terminal1` for the next):

- **Read:** it has no data of its own, so a read says what it is, a line each:

  ```
  type terminal
  id id:3f2a...
  path -
  label -
  methods getSize read write
  ```

- **Write:** refused. Opening it to write fails with `EACCES`; a program writes to the terminal
  through its `write` ioctl.
- **Poll:** always ready for `IN` (that text). Ready for `PRI` while the terminal has news: keys
  arrived since the file was last read or asked an ioctl.
- **ioctls:** the terminal's methods below, by name, with their arguments. A method that answers
  nothing gives `true`; a failure is `nil`, its message and `EINVAL`, or `ENODEV` once the terminal
  is gone. `setLabel` sets its label, as on every device file.

<!-- lua-fragment -->
```lua
local columns, rows, mostColumns, mostRows = sys.ioctl(fd, "getSize")
```

A write through the file's ioctl skips the tty: it reaches that one terminal, raw (no `\r` added),
and works from a program with no terminal of its own (a service). Every terminal is in the console's
group, so what the console writes shows there too, and a `read` through the ioctl races the console
for the keys: each key goes to whichever reads first. Read keys through the tty.

## Methods

### write(text) → nothing

Sends `text` to the terminal. Escape sequences in it are decoded by the terminal itself. Writes are
raw: a line feed only moves the cursor down, unless the program turns on new line mode (`ESC[20h`),
so write `\r\n` for a new line. Each write costs a little power.

### read([max]) → string or nil

Takes the bytes waiting, at most `max` of them, as a serial line gives them: the keys typed at this
terminal (an arrow key is its escape sequence's bytes among the others) and the terminal's answers to
reports (such as the cursor position). Keys are never split; a read gives at least one key even if it
is longer than `max`. With nothing waiting, it gives nil.

### getSize() → columns, rows, maxColumns, maxRows

The size now, then the largest it can be made (in the console's group, the group's largest). A
program resizes it with xterm's sequence `ESC[8;rows;cols t`, up to the largest; `ESC[18t` reports
the size and `ESC[19t` the largest. [`resize`](/programs/resize/) does it from the shell.

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

Every tier reads text as UTF-8, as terminal emulators do today: a program writes UTF-8 bytes, and
the terminal shows the characters (a byte that isn't UTF-8 shows as `�`). Each character takes the
cells its width says, as glibc's `wcwidth` gives it (each code point its own width, as xterm's):

- most characters, accented letters, box drawing: one cell;
- CJK and most emoji: two cells;
- combining marks (an accent written after its letter) and format characters (a zero-width joiner):
  none, they go on the character before.

The table is mcos's `/usr/share/wcwidth`, so a program that measures text (mcos's line editing) and
the screen agree. Keys typed and pasted reach programs as UTF-8. The DEC character sets work too:
`ESC ( 0` puts DEC's line drawing in G0 (`ESC ( B` back to ASCII, `ESC ( A` the UK set), `ESC ) 0`
in G1, and SO (`\14`) and SI (`\15`) shift between them; `lqqk` in line drawing is `┌──┐`.

Some sequences a program uses most (`ESC` is `"\27"` in Lua):

| Sequence | Does |
|---|---|
| `ESC[row;colH` | moves the cursor (from 1;1, the top left) |
| `ESC[2J` | clears the screen |
| `ESC[K` | clears to the end of the line |
| `ESC7`, `ESC8` | saves the cursor, puts it back |
| `ESC[0m` | plain text again |
| `ESC[1m`, `ESC[7m` | bold, reverse video |
| `ESC[31m` to `ESC[37m`, `ESC[41m` to `ESC[47m` | a text color, a background color |
| `ESC[38;2;r;g;bm` | a 24-bit text color (`48` for the background) |
| `ESC[?25l`, `ESC[?25h` | hides the cursor, shows it |
| `ESC[?1049h`, `ESC[?1049l` | the alternate screen (a full-screen program's), and back |
| `ESC[6n` | asks where the cursor is: the answer, `ESC[row;colR`, comes back as typed |

`/usr/bin/vtdemo.lua` shows every tier's colors and a picture.

Lines that scroll off the top of the main screen are kept, 200 of them, and the screen view scrolls
back over them. They are lost when the block unloads.

## Examples

A clock in the corner of your terminal, drawn with escape sequences written to the tty while you
work (start it with `&`):

```lua
{% include terminal-clock.lua %}
```

The terminals on the bus and their sizes, from each one's `getSize` ioctl:

```lua
{% include terminal-list.lua %}
```

A notice in the middle of a screen, written through the terminal's own `write` ioctl:

```lua
{% include terminal-sign.lua %}
```
