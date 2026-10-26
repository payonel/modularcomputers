# openos: running OpenComputers programs on mcos

Context for the emulation work on the `openos` branch (the user's: branch for it, not master).

## What it is

`openos program [args]` (`mcos/bin/openos`) runs a program written for OpenComputers' OpenOS on
mcos, as wine runs a Windows program on Linux (the user's: "the wine is just openos as a command").
The user wrote OpenOS (MIT), so its libraries are ported, not reimplemented. Goal: a player keeps
their OpenComputers programs. Not a goal: OpenOS itself (its shell, `process`, its `/bin`); mcos is
the OS, and commands a program runs are mcos's.

## The user's rules for it

- **Fidelity:** what openos can't do is an error saying so (`openos: ... is not supported`), never
  something else done instead. Document every limitation.
- **Native pieces first:** what openos needed from mcos was built as real mcos features (threads,
  `/sys/class/power_supply`), and changes that serve every program went to master (below).
- Threads are mcos's (the kernel's tasks), honouring LungoLua, not OpenOS's; OpenOS's `thread`
  library isn't ported (its event-copying model differs; an adapter over mcos's threads is open).

## How it's built (`usr/lib/openos/`)

Like OpenComputers itself: a machine, then OpenOS on it, all in the program's one mcos process.

- **The machine, native (mcos's env, uses `sys`):** `machine.lua` (makes the program's environment
  and its own `require`, finds the components, boots, runs, restores the terminal); `computer.lua`
  (signals: a queue, and sources: fds one `sys.poll` waits on); `component.lua` (the registry, OC's
  list/proxy/invoke/doc); `terminal.lua` (gpu, screen, keyboard over `/dev/tty`); `unicode.lua`;
  `filesystem.lua` (OpenOS's API over mcos's files); `shell.lua` (parse/resolve as OpenOS's,
  execute is mcos's sh); `device/<mcos type>.lua` adapters: eeprom, redstone, network (modem),
  robotics (robot).
- **OpenOS, ported (`lib/`, run in the program's env):** event (+full_event), keyboard (+full key
  table), term, tty, vt100 (+full_vt), core/cursor (+full_cursor), text (+full_text, transforms
  inlined), buffer (+full_buffer), serialization, sides, colors, uuid (no bit32), robot (OC's),
  core/boot.lua (00_base, 02_os, 03_io, 04_component, 91_gpu, 92_keyboard, 93_term condensed).
  Lazy `package.delay` parts are merged in (mcos: no lazy loading). License:
  `usr/share/openos/LICENSE`.

## Decisions

- **The gpu draws escape sequences** on the process's terminal (`/dev/tty`, raw mode): CUP and
  24-bit SGR; `copy`/`fill` are the VT420's DECCRA/DECFRA (added to the terminal). A shadow keeps
  what the gpu drew, for `get` and palette redraws; a cell it didn't draw reads as blank (OpenOS's
  cursor blink reads under itself, so an error there broke `term.read`).
- **Depth** from Device Attributes (no 22: 1 bit; 22: 4; 22 and sixel 4: 8), OC's palettes (tier 2
  colors, tier 3 grays and its 6x8x5 cube), quantized as OC's packed colors.
- **Keys:** kitty's protocol with every key and associated text (flags 1|2|8|16 = 27), so key_down
  and key_up carry the typed char and LWJGL codes; flag 16 was added to the terminal (the screen
  pairs a key press with its character). Mouse: SGR 1002+1006 to touch/drag/drop/scroll (buttons
  as OC: left 0, right 1, middle 2). Bracketed paste to clipboard. Ctrl+C is OpenOS's
  `interrupted`; Ctrl+Alt+C raises "interrupted" (OpenOS's hard interrupt).
- **Addresses:** a device's id without `id:`; gpu/screen/keyboard random per run; the computer's
  is `/etc/machine-id` (made once).
- **Modem over UDP:** a socket a port, shared by modems; `modem_message(to, from, port, distance,
  ...)` via `sys.recvmsg` (added: where a datagram came in). A one-string message is the string
  raw (interop with `nc -u`); else `"\0openos\0"` .. serialized values.
- **Redstone:** OC sides relative to the block (OC's right is mcos's left), world names without a
  front; input is the face's level (includes own output); `redstone_changed` skips own changes.
- **Robot:** each call starts the action and waits for its event (OC's calls were synchronous);
  up/down walk a block; inventory is the hand (slot 1 only).

## Found on the way (fixed on master)

- `board.uptime` was a server-thread board call (a tick a read): OpenOS's event loop ran a tick a
  read and timers came late. Now the firmware's clock (`call("clock")`).
- Associated text (kitty flag 16), DECCRA/DECFRA, `sys.recvmsg`.
- OpenOS's `event.pull(0)` gives up when its deadline has passed; the boot drains with
  `computer.pullSignal(0)`.

## Costs and limits

- 168 KB of mcos's image (710 KB of a tier 1 disk's 1 MB). An idea: make openos installable, not
  in every image.
- Not there: process, thread, internet, pipe, rc, devfs libraries; filesystem/internet/drive
  components; gpu video memory; viewports; robot tanks, light, compare, slots past the hand;
  OpenOS's shell (would need process, pipe, sh and its /bin: recommended against).
- A 0.1 s `poll` was seen returning after ~0.25 s (not investigated).

## Next (the user's)

1. The user tests a real OpenOS program (item 5).
2. Then simplify: take the vt100 rules out of the tty and simplify its write (the user would have
   skipped OpenOS's tty binding: windows, gpu interception, screen caches; should have been raised
   before building it).

## Tests

`OpenOsTest` (globals and libraries, unsupported library, os.exit, gpu draw and read back, keys,
line editing, timers, modem, redstone); `OpenOsRobotGameTest` (a golem runs a robot program as a
service).
