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
- **Tier 3 terminals draw pixels:** sixel is the plan (a VT340's pixel graphics by escape sequence);
  the terminal answers `;4` in its Device Attributes once it's built, so programs never send pixels
  to a terminal that would drop them.
- **Multiblock screens that grow** (user idea, undecided): neighboring screens merging into one
  bigger display.
- **The all-in-one** is a dev kit: it may go away, or become a creative "everything built in" block.
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
- **Cards still placeholders:** Audio, Audio Sensor. The paint gun (shoots paint at nearby
  wool, out of its nozzle face) does nothing yet.
- **Bluetooth, open:** range limits; whether links show differently in the bus order; what a
  Bluetooth card does on the bus itself.
- **Disks, open:** disks in expansion hubs; labels set by programs.
- **The Disk Station** (user, decided in outline): a block holding disks, to format, wipe, clone
  and install them without crafting a disk alone.
  - **Slots:** the disk it works on; a second, optional slot for a source disk, read only (for
    cloning; a write blocker's role, as forensic docks have), drawn so it reads as optional and
    read-only (a faint outline when empty, a lock and SOURCE in silkscreen), with the clone
    button beside it, an arrow from source to target. A battery slot; a Bluetooth card slot.
  - **Power** from a cable or its battery. **On the bus:** it's a device itself (its methods are
    its actions) and puts its disks on the bus, so a computer mounts them; the source's read only.
  - **Actions** (GUI buttons on the left, in the mod's look; the same as methods): format a
    standard filesystem (hfs); remove the filesystem (raw); clone the source onto the target
    (its mode and contents; refused when they don't fit); install mcos (wipe, then the mod's
    latest mcos); update mcos (the mod's files written over, everything else left; `/etc` too, not
    protected: an update is how a player fixes an install). Every action
    that clears files removes them from the host too.
  - **Eject** pushes a disk out of the station's eject face (into a container there, as a
    dropper does), the front by default. The API matches the GUI: `eject()` uses the configured
    face, and the face has its own methods (`getEjectFace`, `setEjectFace`); the GUI sets it in
    the gear-box tab's side picker (Thermal's, Mekanism's, Ender IO's way).
  - **Closing the GUI never ejects**: a disk stays in until ejected.
  - **Recipe:** chips across the top; emerald, a tier 1 disk, emerald across the middle; iron
    block, piston, iron block across the bottom. Its hint says the disk used loses its files. Its
    look: iron and emerald, with a chip board on top.
  - **Open:** whether other programs ever install from it (copied in, not formatted) or everything
    ships in mcos; tiers.
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

## Getting started in the world

- **The player's introduction** is the next discussion: advancements, archaeology loot, how the
  first photomask is found.
- Programs from ruins and traders (disks), and books with programs in them.
- The photomask gets a device API (programs start and watch its work).
- Saved for later (user): shulker shells for a robot with deep internal storage.

## The Lua CPU and the server

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

- **More devices for user programs:** only devices with `get`/`set` have `/dev` nodes; the rest
  (cards, terminals' own methods) can't be reached from a program yet. The REPL as a workbench for
  trying devices without writing a script waits on that. A `/sys` tree (one file per attribute) is
  postponed (user, 2026-10-20).
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
