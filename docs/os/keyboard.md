---
title: Reading the keyboard
section: Your own OS
order: 2
---

An OS that reads what you type, a line at a time, and says it back. Type `off` and it turns the
computer off. This is where your OS starts talking with the hardware both ways: it waits for a
device, and calls one.

## What you need

- [ ] Your OS disk from [Booting your own code](/os/boot/) in the second SATA socket, its boot entry
  made (`Boot0000`), and mcos's disk in the first, to write files with.
- [ ] A [screen](/hardware/screen/) on the computer. Right-click its glass to open it: what you type
  then goes to the computer.

## The whole thing

Replace `/init.lua` on your OS disk with this:

```lua
{% include os-echo.lua %}
```

[Download os-echo.lua](/examples/os-echo.lua)

Boot it (`efibootmgr -n 0`, then `reboot`) and type:

```
Type something. Type off to turn the computer off.
> hello
you typed: hello
> off
```

The computer turns off. Typing mistakes can be rubbed out with Backspace; the arrow keys do nothing.

## How it works

### The screen shows nothing by itself

A [terminal](/devices/terminal/) is two devices in one, like a real VT100 on a serial line: a screen
that shows what's written to it, and a keyboard whose keys wait in the terminal until a program reads
them. When you press a key, nothing appears. The letter appears only because the OS writes it back
(`write(key)`). That's called *echo*, and every OS does it: in mcos, the kernel's terminal driver
does.

### Sleeping until a key comes

```lua
board.waitForInterrupt(nil, "console")
```

The computer stops here and draws only its waiting power, until the console has keys. `nil` means
no time limit. This is an [interrupt](/concepts/interrupts/): a device with news raises its line,
and the waiting computer wakes. Interrupts are *level-triggered*: the line stays up while the keys
are waiting, so the call returns at once if you typed while the OS was busy. Nothing is lost.

### Taking the keys

```lua
local ok, keys = board.invoke("console", "read")
```

Every call to a device answers first with whether it worked. Then `read` gives what was typed since
the last read, as one string of bytes (several keys, if you typed fast), or `nil` when there's
nothing.

That's why the OS waits first and reads second: **a device's methods never wait**. `read` answers at
once with what the terminal has, nothing if no key's been pressed; only `board.waitForInterrupt`
waits. Reading in a loop without the wait would work, but it would spin, calling the terminal
thousands of times a second at the CPU's running power. (A key can still be taken by another
reader between the wait and the read, so the loop checks for `nil` and goes round again.) In mcos,
`io.read()` waits because the kernel does these same two calls for you; your OS has no kernel
underneath, so it does them itself. See [Interrupts](/concepts/interrupts/#device-calls-never-wait). The loop handles each byte:

| You press | The byte(s) | The OS |
|---|---|---|
| a letter, a digit, space... | the character | adds it to the line, and echoes it |
| Enter | `"\r"` (13) | ends the line |
| Backspace | 127 | removes the last character, and writes `"\b \b"`: back a column, a space over the letter, back again |
| an arrow key, F1... | ESC (27), then `[` and more, ending in a letter or `~` | skips the whole sequence |

So `readline` builds the line in a string, and returns it at Enter. The main loop at the bottom reads
a line, turns the computer off if it's `off`, and otherwise writes it back. It never ends: an OS
that returns makes the CPU abort.

### `write` and new lines

`write` turns every `\n` into `\r\n`, so the rest of the code can write `"\n"` as usual. A terminal
moves down a row on `\n` but goes back to the left only on `\r`.

## When it goes wrong

- **Nothing appears when you type:** check that you're typing into the screen (its glass right-clicked open), and
  that the OS reached its prompt (`> `).
- **The light blinks amber:** an error while it ran (or your code ran out: an OS must never return)
  aborted it. Why is kept on the board: boot mcos and `cat /sys/firmware/last_shutdown/message`
  says the line. A typo in a name (`board.invok`) shows only then, not at boot, because Lua finds it
  only when that line runs.

Next: [A command shell](/os/shell/).
