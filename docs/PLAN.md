# Plan

The user's roadmap and the design ideas discussed so far. Items move from here into
`AGENTS.md` ("Current state") as they're built. Design approach: abstract how real PCs work,
skip steps freely, never invent false steps (see `AGENTS.md` section 1).

## Done

- **All-in-one computer:** screen + keyboard on the front, power button on the back, built-in
  display, keyboard and disk on its bus, soldered memory.
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

- **RAM and disk slots** in the case view, with RAM and disk items. Today memory and the disk
  are soldered into the all-in-one.
  - The case view should have four slot boxes: CPU, RAM, EEPROM, disk.
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
