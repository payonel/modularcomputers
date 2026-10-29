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
- **Commands:** `./gradlew :common:test --tests '*SomeTest'` while iterating (seconds);
  `./gradlew build` (compile and unit tests, about 20 s: they use the whole host, a JVM a core, so
  tests are written for a loaded host: `.agents/TESTING.md`); `./gradlew testAll` (unit tests,
  server game tests on both loaders, Fabric client game tests on Xvfb against the release jar)
  before claiming anything works. `.agents/TESTING.md` has the details; `scripts/headless.sh` runs a
  client on a virtual display (`:fabric:runClient` plays in `fabric/run`).
- **Tests are the play-testing**, at the lowest layer that can express them: plain-Java unit tests,
  then server game tests, then client game tests (a visible feature gets one, and its screenshots
  get looked at). Machine logic stays plain Java; Minecraft glue stays thin.
- **The manual's programs are tested:** each `docs/examples/*.lua` runs in a `*ExamplesTest` (one
  class a group of devices, on `McosHarness`: a computer booting mcos, as `McosTest` uses).
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
  passes, gated on its exit status (never on grepping its log); a change only to what no test
  reads (AGENTS.md, `.agents/`) needs no tests (the user's). Never branch. No model
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
  folder) through a small source, never a copy synced back. Point out the pattern when seen. Slots
  kept on an item are an `ItemContents` (a crafting card's, a held golem's parts and hands).
