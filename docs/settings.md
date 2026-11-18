---
title: Settings
section: Start
order: 2
---
Modular Computers has a few settings a server's admin can change: how many computers run at once,
how long batteries last, what devices' actions cost, how far internet cards reach, and more. This
page lists every one. Other pages say when something is a setting and link here; the values are
only here, as they're the server's to change.

## Editing them

The settings are in `config/modularcomputers.properties` in the game folder (on a server, the
server's). The game writes the file with every setting at its default the first time it starts.
Each line is one setting, `key: value`, under a comment that explains it; lines starting with `#`
are comments.

- Edit the file, then run `/modularcomputers reload` (an operator's command) to read it again
  without a restart, or restart the game.
- A setting missing from the file, or with a value it can't use, takes its default, and the game
  logs a warning saying which. A line whose key isn't a setting is ignored, with a warning too.
- A file from an older version gets the settings it's missing added at the end, at their defaults,
  so new settings show up to edit. What you set is kept.
- A server sends the settings players see (how long batteries last, for charge bars and tooltips)
  to each player who joins it, so a player sees the server's, not their own game's.

Costs are counted in ticks of a running [CPU](/hardware/cpu/)'s power (100 W for a tick): `1` costs
what a running CPU draws in one tick, `0.5` half that. Fractions work everywhere a number does; a
cost of `0` makes that action free. See [Power](/concepts/power/).

## computer {#computer}

| Setting | Default | What it does | Values |
|---|---|---|---|
| `computer.cpu_cores` | `4` | How many computers run their Lua at once: the server's cores they share. A busy computer runs as fast as its core goes; with more busy computers than cores, they take turns. A computer waiting (for a key, a device, the server) takes no turn. See [Persistence](/concepts/persistence/#what-a-budget-means). | a whole number, 1 or more |

## power.battery {#power-battery}

[Batteries](/hardware/battery/) are rated in Minecraft days (20 minutes each) of a running computer:
its CPU running and its screen.

| Setting | Default | What it does | Values |
|---|---|---|---|
| `power.battery.tier1.duration` | `10` | How many days a full tier 1 battery runs a computer. | a number, 0 or more; 0 never runs out |
| `power.battery.tier2.duration` | `20` | How many days a full tier 2 battery runs a computer. | a number, 0 or more; 0 never runs out |
| `power.battery.tier3.duration` | `0` | How many days a full tier 3 battery runs a computer. | a number, 0 or more; 0 never runs out |
| `power.battery.tier2.recharge_rate` | `0.5` | How fast a tier 2 battery recharges itself while it isn't running anything, as a share of a running computer's draw: `0.5` takes two seconds of rest to charge one second of running, `1` charges as fast as it runs. | a number, 0 or more; 0 turns it off |

## power.rate {#power-rate}

How fast blocks give power, in running CPU ticks each tick.

| Setting | Default | What it does | Values |
|---|---|---|---|
| `power.rate.charging_pad` | `10` | What a [charging pad](/hardware/charging-pad/) gives the robot on it. | a number, 0 or more |

## power.cost {#power-cost}

What devices' actions cost, in running CPU ticks. The bot golem's own costs are under
[robot](#robot).

| Setting | Default | What it does | Values |
|---|---|---|---|
| `power.cost.redstone.change` | `1` | A [redstone card](/devices/redstone-card/): each change of an output. | a number, 0 or more |
| `power.cost.robotic_piston.push` | `5` | A [robotic piston](/devices/robotic-piston/): each push, extend or retract, whether it moved anything or not. | a number, 0 or more |
| `power.cost.field.hold` | `2` | An [energy field card](/devices/energy-field-card/): each tick it holds something. | a number, 0 or more |
| `power.cost.field.move` | `1` | An energy field card: each block it moves what it holds. | a number, 0 or more |
| `power.cost.crafting.craft` | `1` | A [crafting card](/devices/crafting-card/): each craft. | a number, 0 or more |
| `power.cost.crafting.move` | `1` | A crafting card: each move of items (between its slots, into its robot's hand, to or from a container, a pickup, a drop). | a number, 0 or more |
| `power.cost.disk_station.action` | `20` | A [Disk Station](/devices/disk-station/): each action (a format, wipe, clone, install or update). | a number, 0 or more |
| `power.cost.network.wired_send` | `0.1` | A [network card](/devices/network-card/): each frame it sends. | a number, 0 or more |
| `power.cost.network.wireless_send_per_block` | `0.0625` | A [WLAN card](/devices/wlan-card/): each frame it sends, for each block of its strength, so a farther signal costs more. | a number, 0 or more |
| `power.cost.internet.request` | `1` | An [internet card](/devices/internet-card/): each request. | a number, 0 or more |
| `power.cost.internet.data` | `0.1` | An internet card: each 4 KiB of a body read (a part of 4 KiB costs it whole). | a number, 0 or more |
| `power.cost.terminal.write` | `0.01` | A [screen](/devices/terminal/): each write to it, whatever its length. | a number, 0 or more |

## device {#device}

| Setting | Default | What it does | Values |
|---|---|---|---|
| `device.ring_depth.all` | `512` | Every device's [ring depth](/concepts/interrupts/#no-end-is-ever-lost): how many of its news events it keeps (the oldest go when more come), and how many of its commands' ends wait unread before it refuses its next command. A network card's frames wait as many. | a whole number, 1 or more |
| `device.ring_depth.<type>` | not set | One type's own ring depth, in place of `device.ring_depth.all` (see below). | a whole number, 1 or more |

**A type's own ring depth is opt-in.** It isn't in the file: add a line for the type you want, named
by its kind as `/sys/class` names it (`device.ring_depth.robotics`, `device.ring_depth.redstone`,
`device.ring_depth.motion`, `device.ring_depth.network`...). A type without its own line takes
`device.ring_depth.all`. So it has no default of its own: its default is everyone's.

## internet {#internet}

[Internet cards](/devices/internet-card/) make HTTP and HTTPS requests from the server. By default
they reach only the public internet: never the server itself (localhost), its network (10.x,
192.168.x...), link-local addresses (169.254.x, where a cloud machine keeps its metadata), or other
private and reserved ranges.

| Setting | Default | What it does | Values |
|---|---|---|---|
| `internet.enabled` | `true` | Whether internet cards work at all. Off, every request fails. | `true` or `false` |
| `internet.allow` | nothing | Hosts, domains or ranges reached though they'd be refused: a LAN service you mean computers to use. | a comma-separated list: hosts, domains (`.example.com`), ranges (`203.0.113.0/24`) |
| `internet.deny` | nothing | Hosts, domains or ranges also refused. A deny wins over an allow. | a comma-separated list, as `internet.allow` |
| `internet.response_buffer_kib` | `64` | How much of a response's body each request holds unread, in KiB, before its download waits for the program to read it. | a whole number, 1 or more |

## robot {#robot}

The [bot golem](/hardware/bot-golem/) and its [robotics](/devices/robotics/).

| Setting | Default | What it does | Values |
|---|---|---|---|
| `robot.pathfinding_range` | `96` | How far its pathfinding searches, in blocks walked. Where something stops a walk, it searches once, within this, for its way back onto its line past it; none found, the walk ends blocked. A walk itself goes however far it's told. A flight's way is found whole within this, or not at all. | a whole number, 1 or more |
| `robot.cost.walk` | `1` | Each block walked (a part of a block, its part). | a number, 0 or more |
| `robot.cost.turn` | `1` | Each turn, however far. | a number, 0 or more |
| `robot.cost.hand` | `1` | Each action with its hand: a use, a swing, a store into a container, a take from one. | a number, 0 or more |
| `robot.cost.thrusters` | `5` | Each tick its thrusters fire, flying or hovering. | a number, 0 or more |
