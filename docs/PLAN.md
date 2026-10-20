# Plan

The user's roadmap and the design ideas discussed so far. Items move from here into
`AGENTS.md` ("Current state") as they're built. Design approach: abstract how real PCs work,
skip steps freely, never invent false steps (see `AGENTS.md` section 1).

## Done

- **All-in-one computer:** screen + keyboard on the front, power button on the back, built-in
  display, keyboard and disk on its bus, soldered memory.
- **Motherboard case view** (the user's chosen design: mockup "E+ round 3"):
  - slots for CPU, ROM, UPS battery, 4 RAM, 4 SATA and 4 expansion;
  - live indicators;
  - a charge meter;
  - a bus order panel with etched numbers and working ↑/↓ reordering.
- **Case view** in the all-in-one, opened from the GUI's motherboard toggle or from the
  window into the case on the block's back, with **CPU** and **EEPROM** slots. The CPU is optional:
  without one the power switch still works, but the button is faded and unlit, and nothing
  runs. The EEPROM holds the stand-in "hello world" program; without one the CPU shows the
  soft gray **ABRT** screen.
- **`/modularcomputers giveall`:** a cheat command that gives one of every mod item.
- **Screen block and cable** (2026-10-11): single-block screens joined to computers by
  cables that visibly join their neighbors. Computers discover wired screens and list them
  in the bus order panel ("cable, Nm"), with the **light bulb** (outline in the world) and
  the **connect/disconnect plug**. Every connected screen shows hello world (hard-coded).
- **Machine model:**
  - The CPU loads its program from the fixed EEPROM slot.
  - A physical bus of self-describing devices, and a virtual bus with the always-present
    console splitter.
  - Screens with per-cell colors.

## Next: parts and slots

- **Make the placeholder parts do something:**
  - RAM sizes the machine's memory (and none is a boot error).
  - Disks hold data.
  - WLAN cards network.
  - Batteries are built (three tiers; see AGENTS.md). Still open: mains power, with the
    UPS as a real backup that takes over when it drops, and the battery's bus API.
  - Battery ideas not decided: refusing overflow when charging (today extra material is
    wasted), a range or rate for tier 2's self-charging, and whether the power light should
    stay lit with no battery.
- **CPU status dots:** the 4 lit dots left of the CPU socket are a good place for more
  status later. Today they just light when a CPU is in.
- **Narrow windows:** the bus order panel is hidden below about 382 GUI units of width.
  Consider a toggle or a smaller layout.
- **Tiers and power:** components with tiers. Real power supply and distribution.
  - The power button's look follows available power: faded red/green when unpowered, lit
    when powered.
  - Only a lit green button actually runs the machine. Today "faded" means "no CPU".
- **Programmable EEPROMs** once the Lua runtime exists. It's the user's own project and isn't
  ready yet.
  - The EEPROM item gets contents.
  - The CPU runs Lua from it instead of the Java stand-in.

## Mock runtime: programs calling devices (built 2026-10-11; see AGENTS.md)

The Lua runtime is the user's own project and will take time. A mock layer pretends to be it,
so the machine can be built and tested as if Lua code were calling devices. Modeled on how OC
did it (`machine.lua` kernel, `component.invoke`, `@Callback`, signals), but redesigned.

Decided by the user:
- **Names:** real hardware terms, `bus` and `device`. Never "component" in code or docs.
- **Calls are immediate by default** (a two-way door): the device method runs right away.
- **On the server tick** for now. Running machines off-thread, asynchronously, is a
  documented follow-up (TODO), not built.
- **Addressing:** the motherboard API looks devices up by stable id and by bus order.
- **Saving:** a running Lua computer is saved with Styx (its firmware's ring with it), and its
  devices save their data (built 2026-10-13); a Java mock program still isn't.
- **Device methods never kill the machine.** Every device failure is soft, like a status bit
  the driver reads and decides about. Only the motherboard's own supervision ends a run: the
  CPU and power (it should check them on a schedule too, not only on events).
- **External devices are hot-plugged by design.** A device that isn't online isn't on the bus;
  calls to it fail softly. No deferring calls until a device "comes online" (OC's GUID race
  and its timeout hacks).
- **Interrupts per device, not a machine-wide queue:** a device raises "I have something";
  its data stays in the device until the program reads it, which clears it.
- **20 calls a tick** per machine, then it's paused until the next tick (the host's choice,
  not the machine's).

Still to do:
- Running machines off-thread, asynchronously, and how they share the server tick.
- **The console's input modes:** `icrnl` (Enter's CR becomes LF), `echo`, `icanon` (line
  editing). Undecided; today a program echoes for itself.
- **A drawn cursor** on terminals (hidden by `ESC[?25l`), and the bell.
- Device methods for the rest: disks, the
  UPS battery (charge, time left), cards, the EEPROM.
- Opaque handles (Lua userdata) in the value model.

## Tiers (user design, 2026-10-11; not built)

Tiers are trade-offs the Lua engine can honour, not every possible feature. Three tiers per part.

- **CPU:** bus capacity comes **entirely** from the CPU (its "address width"); the case's
  installed devices and external ones share it, so a tier 3 CPU in a bare tier 1 case leaves
  nearly the whole bus for external devices. A smaller CPU shrinks the
  bus and the existing overflow handling covers what no longer fits. Higher tiers draw more
  running and less waiting (halted is 0 for all). Starting values (user: "a good starting
  point"), per tick. **The case's own devices count against the bus too** (only the ones
  installed; built: tier 1 4, tier 2 16, tier 3 64): a tier 1's 4 are the essentials (EEPROM, a
  disk, a card, one more card or external device; batteries aren't on the bus), so a tier 1 CPU can't use a full tier 3 case.

  | Tier | Running | Waiting | Bus |
  |---|---|---|---|
  | 1 | 100 | 50 | 4 |
  | 2 | 150 | 20 | 16 |
  | 3 | 200 | 5 | 64 |

- **Cases (case == board, for our purposes):** higher tiers have more built-in, more slots and
  a bigger power buffer. **Every tier has the same board layout**, like a fab printing one board
  and leaving features out on cheaper ones: lower tiers show the same art with the missing
  slots and parts greyed out. Nothing moves between tiers; a slot a tier 1 has sits exactly
  where it is on tiers 2 and 3, and so does the charge meter.
- **Storage:** more space, and less power, at higher tiers.
- **Memory:** more space. (Idea, not decided: no RAM, no boot, with a POST-style beep code.)
- **Terminals:** all are the same VT decoder (same driver code), with a capability profile:
  tier 1 text only (no foreground or background colours), tier 2 text with 16 colours, tier 3
  pixel graphics as well as text. How pixels are drawn is open: sixel (the VT340's pixel
  graphics by escape sequence, finer than the text grid), ReGIS (vectors), the kitty graphics
  protocol, or block/braille characters; the user hasn't chosen. Ideas: tier 1 keeps reverse
  video as the VT100 did; programs learn a terminal's tier from its Device Attributes reply
  (`ESC[c`), as on real terminals.
- **The all-in-one is a dev kit / placeholder** for mapping out the full layout; it's like a
  tier 3. It may go away, or become a creative "everything built in" block. **To revisit:** a
  case + terminal block (maybe tier 2 and 3 screens you can put a case inside), and whether
  any case has a built-in terminal.
- **Panel bay idea (discussed, undecided):** real laptops and all-in-ones split it: the chassis
  has a panel bay (mounting and the internal display connector), the panel decides quality. So
  the case tier would decide whether there's a bay, and the screen put in it (a Screen item:
  "screen" means a terminal, display and keyboard) decides the terminal tier. The screen keeps
  its id built in or cabled. The case model changes with what's in the bay (a block state,
  e.g. `panel = none | tier1 | tier2 | tier3`, like the power button's lit/faded models). It
  takes the terminal's place (1) on the bus. Room on the board: an 18x18 slot fits between the
  RAM sticks and the expansion row, left of SATA (board 110, 80); the two status LEDs and three
  bus traces under it would move or reroute, nothing else.

