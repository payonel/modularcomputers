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
  succeeded in no run fails (`EINVAL`); the card's `get` then says what the server answered. An
  empty line fails the same way.
- **Read:** its log, a line for each command it runs (any program's), from the moment the file was
  opened: the command, how many of its runs succeeded, its result and what it said. A read waits for
  the next one, so a `cat` or `tail -f` left running shows commands as they run. The card keeps its
  last 32; a `lost n` line says some went before a reader took them.
- **Poll:** `IN` once a line is there to read.
- **ioctls:** its methods below, by name, in any open mode:
  `sys.ioctl(fd, "command", "time set day")`. A failure is `nil`, its why, `EINVAL` and the
  failure.
  `setLabel` sets its label, as on every device file.

```sh
tail -f /dev/operator0 &
echo time set day > /dev/operator0
```

The `tail` shows the command as it runs: `time set day: 1 1000 Set the time to 1000`.

## Methods

### command(text) → {successes, result, message, key}

Runs a server command, with or without its leading `/`, and answers with what the game gave of it,
as an HTTP response is a server's: the card ran it, and the rest is the command's own.

<!-- lua-fragment -->
```lua
local ok, answer = board.invoke(card, "command", "time set day")
-- ok = true, answer = {successes = 1, result = 1000, message = "Set the time to 1000\n", key = "commands.time.set.absolute"}
```

| Field | Value |
|---|---|
| `successes` | how many of its runs succeeded (a command block's success count: 0, it didn't work; more than 1 for a command run for several targets, such as `execute as @e ...`) |
| `result` | its last run's value (what `execute store result` would keep), when it succeeded |
| `message` | what it said, its lines, each ending in a new line: words for a person, never to parse |
| `key` | its last message's translation key (`commands.time.set.absolute`, `argument.item.id.invalid`), the same in every language the server speaks; `""` when it said nothing, or nothing translatable |

A command that didn't work still answers, with `successes = 0`: only an empty command fails the
call, with `bad_argument` (`expected = "command"`).

### get() → {successes, result, message, key} or nil

Its last command's answer, as a command block keeps its own (nil before the first).

### pull() → entry or nil

Its log's oldest entry, a command it ran: `{event = "command", command, successes, result, message,
key}` (`command` as it ran, its `/` dropped), and `lost` when entries went before it.

## Events

Each command it runs is an entry in its log (`pull`), and raises its interrupt.

## Examples

Endless day: at dusk the program writes two commands to the card's file:

```lua
{% include operator-daylight.lua %}
```

A file of commands run through the `command` ioctl, each logged, stopping at the first that fails:

```lua
{% include operator-script.lua %}
```

A greeter at the door: a redstone card hears a pressure plate, `command` runs a `title` command, and its
`message` says who it was shown to:

```lua
{% include operator-greeter.lua %}
```
