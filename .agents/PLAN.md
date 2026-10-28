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
  It could also read a machine's state (the user's, 2026-10-26): why an aborted computer stopped (its
  board's last shutdown record: power lost, its CPU or RAM changed, its program's error and stack),
  without powering it on, as a technician's probe reads a dead board.

## Robots: the bot golem, then drones

- **A GPS card** (the user's, 2026-10-26: players may work in world coordinates if they choose):
  a card that gives where it is in the world, data only, never coupled to the robotics device (no
  method added to it, no talking to it). A program uses its numbers, optionally, with the robotics
  device's `setPosition(forward, right, up, heading)` (where it is, from its origin) to put its
  position in the world's frame. Open: the axes (forward north (-z), right east (+x), up y, so a
  program passes its fix as `-z, x, y`); its heading (a fix has none, as a real GPS's: move a block
  and fix again); its precision (whole blocks, a block's middle), range or satellites.
- **Beacon positioning, a primary use case** (the user's, 2026-10-26; the GPS card only makes it
  simpler): fixed computers with WLAN cards as beacons, each knowing its place in a shared frame,
  and a golem working out its own from the distances their frames carry (trilateration, as GPS
  itself does), then `setPosition` with it, so every golem agrees on one frame and a target sent
  over the network is the same block to all. Distances are already from block to block (a card's
  place is its block), rounded to 0.1 m: whole-block fixes come out exact for beacons spread well
  apart, rounding worse far off (a check with numbers, then a test, before it's documented as a
  tutorial). It measures from its plan exactly: `walk()` or `fly()` with no arguments (below) puts
  it back on its block's middle at a right angle, and its tag says when.
- **The board's own log, behind a warning triangle** (the user's, 2026-10-26; a BMC's event log and
  console, apart from the OS): an aborted computer's why is otherwise readable only after a boot that
  works (or with the scanner), so one that can't boot (no RAM) shows only its blinking light. A
  triangle in the case view's button column, lit amber after an abort, opens a small read-only
  terminal the firmware writes (each abort's cause and message, a crash's error and stack): not a
  bus device, never written by user programs.
