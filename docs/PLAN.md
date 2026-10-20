# Plan

## What this file is, and how it stays short

This is the roadmap: **only what isn't built yet**, ideas the user has given and questions still
open, each with the user's reasons where they gave them. What's built is recorded by the code, and
the reasons behind its design by `AGENTS.md`.

Keeping it reduced (user, 2026-10-20):

- **When an item is built, take it out** in the same commit. If its design had a reason the code
  doesn't show, move that reason to `AGENTS.md` (architecture decisions) first.
- **No history here:** no "Done" list, no "built on" notes, no record of how a decision was
  reached. Git has the history.
- **An item is a few lines:** what it is, why (the user's words when they gave them), and whether
  it's decided or still open. A design worked out in detail goes into the code once it's built, not
  here.
- **When a change touches an area, read that area's items here** and drop or update the ones it
  settles or makes wrong.
- **Something the user turned down stays only if it's likely to come up again**, as a short "not
  wanted" note, so it isn't proposed twice.

## Hardware and blocks

- **Mains power:** a real supply, with the UPS batteries as a backup that takes over when it drops.
- **Battery ideas, undecided:** refusing to overcharge (extra material is wasted today); a range or
  rate setting for tier 2's self-charging.
- **CPU status dots:** the 4 dots left of the CPU socket only light when a CPU is in; a place for
  more status later.
- **Narrow windows:** the bus order panel is hidden below about 382 GUI units; maybe a toggle or a
  smaller layout.
- **Tier 3 terminals draw pixels:** sixel is the plan (a VT340's pixel graphics by escape sequence);
  the terminal answers `;4` in its Device Attributes once it's built, so programs never send pixels
  to a terminal that would drop them.
- **Multiblock screens that grow** (user idea, undecided): neighboring screens merging into one
  bigger display.
- **The all-in-one** is a dev kit: it may go away, or become a creative "everything built in" block.
- **Cards still placeholders:** WLAN, Audio, Audio Sensor. The paint gun (shoots paint at nearby
  wool, out of its nozzle face) does nothing yet.
- **Bluetooth, open:** range limits; whether links show differently in the bus order; what a
  Bluetooth card does on the bus itself.
- **Disks, open:** disks in expansion hubs; labels set by programs.
- **A bell:** terminals ignore BEL, and a full key queue drops keys silently, where a real keyboard
  beeps.
- **Beep codes:** audible boot errors for headless machines.
- **Monospace bitmap font:** text uses Minecraft's proportional font, centered per cell.
- **Console, open:** a log ring in the console (each reader with its own position, and a "messages
  dropped" line instead of silent loss, like printk and `/dev/kmsg`); a stored preference for which
  displays it uses (an NVRAM store, like UEFI's `ConOut`); today it's every display, at the
  smallest one's size.
- **The bus walk has no gaps** (disconnected and absent devices are left out). Open: whether
  firmware should see them, as PCI addresses skip empty slots.

## Getting started in the world

- **The player's introduction** is the next discussion: advancements, archaeology loot, how the
  first photomask is found.
- Programs from ruins and traders (disks), and books with programs in them.
- The photomask gets a device API (programs start and watch its work).
- Saved for later (user): shulker shells for a robot with deep internal storage.

## The Lua CPU and the server

- **RAM sizes:** settle the tiers' sizes (and their ring sizes) once the VM and mcos are finished,
  with OC's values as a reference.
- **mcos needs a 1 MiB stick** (the user: optimize later; maybe raise tier 1's capacity): the shell is
  the biggest cost. Options: a smaller boot set, bigger RAM tiers, asking LungoLua what a compiled
  prototype costs (about 1.4 times the C++ build's).
- **Server time across many computers:** each computer gets its own server time a tick, with no
  overall management; later, round robin which machines get time each tick, if many busy
  computers starve the tick.
- **Settings:** tunables stay named constants until settings are revisited.

## mcos

- **Devices for user programs** (user, 2026-10-19: hold off; they have concerns to discuss first):
  programs can't reach devices (`board` is the kernel's). The plan so far is `/dev` files and
  `ioctl`. Waiting on it: editing the EEPROM through a `/dev` file (the user: not a `flash`
  command), and the REPL as a workbench for trying devices without writing a script.
- **A tty per terminal:** the boot option (`console=`) names one device; a tty, interrupt handler
  and shell per terminal isn't started yet.
- **Signal handlers run only on the way back from a syscall** (a stopped process can't be made to
  call one), so a busy process sees its signal at its next syscall; Linux runs them on any return to
  user mode.
- **Job control:** `jobs`, `fg`, `bg`, ^Z, SIGTTIN. Background jobs share the shell's group and
  ignore SIGINT, as sh without job control.
- **The shell's language:** `if`, `for`, `while`, `case`, functions, here-documents (user: maybe
  later).
- `/proc`; a filesystem on raw disks; permissions and users; `os.date` (no real-time clock: the time
  is seconds since boot); a page cache; noticing the bus's hot-plug at once, not when the kernel
  idles.
- **Later, from the user's review of OpenOS (2026-10-19):**
  - `type -a`;
  - `install`, once loot software is worked out;
  - `rc`, once there are start-up services;
  - `sides`, with the redstone card's API;
  - profile, `.shrc` and motd;
  - maybe `label`, `primary`, `resolution`, `address`.
  - The internet card's commands (`wget`, `pastebin`) will be its device's API.
- **Not wanted** (user, 2026-10-19): `which` (it's `command -v`), `lshw`, `dmesg`,
  `useradd`/`userdel`, OpenOS's `transforms` and `event`/`keyboard` libraries.
