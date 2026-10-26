---
title: OpenComputers programs
nav: openos
section: mcos
order: 78
---

`openos` runs a program written for OpenComputers' OpenOS, as wine runs a Windows program on Linux:

```
$ openos /home/hello.lua
$ openos /home/door        # (.lua optional)
```

The program gets a machine of its own, made of mcos's: OpenComputers' `component`, `computer` and
`unicode`, and OpenOS's own libraries, ported (`event`, `term`, `keyboard`, `shell`, `filesystem`,
`serialization`, `text`, `sides`, `colors`, `uuid`, `robot`). So `event.pull`, `term.read`,
`os.sleep` and `component.gpu` behave as they did in OpenOS.

```lua
local component = require("component")
local event = require("event")
local gpu = component.gpu

gpu.setForeground(0x33CC33)
gpu.set(1, 1, "press a key")
local _, _, char, code = event.pull("key_down")
print("you pressed " .. unicode.char(char) .. " (code " .. code .. ")")
```

## What's a component

| OpenComputers | Here |
|---|---|
| `gpu`, `screen`, `keyboard` | the program's terminal: the gpu draws on it, its keys and mouse are the signals |
| `computer` | this computer (its address is `/etc/machine-id`'s) |
| `eeprom` | the EEPROM: its code and label |
| `redstone` | a [redstone card](/devices/redstone-card/), sides relative to the block, as OpenComputers' |
| `modem` | a [network card](/devices/network-card/), its messages UDP datagrams |
| `robot` | a [bot golem](/hardware/bot-golem/)'s robotics: its moves wait until done, as OpenComputers' |

Other devices aren't components (yet): the program finds none of them.

## Signals

`computer.pullSignal` waits on everything at once (the terminal, each device, each open port):
`key_down` and `key_up` (with each key's character and OpenComputers' key code), `touch`, `drag`,
`drop`, `scroll`, `clipboard` (a paste), `redstone_changed`, `modem_message`, `component_added` and
`component_removed` (a device cabled up or taken away), `screen_resized`. Ctrl+C is OpenOS's
`interrupted` signal, and Ctrl+Alt+C ends the program.

## What's different

openos never does something else in place of what a program asked: what it can't do is an error
that says so (`openos: OpenOS's 'process' library is not supported`).

- **Not there:** the `process`, `thread`, `internet`, `pipe`, `rc` and `devfs` libraries; the gpu's
  video memory; viewports smaller than the screen; filesystem and internet components; a robot's
  tanks, light, and slots past its hand (a golem's inventory is its hand).
- **mcos's commands:** `os.execute` and `shell.execute` run mcos's `sh`, so `shell.execute("ls")` is
  mcos's `ls`. `filesystem` is mcos's files (no mounting).
- **The robot walks:** `robot.up()` walks a block up (a golem jumps onto it); it doesn't hover.
- **Redstone input** is a face's level, which includes the card's own output.
- **A modem message** of one string is that string as a datagram, so an mcos program (`nc -u`) can talk
  with it; other messages are their values serialized.
- **The screen** before the program ran, or after a command it ran, reads as blank to `gpu.get`.

OpenOS's libraries are MIT-licensed, the license in `/usr/share/openos/LICENSE`.

See [openos(1)](/programs/openos/), and [Coming from OpenComputers](/tutorials/from-opencomputers/)
for doing the same things mcos's way.
