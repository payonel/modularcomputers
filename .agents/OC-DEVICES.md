# OpenComputers' components against modcomp's devices

OpenComputers' (OC's) components, each with its API (its `@Callback`s) and signals, against the
nearest modcomp device, from OC's `master-MC1.12` branch and modcomp's master (2026-10-26). Each
lists what modcomp lacks: the component, a signal (or its signature), a method or a behavior. A
difference AGENTS.md or PLAN.md makes on purpose or plans is said so under **Deliberate**.

Read a modcomp "event" as a table a device keeps and a program takes with `pull()` (after its
interrupt), where OC pushed a signal; and its faces as names, where OC's sides are numbers. Those
two hold everywhere and aren't repeated as differences.

## Summary

**No modcomp equivalent:** apu, server (racks), microcontroller, tablet, generator, solar
generator, chunkloader, bundled and wireless redstone (bundled planned), drone (planned: a
legless golem), tunnel (a wired card over Bluetooth does its job), relay/access point, net
splitter, tank controller, tank, experience, sign, angel, trading, database, barcode reader, data
card, transposer, hologram, 3D printer (the photomask's API is planned), waypoint (a GPS card is
planned), assembler (planned).

**Nearest, with gaps:**

| OC | modcomp | Main gaps |
|---|---|---|
| gpu, screen, keyboard | the terminal (escape sequences, byte reports) | no cell read-back, palette, depth change, viewport, video memory; no address or player in reports; no `screen_resized` push; no `walk` |
| filesystem, drive | a disk (hfs or raw) | `makeDirectory` not recursive, `remove` not recursive; no sectors; labels through `board` |
| eeprom | the EEPROM | no size, checksum, read-only; data is the NVRAM's boot variables |
| disk_drive | the Disk Station (a dock) | no eject, no media address |
| computer | `board`, `sys` | no address, tmpAddress, realTime, users, pushSignal, beep, architectures, remote start/stop |
| modem | the network card, mcos's sockets | ports are the kernel's; one string, not typed values; no isWired; wake matched anywhere, no fuzzy |
| internet | the internet card | no TCP (`connect`, `internet_ready`); arguments and answers in other shapes; no redirects followed |
| redstone | the redstone card | input includes its own output; no comparator, no wake threshold; no old value in the event |
| motion_sensor | the motion sensor card | no sensitivity; no line of sight; a cube, not a sphere |
| piston | the robotic piston (a block) | no `isSticky`; push and pull shaped differently |
| robot | the robotics device | one hand, no slots or `select`; no tanks, light, compare; drop only into containers; moves end in events |
| inventory controller | the crafting card, robotics | no single-slot queries, sizes, equivalence; no item inventories |
| crafting | the crafting card | its own grid; an event later |
| leash, tractor beam | the field card, `pickup()` | a different design (suspend and move) |
| navigation | the robotics pose | no range, no waypoints |
| debug | the operator card | commands only: no world, player or scoreboard objects, no card-to-card messages |
| geolyzer | robotics `scan`, the weather card | no area density scan, no sky or sun checks |

**Found on the way:** mcos on master has no power readings: `/sys/class/power_supply` (and
mcos's threads) are on the `openos` branch only.

## Display and input

### gpu (GraphicsCard.scala; GpuTextBuffer has no @Callback)
**modcomp:** none as a device: programs write escape sequences to the Terminal (`write(text)`, device/Terminal.java; decoded by display/VtDecoder.java).
**Signals:**
- none in OC.
**API:**
- `getActiveBuffer`, `setActiveBuffer(index)`, `buffers()`, `allocateBuffer([w, h])`, `freeBuffer([index])`, `freeAllBuffers()`, `totalMemory()`, `freeMemory()`, `getBufferSize([index])`, `bitblt(...)` (video memory): **missing**. Nearest: the alternate screen (`CSI ?1049h`/`?47h`/`?1047h`), one hidden buffer, not addressable; a copy within the screen is DECCRA.
- `bind(address[, reset]): boolean`, `getScreen(): string`: **missing** (no gpu: a program writes to the terminal it wants; the console group fans `console` out). Nearest to `reset`: `ESC c`.
- `getBackground()`, `getForeground(): number, boolean`: **missing** (nothing reads back the SGR state).
- `setBackground(value[, palette])`, `setForeground(value[, palette]): old, oldIndex`: SGR `40-47`/`100-107` (`30-37`/`90-97`), `48;5;n`, `48;2;r;g;b`, `49` (`38;...`, `39`); no answer (no old value); a color is an index or r;g;b, not packed 0xRRGGBB. modcomp also has bold and reverse.
- `getPaletteColor(index)`: **missing** (xterm's fixed 16, `PALETTE`). `setPaletteColor(index, color)`: **missing** (OSC 4 is parsed and ignored).
- `getDepth()`, `maxDepth()`: no method; fixed by tier (ColorDepth MONOCHROME/SIXTEEN/TRUE_COLOR), learned from the device info's `colors` (2, 16, 2^24, not bits) or Device Attributes (`?6c`, `?65;22c`, `?63;4;22c`). `setDepth(depth)`: **missing**.
- `getResolution(): w, h`: `getSize()` (columns, rows, largest columns, rows), or `CSI 18t` → `CSI 8;rows;cols t` (rows first) in the read stream.
- `setResolution(w, h): boolean`: `CSI 8;rows;cols t` (rows first): clamped to 1..largest, not an error; 0 the largest; an omitted one kept; no answer.
- `maxResolution(): w, h`: `getSize()`'s 3rd and 4th, or `CSI 19t` → `CSI 9;rows;cols t` (in the console group, the group's largest).
- `getViewport()`, `setViewport(w, h)`: **missing** (DECSTBM's scroll region isn't a viewport).
- `get(x, y): char, fg, bg, fgIndex, bgIndex`: **missing** (no cell can be read back).
- `set(x, y, value[, vertical]): boolean`: CUP (`CSI y;xH`) and the text; unlike OC's, the text moves the cursor, wraps (`?7`), and its controls and escapes are interpreted (UTF-8 bytes); `vertical` **missing** (a character and a cursor move each).
- `copy(x, y, w, h, tx, ty)`: DECCRA (`CSI t;l;b;r;page;toTop;toLeft;page $v`): inclusive corners and an absolute destination, not a size and a translation; whole lines also SU/SD, IL/DL.
- `fill(x, y, w, h, char)`: DECFRA (`CSI char;t;l;b;r $x`): the character a decimal code point, inclusive corners, printable only, the current colors; spaces also ED/EL/ECH.
- modcomp only: `write`, `read([max])`, `getSize()`, DECSCUSR, sixel (tier 3), BEL, reports `5n`/`6n`/`c`, G0/G1.
**Deliberate (AGENTS.md):** terminals are VT102/xterm devices (no gpu object, bind or draw calls); each tier's largest is OC's screen size, resized by `ESC[8;rows;cols t`, no multiblock screens; depth is each tier's model, fixed; the server's decoder is the only one; methods never block.

### screen (common/component/Screen.scala, TextBuffer.scala; walk in common/tileentity/Screen.scala)
**modcomp:** Terminal (device/Terminal.java; TerminalTier, Resolution, ColorDepth). The mouse is byte reports in its `read()` stream, only while a program has DECSET tracking on, only from the screen view.
**Signals:**
- `touch(address, x, y, button[, player])`: a press report, `CSI <b;x;yM` (SGR, `?1006`) or X10 `ESC [ M` + 3 bytes, with `?9`/`?1000`/`?1002`/`?1003` on. No address, no player; modifier bits extra (Shift 4, Meta 8, Control 16); buttons xterm's (0 left, 1 middle, 2 right; OC's 0 left, 1 right); whole 1-based cells only (no precise mode); X10 can't report past 223.
- `drag(address, x, y, button[, player])`: a motion report (32+button), in `?1002` while held, or `?1003` any motion (OC has none: 32+3 with no button); as touch.
- `drop(address, x, y, button[, player])`: a release report (`?1000` up, never `?9`); SGR `CSI <b;x;ym` (button kept), X10 code 3 (no button); as touch.
- `scroll(address, x, y, delta[, player])`: a wheel report, 64 up, 65 down, plus modifiers, a report a notch (no signed delta); with no tracking the wheel scrolls back (3 lines) and the program gets nothing.
- `walk(address, x, y[, player])`: **missing** (no stepping on screens).
- `screen_resized(address, w, h)`: **missing** as a push (a program asks, `CSI 18t`); the Terminal pushes nothing on resize (a pty's TIOCSWINSZ sends SIGWINCH; a terminal's own resize: not traced).
**API:**
- `isOn()`, `turnOn()`, `turnOff()`: **missing** (bus powered: dark when no running computer powers it).
- `getAspectRatio(): w, h`: **missing** (no multiblock screens).
- `getKeyboards(): table`: **missing** (its keyboard is the same device).
- `isPrecise()`, `setPrecise(enabled)`: **missing** (cells only).
- `isTouchModeInverted()`, `setTouchModeInverted(value)`: **missing** (no in-world touch: a right click opens the screen view).
- modcomp only: `getSize()`; mouse modes chosen by the program; scrollback (Shift+PageUp/PageDown, the wheel), `ESC[3J`.
**Deliberate (AGENTS.md):** the mouse is xterm's (DECSET 9, 1000, 1002, 1003, 1006), by cell, the wheel scrolling back with none tracking; a screen and keyboard are one device; no multiblock screens; scrollback is the terminal's; data only while a program runs; reports are bytes in the stream, not pushed events; size news is the `18t` report.

