# Modular Computers: an overview for agents

`CLAUDE.md` imports this file. It describes the code as it is and the decisions behind it, briefly,
so an agent starts with the right picture. It holds no history (git has it) and no plans
(`.agents/PLAN.md` has them). The code and its comments are the truth for names, behavior and values;
this file says what things are for and why. Keep it current: a change that alters what's here
updates it in the same commit.

## What it is

**Modular Computers** is a mod for the latest Minecraft (Java), on Fabric and NeoForge, by payonel
(GitHub), one of the original authors of **OpenComputers** (OC). It's OC's idea built again from
scratch: computers players assemble from parts (case, CPU, RAM, EEPROM, disks, cards, screens,
cables) and program in Lua, to automate their world (redstone, sensors, pistons) and to learn how
computers really work.

- **Built green.** None of OC's code, build, versioning or addon process. OpenOS (the user wrote
  it; MIT) may be ported. OC (`~/code/OpenComputers`, `master-MC1.12` newest) is only for ideas,
  when the user asks how OC did something.
- **Real hardware, real Linux.** Designs abstract how real PCs and Linux work, choosing what to
  mimic or skip: steps may be skipped, never invented. Explain the real mechanism first when
  discussing a design, and say plainly when the code, or the user's idea, drifts from it; recommend
  the real behavior. Convenience over realism is sometimes the user's call.
- **mcos** is the computers' OS: Linux-like, in Lua, on a crafted disk. The user reviews it as a
  Linux user would; OpenOS is a source of ideas, not a model.
- The user has been away from modding for years: explain ecosystem changes when relevant.

## Building and testing

