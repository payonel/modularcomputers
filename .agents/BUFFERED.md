# Buffered devices: the spec and its build

The design, worked out with the user (2026-10-26 to 28), after a tags-and-queues design was set
aside for being too error-prone (lost events, deadlocks, hard to write for): **a device that acts
over time runs one command at a time; a call while one runs waits.** The user's words: "buffered,
buffer 1"; "if more work needs to be queued, that's why the user should use threads". This file is
the spec the build follows and its checklist, items marked done as they land.

## The spec

### Kinds

- A **buffered** device runs at most one command at a time (`Buffered`, a Java interface): robotics
  (the crafting card's actions too, through its robot), the energy field card, the redstone card,
  the robotic piston. Every other device is **direct**, and answers as it does today (the sound card
  and the internet card included: they're streams with their own flow control, a write taking what
  fits, a read answering "not yet", not one command at a time).
- A buffered device's methods are **commands**, except those it names **free** (its live state,
  `get`; its events, `pull`; `stop`): a free method answers at once, always.
- A command is **active** from its start until it ends; one that's done at once (a `use`, a
  `setPathfinding`, a read like `position`) ends as it starts.

### Layer 0: the board

- A command to an idle device starts, and answers at once: `true` and what it answers (a read: its
  answer; an action: `true`, its outcome to come).
- A command while another is active is refused at once with `busy`, doing nothing. A robot held in
  a field refuses commands with `held` (its robotics is frozen).