## Separate blocks: cases, screens, cables

The all-in-one stays the test bed. Later there are separate blocks:

- **Case blocks:** right-clicking a case opens the **case GUI**: slots plus the power button.
  Cases provide the power toggle.
- **Screen blocks:** right-clicking a screen opens the screen view ("modal mode"). Its power
  button shows only when the screen is connected to a case.
- **The all-in-one is both.** Right-click on it opens the screen (the screen wins). The view
  toggle button switches between the case view and the screen view. Built.
- **No separate keyboards** (user, 2026-10-11): a screen is a terminal, screen and keyboard
  in one device. Cables and screens are built.
- **Multiblock screens that auto-grow (user idea, 2026-10-11, undecided):** neighboring screen
  blocks merging into one bigger display. For now screens are single blocks.

### Cabled devices and the bus (user decisions, 2026-10-10; built 2026-10-11)

- **One device, many computers.** A cabled device (screen, ...) may be connected to
  several computers at once.
- **Connect/disconnect toggle.** Each cabled device's row in the bus order panel gets a small
  toggle.
  - It shows plugged in (connected) or unplugged (disconnected), and clicking it switches.
  - New cabled devices start connected.
  - Built-in devices and expansion cards can't be disconnected; only cabled ones can.
  - A disconnected device leaves a **gap** in the bus walk.
