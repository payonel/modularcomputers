---
title: A command shell
section: Your own OS
order: 3
---

A prompt that runs commands, the way every operating system's first shell did. Type a command's name
and its arguments, press Enter, and it runs; then the prompt comes back. Its commands are a tour of
the board's API: the devices on the bus, their methods, calling any of them, waiting for interrupts,
files on the disk, and programs. It's the [last page](/os/keyboard/)'s OS with a table of commands
added.

## What you need

- [ ] Your OS disk, first in the bus order, and the mcos disk to write files with.

## The whole thing

Replace `/init.lua` on your OS disk with this (it's long to type: [download it](/examples/os-shell.lua)
with `wget`, or copy it into the disk's folder; see [Booting your own code](/os/boot/#2-write-initlua)):

```lua
{% include os-shell.lua %}
```

Boot it and try each command:

```
tiny OS, booted from sata:0. Type help.
# help
devices                    the devices on the bus
...
# devices
eeprom eeprom id:6f1c...
sata:0 storage id:9a02...
id:41be... terminal id:41be...
# methods storage
changes(): the oldest change on it not yet taken, ...
getCapacity(): how many bytes it holds
...
# call sata:0 getCapacity
1048576
# save /hello.txt Hello, disk!
# cat /hello.txt
Hello, disk!
# ls
hello.txt
init.lua
# uptime
41.5
# off
```

## The commands

| Command | What it does | The board call |
|---|---|---|
| `help` | lists the commands | |
| `devices` | each device on the bus: its address, type and id | `board.list()` |
| `methods <device>` | every method of a device, with what it does | `board.methods`, `board.doc` |
| `call <device> <method> [args]` | calls a method, and shows what it answered | `board.invoke` |
| `wait [seconds]` | waits for any device's interrupt, and shows which | `board.waitForInterrupt` |
| `ls [dir]` | the files in a folder of the boot disk | the disk's `list` |
| `cat <file>` | shows a file | the disk's `open`, then the file's `read` and `close` |
| `save <file> <text>` | writes the text (the rest of the line) to a file | the disk's `open`, the file's `write`, `close` |
| `run <file> [words]` | runs a Lua program from the boot disk | `load` |
| `uptime` | seconds since the computer started | `board.uptime()`, in ticks |
| `reboot`, `off` | restarts the computer, or turns it off | `board.reboot()`, `board.poweroff()` |

A `<device>` is an address (`sata:0`, `exp:1`, `id:...`, `console`) or a type (`redstone`, meaning
the first redstone card on the bus).

## How it works

### Reading a command

The loop at the bottom reads a line with `readline` (the last page's), splits it into words at spaces,
and looks the first word up in the `commands` table. Each command is a function given the other
words, and the whole line for a command that wants it as typed (`save` keeps the spaces in your
text; `call` reads its arguments as Lua).

The command runs inside `pcall`, so a mistake (a bad argument, a bug in a command) is printed as
`error: ...` and the shell goes on. Without it, any error would end your OS, and the CPU would abort.

### Calling any method

`call` is the most powerful command: anything a device can do, you can do from the prompt.
Everything after the method's name is read as Lua values, so you write arguments as you would in a
program: numbers, `"strings"` in quotes, `true`, and tables like `{north = {powered = 15}}`. Its
answers are shown the same way, tables as `{key = value}`.

```
# call sata:0 stat "/init.lua"
{directory = false, modified = 1200850, size = 8431}
# call sata:0 nope
failed: no such method: nope
```

A call never stops the computer. When it fails, `board.invoke` answers `false` and why, and `call`
shows `failed:` and the message.

### Files on the disk

A disk on its own knows nothing about paths beyond its own files: `open` gives a *handle*, an
address of its own, and you call `read`, `write` and `close` on the handle as on a device. A file's
new contents are kept when it's closed, all at once. `readfile` and `writefile` wrap those calls.
See [Hard disk](/devices/disk/) for everything a disk does.

### Running programs

`run` reads a Lua file from the disk, loads it, and calls it with its words. The program sees the
OS's `print`, `write` and `readline`, its words as `arg`, and every global (`board` among them). The
shell waits while it runs, and its prompt comes back when the program returns. There's an example
on the [next page](/os/devices/).

## Adding a command

A command is one function in the table. To add `echo`, which writes its words back:

```lua
commands.echo = function(words)
  print(table.concat(words, " "))
end
```

Put it with the other commands (before the shell's loop at the bottom), and add a line to `help`.

## When it goes wrong

- **`<name>: no such command`:** a typo, or a command you haven't added.
- **`no device <name>`** (from `methods`): nothing on the bus has that address or type. `devices`
  lists what's there.
- **`bad arguments: ...`** (from `call`): what follows the method isn't valid Lua: a string without
  its quotes, say (`"/init.lua"`, not `/init.lua`).

Next: [Talking to devices](/os/devices/).
