# The plan: what isn't built yet

Ideas the user has given and questions still open, each with the user's reasons where they gave
them. `AGENTS.md` describes the code as it is; this file is what isn't built. Keep it short:

- **When an item is built, take it out** in the same commit; if its design had a reason the code
  doesn't show, it goes in `AGENTS.md`'s decisions.
- **No history:** no "done" list, no "built on" notes, no record of how a decision was reached (git
  has it).
- **An item is a few lines:** what it is, why (the user's words when they gave them), and whether
  it's decided or still open.
- **When a change touches an area, read its items here** and drop or update the ones it settles or
  makes wrong.
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
- **Resizes, what's left:** readline, `less` and `edit` don't redraw on SIGWINCH yet (they see a
  new size at their next line or start); a terminal doesn't report its own size changes (the
  device-reporting rule: today the kernel hears only through `TIOCSWINSZ`, as on a serial line;
  xterm's in-band resize reports, mode 2048, are a way); a 160-column row is ~1.1 KB to send
  (colors per cell), so a full redraw at 160×50 is ~56 KB: run-length colors if it matters.
- **Sixel, what's left:** pictures aren't kept in the scrollback (it's text) or on the main screen
  behind the alternate one; DECSDM draws at the cursor, not xterm's top left; no `img2sixel`-like
  tool yet (pictures are made by programs).
- **Multiblock screens that grow** (user idea, undecided): neighboring screens merging into one
  bigger display.
- **The gear-box tab, next** (user, 2026-10-20): on every block's GUI, a right-side tab (a gear-box
  icon) showing the block's device type ("tier 3 case", "tier 1 screen"), its device id (or
  "not assigned"), and a wireframe of the block with a faint render of the world around it, where
  clicking a side opens or closes its cable port (`CablePorts`, which every cabled block entity is to
  hold; none does yet).