- **Named constants**, each with a comment, for every tunable; no magic numbers.
- **No old-save compatibility:** formats change freely, without shims.
- **Debugging:** run one quick test before a long suite after a risky change
  (`--tests '*McosTest.haltPowersOff'`, under `timeout`). A test that fails alone but passes with its
  class is a timing bug (a cold JVM's slow board calls make a different, legal state): make that
  state on purpose. Spawns failing at random are usually memory running out. Before shrinking
  memory peaks, compare what stays resident to the minimum. A hang is a thread dump away.
- **Every fix, feature and design change looks for its similar cases** (the user's rule): the
  same scenario in other devices, blocks, programs or states (disconnected, missing, held by
  another board, over capacity, unloaded, off), and makes the change a rule the code enforces for
  all of them, not a one-off for the case reported. Say which cases were found.
- **A mistake leaves a lesson and a test** (the user's rule): a bug fixed gets a test that fails
  without the fix (run it against the old code once to see it fail), and what made the mistake easy
  goes in this file, as a rule or a pitfall, so the next change doesn't make it again.
- **Subagents** doing file work in parallel get disjoint scopes and no bulk edits, and each its own
  test class (they share one Gradle build folder: a run's results may be another's; run your own
  tests again once they're done). A subagent's prompt is written before it's launched.

## Layout

- `common/src/main/java/com/payonel/modularcomputers/`: `board` (`BoardAssembly`, the populated
  board: parts, devices, power, saving, whatever holds it; `BoardHost`), `block` (block entities, cables:
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
  loader's, from one list. A packet goes only to a client that takes it. One about a terminal or a
  computer names it by `HostRef` (a block's position or an entity's id), found on each side and
  checked for reach on the server; views find it as a `ViewHost` (a screen view and a parts view)
  or a `BoardHolder` (its board and power light), so a golem's screen works as a block's.
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
it does (a disk used in a recipe loses its files). Every view of a computer, a screen or a card
holder has one button column (`ComputerButtons`, the user's: the same buttons in the same places in
every view, an icon a view that never changes, the open one ringed in yellow): power, the screen
view, the parts view, a robot's hands, the cards' tabs; a view a block lacks is dimmed, not removed.

### Items

Items stack to 64, except what holds data (EEPROMs, disks). A part that's been in a machine carries
its id and stacks only with identical ones; crafting it alone clears it (a new device). A device's label is on its item (`ModComponents.LABEL`, shown in its details), set by programs
(`setLabel`, mcos's `label`), not its anvil name (that's only the item's name); crafting it alone
clears it with its id. Every disk
tier comes with mcos (its files are made when the disk is first installed); crafted alone it's
blank, and its files leave the world save when the player takes it.

### The machine is a real PC, abstracted

- **A board assembly is the computer, whatever holds it** (`BoardAssembly`, a PCBA: the plain-Java
  `Motherboard` with its parts in, their devices, its power buffer and batteries, its saved program):
  a case's block entity holds one, and so will a golem; a `BoardHost` says only where it is, which
  way it faces, and how its holder is saved and shown. Which slots it has is its `BoardLayout`
  (`CaseTier`'s). One board, so a fix to it is a fix for every holder.
- A CPU only runs what the EEPROM holds (a fixed slot, like a reset vector). The motherboard owns
  the bus (cases change; the board's firmware doesn't).
- **No halted state; aborts** (user design, 2026-10-26): the only clean ways out are the board's
  `poweroff` and `reboot` (and the power button); anything else aborts: off, its power light
  blinking amber until the next power-on (`Machine.isAborted`). Aborts are a program leaving its
  EEPROM (returning, or an error), nothing to run at power-on, its power running out, its CPU or RAM
  changing under it, or its program not saved with the world (`Shutdown.Cause`); disks, cards, the
  EEPROM and batteries swap while it runs. Why is the board's record (`Shutdown`, a chipset's
  power-failure flags, saved with it), read at the next boot (`board.lastShutdown()`; mcos's
  `/sys/firmware/last_shutdown` and its log), and by the planned scanner without powering it on.
  mcos's `halt` powers off. **Restore on AC power loss** is the EEPROM's NVRAM (it goes with the
  chip, as a BIOS setting goes with its board's CMOS): a power-lost abort powers itself back on when
  power returns. Tests' programs park (`Program.park`: a wait nothing raises) instead of halting.
- **Boot order is the EEPROM's NVRAM, not the bus's** (user design, 2026-10-25): a case's sockets are
  fixed connectors, as a motherboard's ports, and the EEPROM keeps UEFI's boot variables on its item
  (`ModComponents.NVRAM`, `BootVariables`): entries (a disk by its id, a file, a label), BootOrder,
  BootNext. A closed set of typed values, checked by the chip at every write, never a free store
  (UEFI's let bad writes brick laptops); an entry is checked for its form, not for its disk being
  there (UEFI keeps an unplugged stick's). The factory bootloader is a boot manager: BootNext once
  (cleared first), BootOrder, skipping absent disks, then the bus-order scan (UEFI's fallback). The
  parts view marks the disk it boots, read from the EEPROM item in the slot, so the mark is the
  chip's. In mcos: `efibootmgr` writes them (the chip's methods, through `/dev/eeprom`), and
  `/sys/firmware/efi/efivars` reads them, text a file each, plain names (no vendor GUIDs).
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
- **A part is one thing in one place, by its id** (`PartHolds`; the user's): creative can copy an
  item with all it holds (Ctrl and pick block, the creative inventory), and a copy is the same part:
  placing an item that carries ids moves it into the world (used up in creative too), a copy of one
  in the world can't be placed, and a copied part in another computer waits off its bus until the
  one in the world goes. Never a new id for a copy (the user's: OpenComputers did, and lost which
  was which).
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
- **Device methods never block** (a real device's registers don't): a method answers at once with
  what the device has (a terminal's `read` with no keys: nil), and a slow action answers once it's
  started, its outcome an event later; a buffered device given a command while one runs refuses
  it (`busy`) rather than wait (see buffered devices, under mcos). `board.waitForInterrupt` is the
  only wait; mcos's blocking reads and commands are the kernel waiting for a process.
- **Device failures are soft:** no device can stop the machine; only the program's own error or the
  board's supervision (CPU gone, power gone) ends a run. External devices are hot-plugged: one
  that isn't there isn't on the bus, and calls to it fail at once.
- **Saving a running computer** (Styx) happens at a safe point with no call in flight, so a save
  never splits a program from its devices; no timeouts, no kills (a persist that never ends is a bug).

### Sensors and the Bluetooth card

- **The audio sensor hears what a player at its block would** (the game's range for each sound):
  the sound the game plays, by its id (`minecraft:entity.pig.ambient`), and where; nothing else
  (no category). Just what the game plays, never a stand-in (the user's call,
  over subtitles: a skeleton's step is `entity.skeleton.step`, and the stone under it is silent). It hears every sound the server sends players
  (`ServerLevelSounds`, a mixin on `playSeededSound`) and the sounds players make of level events
  (`LevelSounds`, the client's table, kept by hand; a block broken plays its own break sound).
- **Sensors say where in their computer's frame** (the user's, 2026-10-27): `forward, right, up`
  from its middle, as the field card's positions (`FieldProjector.Frame`: a block's front and right
  as `Face` names them, a robot's heading and its own right), never the world's axes, so a program
  works however its computer is turned, a robot's as it turns. `CardWorld.tick` takes each holder's
  frame.
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

### The bot golem

A robot a block high, an iron golem's look (user design, 2026-10-24), in two tiers (the user's,
2026-10-26: a tier 1 walks; a tier 2 flies too, has a second battery bay and 4 RAM, the same cards
and storage, and looks more advanced: dark steel, its nozzles' collars round its leg sockets; an
entity type a tier, so the board's layout is fixed as it's made): an entity holding a
`BoardAssembly` (as a case does) in `GolemLayout`'s slots: its batteries, its RAM, a disk, a gold slot
taking only a tier 1 screen (its face, drawn by `TerminalFace` as a case's front), 2 cards, and its
robotics device soldered onto the last lane (`/dev/robotics0`), the only way it moves and acts: it
never acts on its own (no goals). The device walks it to a point relative to itself, at a player's
walking pace, turns it, and stops it; each ends in an event. **Its moves are deterministic** (the
user's design, 2026-10-25): it knows no world position (a GPS card later) but always its exact
**position** (the user's word: robotics' "pose"), odometry from an origin a dimension (set at its
first tick there or by `setPosition`, saying where it is: its origin there, or a given place, a GPS's fix, on a
block's middle at a right angle; kept through reboots, shoves, fields; lost only when it's an item),
and `walkTo`/`flyTo` go to points from it. A walk is whole blocks and ends on the asked block's
middle, facing as it began (a move squares its heading up, and gives it back), or not `ok` on the
last middle reached (its plan then); turns are right angles. **It runs from its plan** (the user's
design, 2026-10-27): its robotics is buffered (one command at a time), each command measured from
where the last left it; a shove off it (in the air) is drift, said as `shoved` news, and the next
positional command (`RoboticsDevice.POSITIONAL`: moves, turns, scans, its hand's) goes back first
(its own `return` event), as a hover holds its plan like a drone. `position` is the plan (a
command, so it waits its turn); `get` is live (where it really is, its drift). `stop` is free and
at once, where it is (no centering), answering where; the plan and the origin stay. Without pathfinding (the default) it walks straight,
forward then right, never turning (it strafes and backs up), by `GolemSteps`: free within its
elevation (the block its feet are in: slabs), a block up at a time only toward `up`, down three at
most only toward it. With pathfinding (`setPathfinding`, kept like the origin) the game's pathfinder
walks it, the flying one flies it, ending exactly all the same. It names faces in its own frame only (`front`, `back`, `left`,
`right`, `up`, `down`; its left its own, as it turns and walks: not `Face`'s, where left is a
viewer's), since it knows no compass; a block's direction it reads is turned into that frame. It
scans all six spaces at once, and works with its hand (its main hand, one item: a slot in its view,
kept on its item when picked up) as a player does, through the loader's fake player standing where
it is (`Platform.fakePlayer`, `GolemHands`): the game's own interactions (placing, doors, buttons,
buckets), breaking in a player's time with its tool (standing on its legs only: legless it has no
footing, in a field it's frozen), a hit with its weapon's damage (its modifiers put on the fake player, which never ticks, and
its swing's strength from the time since its last), a container's items in and out as a hopper
moves them. What a broken block drops (or its hand is given) is caught: its storage first, else its
off hand (a background catch, the user's: atypical, so storage is always preferred), else it stays
in the world, and the swing's end says whether it kept it all.
- **It flies two ways, each with its price** (the user's design, 2026-10-25), never with its legs
  on and its tools whole: on its **thrusters**, built into a tier 2 (a tier 1's thruster calls fail: `no thrusters`), no card (`fly`, `land`;
  `fly()` takes off where it stands), its legs left standing where it took off (`GolemLegs`, a boat's kind of object), red
  beams from their sockets, 25 blocks a second, five CPUs' power a tick; legless it walks nowhere,
  swings no tool (one in hand works as an empty hand: buttons, doors, its legs popped up), ignores
  block triggers, and can be shoved; `land` comes down onto its legs below it and refuses with none
  there. Or carried in **a field**, its own too, legs and all (10 blocks a second): stasis, as for
  any creature (the user's, 2026-10-27): its robotics freezes (commands refused `held`, mcos waiting;
  a swing under way ends; no tools, no looking), and only its robotics (the user's, 2026-10-29: its
  body is held, and robotics is its one device acting in the world), so its other devices go on (a
  crafting card's craft under way too; the card's commands wait, as for its moves); `frozen` and
  `thawed` news, and the carry moves its
  origin with it (its odometry never saw it: a kidnapped robot, told where it is by `setPosition`);
  nothing shoves it; it isn't flying. Legless with nothing holding it up (its program ended, its power gone, a field let go), it
  falls and powers off, a torso on the ground. Picked up legless it's a torso item; torso over legs
  is a golem again (`GolemAssemblyRecipe`, the torso's components kept). Either item, used crouching
  in a hand, opens its parts view on the item (the user's): parts, hands and cards' slots read and
  written there (`ItemContents`), its board off (an item runs nothing).
- **Mobs fear it** (the user's): spiders (a skeleton riding one with them) avoid any golem, chickens
  and cows a flying one (on its thrusters; held in its field isn't flying), sheep none: an `AvoidEntityGoal` added as each
  registers its goals (`GolemFear`; mixins `SpiderFear`, `ChickenFear`, `CowFear`).
- **One answer says it all** (the user's): a program never checks twice. `store` answers `true, 0`
  when nothing it was asked to move is left, else `false` and how many are (never `false, 0`);
  no container is a failure (nil and why). `drop` fills a container there first and answers how
  many went into the world. A face left out is the front for containers, below for drops. A player's hit picks it up as its item, parts kept; nothing else harms
it; it's solid, as a boat is (players walk into it, not through it), and standing nothing shoves
it (players, mobs, knockback; `standing`: on the ground, thrusters off; hovering it's pushed, even
resting on its own legs). Creative
only until the Advanced Robotics Assembly. Its board's state reaches players by
`BoardStatePayload` (an entity has no block update), its screen's rows as a block's do.

### The crafting card

Storage and a 3x3 grid on a card (user design, 2026-10-24): its slots live on its item (the
vanilla contents component, read and written by `CraftingStorage`, its one home), so they go where
the card goes. Any computer takes it, and its view gets a tab a card (right of the buttons) where a
player crafts by hand, as at a crafting table (the game's result slot). Its methods need robotics:
in a case they fail (`no robotics`), in a robot `CraftingBench` works them (the game's recipes;
what's made into storage, all or nothing: no room, no craft). Its commands (all but `pull`, `list`,
`get` and `stop`) come after its robot's: robotics is how a robot handles things, its hands at a
grid too (the user's, 2026-10-27 and -28), so each waits for the robot's moves to end. A craft's
start costs the robot nothing: the craft is then the card's own work, a tick a craft, the card busy
(its next command waits) and the robot free, so cards craft side by side; its `stop` is its own,
and the robot's leaves it (one device's stop isn't another's). Its `/dev` file reads as its
items (`name count` a line) and a line written dumps its storage (below, or at the face named).
A view's tabs are one mechanism (`SideTabs`, the user's): a tab for each card whose kind
registered one, each opening its panel left of the board (the bus order is the parts view's own
panel), the same list in the screen view; a new card's tab is a registration, nothing else.

### The energy field card

It suspends one creature or item in a field (user design, 2026-10-24): an `EnergyField` entity, not
saved (the card's while it runs), holding what it holds in place each tick and moving it with the
game's collisions (never through blocks, never moving blocks), drawn as a sphere round it. What a
field holds can't act, enforced by mixins asking `EnergyField.held`: a mob's AI doesn't run, a
creeper's fuse dies and it can't blow, a player can't attack, use or break (their client's position
is overridden by teleports). Positions are in its computer's frame (`FieldProjector.Frame`: a
block's front and right as `Face` names them; a robot's heading and its own right), and it reaches
16 blocks from its computer, except a robot holding itself, which carries the card. It lets go
without power or a running program (it's ticked powered or not, so it knows), never because the
process that captured ends. A robot it holds is in stasis too (its robotics frozen, its origin
carried: the bot golem's). Its `capture`, `move` and `set` are commands (buffered); `release` is
free, so a program can always let go.

### The charging pad

A plate a robot stands on to charge (user design, 2026-10-24): green particles round it while it
does. Its power is a device's, as the Disk Station's: the bus's while a running computer has it,
else its own buffer, which its battery fills. It charges the robot's battery (its board's buffer
without one), ten running CPUs' worth a tick. Its device (`charger0`) says what it's doing and
reports a robot docking and leaving.

### The operator card

Creative only (OC's debug card, named for Minecraft's operators: Lua has its own debug). It runs
server commands as a command block does, at its block, with a command block's permission
(gamemaster: `time`, `weather`, `give`..., not `op` or `stop`), and returns how it went and what it
said; each command is logged on the server. In mcos, `/dev/operator0`: a line written runs one, a
read gives what the last one said.

### The internet card

HTTP and HTTPS to the real internet (OC's internet card; the user's recipe: a cobweb and an eye of
ender). The card speaks HTTP itself (`HttpExchange`, its own small client), since TLS is beyond a
program's Lua. A program uses the card as any device (the user's call: no `sys.http`): its methods
start a request (a handle), give its status and headers and read its body as it comes (`false`: not
yet, its interrupt saying when to ask again), and `/dev/internet0`'s read is its access log, an
Apache-like line a request (method, URL, status, time, bytes), read only. `lib/http.lua` is the
library on its ioctls; `wget` and `curl` are built on it. **It's a program
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
as far as its sender's strength (OC's `setStrength`, a method), and a send costs more
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
- **Each tier's font** (`TerminalFonts`): tiers 1 and 2, whose cells are big, draw in Unifont
  (Minecraft's `uniform`, finer magnified); tier 3 on a block's face, small cells, in the default
  font. In a screen's view text is crisp: drawn at a whole number of screen pixels a texel (a
  bitmap font magnified by a fraction is uneven blocks), in Unifont whenever its texels can be a
  pixel or more, tier 3's too (`TerminalFonts.forView`). The grid is sized from the font (`TextGrid`).
- **Each tier's glass and white** (`ColorDepth.glass`, `white`): a real screen is never pure black
  or white; the defaults (pure black and white, as a theme recolors them) show as the tier's (tier
  2 a CRT's cool near black and white, tier 3 a charcoal and a paper white), every other color as
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
- **Paste** (Ctrl+Shift+V, Shift+Insert in a screen's view; Ctrl+V stays ^V): the clipboard as typed,
  new lines as Enter, but no control characters or ESC (a paste can't fake its end or send
  sequences); with bracketed paste on (mode 2004) it comes wrapped, and readline turns that on while
  it reads a line (as bash does), so a multi-line paste goes into the line and runs only at Enter.
- **UTF-8, every tier** (the user's): the terminal decodes the bytes programs write (bad ones
  U+FFFD); a cell holds a code point, a wide character's right half `TextBuffer.WIDE`, combining
  marks on their cell; widths are wcwidth's, each code point its own (xterm's, not grapheme
  clusters), from `/usr/share/wcwidth` (scripts/data/wcwidth.py), the one table the terminal
  (`CharWidth`) and mcos (`lib/wcwidth.lua`, readline's cursor) read, so they agree. A row as text
  is its cells, a character and its marks each: saves and players' copies carry it. Typed text and
  pastes go as UTF-8. DEC's character sets (G0/G1, SO/SI, the line drawing, the UK set).
  Box drawing, block elements and braille are drawn by the terminal itself (`CellGlyphs`, shapes
  read from the characters' Unicode names), in whole pixels filling their cells, so they join.
- **The mouse is xterm's** (the user's: as a real terminal, switched on by a program's DECSET):
  9, 1000, 1002, 1003 and SGR's 1006, the decoder's modes (saved with it); the screen view sends
  what the mouse does over the glass, by cell (`TerminalMousePayload`), and the terminal reports it
  as asked, or with none tracking, the wheel scrolls back.
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
- **Every device has a `/dev` file, and it's the device's API** (the user's call: no kernel device
  library): `/dev` is flat, `console`, `tty`, `null`, `zero` and a node for each
  device by the kernel's name, and its ioctls are the device's methods by name
  (`sys.ioctl(fd, "setStrength", 4)`, in any open mode, with the arguments given and no more: a stream
  method called with none takes what's come; a method answering nothing gives `true`, so one whose
  answer is "none" (a missing path's `stat`, a read at the end) says nil; a handle stays in the
  kernel), so
  nothing mcos makes up stands for a device's method (no invented `SIOC*`). Its file is ready for
  poll (`PRI`) while its device has news, its interrupt since the file last read or asked. **Polling
  takes nothing:** an event a poll found is the file's to give the next `pull` ioctl (or read), so a
  program waits with `sys.poll` and takes with either. Reading gives the
  device's data: `get`'s answer (the EEPROM too: convenient, the user's call), what's written going
  to `set` at close, the device deciding what `set` means; a device with nothing to read says what
  it is (type, id, label, methods). A disk's node
  (`storage0`) is its bytes, as a block device's: read, written and sought by byte offset (its raw
  methods); an hfs disk's refuses (`EMEDIUMTYPE`, the user's call: Linux would let a mounted disk's
  bytes be read). A device with events and
  no `get` (a sensor) has a read-only file, an event a line, and a read with none waiting blocks. A
  log is a device's own small window (a NIC's traffic, the internet card's requests: the last 8,
  older ones dropped), so a reader sees what's current and nothing grows: a NIC's file reads its
  frames, one a line (direction, remote address, distance, bytes, outcome); its data is read
  through sockets.
- **The computer's power is Linux's `/sys/class/power_supply`** (sysfs.lua, from `board.power`): a
  battery a bay (`BAT0`...) and the case's store (`case`, a UPS), Linux's files and units (µWh, µW: a
  unit a tick is a watt); `status` by the store's trend against a reading at least a second old.
- **`/sys` makes devices learnable in the game** (the user's): Linux's `/sys/class` tree, a
  directory a kind, a device by its `/dev` name, its attributes a read-only file each (type, id,
  path, label) and `methods/` a file a method, its arguments and what it does, from its `@Method`
  doc (so a doc begins with its signature: `walk(forward[, right]): ...`). A doc says what a method
  does, never how long it takes or how fast it goes (the user's, 2026-10-29): timing is the mod's to
  change (settings, other mods' recipes), so a program waits for an end, never for a time. No `/sys/devices` tree:
  the bus is flat (`sysfs.lua`). `/dev`'s names coming and going are inotify notices
  (`CREATE /dev/charger0`, udev's way), so a program waits for a device as for a file.
- **Buffered devices** (the user's design, 2026-10-28; its spec and build, `.agents/BUFFERED.md`):
  a device that acts over time (`Buffered`: robotics, the crafting card, the field card, the
  redstone card, the robotic piston; the sound and internet cards stay direct, streams with their
  own flow control) runs one command at a time; a command while one runs is refused `busy`
  at the board (layer 0, which can't wait: a board call freezes the whole program), and mcos blocks
  the calling thread only until the device is free (devfs's `command`, asked again as each command's
  end event comes), so more work in flight means threads. Its live state, its events and `stop` are
  free (`Buffered.free`); a `stop` also ends the threads waiting on it (`ECANCELED`). Each command's
  end is an event named after its method, `ok`, why (`EventQueue.ended`); a method that can run
  over time always ends in its event, even when done at once (an extend already out), so a program
  never has to know which. No queue, no tags, no fault state: a failure ends its own command only
  (the user's: a robot stuck on its first failure looks broken). A tags-and-queues design came first
  and was set aside as too error-prone.
  - **A device busy for another's sake names it** (`Buffered.blocker`): the board's refusal is
    `false, "busy", "<its id>"` (a crafting card waiting for its robot's moves: the robot's busy
    state, the card's interrupt line), and mcos listens on both. `busy` and `held` mean "wait":
    no device method fails with them (a field's target in another field is `captured`).
  - **mcos asks a waiting command again at most once a tick** unless one of its devices raised its
    interrupt (the board's state moves a tick at a time); news its retry takes into other files
    counts as the pass's progress (`k.delivered`), so their waiters are served before an idle.
  - **What a load brought is its program's:** a device restored from a save keeps its events and a
    command under way through the first reset after the load (`EventQueue.clear` answering false: a
    hub's card joining after the program went on), and a restored command's end comes once its
    program takes data (a redstone set waits to apply, a field's move and a robot's command end
    `interrupted`, a piston's move goes on); any later reset clears (a fresh boot's).
- **A device's events go to every file open on it** (the user's): devfs takes them (`deliver`) and
  gives each file its own copy, bounded, so a program and a `cat` each see them all; a file's
  readiness counts what it holds (a poll that took the events itself would otherwise wait for a
  line that already fell).
- **`lib/devices.lua` is user space's convenience over `/dev`, `/sys` and inotify** (the user's;
  `sys` stays the kernel's calls): a device opened as a table of its methods (ioctls; `tostring` a
  method's doc, as OpenComputers' proxies), the bus listed, `devices.wait` for one to come,
  `devices.event` for its next event (the poll and the pull). The manual teaches the three layers
  side by side (the board, `sys`, `devices`: concepts/device-layers, tutorials/device-layers).
- **Threads are the kernel's tasks** (user design, 2026-10-25; Linux's, not OpenOS's): a process is
  one or more, each its own coroutine and syscall under way (one blocked leaves the others running),
  all preempted by the firmware's timer as processes are, sharing the process's globals, files,
  directory, environment and handlers (a thread's table holds its own task's fields and reaches the
  rest through to its process). Nothing is copied between them: devices are files, a device's events
  taken by whichever thread reads them (OpenOS copied every signal to every thread, and its threads
  had to yield). The main thread ending ends the process and its threads; a thread's error ends it
  alone (said on stderr, its join's). `sys.thread`, `join`, `cancel`, `gettid`, `threads`, `mutex`,
  `lock`, `unlock`; `lib/thread.lua` over them. A syscall's task is the one it's given, never "the
  one resumed last" (a blocked one is served without being resumed).
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
  with IP's protocol number), with Linux's names for interfaces (eth0, wlan0) and `SIOCGIFCONF` to
  list them; a card's settings (its wake message, its strength) are its methods, on its `/dev` file;
  no commands for them (`ifconfig`, `ethtool` have no purpose
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

Lessons from mistakes, each with its test:
- **Check a game API in the plain game's jar** (Loom's `minecraft-common-deobf`), not NeoForge's
  patched one: common code is built against the plain game (`onAddedToLevel` is NeoForge's).
- **A mob's speed is squared:** `Mob.setSpeed` makes it the forward push too, so a mob moves as a
  player at its speed's square (`BotGolem.WALKING_SPEED`). (`itWalksWhereItsTold...`)
- **A host that shows a changed power state sends its board's state:** the board doesn't then (a
  case's block update carries its block entity); a golem's entity data doesn't.
  (`BotGolemClientGameTest`)
- **A poll takes nothing:** a device file's `events` must not use up what a read or a method would
  give; what it had to take is the file's to give next (`held`). (`pollingADeviceFileLoses...`)
- **An ioctl passes exactly the arguments given:** `table.pack(arg, ...)` adds a nil, and the
  firmware serves a stream method from what's come only when called with none.
  (`aStreamMethodCalledByIoctl...`)
- **News is pending until the program looks:** interrupts are level-triggered, so news a device
  raises once and doesn't hold pending is lost if nothing was waiting at that moment (an internet
  card's log entry after its request's end). A read that blocks has looked at nothing.
  (`theLogProgramKeepsEvery...`, `aProgramBlockedReading...`)
- **"None" isn't "done":** a method that answers nothing gives `true` through an ioctl, so one whose
  answer means none says nil (`Result.ok((Object) null)`). (`aMethodWhoseAnswerIsNone...`)
- **Never drain and refill a bounded queue another thread fills:** keep what's left of an item
  beside the queue. (`readsSmallerThanAPiece...`)
- **Edit by matching text, never by line numbers:** an earlier edit moves the lines.
- **A bitmap font magnified by a fraction is blocky:** snap its scale to whole pixels a texel, and
  test at a player's window (1080p), not only the test window's 854x480. (`CableClientGameTest`)
- **Creative puts a placed stack's count back** (`ServerPlayerGameMode.useItemOn`): an item that
  must be used up is taken out of the hand; and placement is tested through the server's game mode,
  not the item's `useOn` (a mock player isn't creative unless its abilities say so).
  (`placingAPickedUpComputerInCreative...`)
- **An ignored signal stays ignored across spawn** (as across exec): a caller ignoring ^C while it
  waits (`os.execute`, as `system(3)`) gives its child SIGINT back (`sys.spawn`'s `default`), or the
  command can't be stopped. (`controlCDuringOsExecute...`)
- **A test types only once the program waits for it:** keys typed during an earlier read are that
  read's (a program says it's ready with a file). (`rawReadsWaitAsVminAndVtimeSay`)
- **A buffer fills on demand, not only on its cadence:** a pad whose battery filled its buffer once
  a second charged nothing for up to a second. (`aBatteryPutInChargesAtOnce`)
- **A menu's contents come after its screen opens:** a layout made in `init` from what the menu
  holds (a crafting card in a slot) sees an empty menu; lay out again when what it depends on
  changes. (`BotGolemClientGameTest`: the crafting panel in the bus panel's place)
- **A loader's event may come on the network's thread** (Fabric's disconnect does): work for the
  render thread (textures, sounds) goes to the client's thread (`Minecraft.execute`). Timing-bound,
  so no test catches it reliably: the client test's log showed it.

- **An item's data component is immutable and compares by value** (records, strings,
  `ItemContainerContents`), never an `ItemStack`: NeoForge refuses one when it's set, so a golem
  picked up holding something crashed there (Fabric doesn't check). Items on an item go in an
  `ItemContainerContents` (`ModComponents.HANDS`). (`aPlayersHitPicksItUpWithItsParts`, on NeoForge)
- **A table that reads through to another must not read through what's its own:** a thread's
  task fields, unset, read its process's (`__index = p`), so a thread started while its main thread
  waited took that wait as its own and never ran (and could have taken its pending syscall). Own
  fields answer nil when unset. (`aThreadStartedWhileItsMainThreadWaitsRunsAndFinishes`)
- **The clock is the firmware's, not a board call:** `board.uptime` was a call to the server thread
  (a tick each), so a loop reading the time ran a tick a read and its timeouts came late; it's the
  firmware's `clock` now, answered at once. A read of something the firmware has never goes to
  the server. (`McosTest.readingTheClockCostsNoTicks`)
- **A trend needs readings apart:** one look at a file reads it several times (a stat, an open), so a
  store's last reading was always a moment ago and never showed it charging. Compare against one at
  least a second old. (`sysShowsThePowerSupplies`)
- **Compare a view's tabs by lane, not identity:** `SideTabs` makes its tabs afresh at each call,
  so `tab == open` is never true (the open tab's mark never showed). (`BotGolemClientGameTest`: the
  crafting tab ringed)
- **A power press can end in an abort at once** (no EEPROM, a program returning in its first turn):
  the light shows what the press came to, from the board (`BoardAssembly.pressPower`, for every
  holder), never "on" for an `ON` press. (`cpuWithoutEepromAbortsAndItsLightBlinks`,
  `aProgramReturningAsItStartsShowsItsAbortAtThePress`)
- **A file's readiness counts the events it holds:** a poll that took its device's events into its
  own copy, before the kernel saw the device's line rise, waited for ever: its readiness came only
  from the kernel's news, and the line was already down. (`theTallyCountsWhatBothSensorsSee...`,
  two threads on two sensors)
- **`a and b or c` gives c when b is false:** a redstone file's poll readiness was
  `device.pull and changed or nil`, so "not changed" became nil, "no PRI of its own", and the
  device's news (held until a read or an ioctl) made the poll ready for ever: the door example spun
  its computer. Write the branch out when b can be false. (`theDoorOpensOnAPress...`)
- **A test's server ticks at the computer's pace:** a loop ticking every millisecond ran a starved
  computer's clock ahead of its code on a loaded host (timeouts expired, deadlines passed early),
  which hid the spinning door. (`TestServer.awaitHost`)
- **What a device holds open for a file closes with the file:** an internet request is a connection
  nothing else ends, so a process that died mid-download kept one of the card's 4 for good, and
  four of those locked the card. A driver's `opens` names such a method and its closer, and devfs
  closes what a file left open when it closes. (`requestsAProcessLeavesOpenCloseWithItsFile`)
- **A wait for ever wraps round:** a deadline is now plus its length, and `Long.MAX_VALUE` ticks
  added to now is in the past, so a Java program's park (`Program.park`) answered at once and spun.
  Saturate a deadline at the end of time. (`RuntimeTest.callsAreImmediate...`)
- An hfs file shows its writes only at close: watch a file a program keeps open on `/tmp` (tmpfs).
- Typing more than 256 keys at once in a test drops the rest (a terminal's key queue): split lines.
- Lua 5.5 for-loop variables are constants (a compile error when the script loads: `LuaScriptsTest`
  compiles every script). A `]]` ends a `[[...]]` string: use `[=[...]=]`.
- mcos's `io` is C's stdio: `file:read(n)` is `fread`, which waits until it has all n bytes or the
  end. A program passing a stream on (a device's events, a terminal) reads the descriptor
  (`sys.read(file.fd, n)`: what's there now, as `read(2)`), as `cat` does.
- Lua inside a Java text block is unescaped twice (Java's, then Lua's): use long strings
  (`[[\d]]`) for patterns, and capture a test program's stderr (`2> /err`) to see why it failed.
- A class in the mixin package can't be loaded as an ordinary class (Mixin reserves the package):
  helpers the mixins call live elsewhere (`entity/GolemFear`), and each mixin is its own file.
- **What's taken while blocking may be what the wait is for:** a command refused busy takes the
  device's events into its files before it blocks, and those can hold the running command's end
  (it came between the refusal and the look): a wait for that end then never wakes, as nothing is
  left to raise the interrupt (the square example hung on a loaded host). A command whose blocking
  took anything asks again before it blocks. (`aCommandWhoseWaitEndedAsItWasRefusedRunsAtOnce`)
- **Two devices sharing one busy state need one wake:** a robot's crafting card was refused busy
  while the robot walked, and mcos listened on the card, whose line never rose when the walk ended.
  A device busy for another's sake names it, and the waiter hears both.
  (`BufferedWaitsMcosTest`: the robot and its card)
- **A wait's reason is reserved:** mcos blocks on `busy` and `held` from any device, so a field
  capture failing `held` (the target in another field) waited for ever. Only refusals say them.
  (`whatAnotherFieldHoldsCantBeCapturedAndSaysSo`)
- **The game saves what an entity's movement set:** a golem saved mid-step (its walk turning its
  gravity off) loaded floating, never on the ground, so it never thawed and never walked again; and
  a field's hold left its gravity off too. What a move sets is put back at a load and a freeze.
  (`savedMidWalkItLoadsWithGravity`, `heldMidWalkAndLetGoItThawsAndItsWalkEnds`)
- **An entity's first look at the world comes at its first tick:** a golem just placed isn't on the
  ground yet, so "not on the ground" made it falling (busy) to a program's first call; a fall needs
  it to be going down. (`itTakesFromAChestAndPutsBack`)
- **A close that must send can't block after its descriptor's gone:** a last line to a busy device
  was lost, its close's retry finding no descriptor (EBADF). A file's `flush` runs first, waiting
  with the descriptor still open. (`aClosesLastLineWaitsForABusyRobotAndGoes`)
- **A golem resting on its own legs is on the ground:** `fly()` takes off where it stands, its torso
  on its legs (solid), so `onGround()` stayed true and a hovering golem took no shove. Whether it
  stands asks `standing()` (on the ground, thrusters off). (`shovedInTheAirItHoldsItsPlanAndSaysSo`)
- **A block's state isn't the block:** leaves change state as their log goes, crops as they grow;
  what's tied to a block (a golem's breaking) compares the block, not its state.
  (`aBlockWhoseStateChangesWhileItsBrokenStillBreaks`)
- **Game tests run side by side:** a sensor in one hears and sees its neighbours' (a part put in a
  golem 13 blocks off is a sound): a sensor's test counts only what it made, by where it was.
  (`anAudioSensorHears...`)
- Mouse buttons are SDL-numbered (left 1, right 3): use the constants. SDL sends typed characters
  only while text input is on: a screen that takes typing turns it on.
- `.gitignore` patterns without a leading `/` match at any depth: anchor root-only rules.
- `GameTestHelper.assertValueEqual` throws on a null value (it calls `value.equals`), crashing the
  test server instead of failing the test: compare with `Objects.equals` in an `assertTrue`.
- An empty dedicated server stops ticking after a minute unless `pause-when-empty-seconds=-1`
  (`scripts/devserver.sh` sets it).