- **Tags, and the robot's queue** (the user's design, 2026-10-26 and 27, worked out together; the
  manual teaches it on all three layers, the board, `sys` and `devices`). Its spec and build, being
  built: `.agents/TAGS.md`.
  - **Tags are layer 0's** (the firmware's): it sees every call, the ones it answers itself too (the
    inbox, the clock), so it numbers them, one counter a computer, always increasing (saved with the
    Lua state by Styx), and a call to the host carries its tag (`"call", tag, ...`) so a device sees
    it in its arguments and puts it in the event it ends with. At layer 0 the tag takes `ok`'s place:
    `board.invoke` answers `tag, values...` or `false, why` (any number is true in Lua: nothing that
    tests `ok` changes; a failed call started nothing, so has no tag). Older is smaller, across
    devices. Real code meets tags at completion (NVMe's command ID, io_uring's `user_data`); a
    program's own cookie on a call, carried back in its event (io_uring's), is an idea for layer 1.
  - **Every tag ends in exactly one event:** done (`arrived`, `turned`, `crafted`...), failed
    (`blocked`, `failed`), or `cancelled`, as NVMe completes an aborted command. Today an action can
    vanish (a new walk or `hover` drops a flight with no event): that goes.
  - **A device is queued or direct, all its methods alike** (the user's, 2026-10-28: one rule a
    device, never a method's, which was OpenComputers' direct and indirect calls again). A **queued**
    device's every call goes through its queue in order and answers its tag (in `ok`'s place at
    layer 0, the ioctl's answer at layer 1), its answer coming in its ending event, its reads too (a
    `get` after a `set` sees the `set`); only `pull`, the events themselves, is outside. A **direct**
    device's calls answer in the call, as today (the program frozen until the server answers), no
    tags. A tag means an event: a call that answers no tag has no event to come.
    - Queued: robotics (with the crafting card's actions), the energy field card, the redstone card
      (the user's: a change in the world takes a tick, so the queue applies a face's `set` at most
      once a tick, in order: the game's own limit, respected), the robotic piston (its arm takes
      time), the sound card (writes queue to play; `setFormat` between them in order; its `sync()`
      method goes, `devices.sync` does it), and the internet card (the user's: fully queued, not
      special). The redstone card's `set` is one call, one tag, one event, however many faces it
      names (the user's); its own per-face change events go (they'd report a change twice), so a
      per-face event is the world's (a lever, a neighbour).
    - **The internet card, call by call** (2026-10-28): a request's tag is its handle (the user's: as
      an fd), naming it in its later calls, each with a tag of its own whose event carries its
      payload. `request(url, method, headers[, length])` ends once its request line and headers are
      sent; `write(tag, data)` takes what fits, answering `tag, n` at once, its event once they're
      sent (the user's: the program writes again with the rest; a body of unknown length goes chunked, `length` for a
      server that wants `Content-Length`); `response(tag)` finishes the body and ends once the status
      and headers are in (asking for the response ends the upload, as Java's `getInputStream`:
      a request ending at its headers would deadlock an upload); `read(tag, n)` ends with the next
      piece of the body (capped), `""` at its end; `close(tag)` ends it and every call still pending
      on it (`cancelled`). No "false: not yet" answers, no card timeouts (the user's: the kernel
      waits as long as it likes; the process closes its fd to give up). An endless stream flows at
      the program's pace in bounded memory: a request's buffer full, its thread stops reading the
      socket, and TCP's window stops the server. Freezable, never persistable (the user's): a save
      lets the connection run on; an unload or a server stop ends it.
    - **Order is the device's business** (the user's: a queue or a bag): a device may run any number
      of tags at once, so long as results match the order of calls wherever order carries meaning
      (a robot's commands; one request's reads, which would scramble its stream).
    - Direct: the disk (the user's: storage blocks, a user doesn't want to deal with it), the EEPROM,
      terminals, the network card (a send's outcome is decided as the server handles it), the
      Bluetooth card, the operator card, the charging pad, the sensors, and the Disk Station (the
      user's: storage, so it blocks; no different from a disk's writes). A performance note for both,
      not an API question: their host I/O runs on the server thread (a file's close writes it whole;
      a clone a whole disk, up to 16 MB), which may stall a 50 ms tick. Measure; if it does, do the
      I/O off the server thread while the calling program waits.
    - Large data a queued device gives comes in capped chunks, one a read.
  - **Each layer has one non-blocking call and one opt-in wait:** `board.invoke` and `board.sync`,
    `sys.ioctl` and `sys.sync`, a device's methods and `devices.sync` (all taking a timeout, giving
    every event taken meanwhile, in order, and the tags still out). `board.sync(seconds, tags...)`
    is the firmware's (it knows which device each tag went to), for single-program code (EEPROMs, a
    tutorial's own OS): it freezes the program, so mcos's kernel never uses it; `sys.sync` blocks
    only the calling process (as `read` does), built on the kernel's own event handling;
    `devices.sync` wraps it. Layer 1 otherwise never blocks (the user's: a script pushes all it wants
    into queues, and syncs once).
  - **The firmware protects programs from devices** (the user's, 2026-10-28): every tag ends in an
    event, always, whatever happened to its device. The firmware knows every tag it gave out and
    the device (by id) it went to, and ends itself, with `failed` and why, each one a device can no
    longer end: a device that left the bus (its chunk unloaded, broken, taken by another board, a
    device in another chunk that blew up while the computer's was loaded), and after a restore,
    each tag a device no longer holds (it asks: a device that resumes its work, the robot's queue,
    keeps its tags; one that can't, a connection, forgets, and the firmware ends them). As a
    driver fails every outstanding command when its controller resets. So a lost device never
    leaves a program waiting on an event; a gone device's last events are still read from where
    its file was.
  - **Events: tags identify, names describe** (the user's, 2026-10-28): a completion's event is named
    after its method (`{event = "walk", tag, ...}`; today's `arrived`, `turned`... go), ending in
    `failed` (with why) or `cancelled` instead, so no method may be named either. A completion always
    has a tag and news never does, so a news name may match a method's without confusion; the
    issuing program knows each tag's method, so `failed` carries no `method` field.
  - **Who gets which event, in mcos:** within one fd (a process's threads sharing it), whoever reads
    first (the user's: a race in the program is the program's, as a deadlock it writes); across fds,
    a completion goes to the fd that issued its tag, and news to every fd with the device open
    (Linux's evdev gives each client every event; io_uring a completion to its ring). The kernel's,
    in devfs (a tag-to-fd table, a copy of news a file): the firmware knows no fds.
  - **Closing an fd cancels no command** (the user's: sent and gone; a walk runs to its end), but the
    kernel closes what an fd's calls opened and nothing else ends (an internet request, a
    connection: as closing a socket's fd does), its pending calls ending `cancelled`. Today's leak
    of that is a bug, fixed apart from this plan.
  - **The rest, as proposed:** a queued device's `/dev` file's read blocks only the reading process
    until its event (the driver issues the call); `stop()` in stasis needs no exception (the user's
    rule above): its arrival clears the queue, and its halt waits like any command until the field
    lets go, so nothing of the old plan runs after; a device's kind shows in `/sys` and
    its manual page (at layer 1 a tag and a direct answer can both be numbers); `dmesg` logs an
    event's name, tag and size, never its data; a queued `write` (the sound card's, the internet
    card's) takes what fits (the user's), answering at once `tag, n` (the room is checked as the call
    arrives, so the count is part of its acceptance, as the tag is: a queued call answers at once
    only what was accepted, how it ended coming in its event); none fits, no tag (`full`); its event
    comes once the bytes it took have played or been sent, so `devices.sync` means done, and the
    sound card needs no `drain`; a field card's capture outlives the process that made it (the
    user's: a command; the field holds until it's released); a reboot drops every device's queue, as power-off does (the
    tag counter starts again).
  - **To rewrite when built:** `lib/http.lua`, `wget`, `curl`, `aplay` (the sound card's `sync`), the
    robotics and redstone drivers, `lib/devices.lua`, the manual's examples and its three-layer
    pages, and the tests that read today's event names.
  - **No room, no tag** (the user's, 2026-10-28; io_uring's `IORING_FEAT_NODROP`): a tag is a promise
    of room for its event, counting the events waiting for the firmware and the completions already
    promised. A program that falls behind fills its device, and new calls fail at once (`false,
    "full"` at layer 0; `nil, "full", EAGAIN` at layer 1), its interrupt saying when there's room.
    Every tag has an event; not every event has a tag. Untagged news (a `shoved`, a sensor's) never
    takes a tag's room: under pressure the oldest news goes, counted (`lost`), as `EventQueue` does
    now. What a device holds counts against the program's memory, so a program short of it gets
    `full` sooner.
  - **Everything a robot does is queued and tagged** (the user's: what depends on where it is, or on
    its hand, has an order), a motion controller's command buffer (G-code, a 3D printer's planner):
    its moves, `swing`, `use`, `swap`, `store`, `take`, `scan`, `held`, `contents`, `setPosition`,
    `setPathfinding` and `position()`. Only `get()` (the `/dev` file's read: its live state, the
    queue, the running tag, its drift) and `pull` answer outside the queue. Bounded: a full queue
    refuses at once, and its interrupt says when there's room (the handshake). An argument that's
    wrong fails the call at once (no tag: nothing queued); what only running finds (already on its
    legs, holding nothing) is the command's `failed` event. Saved with the device. The program
    ending, aborting or its robotics losing power drops the queue and stops it.
  - **Commands run from the plan:** each command's place is where the ones before it leave the golem
    (the plan), and relative moves are measured from it, so `fly(A) + fly(B) == walk(A) + walk(B)`
    (the user's: predictable). A shove (only in the air: nothing shoves it on its legs) is drift: the next command returns to its plan first, so `fly(A) + shove + swing ==
    fly(A) + swing`. `walk()` or `fly()` with no arguments returns to the
    plan explicitly. `position()` is queued and answers the plan. No way to accept drift into the
    plan for now (`replan`, `settle`, `resetOrigin` were tried and dropped: names didn't land); no
    `drift()` method: the `shoved` event carries the drift (its total), and `get()` shows it.
  - **`stop()` is forceful, and an ordinary queued request** (the user's): its arrival at the device
    clears the queue (each command `cancelled`), and it's then all that's queued, so it runs at once:
    it halts where it is, as soon as it can (mid-block, or hovering in place), without centering, and
    ends in `stopped` with its tag and `at`, where it really stopped from its origin. The plan and
    the origin don't move: what it interrupted never happened, so the next command returns to the
    plan first.
  - **No `hover`:** `fly()` is "take off where it stands, and hold" (as `fly(0, 0, 0)` already is).
  - **A field holding the golem is stasis for its robotics, not its computer** (the user's: as a held
    mob can't act), whoever's field it is, its own too: no robotics, no tools (a tier 1's kind of
    flight, worse than thrusters; AGENTS.md's "it keeps its tools" in its own field changes). Its
    robotics queue freezes where it is (the command under way too) and goes on once it's let go
    (`held`, `released` events, without distances); its program, sensors and other cards run on.
    The field card has its own queue, tagged, in a case or a robot, so a frozen robot doesn't freeze
    its own field.
  - **A field's carry moves the origin with the golem** (the user's: unobservable to its robotics,
    frozen in stasis: robotics' kidnapped robot problem): `position()` reads the same after, every
    command works from where it was left (a tier 1 travels by its own field, nothing pulls it back),
    and what the program saved (home, a chest) is offset by the carry until it re-localizes (a GPS
    fix or the beacons, then `setPosition`). A shove is different: it comes while its robotics runs
    (holding itself in the air, it feels the push, as a drone's inertial sensors do), so it's drift,
    reported (`shoved`) and corrected by the next command. AGENTS.md's origin "kept through ...
    fields" changes.
  - **Sensors report from the body, relative to their computer** (the user's: cases and robots
    alike): in a robot, forward, right and up (its own frame, as the field card's already are); in a
    case, its front and right as `Face` names them. The motion and audio sensors' world `x, y, z`
    go: they can't be turned into a robot's moves, and they leak its world heading. Reacting to a
    sensor from a drifted spot means returning to the plan first (`fly()`), or adding the drift.
  - **What needs robotics joins the robot's queue** (the user's line): the crafting card works only in
    a robot, so all its actions queue there, its face actions (`drop`, `pickup`, `store`, `take`)
    running from the plan as `swing` and `use` do (a CNC's toolhead commands in its G-code stream),
    and `craft` too (the user's: the robot is busy while it crafts; one ending event, `craft(5)`:
    one, with the count made). The energy field card
    works in a case too: its commands stay its own, tagged (`move`: one ending event), its positions
    in its computer's frame, from the body.
  - **The devices layer's one wait: `devices.sync(dev, ...[, seconds])`** (the user's): blocks until
    every tag issued through those handles and not yet ended has ended, and returns every event it
    took meanwhile, in order (the untagged ones too: a `shoved`, a sensor's: nothing lost by
    waiting); with a timeout, what it has and the tags still out. CUDA's
    `cudaDeviceSynchronize`, OpenGL's `glFinish`, Marlin's `M400`. A library function, never a
    method on a device's table (a sound card has its own `sync()`), and every device's (every method
    returns a tag): the robot's queue, crafts, field moves, internet requests, a Disk Station's jobs
    if they become slow, assembly machines (the user's idea). Several devices at once
    (`devices.sync(robot, crafter)`). Each handle keeps its own buffer of events, so a process's
    threads each see them all (a pump thread and a watcher). Later: the sound card's `sync()` as
    tagged writes that end when played, so `devices.sync(dsp)` replaces it.
  - **The ioctl report** (the user's, to keep current as the APIs change): every device method and
    what `sys.ioctl` gives back, from the code, grouped by device, at
    https://claude.ai/artifact/YXP5MWRaU7rjee75jUnonm. Misalignments it shows, to settle into one
    intuitive pattern: `false` as a value (send, both `store`s; `response`, `read`, `sync`'s "not
    yet") beside failures as `nil, why, errno`; the internet card's request handles are already tags.
- **Walking, decided and built (2026-10-25):** a position (odometry from an origin a dimension), whole-
  block walks ending exactly, straight without pathfinding (the default), the game's pathfinder as
  a mode, walkTo/flyTo from the origin. Still to consider: whether the field card's moves should land
  on block middles too (they're exact offsets now).
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
- **A save test fails now and then under testAll's load** (2026-10-25, and again 2026-10-26, LuaRunnerTest.aSaveCarriesOutNoCalls...,
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