### keyboard (Keyboard.scala; no @Callback)
**modcomp:** the Terminal's keyboard (Terminal.java `key`, `type`, `paste`; TerminalKeyboard.java): bytes in `read([max])`, its interrupt raised while any wait.
**Signals:**
- `key_down(address, char, code[, player])`: legacy by default (a VT100's): typed text as UTF-8, Ctrl+letter a control character, special keys as escape sequences (`CSI A`, `ESC O A` in `?1`, `CSI 1;5A` with modifiers); with kitty's flags (`CSI > flags u`): `CSI code;mods u`, flag 8 every key (modifiers, locks), flag 16 the typed text (`CSI code;mods;text u`). Codes are kitty's (a text key's character, private use 57348+), not LWJGL's. No address, no player; modifier bits and repeats (flag 2, `:2`) extra.
- `key_up(address, char, code[, player])`: only with flag 2, `CSI code;mods:3 u` (nothing in legacy mode); no character (associated text is a press's); no address, no player. Whether held keys are released when a player leaves the view (OC's `releasePressedKeys`): not traced.
- `clipboard(address, line[, player])`: a paste as bytes as if typed (bracketed in `ESC[200~`..`ESC[201~` with `?2004`). OC a signal a line, ends kept; modcomp one stream, new lines as `\r`; C0/C1 and ESC stripped, tabs kept; 4096 characters at most; no address, no player; Ctrl+Shift+V or Shift+Insert in the screen view.
**API:**
- none in OC. modcomp's input is `read([max])`: the bytes waiting, whole keys, or nil, never blocking; 256 keys queued, past that dropped.
- modcomp only: kitty's flag stack (`CSI > u`, `< u`, `= u`, `? u`); reports' replies in the same stream.
**Deliberate (AGENTS.md):** a terminal is a screen and keyboard as one, its keyboard modes its own (raw mode is termios's); paste as typed, no controls or ESC, bracketed with 2004; UTF-8; keys only while a program runs; level-triggered interrupts and rings: input taken with `read`, not pulled as signals.

## Storage and boot

### filesystem (FileSystem.scala)
**modcomp:** a Disk in hfs mode (device/Disk.java; DiskTier: 1, 4, 16 MiB); an open file is a FileHandle with methods of its own.
**Signals:**
- none in OC. modcomp adds `changes()`: a change a call, `{event, path}` (modify, create, remove, rename with `from`, format), raising its interrupt.
**API:**
- `getLabel(): string`: no Disk method: `board.info(address).label` (from the item); OC nil with none.
- `setLabel(value): string`: no Disk method: `board.setLabel(address, label)`: answers nothing, refuses over 32 characters or unprintable ones (`Label.invalid`) instead of cutting; nil or "" clears.
- `isReadOnly(): boolean`: **missing** as a method (`Disk.isReadOnly()` is Java's: a Disk Station's source; `info()` doesn't say).
- `spaceTotal(): number`: `getCapacity()` (the tier's bytes; never infinite).
- `spaceUsed(): number`: `getUsed()` (512 bytes an entry, plus open handles' uncommitted writes).
- `exists(path)`, `size(path)`, `isDirectory(path)`: `stat(path)` (nil when nothing; `.size`, `.directory`); no separate methods.
- `lastModified(path): number`: `stat(path).modified`, world time in ms (a tick 50), not real time; `stat(path, true)` a link's target too.
- `list(path): table`: `list(path[, typed])`: path optional (`/`), sorted, directories with `/`; `typed` gives `{name, type}`.
- `makeDirectory(path): boolean`: `mkdir(path)`: not recursive, fails when it exists (OC makes the parents).
- `remove(path): boolean`: `remove(path)`: a file, a link or an empty directory only ("directory not empty"; OC's is recursive).
- `rename(from, to): boolean`: `rename(from, to)`: fails when `to` exists or a directory into itself; an error message where OC gives false.
- `open(path[, mode]): handle`: `open(path[, mode])`: fopen's modes (r, r+, w, w+, a, a+; `b` stripped), OC r, w, a (with b); 16 handles for every program together (OC's maxHandles a caller).
- `read(handle, count)`: `FileHandle.read(n)` (at most 4096; nil at the end).
- `seek(handle, whence, offset)`: `FileHandle.seek([whence[, offset]])` (both optional, whence "cur"; before the start refused).
- `write(handle, value): boolean`: `FileHandle.write(data)`: buffered, committed at close, atomically; answers nothing; a full disk ENOSPC, full memory ENOMEM (OC writes at once, true).
- `close(handle)`: `FileHandle.close()` commits; if that fails, the file stays open, its writes kept.
- Every failure is pcall's `false, why` from `board.invoke`; OC `nil, why` or an error.
**Deliberate (AGENTS.md):** hfs disks serve real files as 9P does (no host file held open; writes atomic at close); file times are the world's; links are CIFS XSym files the VFS follows; a disk reports every change (`changes()`); its format is on its item, `getMode()`/`setMode("hfs"|"raw")`: one device for OC's filesystem and drive.

### drive (Drive.scala)
**modcomp:** a Disk in raw mode (device/Disk.java).
**Signals:**
- none in OC. modcomp: `{event = "write", offset, length}` through `changes()`.
**API:**
- `getLabel()`, `setLabel(value)`: as the filesystem's (`board.info`, `board.setLabel`); OC's read-only lock (isLocked) only as a Disk Station's source.
- `getCapacity(): number`: `getCapacity()`.
- `getSectorSize()`, `getPlatterCount()`: **missing**.
- `readSector(sector)`, `writeSector(sector, value)`: **missing**; nearest `read(offset, n)` (any offset, 4096 at most; fewer near the end, nil at it; unwritten space zeros) and `write(offset, data)` (what fits; answers bytes written).
- `readByte(offset): number`: `read(offset, 1)`: offsets from 0 (OC's from 1), a one-byte string (OC a signed number).
- `writeByte(offset, value)`: `write(offset, string.char(value))`: offsets from 0, a string.
- An hfs disk refuses `read`/`write`; a raw disk every file method.
- No head position or seek-time model (OC charges per sector moved).
**Deliberate (AGENTS.md):** byte offsets, no sectors (the user's call); a raw image ends where its data does, zeros past it; one device switches hfs and raw (`setMode`).

### eeprom (EEPROM.scala)
**modcomp:** Eeprom (machine/Eeprom.java; boot variables machine/BootVariables.java).
**Signals:**
- none in either.
**API:**
- `get(): string`: `get()` (from the item).
- `set(data)`: `set(data)`: takes effect at the next power-on; no size limit (OC's eepromSize), no read-only refusal, no energy cost, no pause (OC's 2 s).
- `getLabel(): string`: `board.info(address).label`, nil when none (OC "EEPROM").
- `setLabel(data): string`: `board.setLabel(address, label)`: 32 printable characters at most, refused not cut; nil or "" clears (OC trims to 24, falls back to "EEPROM", answers the label).
- `getSize(): number`: **missing** (no limit for now; `board.info(address).used` the bytes in use).
- `getChecksum(): string`: **missing**.
- `makeReadonly(checksum): boolean`: **missing** (no read-only EEPROM).
- `getDataSize()`, `getData()`, `setData(data)`: **missing** as a free store; instead `getBoot()` (`{entries = {{number, disk, path, label}...}, order, next}`) and checked calls: `setBootEntry(number, disk[, path[, label]])` (0-7), `removeBootEntry(number)`, `setBootOrder(numbers)`, `setBootNext([number])`; `setBootCurrent([number])` the device's own, not the NVRAM's.
**Deliberate (AGENTS.md):** boot order is the EEPROM's NVRAM, UEFI's typed boot variables checked at every write, never a free store; it's "eeprom", not firmware; its data's one home is the item.

### disk_drive (common/tileentity/DiskDrive.scala; the rack's DiskDriveMountable.scala)
**modcomp:** nothing matches: a disk sits in a case's or golem's slot; the nearest block is the Disk Station (device/DiskStation.java), a dock with target and source bays.
**Signals:**
- none in either (disks coming and going: the machine's component_added/removed in OC, the bus's interrupt in modcomp).
**API:**
- `isEmpty(): boolean`: nearest `DiskStation.get()` (`{target, source}`, an empty bay left out; each `{tier, mode, used, capacity, label}`).
- `eject([velocity]): boolean`: **missing** (by hand, in its GUI).
- `media(): string`: **missing** (`get()` gives no disk's address; the docked disks are on the bus as `id:<uuid>`, but nothing links a bay to its disk's address).
- The rack's: the same three; no racks in modcomp.
- modcomp only: `format()`, `wipe()`, `clone()`, `install()`, `update()`, `set({action = ...})` (fail with "no disk", "no source disk", "no power").
**Deliberate (AGENTS.md):** a dock and duplicator, not a drive (its source only read); disks by hand, no eject; a device behind a host is on the bus only while its host is.

## The computer, its parts and its power

### computer (server/machine/Machine.scala, luac/ComputerAPI.scala, lua/machine.lua `libcomputer`, lua/bios.lua)
**modcomp:** the firmware's `board` (lua/cpu/machine.lua `sandbox.board`, machine/MotherboardApi.java, runtime/Board.java) and mcos's `sys` (lua/mcos/init.lua, docs/mcos/sys-api.md); no "computer" device on the bus.
**Signals:**
- `component_added(address, type)`, `component_removed(address, type)`: the `"bus"` interrupt (`Board.BUS`) from `board.waitForInterrupt`, then `board.list()` (no arguments: the program compares the lists); in mcos inotify's `CREATE`/`DELETE /dev/<name>`, or `devices.wait`.
- `component_available(type)`: OpenOS's, not the machine's; nearest `devices.wait(type)`.
- `computer.signal` / `computer.checked_signal` (a component queueing a signal): a device raising its interrupt line (`Device.interruptPending`) and keeping its events until a method takes them; interrupts carry nothing.
- `computer.start`: the wake line (`Device.wakeLine`: Wake-on-LAN presses the power button). `computer.stop`: **missing** (nothing outside a machine stops it).
- `computer.started` / `computer.stopped` (to components): no message (devices take data only while a program runs: a rule, not an event).
**API:**
- `address(): string`: **missing** in `board` (the board's id, `BoardAssembly.boardId`, only through a Bluetooth card's `getBoard()`); mcos's nearest `sys.gethostname()` (falls back to the first card's address).
- `tmpAddress(): string`: **missing** (`/tmp` is tmpfs, no device).
- `freeMemory()`: `board.hardware()`'s `memoryTotal - memoryUsed` (RAM above the baseline); `sys.sysinfo().freeram`; `free`.
- `totalMemory()`: `board.hardware().memoryTotal` (and each stick: slot, tier, capacity); `sys.sysinfo().totalram`.
- `energy()`, `maxEnergy()`: `board.power().store.stored` / `.capacity` (`math.huge` infinite; also `batteries` and `load`). mcos on master: none (`/sys/class/power_supply` is on the openos branch only).
- `uptime(): seconds`: `board.uptime()` in ticks (the firmware's clock, at once); `sys.uptime()` seconds; `uptime`.
- `realTime()`: **missing** (`board.time()` the world's game time in ticks; `sys.time()` seconds from the world's epoch).
- `shutdown([reboot])`: `board.poweroff()` / `board.reboot()` (and `board.halt()`); `sys.reboot("poweroff"|"restart"|"halt")` closes files first; `poweroff`, `reboot`, `halt`.
- `getBootAddress()` / `setBootAddress(address)` (bios.lua, EEPROM data): the NVRAM's `getBoot`, `setBootEntry`, `removeBootEntry`, `setBootOrder`, `setBootNext` (`/dev/eeprom`, `efibootmgr`, `/sys/firmware/efi/efivars/*`): entries, BootOrder, BootNext, not one address.
- `users()`, `addUser(name)`, `removeUser(name)`: **missing** (no owners).
- `pushSignal(name, ...)`: **missing** (no signal queue; mcos has pipes, sockets, `sys.kill`).
- `pullSignal([timeout])`: `board.waitForInterrupt(seconds, ...sources)` (the addresses with an interrupt pending), then each device's stream method by `board.invoke`; mcos `sys.poll` and a `pull` ioctl or read; `devices.event`.
- `beep([frequency|pattern[, duration]])`: **missing** as a method; a BEL to a terminal rings `TerminalBell` (one sound, no frequency or duration, 200 ms merged); the Sound Card plays samples.
- `getDeviceInfo()` (pauses 1 s): `board.info(address)` a device at a time, `board.hardware()`, `board.status()`; mcos `/sys/class/<type>/<name>/...`, `lsdev`, `sys.devices()`; no vendor, product or clock.
- `getProgramLocations()`: **missing**.
- `getArchitectures()`, `getArchitecture()`, `setArchitecture(name)`: **missing** (Lua only).
- `isRobot()`: no `board.isRobot`; a robot has a `robotics` device (`board.list()`, `/dev/robotics0`).
- `start()`, `stop()`, `isRunning()` (the computer component): only Wake-on-LAN starts one; nothing stops or asks remotely (`board.poweroff()` on its own).
- `crash(message)` (Java's): a program's error panics the CPU (ABRT on the screen, machine/Cpu.java); not readable back.
**Deliberate (AGENTS.md):** no signal queue (level-triggered interrupts, devices keep their events, `waitForInterrupt` the only wait); boot order is NVRAM's; the clock is the world's; "device"; memory above a baseline; mcos programs only through `sys`, Linux-shaped device info; lazy power, `PowerUnits`; methods never block (OC's beep and getDeviceInfo pause).

### cpu (CPU.scala)
**modcomp:** machine/Cpu.java, CpuTier.java: a part in the socket, not a device; `board.hardware().cpu` = {tier, busCapacity}.
**API:**
- none in OC (device info: class processor, product, clock = call budget); no vendor/product/clock; its tier sets bus capacity (4/16/64) and power draw.
**Deliberate (AGENTS.md):** board calls limited by server time a tick (1 ms), not call counts; bus capacity from the CPU (OC's cpuComponentCount).

### apu (APU.scala)
**modcomp:** none
**Missing in modcomp:** a CPU with graphics (no gpu at all: terminals take escape sequences).

### memory (Memory.scala)
**modcomp:** machine/MemoryTier.java, item/MemoryItem.java (1/2/4 MiB); `board.hardware().memory` = [{slot, tier, capacity}].
**API:**
- none in OC (device info); no clock; a stick's tier sets the firmware's inbox (tier 1 none).
**Deliberate (AGENTS.md):** RAM above a measured baseline; only the firmware caches, tier 1 has no cache (slow on purpose).

### server (Server.scala)
**modcomp:** none (no racks); nearest a case.
**Missing in modcomp:** a rack-mounted computer (no methods of its own; its machine's are the computer's).

### microcontroller (common/tileentity/Microcontroller.scala)
**modcomp:** none; nearest a tier 1 case.
**Missing in modcomp:** a sealed one-block computer. OC API: start(), stop(), isRunning(), lastError(), isSideOpen(side), setSideOpen(side, open); network messages forwarded by side.

### tablet (Tablet.scala, common/item/Tablet.scala)
**modcomp:** none (the golem is the only computer that isn't a block).
**Missing in modcomp:** a handheld computer. OC API: getPitch(), getYaw(); signal `tablet_use(data)`.

### battery upgrade (UpgradeBattery.scala)
**modcomp:** item/BatteryItem.java, power/BatteryTier.java, BatteryCharge.java: a case's bays, a golem's slot; not devices: `board.power().batteries` = [{bay, tier, charge {stored, capacity, infinite}}]; charged by crafting, upgraded keeping charge, a golem's by the charging pad.
**API:**
- none in OC (device info: capacity). modcomp's tiers differ in kind (tier 2 recharges idle, tier 3 never runs out).
**Deliberate (AGENTS.md):** lazy power, `PowerUnits`.

### generator (UpgradeGenerator.scala)
**modcomp:** none
**Missing in modcomp:** a fuel-burning power source in a robot. OC API: insert([count]), count(), remove([count]).

### solar generator (UpgradeSolarGenerator.scala)
**modcomp:** none
**Missing in modcomp:** power from sunlight (passive, no methods).

### chunkloader (UpgradeChunkloader.scala)
**modcomp:** none (nothing force-loads chunks; not in PLAN.md).
**Missing in modcomp:** keeping a computer's or robot's chunk loaded. OC API: isActive(), setActive(enabled) (on and off with the computer).

## Networking

### modem (NetworkCard.scala, WirelessNetworkCard.scala, traits/WakeMessageAware.scala)
**modcomp:** NetworkCard (wired `NETWORK`, WLAN `TIER1_WLAN`/`TIER2_WLAN`; device/NetworkCard.java, carried by device/Ether.java); mcos's sockets (lib/modules/net.lua)
**Signals:**
- `modem_message(receiverAddress, sender, port, distance, data...)`: the card's interrupt and `receive()` → `{from, data, distance, broadcast}`: no port; `data` one string (OC up to 8 typed values); a wired card's distance is cable metres (OC's 0). In mcos: `recvfrom()` → `data, address, port`, or `recvmsg()` and `{interface, to, distance}`.
**API:**
- `open(port)`, `close([port])`, `isOpen(port)`: **missing** on the card (mcos's `bind(socket, port)`, 0 any free, EADDRINUSE when taken; closing a socket frees its port; no "close all", no way to ask whether a port is open; no "too many open ports" limit).
- `isWireless(): boolean`: no method (its `info()`'s `wireless`, in the board's device info).
- `isWired(): boolean`: **missing** (a WLAN card is never wired; OC's tier 2 wireless card sent over cables too).
- `send(address, port, data...): true`: `send(address, data)`: one string of 8192 bytes at most, no port; `true`, or `false, "busy"|"unreachable"` (OC always true); `id:` prefix allowed. mcos: `sendto(data, address, port)`.
- `broadcast(port, data...): true`: `broadcast(data)`: one string, no port; how many cards took it.
- `getStrength(): number`: `getStrength()` (whole blocks; fails on a wired card).
- `setStrength(strength): number`: `setStrength(blocks)`: an integer clamped to 0..16 or 0..400 by tier, answers what's set; fails on a wired card. A send costs `strength * WIRELESS_SEND_COST_PER_BLOCK` (`power.charge`; OC errors "not enough energy").
- `getWakeMessage(): string, boolean`: `getWake()`: the message only, no fuzzy flag.
- `setWakeMessage(message[, fuzzy]): string, boolean`: `setWake(message)`: the old one only; nil or empty clears; 256 bytes at most. Matching: modcomp anywhere in the data, only while its computer is off; OC the first value equals it (or starts with it, fuzzy), any port.
- modcomp only: `address()`, `receive()`, `pull()` (a traffic log of 8: `{direction, address, distance, bytes, outcome}`).
**Deliberate (AGENTS.md):** ports are the kernel's, in the frame's data (no open/close/isOpen; send and broadcast take no port); a unicast frame is taken or refused at once with why, never retried; a broadcast is best effort (a count); nothing routes between wired and WLAN; Wake-on-LAN matched anywhere, listening while off; a send costs more the farther it reaches (OC's tier ranges 16 and 400).

### tunnel (LinkedCard.scala)
**modcomp:** none. The BluetoothCard (device/BluetoothCard.java, block/Bluetooth.java) is the nearest: a link joining two blocks as a cable does, not a message device; a wired card's frames cross Bluetooth links (Ether's cable walk), so a wired card over a link does a tunnel's job. Bluetooth is per `Level` (not across dimensions); a range limit: unclear (none found).
**Missing in modcomp:** a paired point-to-point card with a shared channel, across dimensions.
**Signals:**
- `modem_message(receiverAddress, sender, 0, 0, data...)`: **missing** (nearest: a wired card's `receive()` over a link).
**API:**
- `send(data...)`, `maxPacketSize()` (NetworkCard's MTU is a constant 8192, no method), `getChannel()` (nearest: Bluetooth `getLink()`/`getBoard()`, boards not a channel), `getWakeMessage()`/`setWakeMessage(...)` (NetworkCard's work over a link): **missing**.
- modcomp only (Bluetooth): `setLink(id)`, `get()` → `{link, board}`, `set({link})`.
**Deliberate (AGENTS.md):** none (Bluetooth is a way onto a bus; nothing about tunnels).

### internet (InternetCard.scala)
**modcomp:** InternetCard (device/InternetCard.java; internet/HttpExchange.java, InternetPolicy.java); mcos's `/dev/internet0`, lib/http.lua, `wget`, `curl`
**Signals:**
- `internet_ready(cardAddress, socketId)` (TCP sockets only): **missing** (no TCP). For HTTP, the card's interrupt comes at the next tick with news; not an event: the program asks `response`/`read`. Each finished request adds a `pull()` entry `{method, url, status, millis, bytes, why}`.
- `http_response`: in OC's `request` doc, never pushed on this branch (no `"http_response"` in its code); the interrupt plays that role.
**API:**
- `isHttpEnabled()`: **missing** (`request` fails: "the internet is turned off on this server").
- `request(url[, postData[, headers[, method]]]): handle`: `request(url[, method[, headers[, body]]])`: another order, an integer handle (not an object) passed to the card's methods; soft failures (off, more than 4 open on a card, 64 on the server; OC errors "too many open connections"); GET, or POST with a body.
- `isTcpEnabled()`, `connect(address[, port])`: **missing** (no raw TCP; PLAN.md: maybe later).
- handle `finishConnect()`: **missing** (`response` false, or failing with why, covers it).
- handle `response(): code, message, headers`: `response(handle)` → one table `{status, message, headers}`: headers lower-case names to one string (OC's lists); not yet `false` (OC nil); fails with why when the request did.
- handle `read([n])`: `read(handle[, n])`, answers swapped against OC's: nothing yet `false` (OC `""`), the end `""` (OC nil); both fail with why; modcomp gives the body at any status (OC errors on an error status).
- handle `close()`: `close(handle)`.
- TCP handle `finishConnect`, `read`, `write`, `close`, `id`: **missing**.
- modcomp only: `pull()`, the access log of the last 8 (`/dev/internet0`'s lines).
- Behavior: OC follows redirects (HttpURLConnection's default) and uses the server's proxy, modcomp neither; OC may refuse every header by config, modcomp only the framing ones; OC's "only the owning computer" is modcomp's one-bus rule (BusHolds).
**Deliberate (AGENTS.md):** it speaks HTTP itself (TLS is beyond a program's Lua); used as any device (no `sys.http`, no handle objects, `read`'s `false` for not yet); kept to the public internet (private, loopback, link-local, CGNAT, multicast, reserved refused in IPv4 and IPv6; every resolved address judged, the socket to the one judged; redirects the program's, each hop judged; framing headers, line breaks, credentials refused; no proxies, cookies or server details); admins' `internet_*` settings; its events wait with a handshake (a full card pauses the download).

### relay (common/tileentity/Relay.scala; the access point and switch are folded into it on this branch)
**modcomp:** none as a device: cables, the expansion hub (block/ExpansionHubBlockEntity.java) and screens carry wired frames (Cabling's walk, `NetworkCard.reach`), Bluetooth links too; none programmable.
**Missing in modcomp:** a relay, access point or repeater (joining wired, WLAN and linked networks, re-sending WLAN frames, queueing them).
**Signals:**
- `modem_message(side, port, answerPort, data...)`: OC's ComputerCraft integration only, out of scope.
**API:**
- `getStrength()`, `setStrength(strength)`, `isRepeater()`, `setRepeater(enabled)`: **missing**.
**Deliberate (AGENTS.md):** nothing routes between wired and WLAN; a case ends the walk, hubs and screens carry it; frames go at once and in order (no relay queue).

### net_splitter (common/tileentity/NetSplitter.scala)
**modcomp:** none
**Missing in modcomp:** a block switching cable connections by side, by program or redstone. OC API: setSides(settings), getSides(), open(side), close(side) (redstone input inverts the open sides; no signal).
**Deliberate (AGENTS.md):** none.

## Redstone, sensors, the piston

### redstone (Redstone.scala, RedstoneVanilla.scala, RedstoneSignaller.scala; card tiers 1/2)
**modcomp:** RedstoneCard (device/RedstoneCard.java), in a case or a hub
**Signals:**
- `redstone_changed(address, side:number, oldValue, newValue[, color])`: event `{name="redstone", side, level, powered}` taken with `pull()`. Side is a world name string, not a number; no oldValue (the face's state now: `level`, the strongest there, and `powered`, its own output). OC fires only on input changes; modcomp on any change to a face (input, its own `set`, another card in the block).
**API:**
- `getInput([side:number]): number or table`: `get([sides])`'s `level`. Always a table of faces by name (`{north={level,powered},...}`), never a bare number; `level` includes the block's cards' outputs (OC's input is external only).
- `getOutput([side:number]): number or table`: `get([sides])`'s `powered`; same format.
- `setOutput([side:number,] value:number or table): previous values`: `set(sides)` takes only `{face={powered=n}}` (no `(side, value)` form), validates all first, clamps 0..15, and returns every face's state after the change, not the previous values. Each change costs `CHANGE_COST`; OC pauses `redstoneDelay`, modcomp doesn't.
- `getComparatorInput(side:number): number`: **missing** (no comparator reading anywhere).
- `getWakeThreshold(): number`: **missing** (RedstoneCard has no `wakeLine`; only NetworkCard has one).
- `setWakeThreshold(threshold:number): number`: **missing** (OC starts the computer on a signal past the threshold; modcomp's redstone can't power a computer on).
**Deliberate (AGENTS.md / PLAN.md):** faces are names (world or relative, `Face`), never numbers or `sides`; a device reports every change to its state, whatever caused it; devices speak tables, mcos's driver makes lines (`north 7 7`); several cards in a block put out the strongest. Wake-on-redstone isn't in PLAN.md.

### redstone, bundled (RedstoneBundled.scala)
**modcomp:** none
**Missing in modcomp:** 16-color bundled I/O: `getBundledInput([side[, color]])`, `getBundledOutput([side[, color]])`, `setBundledOutput([side[, color,]] value or table)` (previous values), and `redstone_changed`'s 5th argument `color`.
**Deliberate (AGENTS.md / PLAN.md):** planned ("Bundled redstone, later": one of 16 colors on a face, maybe a tier 2 card, through Ender IO's conduit on NeoForge or a bundled cable of ours; no Fabric mod carries bundled signals on 26.x).

### redstone, wireless (RedstoneWireless.scala; WR-CBE integration)
**modcomp:** none
**Missing in modcomp:** wireless redstone by frequency (Wireless Redstone CBE integration): `getWirelessInput`, `getWirelessOutput`, `setWirelessOutput(boolean)`, `getWirelessFrequency`, `setWirelessFrequency`; signal `redstone_changed(address, "wireless", old, new)`.
**Deliberate (AGENTS.md / PLAN.md):** none; not planned (depends on a third-party mod; network cards cover messages over the air, not redstone).

### redstone I/O block (common/tileentity/Redstone.scala)
**modcomp:** none as a block; a RedstoneCard in a hub (or a case) fills its role.
**Signals:**
- `redstone_changed(...)`: as the card's. OC's block wakes every computer it reaches (`wakeNeighborsOnly = false`); modcomp has no wake.
**API:**
- The vanilla card's methods (or the bundled set with a bundled mod): see above.
**Deliberate (AGENTS.md / PLAN.md):** PLAN.md: a redstone terminal block was built and dropped (2026-10-21); the user went back to the card.

### motion_sensor (MotionSensor.scala)
**modcomp:** MotionSensorCard (device/MotionSensorCard.java; scanning in block/CardWorld.java `entities`). OC's is a block, modcomp's a card.
**Signals:**
- `motion(address, x, y, z[, name])`: event `{mob, x, y, z}` via `pull()`. x, y, z relative to the block's center in both (modcomp rounds to 0.1); `mob` is the entity type id (`minecraft:pig`), not OC's entity or player name (OC sent it only with `inputUsername` on).
- Detection: OC a sphere of radius 8, every 10 ticks, line of sight needed, invisible entities skipped, fires past its sensitivity (distance² > 2·s²); modcomp a cube of 8 (AABB inflate), every 5 ticks (`MOTION_TICKS`), no line of sight or invisibility check, fires on a move of 0.1 or more on any axis (`MOVED`). Both fire for a newly seen entity.
**API:**
- `getSensitivity(): number`: **missing** (fixed threshold, `MOVED` = 0.1).
- `setSensitivity(value:number): number`: **missing**.
**Deliberate (AGENTS.md / PLAN.md):** tier 1 has no settings on purpose (its class doc; PLAN.md "Motion sensor, tier 2 maybe": finer motion, fluids, drops, filters by mob). Line of sight and invisibility: unclear whether left out on purpose.

### piston (UpgradePiston.scala: piston and sticky piston upgrades)
**modcomp:** RoboticPiston (device/RoboticPiston.java), a cabled block (block/RoboticPistonBlock.java, sticky or not); OC's is a robot/drone/tablet upgrade.
**Signals:**
- none in OC.
**API:**
- `isSticky(): boolean`: **missing** (the block's, `RoboticPistonBlock.sticky()`, not readable by programs).
- `push([side:number]): boolean`: `push(side)` pulses (out and back by itself); side required, a name (front is opposite its cable), OC's optional number defaulting to front. Fails `false, "cannot push"` (OC's `false, "move failed"`). Costs `PUSH_COST` whether or not it moved; OC pauses a tick.
- `pull([side:number]): boolean` (sticky only): `retract(side)`, works on a non-sticky too (a sticky one pulls what's in front); fails `false, "not extended"` with no arm out. OC's reaches out and pulls a block in one call (fails `false, "path is obstructed"`).
- (no OC equivalent) `extend(side)`: the arm stays out until `retract`.
**Deliberate (AGENTS.md / PLAN.md):** faces are names (`Face`). PLAN.md: the piston should report its arms' changes as events, not yet. No robot or drone piston upgrade (the golem's robotics device is its only actuator): unclear whether planned.

### modcomp-only (no OC equivalent)
- AudioSensorCard: `pull()` gives `{sound, x, y, z}`, the sound id the game plays and where, relative to the block.
- WeatherCard: `getWeather()` (clear, rain, snow, thunder); `pull()` gives `{name, old, new}` when the weather changes.
- RoboticPiston's `extend`/`retract` arm states; a cabled block, not an upgrade.

## Robots and drones

### robot (Robot.scala + Agent.scala + traits WorldControl, InventoryControl, InventoryWorldControl, TankControl, TankWorldControl)
**modcomp:** RoboticsDevice (`/dev/robotics0`, device/RoboticsDevice.java), its body entity/BotGolem.java and GolemHands.java; storage is the crafting card's (device/CraftingCard.java, its methods need robotics).
**Signals:**
- `inventory_changed(slot)` (common/tileentity/Robot.scala: a main inventory slot filled or emptied): **missing** (no event for its hand or storage changing).
**API:**
- `name(): string`: **missing** as a robotics method (unclear whether a program can read the golem's name; `board.setLabel` labels devices).
- `swing(side[, face[, sneaky]]): boolean, string`: `swing(face)`: `"attacked"`, or `"breaking"` and later `{event="broke", block, kept, dropped}` or `{event="failed", why}`, or false and why. OC blocks for the break and answers true and `entity`/`block`/`fire`, or false and `air`. No `face`/`sneaky`; putting out fire unclear.
- `use(side[, face[, sneaky[, duration]]]): boolean, string`: `use(face)`: `"placed"`/`"used"`, or false and `"nothing happened"`, `"nothing there"`, `"nothing to place against"`. OC answers `block_activated`, `item_placed`, `item_used`, `item_interacted`. No `face`, `sneaky`, `duration` (holding right click).
- `place(side[, face[, sneaky]]): boolean`: no method of its own: `use(face)` places (`"placed"`); no equivalent of OC's placing in the air.
- `detect(side): boolean, string`: `scan([face])`: a table (`{kind="air"|"block"|"fluid"|"entity", name, state, hardness, solid, container | amount, source | entities, block}`), one face or all six. OC: blocked and `entity`/`air`/`liquid`/`replaceable`/`passable`/`solid`.
- `compare(side[, fuzzy]): boolean`: **missing** (a program compares `scan(face).name` and `held().name`).
- `drop(side[, count]): boolean`: **missing** for the hand (`store` only fills a container: "no container there"); the crafting card's `drop([face][, slot[, count]])` drops from storage (a container first; answers how many went into the world).
- `suck(side[, count]): boolean|number`: `take([face][, slot[, count]])` from a container into the hand (how many came); items lying in the world only by the crafting card's `pickup()` (within 1.5 blocks, any direction, not by side).
- `inventorySize(): number`: no method; the crafting card has a fixed 25 (1-9 its grid, 10-25 storage) plus the hand.
- `select([slot]): number`: **missing** (no selected slot: one hand); nearest: the crafting card's `equip(slot)` (a storage slot swapped with the hand).
- `count([slot]): number`: `held()`'s `count` for the hand; the crafting card's `list()` for its slots.
- `space([slot]): number`: **missing** (unclear whether `held()`/`list()` give a stack's maximum).
- `compareTo(otherSlot[, checkNBT]): boolean`: **missing** (compare `list()`/`held()` names).
- `transferTo(toSlot[, amount]): boolean`: the crafting card's `move(from, to[, count])` (how many went, not a boolean).
- `tankCount, selectTank, tankLevel, tankSpace, compareFluidTo, transferFluidTo` (TankControl): **missing** (no tanks; buckets through `use`).
- `compareFluid(side[, tank]), drain(side[, amount]), fill(side[, amount])` (TankWorldControl): **missing** (`scan` reports a fluid's name, amount, source).
- `getLightColor(): number` / `setLightColor(value): number`: **missing**.
- `durability(): number`: `held()` gives `{name, count, damage, maxDamage, label}` (a program works it out); OC a fraction, or nil and "no tool equipped"/"tool cannot be damaged".
- `move(direction): boolean` (front, back, up, down; a block; blocking): `walk(forward[, right[, up]])` (whole blocks, several, strafing) and `walkTo`; vertical only as steps (1 up, 3 down at most), free vertical is `fly`/`flyTo`. True at once, then `arrived` or `blocked` (`why`); OC blocks, then true or nil and why ("not enough energy", "impossible move", a blocking kind such as "solid").
- `turn(clockwise): boolean`: `turn("left"|"right"|"around")`, true at once, then `turned`.
- Sides: OC's actions take front, up, down only (`checkSideForAction`); modcomp all six, its own frame, as names.
- modcomp only: `pose`, `resetPose`, `setPathfinding`, `stop`, `held`, `swap`, `contents`, `store`, `hover`, `land`, `get`, `set`, `pull`.
**Deliberate (AGENTS.md / PLAN.md):** moves and breaks start at once and end in events (methods never block); deterministic whole-block walks from a pose; faces in its own frame, names; one hand and an off hand catching drops, not a slotted inventory (storage the crafting card's, PLAN.md stage 2); two kinds of flight instead of free vertical moves; one answer says it all (`store`'s `true, 0` / `false, n`). Not deliberate or planned (in neither doc): tanks, light color, compare.

### drone (Drone.scala + Agent.scala + the same traits)
**modcomp:** none yet; PLAN.md: "Drones are golems without legs" (the same machine and RoboticsDevice, flying). Nearest today: RoboticsDevice's `fly`/`flyTo`/`hover`/`land`.
**Missing in modcomp:** a drone entity or device; the Agent and trait methods map as the robot's above.
**Signals:**
- `hit(x, z, y[, playerName])` (common/entity/Drone.scala: an entity hits a running drone, knocked back): **missing** (a player's hit picks the golem up as an item; no event).
**API:**
- `getStatusText(): string` / `setStatusText(value): string`: **missing** (the golem's optional screen is a terminal).
- `getLightColor()` / `setLightColor(value)` (flap lights): **missing**.
- `move(dx, dy, dz)` (adds to its target, world axes, no answer, non-blocking): `fly(forward[, right[, up]])` in its own frame, or `flyTo` from the pose; true at once, then `arrived` or `blocked`. OC's targets add up and nothing says it arrived.
- `getOffset(): number` (distance to its target): **missing** (`pose()`, `get()` give position and state).
- `getVelocity()` / `getMaxVelocity()`: **missing** (thrusters 25 blocks a second, the field 10, fixed).
- `getAcceleration()` / `setAcceleration(value)`: **missing**.
- Agent/trait methods: as the robot's, except OC's drone takes any side (`checkSideAny`) and its `suck` collects items in its own block too; a golem on its thrusters swings no tool (in its field it keeps them).
**Deliberate (AGENTS.md / PLAN.md):** drones planned as legless golems on RoboticsDevice; flight ends in events at exact points from the pose, its own frame, not world-axis offsets; a hit picks it up (AGENTS.md).

### robot, from outside (common/tileentity/RobotProxy.scala: the robot as a neighbouring computer's component)
**modcomp:** none (the golem is an entity with no cable port; no device controls it from another computer).
**Missing in modcomp:** another computer starting, stopping or naming a golem: `start()`, `stop()`, `isRunning()`, `setName(name)` (the old name; nil and "is running" while running), `getName()`.
**Deliberate (AGENTS.md / PLAN.md):** none stated.

## Upgrades and other blocks

### inventory_controller (UpgradeInventoryController.scala + traits InventoryAnalytics, InventoryWorldControlMk2, WorldInventoryAnalytics, ItemInventoryControl)
**modcomp:** CraftingCard (device/CraftingCard.java) for storage and container moves; RoboticsDevice for `contents`/`held`/`take`/`store` with the hand.
**Signals:**
- none in OC.
**API:**
- `equip(): boolean`: CraftingCard `equip(slot)` (a slot named swapped with the hand: no selected slot); RoboticsDevice `swap()` swaps the hands.
- `getStackInInternalSlot([slot]): table`: CraftingCard `list()` (every filled slot, `{slot, name, count, ...}`) / RoboticsDevice `held([hand])` (`{name, count, damage, maxDamage, label}`); no single-slot getter.
- `isEquivalentTo(otherSlot)`: **missing** (OC compares ore dictionary ids).
- `storeInternal(slot, db, dbSlot)`, `compareToDatabase(...)`: **missing** (no database).
- `dropIntoSlot(facing, slot[, count[, fromSide]]): boolean`: CraftingCard `store([face][, from[, count[, to]]])` (`true, 0` / `false, n` / `false, why`); robotics' `store` moves the hand's.
- `suckFromSlot(facing, slot[, count[, fromSide]]): boolean`: CraftingCard `take([face][, from[, count[, to]]])` / RoboticsDevice `take([face][, slot[, count]])` (how many came, not a boolean).
- `getInventorySize(side)`: **missing** (`contents(face)` lists filled slots only).
- `getSlotStackSize`, `getStackInSlot`, `getAllStacks(side)`: RoboticsDevice `contents([face])` (filled slots; no single-slot query, no iterator).
- `getSlotMaxStackSize`, `compareStacks`, `areStacksEquivalent`, `compareStackToDatabase`, `store(side, slot, db, dbSlot)`: **missing**.
- `getInventoryName(side)`: **missing** (unclear whether `scan`'s `name`/`container` is meant to cover it).
- `getItemInventorySize`, `dropIntoItemInventory`, `suckFromItemInventory`: **missing** (no item-in-item inventories).
- Faces: OC side numbers; modcomp the robot's own face names.
**Deliberate (AGENTS.md / PLAN.md):** faces in the robot's frame; one answer says it all; the crafting card's methods need robotics.

### tank_controller (UpgradeTankController.scala + traits TankInventoryControl, WorldTankAnalytics)
**modcomp:** none
**Missing in modcomp:** fluids (the nearest: `scan`'s `{kind="fluid", name, amount, source}` for a space, not a tank). OC API: getTankLevel, getTankCapacity, getFluidInTank, getTankCount (sided); getTankLevelInSlot, getTankCapacityInSlot, getFluidInTankInSlot, getFluidInInternalTank, drain, fill.

### tank (UpgradeTank.scala + traits TankControl, TankWorldControl)
**modcomp:** none
**Missing in modcomp:** internal tanks (a bucket through `use(face)` is all). OC API: tankCount, selectTank, tankLevel, tankSpace, compareFluidTo, transferFluidTo, compareFluid, drain, fill; signal `tank_changed(tankIndex, amount)`.

### crafting (UpgradeCrafting.scala)
**modcomp:** CraftingCard (device/CraftingCard.java; worked by entity/CraftingBench.java)
**Signals:**
- none in OC; modcomp adds `crafted` (`{name, count}`) and `failed` (`why`: no recipe, no room) through `pull()`.
**API:**
- `craft([count]): number`: CraftingCard `craft([count])`: from its own 3x3 grid (slots 1-9), not the robot's top-left; `true` at once (or `false, why`), the result an event; half a second a craft; into storage, all or nothing.
**Deliberate (AGENTS.md / PLAN.md):** crafting takes time, each card on its own (more cards, more throughput); needs robotics; players craft in its tab; its `/dev` file reads its items, a line written dumps its storage.

### experience (UpgradeExperience.scala)
**modcomp:** none
**Missing in modcomp:** a robot's experience. OC API: level(), consume().

### leash (UpgradeLeash.scala)
**modcomp:** FieldCard (device/FieldCard.java; entity/EnergyField.java), the nearest
**Signals:**
- none in OC; the field's `move` ends in `arrived` or `blocked` (`pull()`).
**API:**
- `leash(side): boolean`: FieldCard `capture(target)` (an id from `scan`, `"self"`, or a point `forward[, right[, up]]`): holds one creature or item in place (it can't act), not led on a leash; answers its name, or `false, why`.
- `unleash()`: FieldCard `release()` (one target).
- modcomp only: `scan([range])`, `move(forward, right, up)`, `get()`.
**Deliberate (AGENTS.md / PLAN.md):** a different design (the user's, 2026-10-24): suspend and move, 16 blocks, positions in the computer's frame.

### navigation (UpgradeNavigation.scala)
**modcomp:** RoboticsDevice's pose
**Signals:**
- none in OC.
**API:**
- `getPosition(): x, y, z`: `pose()` → `{forward, right, up, heading}`: odometry from its origin, in the origin's frame, not from a map's center; anywhere (OC's needs the map's range).
- `getFacing(): number`: `pose().heading` (degrees to the origin's right: 0/90/180/270), not a side number.
- `getRange()`: **missing**. `findWaypoints(range)`: **missing** (no waypoints).
**Deliberate (AGENTS.md / PLAN.md):** deterministic moves from an exact pose; it knows no world position; a GPS card planned.

### sign (UpgradeSign.scala, UpgradeSignInAdapter.scala, UpgradeSignInRotatable.scala)
**modcomp:** none
**Missing in modcomp:** reading and writing signs. OC API: getValue([side]), setValue([side,] value).

### tractor_beam (UpgradeTractorBeam.scala)
**modcomp:** CraftingCard `pickup()`
**API:**
- `suck(): boolean`: `pickup()`: every item within a block and a half into its storage, answering how many; needs a crafting card in a robot (OC's picks up one random item nearby, a boolean).
**Deliberate (AGENTS.md / PLAN.md):** none.

### angel (UpgradeAngel.scala)
**modcomp:** none
**Missing in modcomp:** placing in mid-air (`use(face)` fails "nothing to place against", entity/GolemHands.java). OC API: none (passive).

### trading (UpgradeTrading.scala, Trade.scala)
**modcomp:** none
**Missing in modcomp:** villager trading (`use` on a villager isn't a trade API). OC API: getTrades(); Trade: getMerchantId, getInput, getOutput, isEnabled, trade.

### mfu (UpgradeMF.scala)
**modcomp:** BluetoothCard (device/BluetoothCard.java), the nearest in purpose
**API:**
- none in OC (a remote adapter bound to a block in range). modcomp's links its computer to a board by id (`getLink`, `setLink`, `getBoard`, `get`, `set`), by hand or by programs: boards, not any block; range or dimension checks unclear (not read).
**Deliberate (AGENTS.md / PLAN.md):** devices join a bus by cable, against the computer, or by Bluetooth.

### database (UpgradeDatabase.scala)
**modcomp:** none
**Missing in modcomp:** an item-description store. OC API: get(slot), computeHash(slot), indexOf(hash), clear(slot), copy(from, to[, address]), clone(address).

### barcode_reader (UpgradeBarcodeReader.scala)
**modcomp:** none
**Missing in modcomp:** an analyzer reading a block's nodes into a tablet. OC API: none (the analyze message only).

### data (DataCard.scala)
**modcomp:** none
**Missing in modcomp:** a data card, and no mcos library for it (no hashing, base64, compression, crypto in `lib/`). OC API: getLimit, encode, decode, deflate, inflate, crc32, md5, sha256, encrypt, decrypt, random, generateKeyPair, deserializeKey, ecdh, ecdsa; key: isPublic, keyType, serialize.

### debug (DebugCard.scala)
**modcomp:** OperatorCard (device/OperatorCard.java)
**Signals:**
- `debug_message(source, port, distance, data...)`: **missing** (no card-to-card messages).
**API:**
- `runCommand(command): number`: `command(text)` → `{success, result, output}`, at its block, a command block's permission (gamemaster); `get()` the last output; `set({command})` runs one.
- `changeBuffer(delta)`, `connectToBlock(x, y, z)`, `test()`, `isModLoaded(name)`, `sendToDebugCard(address, ...)`, `sendToClipboard(player, text)`, `getWorlds()`, `getX/getY/getZ()`, `scanContentsAt(x, y, z[, world])`: **missing**.
- World (getDimensionId/Name, getSeed, is/setRaining, is/setThundering, get/setTime, get/setSpawnPoint, playSoundAt, getBlockId, getMetadata, getBlockState, isLoaded, hasTileEntity, get/setTileNBT, getLightOpacity, getLightValue, canSeeSky, setBlock, setBlocks, insert/removeItem, insert/removeFluid), player (get/setGameType, get/setPosition, get/setHealth, getMaxHealth, getLevel, getExperienceTotal, add/removeExperienceLevel, clearInventory, insertItem), scoreboard (teams, objectives, scores) objects: **missing** as methods; vanilla commands through `command` reach many (time, weather, setblock, fill, data, playsound, setworldspawn, item, gamemode, tp, xp, clear, give, list, scoreboard, team) as text, not typed answers.
**Deliberate (AGENTS.md / PLAN.md):** named for Minecraft's operators, creative only; a command block's permission (not `op` or `stop`), each command logged; `/dev/operator0` a line a command, a read its output.

### geolyzer (Geolyzer.scala + trait WorldControl)
**modcomp:** RoboticsDevice `scan` (robots only); WeatherCard for weather
**API:**
- `scan(x, z[, y, w, d, h][, options])`: **missing** (no noisy density scan of an area).
- `analyze(side[, options])`: `scan([face])` (`{kind="block", name, state, hardness, solid, container}`, fluid, entity, air; all six without a face); OC's fields differ (metadata, harvestLevel, harvestTool, color, properties).
- `detect(side)`: `scan(face).kind` (air/block/fluid/entity) for OC's entity/solid/replaceable/liquid/passable/air.
- `store(side, db, dbSlot)`: **missing** (no database).
- `canSeeSky()`, `isSunVisible()`: **missing** (WeatherCard `getWeather()`: clear/rain/snow/thunder only).
**Deliberate (AGENTS.md / PLAN.md):** none found.

### transposer (Transposer.scala + traits WorldInventoryAnalytics, WorldTankAnalytics, InventoryTransfer)
**modcomp:** none
**Missing in modcomp:** a block moving items or fluids between neighbouring containers (only robots move items). OC API: transferItem(src, sink[, count[, srcSlot[, sinkSlot]]]), transferFluid(src, sink[, count[, srcTank]]), and the inventory and tank analytics above.

### hologram (common/tileentity/Hologram.scala)
**modcomp:** none
**Missing in modcomp:** a hologram projector. OC API: clear, get, set, fill, setRaw, copy, getScale, setScale, getTranslation, setTranslation, maxDepth, getPaletteColor, setPaletteColor, setRotation, setRotationSpeed, getDimensions.

### printer (common/tileentity/Printer.scala)
**modcomp:** none (the Photomask, device/Photomask.java, is a chip fabricator with no methods yet; PLAN.md: "The photomask gets a device API")
**Missing in modcomp:** a 3D printer. OC API: reset, set/getLabel, set/getTooltip, set/getLightLevel, setRedstoneEmitter/isRedstoneEmitter, setButtonMode/isButtonMode, setCollidable/isCollidable, addShape, getShapeCount, getMaxShapeCount, commit, status.

### waypoint (common/tileentity/Waypoint.scala)
**modcomp:** none
**Missing in modcomp:** a waypoint block (the robot works from its pose; a GPS card planned). OC API: getLabel, setLabel.

### assembler (common/tileentity/Assembler.scala)
**modcomp:** none
**Missing in modcomp:** a robot assembler (the golem is creative only until the Advanced Robotics Assembly; PLAN.md plans it). OC API: status(), start().

