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
  - The UPS battery charges and powers the machine when power drops.
- **CPU status dots:** the 4 lit dots left of the CPU socket are a good place for more
  status later. Today they just light when a CPU is in.
- **Bus order light bulb:** highlight a cabled device in the world. It waits for cabled
  devices to exist.
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

## Separate blocks: cases, screens, keyboards, cables

The all-in-one stays the test bed. Later there are separate blocks:

- **Case blocks:** right-clicking a case opens the **case GUI**: slots plus the power button.
  Cases provide the power toggle.
- **Screen blocks:** right-clicking a screen opens the screen view ("modal mode"). Its power
  button shows only when the screen is connected to a case.
- **The all-in-one is both.** Right-click on it opens the screen (the screen wins). The view
  toggle button switches between the case view and the screen view. Built.
- **Keyboards and cables:** external devices joined by cables.

### Cabled devices and the bus (user decisions, 2026-10-10; not built)

- **One device, many computers.** A cabled device (screen, keyboard, ...) may be connected to
  several computers at once.
- **Connect/disconnect toggle.** Each cabled device's row in the bus order panel gets a small
  toggle.
  - It shows plugged in (connected) or unplugged (disconnected), and clicking it switches.
  - New cabled devices start connected.
  - Built-in devices and expansion cards can't be disconnected; only cabled ones can.
  - A disconnected device leaves a **gap** in the bus walk.
- **Open question: how gaps look to firmware.** Today the bus walk is
  `machine.board().bus().devices()`, an ordered list with no gaps. Options:
  - **Position and device pairs, with numbers skipping over gaps.** This is the real-world
    model: PCI addresses skip empty slots, and software just enumerates what's present.
  - **A gapless list.** Programs never see the gap. Simplest for minimal EEPROM code.
  - Either way, a disconnected device should keep its position, so reconnecting puts it
    back where it was.
  - Decide this before the Lua runtime defines the bus walk API.

### KNOWN GAP: where a device's bus position is remembered (undecided)

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
- **Today:** only built-in devices, drives and cards exist. `BusOrder` keys them by slot
  (`sata:1`, `exp:0`) and drops a removed device's position. That's fine for in-case parts,
  but cabled devices need the design above.

### Ordering the devices a case sees (user idea, not decided)

In real PCs, bus order comes from physical slots. For cabled devices, the user is thinking
of letting the player order them:

- The case GUI shows a **list of the physical components connected by cable**.
- **Move up / move down** buttons reorder the list. Order matters, e.g. the console uses the
  first display in bus order.
- A **light bulb** button next to each entry **highlights that device in the world**, so the
  player can tell which screen is which.

## Machine ideas discussed (not built)

- **Console log:** a ring buffer in the console. Each console keeps its own read position,
  and a `messages dropped` line replaces silent loss (like Linux printk and `/dev/kmsg`).
- **Hot-plug takeover:** a screen attached to a running machine joins the console splitter,
  which redraws onto it (like fbcon taking over from dummycon).
- **Stored console preference:** which display(s) the console uses, kept in an NVRAM-like
  store (like UEFI's `ConOut` variable). Today it's every display at the smallest common
  size.
- **Beep codes:** audible boot errors for headless machines.
- **EEPROM as a bus device:** also listed on the physical bus, so programs can read or
  reflash it.
- **Monospace bitmap font:** today text uses Minecraft's proportional font, centered per cell.