- **Bundled redstone, later** (user, 2026-10-20): a face reading one of the 16 colors of a bundled
  input (Ender IO's conduit on NeoForge, or a bundled cable of ours), maybe as a tier 2 redstone
  card. No Fabric mod carries bundled signals on 26.x (RSWires stopped at 1.18.2). (A redstone
  terminal block was built and dropped, 2026-10-21: the user went back to the card.)
- **Devices that should report their state's changes, not yet:** the robotic piston (its arms), the
  photomask (its work), the screen (what it shows changes; keys already come as data), the battery
  of a case (its charge). Each as its own events (the device-reporting rule in `AGENTS.md`).
- **Motion sensor, tier 2 maybe** (user, 2026-10-21): finer motion (fluids, drops) and filters (by
  mob); tier 1 has no settings.
- **The Sound Card, phase 2 (user, 2026-10-24): MIDI-style notes,** an instrument, a note, its loudness, on and off:
  the instruments Minecraft's note block sounds, pitched to the note, played on each player's game
  (little data); General MIDI on a card with sampled instruments is the real model; an mcos player
  for `.mid` files. Open in phase 1: a volume of its own; the bandwidth of many cards near
  many players (44.1 kHz 16-bit stereo is 176 KB/s a card), maybe a setting.
- **Bluetooth, open:** range limits; whether links show differently in the bus order.
- **Disks, open:** disks in expansion hubs; labels set by programs.
- **The Disk Station, open:** whether other programs ever install from it (copied in, not
  formatted) or everything ships in mcos; tiers; its disks' change reports aren't saved with it (a
  case's are).
  Not wanted for now (user): ejecting disks (out of a face, into a container) and choosing an eject
  face; its slot drawn on the block didn't read as a disk's way out.
- **What a block holds, shown on it** (user): a miniature of each thing in a block's slots, drawn
  on the block in the world (the Disk Station's disks), on every block that holds objects, maybe
  even the case (its parts).
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

- **A handheld scanner** (user idea, 2026-10-26, undecided): an item a player carries, with no CPU,
  a device of a computer's bus out in the field (as a Bluetooth barcode scanner is a host's input
  device): what the player scans with it (a block, a creature, what a container holds) goes back to
  the computer as the device's event. Open: how it reaches the bus (a Bluetooth-style link, a
  range), what it scans and reports (where, relative to what: the computer knows no world
  position), its power (a battery in it), whether the computer can answer on it (a line on its
  display, a beep).

## Robots: the bot golem, then drones

- **Walking, decided and built (2026-10-25):** a pose (odometry from an origin a dimension), whole-
  block walks ending exactly, straight without pathfinding (the default), the game's pathfinder as
  a mode, walkTo/flyTo from the pose. Still to consider: whether the field card's moves should land
  on block middles too (they're exact offsets now), and a GPS card for world positions.
- **The bot golem** (user design, 2026-10-24), next to build: an entity a block in size, an iron
  golem's look, walking as fast as a player. Its GUI is a case's (CPU, RAM, EEPROM, disk, battery,
  cards: none soldered) in the golem's own art, and it holds only a tier 1 screen. One slot holds a
  soldered **robotics device**, `/dev/robotics0`, the only way it moves: it never acts on its own.
  It walks to targets relative to itself with the game's pathfinder (turn, stop; its interrupt says
  arrived or blocked), and much more later. It knows nothing of where it is: a GPS card later.
  Creative only until the assembly; a broken one may spawn in the world's ruins.
- **The charging pad:** a golem on it charges (little green glowing particles around it while it
  does); power as other devices': its own battery slot, a buffer, and power over a cable from a bus
  when there is one. Swapping the golem's battery works too.
- **The Advanced Robotics Assembly:** builds robots; a 3x3 crafting area and output slots in its
  GUI; animated (little arms moving little parts, lasers). Its recipes need parts not designed yet.
- **Drones are golems without legs** (the user's idea): the same machine and robotics device,
  flying instead of walking; maybe more of it soldered (hard-printed).

### The golem's hands, crafting, fields and flight (user design, 2026-10-24), building in stages

1. **Robotics, no cards** (built): a scan of the six spaces round it at once (air, block, fluid, entity,
   detailed: a block's state, a fluid's level, a container's size), relative to itself; one item in
   hand (placed in its GUI): place blocks, use tools (an axe, a pickaxe: breaking takes a player's
   time), interact (buttons, doors, levers); a container in front: put the hand's items in, take
   them out. On its legs it presses pressure plates.
2. **The crafting card** (built), in any computer: storage (a 3x3 crafting grid, the rest storage), its
   own tab on the right of the computer's GUI (a tab button each card), where a player crafts by
   hand; its API needs robotics (a case's calls fail, "no robotics available": its Shift details
   say so), and crafting programmatically takes time and
   cards craft at once (more cards, more throughput); the output goes to storage, no room: the craft
   fails; it picks items up from the world and drops them, moves them to and from the hand and
   containers. With one in, a tiny chest on its back, partly sunk in its frame.
3. **The energy field card** (built) (any computer): suspends a creature or an item in a translucent blue
   sphere; held, it falls in place and can't act, move, attack or react (a creeper doesn't blow);
   the field moves, and what it holds with it (lift a player to a higher floor). Never blocks (one
   day, maybe buttons).
4. **Legs** (built): robotics' `detachLegs`: without a field the golem falls over and powers off; inside its
   own field it flies, the field moved by its program, much faster than it walks, its legs left
   behind; `attachLegs` within a small distance of them: it goes to them and lands. In flight it
   still runs, crafts and acts, but uses no tools (buttons and doors yes) and presses no pressure
   plates. Power out or off in flight: the torso falls, an object in the world (hit: picked up;
   right-click: its GUI); torso over legs in a 2x2 grid makes the golem again.
5. **Mobs** (built): spiders (and skeletons riding them) fear golems; a flying golem scares chickens and
   cows (sheep don't care).

## Getting started in the world

- **The player's introduction** is the next discussion: advancements, archaeology loot, how the
  first photomask is found.
- Programs from ruins and traders (disks), and books with programs in them.
- The photomask gets a device API (programs start and watch its work).
- Saved for later (user): shulker shells for a robot with deep internal storage.

## The Lua CPU and the server

- **A boot test timed out once under testAll's load** (2026-10-26, NeoForge's
  DiskGameTest.aNewEepromBootsMcosFromATier1Disk, "waiting for a key on tick 402"): it passed on
  Fabric in the same run and twice on NeoForge alone. A tick deadline on mcos's boot is a timing
  bug under load: make the test wait for the state it needs, not a tick count.
- **A save test failed once under testAll's load** (2026-10-25, LuaRunnerTest.aSaveCarriesOutNoCalls...,
  "the save made no call": the counter was 1): it passes alone and with its class, every time. The
  test waits for queuedCalls() > 0, then persists; nothing in it serves the server queue, so no call
  should be made. Find what made it (a call answered off the server thread? queuedCalls counting a
  call other than the hit?), make that state on purpose in a test, and fix it: a save that moves data
  between a program and its devices would be a real bug.
- **A wait on every source reports the console and its terminal raised with nothing to read**
  (seen 2026-10-24, writing the own-OS shell: `board.waitForInterrupt(0.1)` with no sources, right
  after `board.invoke("console", "read")` took a line, answers `{"console", "id:screen"}`, and a read
  of either then gives nil; again on the next wait). Interrupts are level-triggered, so it looks
  like a stale report: the published status, or the host's `take` filling the inbox for the
  terminal behind the console. Find it with a unit test on `LuaRunner` (a console over one
  terminal; read a line; wait on all), then fix. The tutorial's `wait` leaves terminals out, which
  it should anyway.
- **RAM sizes:** settle the tiers' sizes (and their ring sizes) once the VM and mcos are finished,
  with OC's values as a reference.
- **mcos's memory** (measured 2026-10-20; tier 1 is 1 MiB now, the user's call): it reaches its prompt
  in 492 KiB, and holds ~472 KiB there after full collections, mostly compiled code (~341 KB: sh 86,
  the kernel 70, its modules 88, io+ld 46, readline 27, fs 24). Levers left: stripping debug info
  (below); LungoLua's compiled size (about 1.4 times the C++ build's: ask the lua thread); the
  standard libraries shared read-only between processes (each copies string, table, math, utf8,
  coroutine and its own io/os closures); sh's parser restructured (one splitter for ; && || |, the
  lexer finding $( )'s end: ~10 KB, from studying OpenOS's); sh and readline needing only a few of
  fs.lua's functions. Measure with a throwaway probe that varies one stick's size freely (4 KiB
  steps), not by tier, and a temporary trace of each chunk the firmware compiles (its size, memory
  after) when the order matters (removed once measured: see git, 0be0dc8).