- **Projects:** Java 25 (Gradle provisions Temurin). `common` holds all the mod's code, built
  against the plain game (NeoForge's ModDevGradle in NeoForm mode); `fabric` (Loom) and `neoforge`
  (ModDevGradle) each compile common's sources into their jar. Versions live only in
  `gradle.properties`. Minecraft is unobfuscated (Mojang's names); check Minecraft, Fabric and
  NeoForge names against the jars (`javap`, sources jars in the Gradle cache), not memory.
- **Lua engine:** LungoLua and StyxLua, the user's projects (a Java port of their Lua 5.5 fork, with
  budgets, freezing and persistence), from GitHub Packages (`gpr.user`/`gpr.key` in
  `~/.gradle/gradle.properties`). Never edit them; report needs and bugs to the "lua thread" (another
  agent session; it can't grant permissions). Take the latest builds when told to update. Their bugs
  look like ours: check against stock Lua's semantics.
- **Commands:** `./gradlew build` (compile and unit tests, seconds); `./gradlew testAll` (unit tests,
  server game tests on both loaders, Fabric client game tests on Xvfb against the release jar)
  before claiming anything works. `.agents/TESTING.md` has the details; `scripts/headless.sh` runs a
  client on a virtual display (`:fabric:runClient` plays in `fabric/run`).
- **Tests are the play-testing**, at the lowest layer that can express them: plain-Java unit tests,
  then server game tests, then client game tests (a visible feature gets one, and its screenshots
  get looked at). Machine logic stays plain Java; Minecraft glue stays thin.
- **Tests bring their own programs** (Java `Program`s, or Lua files on the test disk) and never
  assert on what mcos prints; they check files on the disk and whether the machine runs.
- **A test's bus is a case's bus** (`TestBus`, in the tests' `machine` package): parts on the
  motherboard's connectors, each answering to its id too, cabled devices by `id:` name, rebuilt
  through the bus order. `Machine.setDevices` is private and a `Bus` starts empty, so no test can
  build a bus no case builds.
- **Every device type is checked the same way** (`DeviceInterruptsTest`): each card kind and device,
  in a slot, wakes a program waiting on it by its id and by its slot, at the next tick. A new device
  with an interrupt line fails until it's given a stimulus there.
- **What only a real world shows** (a save that won't load, chunk loading): `scripts/devserver.sh`
  runs a dedicated server on a copy of a world, with commands through a fifo and the server thread's
  stack on request. A disk is a folder under the world's `modularcomputers/disks/<id>`: put a script
  there, read what it wrote.
- **Art, sounds and data** (structures) come from Python scripts in
  `scripts/` (Pillow, libsndfile via ctypes); their outputs are committed, never edited by hand.
- **Versioning:** `MAJOR.MINOR.PATCH+MCVERSION` (`.agents/VERSIONING.md`), as Fabric API does.
- **The dev host:** headless Linux, Xvfb with Mesa llvmpipe, `sudo` needs the user's password, no
  Lua interpreter (Lua runs only in the tests).

## Working rules

- **Commits:** on `master` (`git add -A`), autonomously, when an addition is done and `testAll`
  passes, gated on its exit status (never on grepping its log). Never branch, never push. No model
  identifiers in commits or code. End messages with the co-author line.
- **The manual (`docs/`) moves with the mod:** a commit that changes the mod's behavior or
  features (a block, item, device, recipe, mcos program or API) includes the matching update to its
  `docs/` page, as the wiki is what players read. `.agents/DOCS.md` has the rules and page shapes,
  `.agents/DOCS-PAGES.md` the page list; `scripts/docs_check.py` checks pages and links. The Lua in
  pages is tested: `docs/examples/*.lua` run in `McosTest`, and `DocsLuaTest` compiles every block.
- **Words:** "bus" and "device", never "component" (OC's word). **Host**: the Java side.
  **Firmware**: `machine.lua`, layer 0, unbudgeted, unreachable by user code. **User threads**:
  anything budgeted (`user_co` is the root). **Kernel**: mcos's, in user threads. The EEPROM is
  "eeprom", not firmware. "Modal mode" is the user's name for the screen view.
- **Data has one home:** code reads and writes data where it lives (an item's component, a disk's
  folder) through a small source, never a copy synced back. Point out the pattern when seen.
- **Named constants**, each with a comment, for every tunable; no magic numbers.
- **No old-save compatibility:** formats change freely, without shims.
- **Debugging:** run one quick test before a long suite after a risky change
  (`--tests '*McosTest.haltHalts'`, under `timeout`). A test that fails alone but passes with its
  class is a timing bug (a cold JVM's slow board calls make a different, legal state): make that
  state on purpose. Spawns failing at random are usually memory running out. Before shrinking
  memory peaks, compare what stays resident to the minimum. A hang is a thread dump away.
- **Every fix, feature and design change looks for its similar cases** (the user's rule): the
  same scenario in other devices, blocks, programs or states (disconnected, missing, held by
  another board, over capacity, unloaded, off), and makes the change a rule the code enforces for
  all of them, not a one-off for the case reported. Say which cases were found.
- **Subagents** doing file work in parallel get disjoint scopes and no bulk edits.

## Layout

- `common/src/main/java/com/payonel/modularcomputers/`: `block` (block entities, cables:
  `Cabling`, `CardWorld`), `bus` (`Bus`, `BusOrder`, `BusHolds`, `BusPower`, `Device`,
  `InterruptLine`), `device` (cards, disks, terminals), `machine` (`Machine`, `Motherboard`,
  `MotherboardApi`, CPUs, EEPROM), `runtime` (`LuaRunner`, `Board`, `DeviceNames`, device methods),
  `display` (`TextBuffer`, `VtDecoder`), `console` (`ConsoleSplitter`), `network` (packets), `menu`,
  `item`, `power`, `platform` (`Platform`: what a loader must do).
- `common/src/main/resources/data/modularcomputers/lua/`: `cpu/machine.lua` (the firmware),
  `eeprom/` (the boot EEPROM), `mcos/` (the OS: `init.lua` the kernel, `lib/modules/` its modules,
  `lib/` libraries, `bin/` programs, `etc/` settings, `usr/share/man/` man pages).
- `fabric/`, `neoforge/`: each loader's entry point and test mod; `scripts/`: art, sound, host tools.
- `docs/`: the manual, a Jekyll site GitHub Pages builds from `master` (the wiki: hardware, devices,
  mcos, tutorials). `docs/programs/` is generated from the man pages by `scripts/docs_programs.py`
  and never edited by hand. `.agents/`: notes for agents and contributors (the plan, testing,
  versioning, how to write the manual), kept out of the published site.

## Decisions, and why

### Two loaders, one mod

Fabric and NeoForge (not MinecraftForge: the mods that kept up moved to NeoForge). Why NeoForge at
all: the mods worth meeting (Ender IO's conduits, More Red's and Project Red's bundled cables) are
NeoForge-only. Code is written once against the plain game; each loader only starts it and adapts
what the game can't do:
- **Registration** is the game's `Registry.register`: Fabric allows it any time; NeoForge's entry
  point runs the common init in its first `RegisterEvent`. No `DeferredRegister` layer.
- **Packets** are the game's (`ClientboundCustomPayloadPacket`); only registering them is the
  loader's, from one list. A packet goes only to a client that takes it.
- **World saves** reach the mod through a mixin (`MinecraftServerSaves`) on both: NeoForge has no
  event before a save.
- **Server game tests** are the mod's own `@GameTest`, registered by each loader's test mod, so all
  run on both. **Client tests run on Fabric only**: NeoForge has no client test harness (the one
  port of Fabric's found was unlicensed and abandoned).

### Faces

Every device that takes a face takes it through `Face` (the user's rule for every device, now and
to come): the world's names (`north`, `south`, `east`, `west`, `up`, `down`: Minecraft's own) or
names relative to the block's front (`front`, `back`, `left`, `right`, left and right as someone
facing its front sees them: the user's, as a desktop case's left side panel is on your left), so a
program written for a block works however it's turned, and configurations compose. Every block that holds devices has a front, with its own
look, facing the player who placed it (a case's, a hub's card bays), and tells its devices where it
is (`Card.frontFrom`); a robotic piston's front is opposite its cable port. A device with no block
to say (a test's) takes only the world's names. Devices answer with the world's names. Faces are
strings, only (the user's call: no constants table, as OC's `sides` was).

### The look

Blocks, items and their GUIs share one aesthetic, and new ones keep to it: a GUI shows the device's
own insides as real hardware would look (the case view is its motherboard: a green board, gold
traces, sockets and latches, silkscreen labels in the game's font), its controls are that
hardware's parts (switches, LEDs, meters), and every texture comes from the scripts in
`scripts/art/` with their shared palette. Every item has a hint (`Hinted`): a summary in its
tooltip, and with Shift held, details above the crafting grid of how it works and what crafting
it does (a disk used in a recipe loses its files).

### Items

Items stack to 64, except what holds data (EEPROMs, disks). A part that's been in a machine carries
its id and stacks only with identical ones; crafting it alone clears it (a new device). A device's label is on its item (`ModComponents.LABEL`, shown in its details), set by programs
(`setLabel`, mcos's `label`), not its anvil name (that's only the item's name); crafting it alone
clears it with its id. Every disk
tier comes with mcos (its files are made when the disk is first installed); crafted alone it's
blank, and its files leave the world save when the player takes it.

### The machine is a real PC, abstracted

- A CPU only runs what the EEPROM holds (a fixed slot, like a reset vector) and aborts (ABRT, a
  panic) when it can't. The motherboard owns the bus (cases change; the board's firmware doesn't).
- **Devices have several names**, as on a real system: a slot path (`sata:0`, `exp:1`), an id
  (`id:<uuid>`, however connected), and virtual names (`console`, UEFI's ConSplitter). The bus keys
  a device by its address (a part's slot path, a cabled device's id); its other names are aliases.
  mcos names every device by its id (stable when a part moves; an open file holds an id, so a stale
  name never reaches another device).
- **Names meet in one place** (`DeviceNames`): everything the host keeps for a program about a
  device (a wait's sources, streams it reads, its rings) is kept by the program's name. Interrupts
  are the one thing raised at bus addresses, and each is turned into the program's name before
  anything compares it. Why: comparing names as strings in many places once let no slotted card
  wake a computer waiting on its id.
- **Bus order** is UEFI-like: the case's connectors fixed, external devices in a saved,
  player-ordered list by id (so late-loading chunks keep their place).
- **Devices connect by cable, port to port, or by Bluetooth:** a block right against a computer
  (neither's front between them) is on its bus without a cable, as a drive in a dock is (the user's
  call). Screens and hubs carry the connection on; other devices end it.
- **A device behind another is on the bus only while its host is** (a Disk Station's disks behind
  the station, as a dock's drives): `Cabled.host` names the host, and `BusOrder` keeps the device
  off, shown offline, whenever its host is disconnected, missing or another board's, and off when
  the bus has no room for its host. One rule in the bus order, for every host to come.
- **A device is on one bus at a time** (`BusHolds`): whichever board has it first holds it; holds
  are worked out afresh as boards load, never saved (long cables and unloaded chunks would desync
  them).
- **The board's real-time clock is the world's:** game time (`Chassis.time`), the one disks stamp
  files with. mcos's dates are the world's calendar (days from 1, the game's hours, the epoch the
  first sunrise at 06:00), not the Gregorian one: the world has no months.
- **Power is lazy:** devices and batteries settle what they owe when someone looks (every 20 ticks,
  or at an event), from game time; nothing ticks per device (big builds).
- **Power is named in one place** (`PowerUnits.rate`, `amount`): a unit a tick shows as a watt,
  stored units as watt-hours. Every draw, load and charge shown goes through them (the user may
  rename the unit).
- **A device takes data only while a program runs on the board holding it** (keys typed at an off
  computer must not appear at boot).
- **Memory** is the RAM installed, above a measured baseline, so a stick means the same to a program
  whatever the firmware costs.
- **Blocks mark their chunk changed with `ChunkMarks.changed`**, never `setChanged()` or
  `level.blockEntityChanged()`, from anything a chunk's load can reach (`setLevel`,
  `loadAdditional`, a device's `onChange`): the game's calls wait for the chunk being loaded, and the
  server thread deadlocks on itself. Minecraft writes only chunks marked changed.

### The Lua CPU: three layers

The host (a VM runner on the computer's own thread), the firmware (sandbox, budgets, freezing,
device data), and user code, as a real CPU runs firmware under an OS.
- **Board calls run on the server thread** (devices live there), each computer limited by server
  *time* a tick (1 ms), not call counts: fair to the server.
- **Syscalls freeze, they don't yield:** a budget callback freezes the whole user chain wherever it
  is, so no user coroutine sees a yield and `pcall` can't catch it. Traps (`board.trap`) are the
  syscall instruction; a spent slice stops the same way.
- **Interrupts are level-triggered.** Device data is pushed into rings by credit (DMA-like), so
  input needs no call; news "goes cold" at once, so a key's latency is one round trip. Only the
  firmware caches device data; tier 1 RAM has no cache (every read is a call, slow on purpose).
- **Flow control is a handshake, never a silent drop:** a device keeps its data until there's room;
  what a full device does is its own business.
- **A device reports every change to its state, from wherever it came:** an event in its own terms
  (a redstone face's level and whether the change came in, was its own, or another card's; a
  motion; a sound heard; a disk's change by path, or byte ranges raw), raising its interrupt, taken by a method of
  its own. The kernel's drivers turn events into what programs see (poll, `/dev` lines, inotify), so
  no kernel has to watch for changes. A disk's are `changes()`, not `pull()`: its `/dev` file is its
  data.
- **Device failures are soft:** no device can stop the machine; only the program's own error or the
  board's supervision (CPU gone, power gone) ends a run. External devices are hot-plugged: one
  that isn't there isn't on the bus, and calls to it fail at once.
- **Saving a running computer** (Styx) happens at a safe point with no call in flight, so a save
  never splits a program from its devices; no timeouts, no kills (a persist that never ends is a bug).

### Sensors and the Bluetooth card

- **The audio sensor hears what a player at its block would** (the game's range for each sound):
  the sound the game plays, by its id (`minecraft:entity.pig.ambient`), and where, relative to the
  block; nothing else (no category). Just what the game plays, never a stand-in (the user's call,
  over subtitles: a skeleton's step is `entity.skeleton.step`, and the stone under it is silent). It hears every sound the server sends players
  (`ServerLevelSounds`, a mixin on `playSeededSound`) and the sounds players make of level events
  (`LevelSounds`, the client's table, kept by hand; a block broken plays its own break sound).
- **A Bluetooth card in a computer is a device:** its link (the board it links to) and its board
  are read and set by programs as by hand, on the card itself (its item).

### The Sound Card

Phase 1 is a real card's DAC (user design; phase 2, MIDI notes, is in the plan): programs write
samples, and it plays them at its format's rate (8000 to 44100 a second; unsigned 8-bit, signed
16-bit, or IMA ADPCM, which it decodes itself, as a Sound Blaster did: no codec dependency). Its
buffer holds a second; a write takes what fits (the handshake), and its interrupt says when there's
room, or when what's queued has played out (`sync`). It plays by game time: each tick its block
sends what's due, still encoded, to the players near it (`SoundCardPayload`), and their games play
it through a sound whose samples come from a stream (`SoundCardSpeakers`: NeoForge's and Fabric
API's stream hooks, one class answering both). Stereo is two mono speakers half a block either side
of the block, its left and right as seen facing its front: OpenAL doesn't place a stereo sound in
the world. In mcos it's OSS's `/dev/dsp` (`dsp1` the next): an open starts at OSS's defaults (8000,
u8, mono), so `cat file.raw > /dev/dsp` plays, and OSS's ioctls set the format; `aplay` plays WAV
files.

### The operator card

Creative only (OC's debug card, named for Minecraft's operators: Lua has its own debug). It runs
server commands as a command block does, at its block, with a command block's permission
(gamemaster: `time`, `weather`, `give`..., not `op` or `stop`), and returns how it went and what it
said; each command is logged on the server. In mcos, `/dev/operator0`: a line written runs one, a
read gives what the last one said.

### The internet card

HTTP and HTTPS to the real internet (OC's internet card; the user's recipe: a cobweb and an eye of
ender). The card speaks HTTP itself (`HttpExchange`, its own small client), since TLS is beyond a
program's Lua; in mcos a request is a file (`sys.http`, `lib/modules/http.lua`: reads give the body,
`HTTP_RESPONSE` the status and headers), and `wget` and `curl` are built on it. **It's a program
running on the server's network, so it's kept to the public internet** (`InternetPolicy`):
loopback, private, link-local (cloud metadata), CGNAT, multicast and reserved ranges are refused, in
IPv4 and IPv6 (embedded IPv4 judged as itself); every address a host resolves to is judged, and the
socket opens to the address judged (no second lookup: no DNS rebinding); redirects are the
program's, so each hop is judged again; a request can't set the headers that frame it (Host,
Content-Length, Connection...), carry line breaks, or send credentials in its URL; no proxies,
cookies or server details are sent. Admins can turn it off and allow or deny hosts and ranges
(`internet_*` settings). Its events wait in the card with a handshake (a full card pauses the
download), and the kernel caps what it holds unread.

### Network cards

OC's broadcast and peer-to-peer messages in a real NIC's shape. A wired card sends frames over the
cables, a WLAN card through the air, to a card's address (its id, a MAC) or to every card it
reaches; nothing routes between the two networks. WLAN cards come in OC's tiers and ranges (tier 1
16 blocks, tier 2 400, their recipes after OC's: redstone torches, an ender pearl); a frame reaches
as far as its sender's strength (OC's `setStrength`, mcos's `SIOCSIWTXPOW`), and a send costs more
the farther it reaches (OC's wirelessCostPerRange); any tier hears any other.
- **Over the bus's own cables:** a cable is a shared fabric, not one host's private bus. Two cases
  on one cable are two hosts on it (two Macs on one Thunderbolt cable, SAN hosts on a Fibre Channel
  fabric); the bus devices on it still belong to one board at a time, and frames go between the
  cards. A case ends the walk (a host isn't a switch); hubs and screens carry it on.
- **Ports aren't the card's** (OC's modem opened them): they're the kernel's, in the frame's data,
  so any kernel can put its own protocol on a card.
- **A card never drops a frame:** a unicast frame is taken by the other card or refused at once
  with why (`busy`: its queue is full or no program takes data there; `unreachable`); the card never
  retries (software's job, as lossless Ethernet's PAUSE leaves it). A broadcast is best effort (how
  many took it). "Taken" is the card's, never the program's (end to end: only programs can say).
- **Wake-on-LAN** is a device's wake line (PCI's PME#, `Device.wakeLine`), wired whether the
  computer is on or off: a card with a wake message listens while its computer is off, and a frame
  holding it presses the power button. OC's wake message, not a real magic packet; matched anywhere
  in the data, as a pattern-matching NIC does.

### Terminals and the console

- **Terminals are VT102/xterm devices** (a screen and keyboard as one): programs only write escape
  sequences. Their modes (cursor, alternate screen, keyboard reporting) are the terminal's; the
  tty's raw mode is termios, a separate thing. Scrollback lives in the terminal, as xterm keeps it.
- **Sizes:** each tier's largest is OC's screen size (50×16, 80×25, 160×50), and it powers on at it
  (the user's pick, knowing 160×50 is small; no multiblock screens). A program resizes with
  xterm's sequence (`ESC[8;rows;cols t`; `18t`/`19t` report size and largest; `resize` is xterm's
  tool); nothing is wrapped again. The console's group is one size, the largest every member can
  show (UEFI's ConSplitter's common mode). Tests that look at pixels size the console 50×16 first
  (`TestPrograms.READABLE`): sub-pixel text is flaky to count.
- **Each tier's glass and white** (`ColorDepth.glass`, `white`): a real screen is never pure black
  or white; the defaults (pure black and white, as a theme recolors them) show as the tier's (tier
  2 a CRT's cool near black and white, tier 3 a charcoal and a warm white), every other color as
  it is; the block textures' glass matches.
- **Attributes as each tier's model showed them:** bold is the cell's (`TextBuffer.BOLD`, in the
  foreground's top byte, so it goes wherever the color goes) and drawn in the bold font; tier 1, a
  VT102 with one phosphor, shows intensities instead of colors (bold full, normal a little less,
  reverse video a lit cell with dark text: `ColorDepth.glow`).
- **Tier 3 draws sixel graphics** (a VT340's: `DCS q`; its Device Attributes say `;4`; lower tiers
  skip them). A picture lives in the cells it covers, a 6×12 tile of pixels each
  (`TextBuffer.TILE_*`): it scrolls, moves and is saved with them, text over a cell replaces its
  tile, so a screen holds at most a screen of pixels. Tiles go to players run-length encoded
  (`Tiles`), and their games draw them as one texture a screen (`PixelLayers`), rebuilt only when
  pictures change. DECSDM (`?80`) keeps a picture from scrolling (the console's repaint uses it).
  `/usr/bin/vtdemo.lua` shows every tier's colors and a picture.
- **BEL rings the terminal's bell** at its block (`TerminalBell`, a sound from `scripts/art/bell.py`);
  bells closer than 200 ms ring once (xterm's bellSuppressTime: `\a\a\a` is one beep).
- **The server's decoder is the only one:** only changed rows go to players (the user rejected a
  client-side decoder: a client-only bug could show what the server never decoded).

### Disks

Disks in hfs mode serve files themselves (like 9P): real host files in the world save, so players
can look at them. A handle never holds a host file open: writes buffer and commit atomically at
close (power lost before close loses them). A file's time is the world's (game time, 50 ms a tick), never the host's,
set as it changes and kept in the host file's own modification time (its one home); an image copied
onto a disk gets the time it was copied. Paths never leave the disk's folder. Symbolic links are
CIFS's Minshall+French `XSym` files, never host links (one could point out of the save); the disk
never follows them, the OS's VFS does. A raw disk is read and written at any byte offset (`read(offset, n)`,
`write(offset, data)`), the user's call over real disks' sectors (sectors don't make programs
better); an hfs disk refuses those, its filesystem owning it. A raw image ends where its data does: past
it are zeros. A disk's format lives on its item (`ModComponents.DISK_FORMAT`, read and written
through `Disk.formatIn`), its one home, so its tooltip can say it. mcos's files are copied onto a disk once, when it's first
made: a world's disks keep their old mcos; tests copy afresh.

### The Disk Station

A dock for disks (`DiskStationBlockEntity`, its device `DiskStation`), to format, wipe, clone and
install them without crafting a disk alone, as a drive duplicator does. Its target disk is the one
it works on; its source, optional, is only read (a forensic dock's write blocker), for cloning. Both
disks, and the station itself, are on the bus of the computers wired to it. Every action is a method,
a line written to its `/dev` file and a GUI switch alike: format (hfs), wipe (raw), clone, install
mcos (formatted, then the mod's latest), update mcos (the mod's files written over, `/etc` too: an
update is how a player fixes an install). An action is paid from the bus's power while a running
computer supplies it, else from its battery. Disks go in and come out by hand, in its GUI; a switch is dead until the disks it needs are in. A
clone takes the source's format, refused when it doesn't fit (a raw disk only onto a tier as big). Its
recipe uses a disk up, and that disk's files go (`DiskUsingRecipe`, as crafting a disk alone does).

### mcos

- **mcos follows Linux.** The kernel owns every resource and runs only on its own coroutine;
  programs reach it only by syscalls (`sys`), never `board`. Every process starts in `/lib/ld.lua`
  (ld.so and crt1); `/bin/lua` is just Lua's interpreter. Commands are programs in `/bin`; the
  shell holds only shell behavior; line editing is a library (`/lib/readline.lua`). Behavior copies
  Linux/POSIX/GNU, saying where it can't. From OpenOS's experience: no lazy loading of libraries
  (splitting cost speed, complexity and memory) and no legacy compatibility.
- **The shell and line editing behave as bash's** where shells differ (the user's pick, over zsh's
  and fish's): keys typed ahead are echoed by the tty where the cursor is, then the prompt and the
  line follow on that row (`cat/ $ cat`, not a fresh row); readline's keys are GNU readline's
  defaults with bash's usual bindings (Ctrl+Left/Right move a word). readline asks the terminal
  where its cursor is (`ESC[6n`) before drawing, so it draws from where the prompt really is.
- **mcos's interfaces are Linux-shaped; the devices' are for anyone:** devices speak tables
  (methods, events, reports), and mcos turns them into what Linux gives: text, never Lua syntax (the
  kernel loads no user-space library). A device whose data is a table has a driver
  (`lib/modules/drivers/<type>.lua`, found by its type) making it text: a redstone card's file is a
  line a face (`north 7 7`), a line written takes effect at once (a sysfs attribute's write); a
  device without one (the EEPROM) takes everything written at close, all or nothing.
- **`/dev` holds only data,** flat: `console`, `tty`, `null`, `zero`, and a node for each device with
  `get` or `set` (the EEPROM too: convenient, the user's call). Reading gives `get`'s answer; what's
  written goes to `set` at close, and the device decides what `set` means. A disk's node
  (`storage0`) is its bytes, as a block device's: read, written and sought by byte offset (its raw
  methods); an hfs disk's refuses (`EMEDIUMTYPE`, the user's call: Linux would let a mounted disk's
  bytes be read). A device with events and
  no `get` (a sensor) has a read-only file, an event a line, and a read with none waiting blocks. A
  NIC has no `/dev` file (its method is `receive`): the network is read through sockets.
- **`sys.poll(fds, seconds)`** is poll(2) (`IN`, `PRI`, `OUT`; `HUP`, `ERR` unasked), in `sys` (mcos's
  POSIX layer), since Lua's `io` is C's stdio; `sys.fileno` gives an io file's descriptor. A
  redstone file is always readable and reports a change as `PRI` (a sysfs GPIO's way).
- **inotify** (`sys.inotify()`, `sys.watch`) gives Linux's notices as lines (`MODIFY /log`,
  `MOVED_FROM`/`MOVED_TO`, `OVERFLOW`), from the disks' reports turned into paths by their mounts;
  it watches paths, not inodes, and a change on a disk's host isn't seen.
- **Drivers read a device's stream with `k.take`**, which listens for the device when nothing's
  there, so a process blocked on a device is woken when it comes; the idle wait hears exactly the
  devices blocked reads found empty. No driver asks for listening itself (`k.listen` is for a device
  nothing reads now: the network's open connections).
- **The kernel's log** (`k.log`, `sys.klog`; `dmesg`, after OpenOS's: colors, columns, `-w`): devices
  joining and leaving, each event it takes from a device (`k.take`), mounts, crashes, `printk`; the
  last 200, timed by the world's clock (one board call at its first entry, then the machine's tick).
- **A device type costs no memory until used:** devfs looks for a type's driver the first time one
  of its files is opened; nothing in the kernel lists types. **A process's syscall wrappers are made
  at its first use of each** (a metatable on `sys`), so a new syscall costs nothing at boot.
- **The kernel caches the bus list, eventually correct** (as udev after a hot-plug): read again on
  the bus's interrupt and when a call fails because the device is gone. So no `/dev` lookup makes a
  board call (a thread hop, about 50 traps). Not `board` in user space: it would save only the
  traps and lose the kernel's ownership (a fast path would grant one device to one process, as VFIO).
- **Services:** the kernel runs `/etc/rc` with sh before the console's shell (inittab's sysinit,
  BSD's `/etc/rc`): it mounts `/run` and runs `rc boot`. A service is a Lua script in
  `/etc/rc.d/<name>` (as sysvinit's are in the system's language; sh has no `if`), OpenRC's shape:
  settings (`command`, `command_args`) from which `rc` gives start/stop/status, and functions that
  replace those or add actions. `/etc/rc.conf` (`name_enable=YES`) says what starts at boot. `rc`
  starts one with `setsid` (no controlling tty, no ^C). The kernel stays init (it respawns the
  console's shell): a user-space PID 1 would stay resident for nothing.
- **Login shells** (`sh -l`: the console's, rshd's logins) run `/etc/profile`, go to `$HOME` (as
  login does), then an interactive
  shell the file `$ENV` names (POSIX's), as shell commands in the shell: mcos's profile sets
  `HOME=/home`, `ENV=$HOME/.shrc` and a few aliases. `/home` is the user's: an update of mcos never
  writes over it (`LuaScripts.HOME`).
- **ptys** (`sys.openpty`): a tty whose terminal is a program, its master end, through the same line
  discipline as a keyboard's, output bounded (a writer waits), so a remote shell only relays and the
  kernel does the tty work. `spawn`'s `setsid` gives the child its stdin's tty as its controlling
  tty when free (login_tty's step: mcos has no fork); closing the master hangs up (SIGHUP, then EOF
  and EIO). The window size is the pty's (`TIOCSWINSZ`, which sends SIGWINCH, ignored by default).
- **Sockets** are UDP and TCP over the network cards (`lib/modules/net.lua`; a frame's data starts
  with IP's protocol number), with Linux's names for interfaces (eth0, wlan0) and ioctls
  (`SIOCGIFCONF`, ethtool's wake) and no commands for them (`ifconfig`, `ethtool` have no purpose
  here; an address is in `lsdev -i`). No IP: an address is a card's.
  **UDP has no remote dependency:** a datagram's answer is the card's alone; the other kernel never
  answers one (no ICMP), and drops what has no socket or no room, as UDP does. A full socket never
  holds frames back in the card (the sender's answer would hang on a program's pace).
  **TCP is TCP's shape, kept small:** sequence numbers and acks, a receive window, a segment no card
  took tried again in a tenth of a second, one taken and unacknowledged in a second (go back N),
  reset after 8 seconds without progress (a SYN that found no card for 3: EHOSTUNREACH, ARP's time),
  SYN/FIN/RST; no congestion control or TIME_WAIT. Its timers run from kernel hooks (`k.timer`,
  `k.service`, `k.raised`), so the kernel knows nothing of TCP.
- **rsh, rshd, rcp:** the user's psh done again, named for Berkeley's r-commands because it has what
  they had: no passwords, no encryption. `rshd` is a service, off until enabled; each connection
  runs `rshd -s` with the connection as stdin (inetd's way): a login on a pty, or a command on pipes.
  One TCP connection, framed both ways (`lib/rsh.lua`), so output, errors and the exit status stay
  apart (BSD's used a second connection for stderr and never sent the status). A host is its name
  (`hostname`: set in `/etc/hostname`; unset, the first card's address), a card's id, or a unique
  prefix, found by a UDP broadcast probe on port 514 that rshd answers with its name (asked once:
  the networks don't lose frames, and asking before rshd is up is the asker's mistake, the user's
  call; no retries to hide it). `rcp` is rsh
  running `cat` there (and `mkdir -p` and `find` for `-r`), the host found once for the copy
  (`rsh.resolve`).

## Easy to miss: a change that needs another

- **Mod code goes in `common`**, unless it can only be written against a loader: then behind
  `Platform` (implemented in `FabricPlatform`, `NeoForgePlatform` and the tests' `TestPlatform`) or
  in a loader's entry point calling a common hook. `common` can't see a loader's API.
- **A new packet** goes in `ModNetwork`'s lists (a client handler in `ModNetworkClient`); **a new
  game test class** in `GameTests.CLASSES`. Nothing else registers them.
- **A new cabled block's entity** holds `CablePorts`, and `Cabling` learns its ports and kind.
- **A new device with an interrupt line** gets a stimulus in `DeviceInterruptsTest`; **a device whose
  `get`/`set` take tables** gets a driver (`lib/modules/drivers/<type>.lua`).
- **A new block that holds devices** gets: `Cabling` (its ports, and its kind for the walk), a branch
  in the case's `scanCables` (its devices on the bus; those behind its own device name it as their
  host), a loot table keeping its contents, the pickaxe
  tag, its language entries, and its art from a script.
- **Work for the server thread is always queued** (`MinecraftServer.schedule`), never handed to
  `execute`: once the server is stopping, `execute` runs the task on the calling thread (a CPU's),
  which then touches the world off its thread (a stopping server deadlocked on it).
- **A test opens a menu or sends as the mod does** (`ModMenus.open`, `ModNetwork.send`,
  `GameTests.serverPlayer`): the loaders need their part.
- **A change to an mcos program** (`lua/mcos/bin/<name>`) updates its man page
  (`lua/mcos/usr/share/man/man1/<name>.1`); a new program gets one (options, behavior, exit
  statuses, NOTES where it differs from GNU), using only the macros `/bin/man` supports. A test
  renders every page, and `scripts/docs_programs.py` regenerates its page in the manual
  (`docs/programs/`). **A program's header comment is its spec:** keep it true.
- **Data files under `lua/` that aren't Lua** go under a `share/` folder, mcos's `/home` or its `/etc` (settings,
  sh scripts; `/etc/rc.d`'s services are Lua): `LuaScriptsTest` compiles every other file as Lua.
- **Textures and sounds** change through their script in `scripts/`, then regenerate.
- **A decision** goes in this file when it's made; **an idea not built yet** in `.agents/PLAN.md`.

## Pitfalls

- An hfs file shows its writes only at close: watch a file a program keeps open on `/tmp` (tmpfs).
- Typing more than 256 keys at once in a test drops the rest (a terminal's key queue): split lines.
- Lua 5.5 for-loop variables are constants (a compile error when the script loads: `LuaScriptsTest`
  compiles every script). A `]]` ends a `[[...]]` string: use `[=[...]=]`.
- mcos's `io` is C's stdio: `file:read(n)` is `fread`, which waits until it has all n bytes or the
  end. A program passing a stream on (a device's events, a terminal) reads the descriptor
  (`sys.read(file.fd, n)`: what's there now, as `read(2)`), as `cat` does.
- Lua inside a Java text block is unescaped twice (Java's, then Lua's): use long strings
  (`[[\d]]`) for patterns, and capture a test program's stderr (`2> /err`) to see why it failed.
- Mouse buttons are SDL-numbered (left 1, right 3): use the constants. SDL sends typed characters
  only while text input is on: a screen that takes typing turns it on.
- `.gitignore` patterns without a leading `/` match at any depth: anchor root-only rules.
- An empty dedicated server stops ticking after a minute unless `pause-when-empty-seconds=-1`
  (`scripts/devserver.sh` sets it).