- **Open question: how gaps look to firmware.** Today the bus walk is
  `machine.board().bus().devices()`, an ordered list with no gaps (disconnected and absent
  devices are simply left out). Options:
  - **Position and device pairs, with numbers skipping over gaps.** This is the real-world
    model: PCI addresses skip empty slots, and software just enumerates what's present.
  - **A gapless list.** Programs never see the gap. Simplest for minimal EEPROM code.
  - Either way, a disconnected device should keep its position, so reconnecting puts it
    back where it was.
  - Decide this before the Lua runtime defines the bus walk API.

### Where a device's bus position is remembered (default built; still open)

**Built default (2026-10-11):** first-seen order. A cabled device that goes away (cable cut,
chunk unloaded, broken) keeps its place, as does its disconnected state. At most 32 absent
devices are remembered, earliest-listed forgotten first (`BusOrder.MAX_REMEMBERED`). The
user hadn't decided, so revisit with them. The notes below are the original discussion.

The bus order belongs to the **case**, not the device: the same cabled screen can be 2nd on
one computer and 3rd on another. So the case must remember devices by something unique and
stable (like a GUID).

- **Leaning towards: the order is set the first time a device is seen.** The case caches each
  device's position when it first discovers it.
- **Alternatives discussed:**
  - a "reset bus memory" button, disabled while everything is in default order;
  - a "save current order" button, for players who like the default order they see.
- **The problem: lost devices.**
  - A device that's "lost" (unplugged, broken, or **in a chunk that unloaded**) may come back
    at a different position the next time it's discovered, unless the case remembers it while
    it's gone.
  - Chunk unloads make "lost" ambiguous: the device still exists, the case just can't see it.
  - How long to remember a lost device, and whether an unloaded chunk counts as lost, is
    unsolved.
