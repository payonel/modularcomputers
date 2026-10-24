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
| Address | An expansion slot path such as `exp:0` |
| Availability | Creative only |
| Interrupt | None |
| Info | `kind` (`operator`) |

</div>

The card runs a command at its own block, with a command block's permission: `time`, `weather`,
`give` and the like (a game master's), but not `op` or `stop`. Each command is logged on the server.
It returns how the command went and what it said.

## In mcos

`/dev/operator0` runs commands. A line written runs one, taking effect at once (a leading `/` is
allowed). A write whose command failed fails, and the file then says why. A read gives what the last
command said.

```sh
echo time set day > /dev/operator0
cat /dev/operator0
```

## Methods

### command(text) → {success, result, output}

Runs a server command, with or without its leading `/`. `success` says whether it worked, `result`
is the command's value (0 when it failed), and `output` is what it said (its lines, ending in a new
line). An empty command fails with `no command`. A card not in a world answers
`success = false`, `output = "not in a world"`.

### get() → string

What the last command said.

### set({command = text}) → {success, result, output}

Runs a command, as `command` does. A command that fails makes this call fail, with what it said.

## Events

None.

## Example

```sh
echo "time set day" > /dev/operator0
cat /dev/operator0
```

The second command prints what the server said, such as `Set the time to 1000`.
