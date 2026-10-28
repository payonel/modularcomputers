---
title: Operator Card
section: Devices
order: 110
---

An operator card runs server commands, as a command block does. It exists in creative mode only.

<div class="infobox" markdown="1">

### Facts
| | |
|---|---|
| Type | `operator` |
| Address | An expansion slot path such as `exp:0`, or a hub's |
| Availability | Creative only |
| Interrupt | None |
| Info | `kind` (`operator`) |

</div>

The card runs a command at its own block (in a [case](/hardware/case/), or in an
[expansion hub](/hardware/expansion-hub/), at the hub), with a command block's permission: `time`,
`weather`, `give`, `title` and the like (a game master's), but not `op` or `stop`. Its commands come
from "Operator Card", and each command is logged on the server, so its admins see what ran. It
returns how the command went and what it said. What a command says goes back to the program, not to
any player.

## In mcos

`/dev/operator0` (`operator1` for the next card) runs commands.

- **Write:** a line written runs one, at once (a leading `/` is allowed). A write whose command
  failed fails (`EINVAL`), and the file then says why. An empty line fails the same way.
- **Read:** what the last command said, its lines. The text is taken at the first read; seek back to
  the start to read the newest.
- **Poll:** always ready for `IN` and `OUT`. The card has no interrupt, so never `PRI`.
- **ioctls:** its methods below, by name, in any open mode:
  `sys.ioctl(fd, "command", "time set day")`. A failure is `nil`, its message and `EINVAL`.
  `setLabel` sets its label, as on every device file.

```sh
echo time set day > /dev/operator0
cat /dev/operator0
```

The second command prints what the server said, such as `Set the time to 1000`.

## Methods

### command(text) → {success, result, output}

Runs a server command, with or without its leading `/`, and answers how it went. `success` says
whether it worked, `result` is the command's value (0 when it failed), and `output` is what it said
(its lines, each ending in a new line). A command that fails still answers (with `success` false);
only an empty command fails the call, with `no command`. A card not in a world answers
`success = false`, `output = "not in a world"`.

### get() → string

What the last command said (its `output`), or `""` before the first.

### set({command = text}) → {success, result, output}

Runs a command, as `command` does: what a line written to the file does. A command that fails makes
this call fail, with what it said (`failed` when it said nothing). A table without a `command`
string fails with `no command`.

## Events

None.

## Examples

Endless day: at dusk the program writes two commands to the card's file:

```lua
{% include operator-daylight.lua %}
```

A file of commands run through the `command` ioctl, each logged, stopping at the first that fails:

```lua
{% include operator-script.lua %}
```

A greeter at the door: a redstone card hears a pressure plate, `set` runs a `title` command, and `get`
says who it was shown to:

```lua
{% include operator-greeter.lua %}
```