- A command that runs over time ends in an event (news, as any device's), named after its method
  (one done at once, a read or a `capture`, answers in its call: no event, so the stream isn't
  noise for programs waiting on real ends; a build decision, 2026-10-28):
  `{event = "walk", ok = true}`, or `{event = "walk", ok = false, why = "blocked: minecraft:oak_log
  front"}`; one cut short by `stop` ends `ok = false, why = "stopped"`. It's also the device's "idle
  again", its interrupt pending until the event is taken.
- No queue, no tags, no fault state: a command's failure ends that command only, and the next runs
  as any would.

### Layer 1: mcos

- A command to a busy (or held) device blocks the calling thread only, until the device can take
  it, then starts it and answers (a blocking `write(2)`'s way; the kernel keeps running).
- `stop` on a device also ends, with `ESTOPPED`-like failure (`nil, "stopped", "ECANCELED"`), every
  thread blocked waiting to give that device a command: a stop stops the plan a program's threads
  were feeding it.
- A device's events go to every file open on it (the user's): devfs takes them as they come and
  gives each file its own copy, so a script and a `cat` each see them all. Within one file (a
  process's threads), whoever reads first.
- A command blocked on a device that's gone fails `ENODEV` (the bus read again).

### Layer 2: `lib/devices.lua`

- A device's methods answer as their ioctls do (blocking as above). `devices.ended(dev, method)`
  waits for that method's next completion event and answers `ok, why` (a careful script's check).

### Failure strings

Dry, never prose (the user's): a lowercase reason, a colon, data (ids, faces, numbers):
`blocked: minecraft:oak_log front`, `too high: 2`, `too low: 4`, `too far: 64`, `no path`, `held`,
`busy`, `no legs`, `no legs below`, `on legs`, `no thrusters`, `stopped`. For every device.

### Robotics

- **Commands run from the plan**: where the last command left it (whole blocks, a right angle). A
  positional command first returns to the plan when the golem has drifted (a shove in the air);
  relative moves are measured from the plan. A blocked walk ends on the last block's middle it
  reached, and that's the plan then (its own command put it there: not drift).
- `walk()` / `fly()` with no arguments return to the plan (`fly()` from its legs: takes off and
  holds where it is). `hover` goes.
- `position()` is a command (it waits behind the one running) and answers the plan. `get()` (free,
  live): its state, its real position, its drift, legs, pathfinding.
- `stop()` (free): the active command ends `stopped`; the golem halts at once where it is (no
  centering), and `stop` answers its real position, `{forward, right, up, heading}`, in its call
  (it's done at once, so no event: a build decision). Its plan becomes where it stopped (in the air
  the point; on its legs the block it's nearest), changed after the code review (2026-10-28): with
  the plan left at the stopped command's start, a scan after stopping a walk walked it all the way
  back. A command `interrupted` by a load plans the same way.
- A fall on its legs it didn't ask for (a block gone from under it) isn't drift: it's busy while it
  falls, its plan follows it down, and `fell` news (how far) ends a wait: a build decision from the
  review (digging down broke every positional command after it).
- Its own way back to the plan (after a shove, before a positional command) is a command of its own,
  ending in a `return` event (`ok`, or why): a build decision, so a failed way back is said.
- A shove raises `shoved` (news, with the drift: `{forward, right, up}`).
- Held in a field (any field, its own too): its robotics freezes: the active command pauses, new
  commands are refused `held` (mcos: blocked until it's let go), `stop` still works (ends the
  paused command); `frozen` and `thawed` news. The field's carry moves its origin with it, so
  `position()` reads the same after (the kidnapped robot: re-localize with a fix and
  `setPosition`). No tools in a field, nor in the air (legless it has no footing: the user's rule
  from the legs' design), so a job up a cliff is carried there and set down (the tree job, built
  so). `get` stays free while held: a program follows the carry with it.
- The crafting card's methods are its robot's commands (busy while the robot is; `craft` keeps the
  robot busy until it's crafted, so a robot crafts with one card at a time); in a case they fail
  `no robotics`.
- Events are named after their methods (today's `arrived`, `turned`, `broke`... go).

### The others

- **Energy field card:** `capture`, `move`, `set` are commands; `move` ends once arrived or blocked
  (`blocked: minecraft:stone`); `scan`, `get`, `pull` and `release` are free (a program can always
  let go: a move under way ends `stopped`). A capture outlives the process that made it.
- **Redstone card:** `set` is a command, ending once applied in the world (at its next tick: at most
  one change a tick), its event carrying every face (`result`); `get` is a command (it sees a `set`
  before it). Its own changes raise no per-face news; the world's still do.
- **Robotic piston:** `push`, `extend`, `retract` are commands, each ending once its arm has moved.
- **Sensors** (motion, audio): positions relative to their computer: `forward, right, up` (a robot's
  own frame; a case's front and right as `Face` names them), as the field card's are.

## The build

Each step lands with its tests (a bug's seen failing first), the manual and AGENTS.md, and passes
`./gradlew testAll` before its commit.

- [x] **1. The board:** `Buffered` (free methods, active, busy), completion events, `busy` and
  `held` refusals in the board's call. Tests (a test buffered device).
- [x] **2. mcos:** a command blocks its thread while the device is busy; `stop` ends blocked
  submitters; events to every open file; `lib/devices.lua`'s `ended`. Tests, among them (the
  user's): one thread running a series of blocking commands on a buffered device while another
  thread of the same process makes progress meanwhile (it isn't held up by the first's waits).
- [x] **3. The redstone card and the piston.**
- [x] **4. The energy field card.**
- [x] **5. Robotics and the crafting card:** one command at a time, the plan, drift, `stop`, stasis,
  the field's carry, events by method, `hover` gone, dry strings; its driver; the golem's tests;
  and (the user's) a thread walking a robot through a series of blocking commands while another
  thread makes progress.
- [x] **6. Sensors relative to their computer.**
- [x] **7. Dry failure strings** for every device, and the tests that read them.
- [x] **8. The manual** (devices, concepts, tutorials, examples, the three layers), AGENTS.md's
  decisions.
- [x] **9. A deep code review** by a reviewer who didn't write it (a subagent given this spec and
  the diff: blocked threads that never wake, lost events, races between threads and devices, a
  command that runs twice or never, what new players will find confusing), and its findings fixed.
- [ ] **10. The report** to the user.