- **Stripping debug info from system code** (user, 2026-10-20): debug info is about 30% of compiled
  code (bytecode measured). Lua has no switch for it at compile time; only `string.dump(f, true)`
  strips, and loading that back takes bytecode mode, which the sandbox refuses (crafted bytecode
  escapes VMs). Plan: the firmware strips on request, from text only (compile, dump stripped, drop
  the full function and collect, load the VM's own bytecode back), so text stays the only way in.
  The kernel strips the system set (the boot path to the shell, or `/lib` and `/bin`, as a distro
  ships stripped binaries), user scripts keep their line numbers, and a boot option (`debug=1`)
  turns it off for chasing a system bug. Watch the compile's peak (drop the full function before
  reloading). Alternative to ask the lua thread about: a compile flag that never records it.
- **Server time across many computers:** each computer gets its own server time a tick, with no
  overall management; later, round robin which machines get time each tick, if many busy
  computers starve the tick.
- **Settings:** tunables stay named constants until settings are revisited.

## Other mods

- **Ender IO's conduits** (NeoForge): a conduit carrying our bus cable inside its bundles, as its own
  AE2 module carries ME cable (an ordinary NeoForge registry, `EnderIORegistries.CONDUIT_TYPE`; the
  bundle exposes a capability our cable walk would treat as cable), optional, loaded only when Ender
  IO is. Waits on Ender IO reaching 26.x (its main line is 1.21.1; a 26.2 port is in progress).
- **A NeoForge release-jar check:** the tests run NeoForge from its dev folders only; nothing starts
  the shipped NeoForge jar, and nothing starts a NeoForge client at all.

## mcos

- **A `/sys` tree:** a device's attributes as files, one each (sysfs's shape: `cat
  /sys/class/net/wlan0/strength`), beside the `/dev` file's methods; postponed (user, 2026-10-24).
  The REPL as a workbench for trying devices without writing a script.
- **A tty per terminal:** the boot option (`console=`) names one device; a tty, interrupt handler
  and shell per terminal isn't started yet.
- **Signal handlers run only on the way back from a syscall** (a stopped process can't be made to
  call one), so a busy process sees its signal at its next syscall; Linux runs them on any return to
  user mode.
- **Job control:** `jobs`, `fg`, `bg`, ^Z, SIGTTIN. Background jobs share the shell's group and
  ignore SIGINT, as sh without job control.
- **The shell's language:** `if`, `for`, `while`, `case`, functions, here-documents (user: maybe
  later).
- **rsh, rcp, what's left:** `rcp -p` (keeping times); a remote-to-remote `rcp`; readline doesn't
  redraw a line on SIGWINCH.
- **Networking, later:** a router between wired and wireless networks, raw frames for programs
  (Linux's packet sockets, for a `tcpdump`), a WLAN card's range as a setting (OC's strength, its
  power cost).
- `/proc`; a filesystem on raw disks; permissions and users; `os.date` (no real-time clock: the time
  is seconds since boot); a page cache.
- **Later, from the user's review of OpenOS (2026-10-19):**
  - `type -a`;
  - `install`, once loot software is worked out;
  - `sides`, with the redstone card's API;
  - profile, `.shrc` and motd;
  - maybe `label`, `primary`, `resolution`, `address`.
  - The internet card's commands (`wget`, `pastebin`) will be its device's API.
- **Not wanted** (user, 2026-10-19): `which` (it's `command -v`), `lshw`, `dmesg`,
  `useradd`/`userdel`, OpenOS's `transforms` and `event`/`keyboard` libraries.
- **The internet card, open:** raw TCP connections to the internet (OC's `connect`), if wanted;
  rate limits per card or per player, if servers need them.
- **Screens' look, ideas only** (user, 2026-10-24: not wanted now; unsure about scanlines): faint
  scanlines on the CRT tiers, a vignette (the glass darker toward its edges, a curved tube), a
  phosphor glow around text (costly on many block faces). The tiers' glass and whites are done.