- **Keys:** parts by slot (`sata:1`, `exp:0`; a removed part's position drops), external
  devices by their own id (`id:<uuid>`), however they're connected.

### Ordering the devices a case sees (user idea, not decided)

In real PCs, bus order comes from physical slots. For cabled devices, the user is thinking
of letting the player order them:

- The case GUI shows a **list of the physical components connected by cable**.
- **Move up / move down** buttons reorder the list. Order matters, e.g. the console uses the
  first display in bus order.
- A **light bulb** button next to each entry **highlights that device in the world**, so the
  player can tell which screen is which.

### Sharing a screen between computers (not designed yet)

- **Virtual multicast text buffer (user, 2026-10-11):** today every connected screen gets the
  console (hard-coded hello world). The plan is a virtual device from the motherboard
  (virtual bus) that the EEPROM uses to choose where the console goes.
- **Several computers on one screen:** settled (user, 2026-10-13): a device is on one bus at a
  time (`BusHolds`, see AGENTS.md); the first board to have it holds it. Screens have no power
  button: only the case does (user).

## The Lua CPU on its own thread, and device data in rings (user design, 2026-10-13; built 2026-10-13, see AGENTS.md)

Built in this order:

1. **A thread per running computer** (a platform thread, low priority; no pool until there's a
   reason). The host loop runs `machine.lua` freely, not once a tick. The firmware yields
   `"slice"` (its budget froze user_co: a safe point, honour pinned requests, go on at once),
   `"call", ...` (a device call: carried out on the server thread, `server.execute`), `"wait", ...`
   (the thread sleeps until an interrupt, the time, or a pinned request) and `"halt"`. **Every
   device call is a syscall**: device code only ever runs on the server thread, and no Java frame
   sits in user_co's stack. The 20-calls-a-tick limit goes; instead each machine gets a **budget of
   server-thread time a tick** (past it, its work waits for the next tick). Power never limits
   calls (a bigger supply would make that meaningless).
2. **Pinned requests: freeze, then persist.** The server thread pins "persist" (world save) or
   "persist and stop" (chunk unload, power-off) and blocks until the host thread honours it at its
   next safe point (after a slice, or at once while waiting), never in the middle of a syscall.
   While it blocks, it carries out that machine's queued device calls (no deadlock). Styx persists
   from the main thread only, so this is the host's (`LuaRunner`). Restore on load
   (`luaL_restorestate`, the same libraries and the host's functions as permanents).
3. **Device data in rings** (like DMA rings: the program reads memory, not the device):
   - Four kinds of device traffic: events in (keys, redstone, sensors, messages) pushed; writes out
     (terminal text) batched; rarely-changing state (the bus list, types, methods, sizes) pushed as
     snapshots when it changes; synchronous request/response calls stay syscalls (the slow path).
   - **Flow control by handshake, never silent drops**: a device keeps its data until the
     firmware's ring has room for it. When a device's own buffer is full, what it does is the
     device's choice (a keyboard could beep, as a PC's does).
   - **Only the firmware caches** (user, 2026-10-13, revised the same day: the board's ring in the
     RAM sticks was dropped): machine.lua keeps a queue per device, in the VM, filled straight from
     the devices as a wait is answered, each up to its own room. **Its size comes from the RAM**:
     tier 1 adds none (every device read is then a call, a slow path on purpose), tier 2 adds 2
     packets per device, tier 3 adds 4, summed over the sticks.
   - Batched writes report errors late (as `fwrite`/`fsync`, sockets, ttys do).
   - Interrupts stay level-triggered: pending means the firmware's ring holds data for the source.

## Disk files without open file descriptors (user design, 2026-10-13; built 2026-10-13, see AGENTS.md)

An open handle never keeps a host file open while user code runs. Open reads the file's size and
a window of it into the handle's userdata (its path kept), and closes the host file; reads past the
window fetch the next chunk (a syscall): a file's size is never limited by RAM. Writes go to a
shadow file next to the original; **close is atomic**: one syscall writes what's pending and
renames the shadow over the original (`rename(2)`), and only when the host confirms does the
program drop its buffer (a failure leaves the buffer, to retry: intent is never lost). Writes not
closed are lost at power-off, as in real life. The handle's data is in the VM, so Styx persists it.

## Placeholder blocks and cards (built as placeholders, 2026-10-11; behavior to design)

- **Cards** (all do nothing yet): WLAN, Bluetooth, Redstone, Audio, Motion Sensor, Audio
  Sensor, Weather Sensor. A card works where it's installed: in a computer, or in an
  expansion hub, where a redstone card emits and detects redstone at the hub.
- **UPS battery:** on the bus. The user says it'll have an API (charge, tier, time left).
- **Robotic piston:** one cable port; it will push blocks (pistons) on its other faces.
  Instead of a cable it can take a Bluetooth card in its one slot.
- **Paint gun (user idea, 2026-10-11; placeholder block built):** a block that "shoots" paint
  colors at nearby wool blocks, out of its nozzle face.

## Pairing: one owner per device (built 2026-10-11)

The user's answer to "a device can be on only one bus": deciding an owner at runtime (first
wins, last wins) is a race that chunk loading reshuffles, especially when the owner's chunk
isn't loaded. So ownership is explicit and saved. A device's radio (the Bluetooth card in its
radio slot), when linked, pairs it with that board, a computer or a hub; hubs daisy-chain.
Unpaired devices stay shared, and racy (undefined behavior, accepted). Details are in
AGENTS.md.

## Bluetooth (built 2026-10-11)

