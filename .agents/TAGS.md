# Tags and queued devices: the spec and its build

The design (worked out with the user, 2026-10-26 to 28) is summarized in `PLAN.md`, "Tags, and the
robot's queue". This file is the spec the build follows, and the build's checklist; items are
marked done as they land. The user's fallback if this proves wrong: every device call blocking at
layer 0, no tags. So the build watches for what makes tags error-prone (lost events, deadlocks,
programs that can't be written simply) and reports it.

## The spec

### Kinds

- A device is **queued** or **direct**, every method alike (`Queued`, a Java interface: a device
  that implements it is queued). Queued: robotics (and the crafting card's actions, through its
  robot), the energy field card, the redstone card, the robotic piston, the sound card, the internet
  card. Direct: every other device.
- A direct device's call answers in the call, as today: `true` and its values, or `false` and why.
- A queued device's call, except its stream method (`pull`), is **accepted** or refused at once:
  - accepted: it answers its **tag** in `ok`'s place (`tag, immediates...` at layer 0), the tag a
    positive integer; immediates are only what acceptance itself decides (a write: how many bytes it
    took). How it ends comes in exactly one **completion** event carrying the tag.
  - refused: `false, why`, no tag, no event. A wrong argument is refused (nothing was queued); so is
    a call with no room for its event (`full`: below).
- `pull` stays a plain stream call: `true, event` or `true, nil`.

### Tags

- Numbered by the board (the host's `Machine`), one counter a computer, always increasing, saved
  with the board; a power-on starts it again (a reboot drops every device's queue first).
- A device sees its call's tag in its arguments (`Arguments.tag()`, 0 for a direct call).

### Events

- A completion: `{event = <its method's name>, tag = n, result = <its answer, if any>}`, or
  `{event = "failed", tag = n, why = "..."}`, or `{event = "cancelled", tag = n}`. So no method may
  be named `failed` or `cancelled`. Actions add their own fields where useful (`stop`'s `at`).
- News: a device's untagged events, as today (a sensor's sighting, a `shoved`), never carrying `tag`.
- A completion always has a tag and news never does.

### Room: no room, no tag

- A queued device's events wait in its `EventQueue` (capacity 32). Accepting a call **reserves** a
  slot for its completion; a call is refused `full` when reservations plus completions waiting fill
  the queue. A completion always fits (it had its slot); news takes what's left, the oldest news
  going first when it's full, counted (`lost`, as now). News never takes a reserved slot.
- The queue's interrupt is pending while it holds any event.

### The board's guarantee: every tag ends in exactly one event

- The board keeps every outstanding tag: the name the program called its device by, and the device.
- A tag ends when its completion is delivered to the program (any way: a pull, the inbox, a ring).
- A device that leaves the bus (unplugged, broken, its chunk unloaded, another board's) with tags
  outstanding: the board ends each itself, `failed`, "device gone". Those **tombstones** are served
  by the name the program used: a `pull` there answers them (oldest first), and that name's interrupt
  is pending while any wait. A late completion from a device for a tag already ended is dropped.
- A computer that stops (power off, abort, reboot, a program's end) drops every outstanding tag and
  every device's queue: there's no program to tell.
- After a restore (a computer saved running, loaded again), the board asks each device for each
  outstanding tag whether it still holds it (`Queued.holds(tag)`): a device that saved its work and
  resumes it does; one that can't (a connection) doesn't, and the board ends those, `failed`, "lost:
  the world was unloaded".

### Layer 0: the board API

- `board.invoke(address, method, ...)`: as above.
- `board.sync(seconds, tags...)`: the firmware's. Waits until every named tag has ended (none named:
  every tag this program holds that hasn't), taking events from those tags' devices; returns every
  event it took meanwhile, in order (news too), and the tags still out when `seconds` ran out (nil:
  wait for ever). It freezes the program: for single-program code (EEPROMs, a tutorial's OS), never
  mcos's kernel. The firmware learns each tag's device as `invoke` answers it.

### Layer 1: mcos

- A queued device's ioctl answers its tag and immediates (`local tag, n = sys.ioctl(dsp, "write",
  data)`), or `nil, why, errno` (`EAGAIN` for `full`).
- Events go to files: a completion to the file whose ioctl issued its tag; news to every file open
  on the device. Within a file (a process's threads sharing it), whoever reads first. devfs keeps a
  dispatcher a device: whichever file asks takes the device's events and sorts them into each file's
  own queue. A file's `pull` ioctl answers from its queue; it's ready for poll (`PRI`) while its queue
  has events or its device has news.
- `sys.sync(fd[, seconds][, tags...])`: blocks the calling process (only it) until those tags (none:
  every tag issued through `fd` not yet ended) have ended; answers every event it took from `fd`'s
  queue meanwhile, in order, and the tags still out.
- Reading a queued device's file issues its `get`, and blocks the reader until its completion.
- A file's close cancels no command, but closes what its calls opened and nothing else ends (a
  driver's `opens`: the internet card's requests).
- `dmesg` logs an event's name, tag and size, never its data.

### Layer 2: `lib/devices.lua`

- A device's methods answer as their ioctls do. `devices.sync(dev, ...[, seconds])` waits on every
  handle given (`sys.sync` on each), returning their events merged in order and the tags still out.

### The devices

- **Redstone card:** `get`, `set` queued; a `set` is applied at the next tick (at most one `set` a
  face a tick), its completion once applied, carrying every face as `get` gives (`result`); `get`'s
  completion carries the faces. Its own changes raise no per-face news; the world's still do.
- **Robotic piston:** `push`, `extend`, `retract` queued, each ending once its arm has moved.
- **Sound card:** `setFormat`, `getFormat`, `write`, `getDelay`, `reset` queued; `write` takes what
  fits (`tag, n`; none fits: refused `full`) and ends once those bytes have played; `reset` drops
  what's queued (each write still waiting ends `cancelled`). `sync` goes (`devices.sync` does it).
- **Internet card:** every method queued; a request's tag is its handle. `request(url, method,
  headers[, length])` ends once its request line and headers are sent; `write(tag, data)` takes what
  fits (`tag, n`) and ends once those bytes are sent; `response(tag)` ends the body and ends once the
  status and headers are in (`result` = `{status, message, headers}`); `read(tag[, n])` ends with
  the next piece of the body (at most `n`, capped), `""` at its end; `close(tag)` ends it, and every
  call pending on it ends `cancelled`. No card timeouts. A request that fails ends what's pending on
  it `failed`. The access log stays news.
- **Energy field card:** `scan`, `capture`, `move`, `release`, `get`, `set` queued (its own queue,
  in a case or a robot); `move` ends once it's arrived or blocked; a capture outlives the process.
- **Robotics** (with the crafting card's actions): every method but `get` and `pull` queued and run
  in order. Commands run from the plan (where the commands before leave it: whole blocks, a right
  angle): a positional command first returns to the plan when the golem has drifted (a shove in the
  air). Relative moves are measured from the plan. `walk()`/`fly()` with no arguments return to it.
  `position()` answers the plan. `get()` (live) answers its state, its real position, its drift,
  what's queued and running. `stop()`'s arrival clears the queue (each `cancelled`), then it halts
  at once where it is and ends with `at` (its real position). `hover` goes (`fly()`). A shove raises
  `shoved` (news, with the drift). Held in a field (any field) its robotics freezes: the command
  under way pauses, nothing starts (`frozen`, `thawed` news), and the field's carry moves its origin
  with it (so `position()` reads the same after). The crafting card's methods queue in its robot's
  queue (fail in a case: `no robotics available`). Event names are the methods'.
- **Sensors** (motion, audio): positions relative to their computer: a robot's forward, right, up;
  a case's front and right as `Face` names them.

### Not changed

Direct devices (disk, EEPROM, terminal, network, Bluetooth, operator, charging pad, sensors' calls,
Disk Station) answer as today.

## The build

Each step lands with its tests (a bug's seen failing first), the manual's pages and AGENTS.md, and
passes `./gradlew testAll` before its commit.

- [ ] **1. The board's tags** (Java): `Queued`, `EventQueue` with reservations, completions and news;
  tags numbered and passed (`Arguments.tag()`); the outstanding registry, delivery ending a tag, dead
  tags dropped, tombstones for a gone device (by name, pending, pulled), stops clearing all; saved
  and reconciled after a restore (`Queued.holds`); `LuaRunner` putting the tag in `ok`'s place. Tests
  (`TagsTest`, a test device that's queued): acceptance and refusal, room (`full`), every way a tag
  ends, a gone device, a stop, a restore, news never taking a reservation.
- [ ] **2. `board.sync`** (firmware). Tests: a sync over several tags and devices, its events, its
  timeout, a gone device's tombstone ending it.
- [ ] **3. mcos's layer 1 and 2:** devfs's dispatcher (completions to their file, news to all),
  tagged ioctl answers (`EAGAIN` for `full`), `sys.sync`, a queued device's file's read, `dmesg`
  without payloads, `lib/devices.lua`'s `sync`. Tests (`McosTest`): two files on a device, `sys.sync`,
  a blocking read, a process's threads.
- [ ] **4. The redstone card and the piston.**
- [ ] **5. The sound card** (and `aplay`, `/dev/dsp`).
- [ ] **6. The internet card** (and `lib/http.lua`, `wget`, `curl`).
- [ ] **7. The energy field card.**
- [ ] **8. Robotics and the crafting card** (the queue, the plan, drift, stop, stasis, the field's
  carry, events by method), its driver and the golem's game tests.
- [ ] **9. Sensors relative to their computer.**
- [ ] **10. The manual's three layers** (concepts, tutorials, `sys` API), AGENTS.md's decisions.
- [ ] **11. A deep code review**, by a reviewer who didn't write it (a subagent given the spec and the
  diff, told to look for lost events, deadlocks, races, a tag that can end twice or never, and
  programs this makes hard to write), and what it finds fixed.
- [ ] **12. The report** to the user: what worked, what failed, what wasn't planned, the decisions
  made to finish.