Linking, resetting, daisy-chaining, distances and the held-card highlight are built (see
AGENTS.md). Open: range limits, whether links should show in the bus order differently, and
what a Bluetooth card does on the bus itself (it's a placeholder `NETWORK` device).

## Disks (built 2026-10-12; see AGENTS.md)

- Built: hfs and raw modes, file handles, data in the world save by id, the OS's VFS and shell in
  `Mcos`.
- Open: disks in expansion hubs; hfs labels set by programs; a Lua runtime's `__gc` closing
  handles; whether a mount table belongs on the board (a kernel's job) once there's a real OS.

## Recipes (built 2026-10-12; see AGENTS.md)

- Draft page: a private draft page (private to the user).
- Theme (user): vanilla ores, minerals and drops, few custom components; real computer materials
  (quartz for silicon until the introduction's chip is designed, amethyst for the clock crystal,
  copper boards, gold pins, iron frames; tiers iron, gold, diamond). No bricks.
- Every radio (WLAN and Bluetooth cards) takes an ender pearl and an emerald (beryl: beryllia under
  a transmitter's power amplifier); Bluetooth adds lapis. Cable is dried kelp around copper (8).
  The case's note block is its speaker. Screens: glass, glowstone for tiers 2 and 3. Expansion
  hub: four comparators around quartz, copper and gold. Paint gun: red, green, blue dye over a
  dispenser.
- **Every tier upgrade takes the tier below as its center** (user, 2026-10-12): CPUs, RAM, disks,
  screens, cases, batteries. The audio card takes a jukebox; a terminal's keyboard is a block of
  chiseled stone bricks; tier 3 screens take prismarine crystals.
- Cases upgrade from the tier below (user: old stuff isn't junk) and need something earned:
  tier 2 breeze rods (trial chambers; the case's fans) and gold; tier 3 echo shards (ancient
  cities; memory, as a recovery compass remembers), a heart of the sea (power, as it powers a
  conduit), diamonds, a block of redstone. The user found Wither and End costs far too much.
- Saved for later (user): shulker shells for a robot with deep internal storage.
- Later (user, 2026-10-12): EEPROMs hold a small bootloader and the OS (mcos, Java and Lua
  versions) is installed on a disk; programs come from ruins and traders (disks), and books with
  programs in them; the photomask gets a device API (programs start and watch its work).
- The player's introduction to the mod (silicon from quartz in a blast furnace, chips from the
  stonecutter, advancements, archaeology loot) is the next discussion.

## Machine ideas discussed (not built)

- **Hot-plug takeover:** built. The console keeps its own copy of what it shows and redraws
  it onto a screen that joins a running machine.
- **Console log:** a ring buffer in the console. Each console keeps its own read position,
  and a `messages dropped` line replaces silent loss (like Linux printk and `/dev/kmsg`).
- **Stored console preference:** which display(s) the console uses, kept in an NVRAM-like
  store (like UEFI's `ConOut` variable). Today it's every display at the smallest common
  size.
- **Beep codes:** audible boot errors for headless machines.
- **EEPROM as a bus device:** also listed on the physical bus, so programs can read or
  reflash it.
- **Monospace bitmap font:** today text uses Minecraft's proportional font, centered per cell.

## Open for later (user, 2026-10-13)

- **RAM sizes:** the tiers' sizes (and their per-device ring sizes) can't be judged yet: settle
  them once the VM and mcos are finished, with OC's values as a reference.
- **Server time across many computers:** each computer gets its own server time a tick for now
  (`SERVER_NANOS_PER_TICK`), with no overall management; later, round robin which machines get
  server time each tick, if many busy computers starve the tick.
- **Settings:** tunable values stay named, hard-coded constants until settings are revisited.
- **A bell:** terminals ignore BEL, and a full key queue drops keys silently; a real keyboard beeps.

## mcos (user design, 2026-10-13; the Lua OS on a tier 1 disk)

- **No lazy loading.** OpenOS split libraries into halves loaded on first use (`package.delay`,
  `full_*.lua`) to fit OC's RAM limits, which were as real as ours. It wasn't worth it: slow loads,
  much complexity, and loading in pieces costs more memory than loading whole. mcos loads what it
  needs, whole.
- **No legacy compatibility:** OpenOS grew complex supporting old programs; mcos isn't tied to them.
- **The terminal decodes VT100:** mcos writes bytes and escape sequences; none of OpenOS's VT
  parsing, software cursor blink, GPU windows or viewports.
- **Built (2026-10-15): processes, syscalls, a VFS and a shell** (AGENTS.md, "mcos in Lua"). The
  user asked for a filesystem and io built autonomously, to review; decisions taken without them,
  each open to change:
  - **`/bin` and `PATH` are back** (the user had said no `/bin` for the echo-only v1: it needs PATH).
    With a filesystem the shell runs programs, so it looks commands up in `PATH` (`/bin`, set by the
    kernel), as sh does; a path with a `/` runs as is. Programs have no `.lua` (as Linux's); every
    process starts in `/lib/ld.lua` (the loader, as ld.so and crt1), which calls the program: all of
    them are Lua, native to the CPU; `/bin/lua` is just Lua's standalone interpreter (2026-10-19).
  - **Disks mount at `/mnt/<kind>/<name>`** (`sata:1` at `/mnt/sata/1`, `id:x` at `/mnt/id/x`), as the
    Java mcos does; a mount point needs no directory on the disk beneath it (the VFS shows one).
    Linux needs the directory; udisks makes it.
  - **The syscall table is Lua-shaped**, not C's: fopen modes for `open`, errors as `nil, message,
    errno name`, `spawn` (posix_spawn) instead of fork and exec (a coroutine can't be forked), wait
    statuses as luaposix gives them. User space on top: Lua's own `io`, `os`, `require`, plus `fs`
    (paths, listing, copy, glob), not OpenOS's `filesystem`, `term` or `shell` libraries.
- **The line discipline is mcos's** (user, 2026-10-13, as in Linux, where it's kernel code): a
  terminal only sends bytes (`\3` for Ctrl+C) and raises its interrupt. Each tty (one per input
  device mcos uses) owns its input buffer, its termios flags and its foreground program, whatever
  process is running; processes inherit their tty, so a program never knows which one it is.
  readline clears `icanon` and `echo` and keeps `isig`, so Ctrl+C is still SIGINT while it edits.
- **Console mode is a boot option** (user, 2026-10-13), like Linux's `console=`: by default one tty
  on the board's `console` (all terminals share one shell); or a tty, interrupt handler and shell
  per terminal (`ttyS0`, `ttyS1`...), never reading `console` then. The bootloader hands init a
  command line (e.g. from a file on the boot disk, like GRUB's config).
- **Preemption is built** (2026-10-15): new input and a spent slice stop the running process and
  return to the kernel (LungoLua 1.1's thaw with stops), and syscalls trap the same way
  (`board.trap()`). Ctrl+C ends a program stuck in a loop.
- **mcos no longer fits one tier 1 stick** (2026-10-15; the user: optimize later, maybe raise tier 1's
  capacity; for now it needs a tier 3 stick, 1 MiB, see AGENTS.md): with processes, a
  VFS and a real shell it takes about 400 KiB above the firmware (kernel 194, shell 120, a small
  process 16; compiled code is most of it, shared between processes already). A first computer
  with one 256 KiB stick won't boot it. Options: a smaller boot set (the kernel's modules and a
  smaller shell), bigger RAM tiers, or asking LungoLua what a compiled prototype costs (its count
  is about 1.4 times the C++ build's for the same file).
- **To come in mcos:**
  - **Signal handlers run only on the way back from a syscall** (a stopped process can't be made to
    call one): a busy process with a handler sees its signal at its next syscall. Linux runs them on
    any return to user mode.
  - **Job control** (`setpgid`, `tcsetpgrp`, `fg`, `bg`, Ctrl+Z, SIGTTIN): background jobs share
    the shell's group and ignore SIGINT, as sh without job control.
  - **`/proc`**: `ps` and `mount` use syscalls (`processes`, `mounts`) instead.
  - **Raw disks** have no filesystem driver (an mcos filesystem on sectors); **devices as files**
    (`/dev/sata1`, terminals by address) aren't in devfs yet; **no permissions or users**.
  - **os.date** (no real-time clock: the time
    is seconds since boot), **a page cache** (every program start reads `/lib/ld.lua` and `/lib/io.lua`
    off the disk), **the bus's hot-plug is noticed only when the kernel idles**.
  - A console tty per terminal (the `console=` option names one device; several aren't started yet).
  - **Devices for user programs** (user, 2026-10-19: "skip anything that further develops /dev", they
    have concerns to discuss first): programs can't reach devices yet (`board` is the kernel's); the
    plan so far is `/dev` files and `ioctl`. Waiting on it: the EEPROM edited through a `/dev` file
    (the user: not a `flash` command), and the REPL as a device workbench.
  - **Later, by the user's review of OpenOS (2026-10-19):** `type -a` (the user, "when we support
    alias": `sh` has `alias` and `type` already), `install` (once loot software is worked out), `rc`
    (no start-up services yet), `sides` (with the redstone card's API), the shell's language (`if`,
    `for`, `while`, `case`, functions), profile/`.shrc`/motd; maybe `label`, `primary`, `resolution`,
    `address`. Not wanted: `which` (`command -v`), `lshw`, `dmesg`, `useradd`/`userdel`, OpenOS's
    `transforms`, `event`/`keyboard`; the internet card's commands will be its device's API.
- Answered for now (2026-10-15, for the user to review): the shell runs `/bin` programs (above).
