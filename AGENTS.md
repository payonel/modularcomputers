# Modular Computers: agent handoff

This file is the full context for any agent session working on this repo. `CLAUDE.md` imports it
(Claude Code reads `CLAUDE.md`, not `AGENTS.md`; other agent tools read `AGENTS.md`). Keep it
current: when a decision is made or a gotcha is found, record it here.

Last updated: 2026-10-11 (battery tiers: recipes, charging, drain, power).

---

## 1. The user and the project

- The user (GitHub: payonel) is one of the original authors of **OpenComputers** (OC), the
  Minecraft mod that adds programmable Lua computers. They have been away from Minecraft
  modding for a long time; explain changes in the modding ecosystem when they're relevant.
- **Modular Computers** is a new mod that deliberately reuses many of OC's paradigms and
  features (computers, components, Lua machines, networks, etc.), but it is built **from
  scratch, green**:
  - Do **not** copy OC source code into this repo.
  - Do **not** reuse OC's build process, versioning scheme, or plugin/addon process.
  - **Design from real hardware, not from OC.** The user designs by abstracting how real PCs
    work (CPU, motherboard, buses, firmware, consoles). Then they decide what to mimic and
    what to skip.
    - Steps may be removed or skipped, but never invented ("no false steps").
    - When discussing a design, explain the real-world mechanism first, e.g. how UEFI or Linux
      does it.
    - **Lean toward real Linux every time** (user, 2026-10-11). The user appreciates being told
      when the code, or their own idea, drifts from how the real thing works (e.g. "a returned
      init is a panic in Linux", "`/dev/console` reads keyboards too"). Call it out plainly,
      then recommend the real-world behavior.
    - Use OC only as a source of feature and behavior ideas, when the user asks for more
      ideas or asks "how did OC do X?". Answer those from the local clone (section 7).
- Java mod, **Fabric** loader, targeting the **latest Minecraft version**.

## 2. Standing guidance from the user (follow always)

**Vocabulary:** the user calls viewing a screen through the full-window display GUI
(`DisplayScreen`, opened by right-clicking a screen) **"modal mode"**. "Screen GUI" means the
same thing.

1. **Git branching:**
   - Work on `master` (mainline). It always holds the latest dev work.
   - **Never create branches unless the user explicitly asks.** AI agents tend to spray
     feature branches; the user does not want that.
   - Release branches are cut from `master` only when the user declares that a version on `master`
     is a release. They are per Minecraft version and/or per mod major version.
2. **Test-driven, autonomous verification:**
   - Build everything around automated tests so that human play-testing is rarely necessary.
   - Prefer mocking UI behaviour and events over loading the game.
   - When the game must run, it runs autonomously from a test runner: start the game, verify
     mod output during startup, create/start a world, exercise the mod, then shut the game
     down. This has been designed in from day one; keep it working.
3. **Versioning** follows the ecosystem convention in section 4. Users must be able to see at a
   glance which Minecraft version a jar is for.
4. **No old-save compatibility** (user, 2026-10-11): this is local dev work. Change save
   formats, rename items, components and keys freely, and never keep shims, legacy keys or
   unused registrations so old worlds load. Old test worlds may simply break.
5. **Items stack** (user, 2026-10-11; applies to everything we add): every item and block item
   stacks to 64 like vanilla's (`ModItems.register`'s default), **except things that will hold
   data of their own** (EEPROMs and hard disks: `ModItems.holdsData`, 1). Items stack only with
   identical data, so batteries stack at the same charge, and a card or screen that has been in a
   machine carries its serial number (its identity) and no longer stacks with fresh ones. Its
   tooltip shows the id with Shift only ("Id: <uuid>"; user: ids are long), and **crafting it alone clears it** (`ClearIdRecipe`,
   user's idea): it stacks again, and is a new device the next time it goes in. Part
   slots and card slots still hold one each.
6. **Commits:** commit **autonomously**, without asking, every time a new addition is done
   and `./gradlew testAll` passes.
   - Run `git add -A`, then commit on `master` with a message describing the addition.
   - Never commit with failing tests.
   - Don't push (the repo is local only), and don't open pull requests unless asked.
   - Keep model identifiers out of commit messages and code.

## 3. Current state

- Repo: `~/code/ModularComputers`, branch `master`. The bootstrap is the initial commit.
- **Local only:** there's no remote. Don't add one or push anywhere (including GitHub) until
  the user says so.
- `./gradlew testAll` passes in about 20 seconds. It runs:
  - unit tests
  - server game tests
  - client game tests against the release jar, on Xvfb
- Gameplay so far: the **all-in-one computer** (`modularcomputers:all_in_one`), plus a
  **screen** block and a **cable** (see "Screens and cables" below).
  - It's a full cube in beige retro style, with a screen above a keyboard on the front face.
    It faces the player who places it.
  - It's listed in the mod's own creative tab, "Modular Computers" (`ModCreativeTab`). Fabric
    puts mod tabs on **page 2** of the creative inventory (the ▶ arrow by the tab title).
    It's also in creative search, and `/give @s modularcomputers:all_in_one` works.
  - **Parts:**
    - The display and keyboard are built in. Everything else is an item (`ModItems`) in the
      **case view**'s 17 slots (`CaseMenu`):
      - CPU (three tiers, `CpuItem`/`CpuTier`: bus capacity 4/16/64; draw a tick 100/150/200
        running, 50/20/5 waiting, 0 halted; iron, gold and diamond lids), EEPROM (labeled ROM), 3 UPS battery bays, 4 RAM (three tiers, `MemoryItem`/`MemoryTier`:
        256 KiB, 512 KiB, 1 MiB; not used by the machine yet), 4 SATA (hard disks, three tiers,
        `DiskItem`/`DiskTier`: 1, 4, 16 MiB, drawing 8/5/2 a tick: bus-powered like terminals,
        `getCapacity()`; no data yet) and 4 expansion
        slots, which take any card (`ModItems.CARDS`, `device/CardKind`): WLAN, Bluetooth,
        Redstone, Audio, Motion Sensor, Audio Sensor, Weather Sensor. All cards are
        placeholders with no function.
      - Only the CPU and EEPROM affect the machine so far.
      - On the bus: the **EEPROM** (`Eeprom` is a `Device`, `FIRMWARE`, row "ROM"), hard
        disks (`Disk`) and cards (`Card`, typed by kind). The CPU still loads from the EEPROM
        slot directly. **Batteries aren't on the bus** (user, 2026-10-11, reversing an earlier
        choice): on real machines the firmware reports a battery (ACPI), so if programs need
        battery info it would be a board call, not a device.
      - RAM is a placeholder: the machine still uses the soldered `Hardware` memory.
      - **Batteries** (user design, 2026-10-11; `item/BatteryItem`, plain-Java
        `power/BatteryTier` and `power/BatteryCharge`). **The computer runs on its battery:**
        - **Power is counted in whole units** (user, 2026-10-11; `power/PowerUnits`): per tick, a
          CPU draws 100 while it executes (nothing halted or waiting for an interrupt), a
          terminal 10 while it has power (the user: CPU about 10x a terminal), and a write to a
          terminal costs 1. The yardstick is a running all-in-one, CPU and built-in terminal,
          110 a tick: batteries are rated in days of it (`PowerUnits.DAY`, 2,640,000), so a full
          tier 1 (26,400,000) runs one for 10 days. An idle machine (CPU waiting) draws only its
          terminals, so it lasts 11 times longer. Players see units without a name, short with an
          SI prefix like a kWh rating (`PowerUnits.format`: "950", "2.64M", "26.4M"; infinite is
          "inf"). Charge is `ModComponents.CHARGE`:
          `BatteryCharge(units, settledAt)`. A day is 24000 ticks.
        - **Bus power** (user design, 2026-10-11; `bus/BusPower`, `Machine.powerUse`): every
          bus-powered device (terminals so far) has power while at least one running computer
          has it on its bus, like USB's VBUS, and goes dark when the last lets go (power off,
          unplugged, the computer broken or unloaded). **Nothing ticks for it** (the user's worry
          for big builds): power follows events, and a device's debt (its draw for the time it
          had power, plus its per-call costs, which only the device knows) is settled lazily
          when a computer collects, every 20 ticks. A device shared by several computers is paid
          for once, by whichever collects first (the user: a race, and it doesn't matter).
        - **How long each tier lasts is a setting** (see "Settings" below):
          `battery_tier1_duration` 10, `battery_tier2_duration` 20, `battery_tier3_duration` 0, in
          days; 0 means it never runs out (`BatteryTier.setDays`, `infinite()`). Any tier can be
          infinite, so every battery item carries a charge component.
          - Charging materials are percentages of a full tier 1, so they follow its setting.
        - **Tier 1** (`tier1_battery`): copper×3 / iron, redstone, iron / iron, redstone, iron.
          Full, it runs an all-in-one for 10 days and nights, 10% a day.
        - **Tier 2** (`tier2_battery`): gold×3 / iron, a tier 1 battery, iron / sea lantern, blaze
          rod, sea lantern (user changed it, 2026-10-11: self-recharge "might be op"; built around
          the tier below, 2026-10-12, keeping its charge: `BatteryUpgradeRecipe`). It holds twice
          as much. It recharges itself whenever it isn't running a computer (out of the UPS slot,
          or in a case that's off) at `battery_tier2_recharge_rate`, a share of a running
          all-in-one's draw: 0.5 by default, so two seconds of rest charge one second of running.
          Rest too short to earn a whole unit
          carries over (`BatteryCharge.rest` advances `settledAt` only by what it paid for).
          - This is worked out lazily from `settledAt` (game time), so it charges in chests and
            unloaded chunks too. It's settled every 20 ticks in a player's inventory or in a case.
          - A new battery is `UNSETTLED`: it starts counting at the first look.
        - **Tier 3** (`tier3_battery`): diamond, nether star, diamond / netherite, a tier 2
          battery, netherite /
          netherite, ender eye, netherite. It never runs out and has no charge component.
        - **Charging** (`BatteryChargingRecipe`, a special crafting recipe, like repairing a
          tool): put the battery and glowing materials in any grid (2x2 is enough). Each
          material adds a share of a *tier 1's* capacity: sea lantern 100%, glowstone block and
          shroomlight 25%, glow ink sac 10%, glow lichen 5%. They add up, to full.
          - A tier 2 takes the same amounts, so a sea lantern fills half of one (my reading of
            "charged the same, but double storage").
          - A full battery, a tier 3, or anything else in the grid gives no result.
        - Crafted batteries start empty (my default). The creative tab and `giveall` give
          charged ones (`BatteryItem.charged()`).
        - Items show a charge bar. Their hint (see "Item hints" below) shows "Charge: 73%" and
          "Stored: 19.3M" (a tier 3: "Charge: 100%", "Stored: inf"; user's format); with Shift, tier 2's "Recharges itself while not in use" (the user dropped the rate line), and
          for tiers 1 and 2 the glowing items with what each adds to *this* tier ("+50%").
        - **The case's power buffer** (user design, 2026-10-11; `power/PowerBuffer`, saved as
          `power_buffer`): the computer runs on the case's own store, which holds a **hundredth of a day** of a running all-in-one on a tier 3 (12 seconds; user cut it from a day, 2026-10-11).
          - A battery going in fills it right away, up to a day's worth, out of the battery.
            After that the buffer tops itself up from the battery every 20 ticks
            (`AllInOneBlockEntity.updateBattery`), so the battery drains while the computer
            runs. Time the chunk wasn't loaded doesn't count.
          - With the battery out or flat, the buffer runs down while the computer runs. Pulling
            the battery doesn't stop a running computer.
          - **Low-power alarm** (user, 2026-10-11, like a UPS on its last minutes): under half
            its buffer with a load (`PowerBuffer.isLow`, `AllInOneBlockEntity.lowPowerAlarm`),
            the case plays the error beep at every battery check, once a second.
          - **Three battery bays** (user, 2026-10-11: "if 3 fit, put 3"; `CaseMenu.UPS_SLOT`,
            `UPS_BAYS`; not on the bus): in a row at the bottom left of the board (the expansion
            slots moved right and closer to make room). The case tops up from the **last bay
            first, the first bay last** (user). One meter for all.
          - **The charge meter under the bays shows the buffer, not the batteries.** Empty:
            all bars off; any power: at least one red bar.
          - Hovering the meter shows a hint (`CaseScreen.powerTooltip`, user's format, 2026-10-11):
            "Case: 75%", "Batteries: 90%" (all the bays as one, or "none"), "Stored: 29.0M" (case
            and batteries; "inf" with a tier 3), "Load: 205" (what it draws now, a tick) and
            "Time: 00:15:00" (how long the stored power lasts at that load; "-" with no load,
            "forever" with a tier 3). The load is the machine's current draw, not an average (the
            user found the average far too slow): the CPU at its tier's rate for what it did last
            tick, plus every powered bus device's draw, without per-call costs
            (`Machine.currentDraw`), sent with the buffer level (`CaseMenu.load`).
          - The case view gets the buffer level through the menu's data (`CaseMenu.powerLevel`,
            a `ContainerData` like a furnace's progress), so it updates every second, as the
            battery's slot does. Menu data travels as shorts, so the level goes as two (low and high 16 bits,
            `CaseMenu.POWER_DATA`). The block
            entity also syncs it whenever its percentage changes.
        - **Times read DD:MM:SS** (`power/Durations.clock`, user's format): Minecraft days, then
          the real minutes (00..19, 20 to a day) and seconds into the day.
          - When the buffer runs out, a running computer dies with the **power-down sound**
            (`power_down`, user, 2026-10-11): the error beep sagging from 180 Hz to 45 Hz and
            fading out over 1.4 s, its harmonics dulling first (`scripts/art/power_down.py`;
            `AllInOneBlockEntity.dyingSound`). Pulling the CPU still gives the plain error beep.
        - **No power** (an empty buffer): pressing power does nothing at all
          (`PowerPress.NO_POWER`): a click, no beep and no flash. The power check comes before
          the CPU check.
        - **The power button is faded unless the computer is ready** (block state `ready`): a
          CPU is in and the case has power (user, 2026-10-11).
    - A newly placed computer is empty. Get the parts from the creative tab, or with the
      cheat command **`/modularcomputers giveall`**.
      - It gives one of every item registered under the mod's namespace, so new items are
        included automatically (`ModCommands`).
      - The essentials go into free hotbar slots first, in order (user, 2026-10-11): all-in-one,
        CPU, EEPROM, a charged tier 1 battery, screen, cable (`ModCommands.hotbarFirst`).
      - It needs operator permission, like `/give`: cheats on in singleplayer.
    - **Breaking it doesn't spill its parts** (user, 2026-10-12): like a shulker box, it drops
      as one item that keeps them (`minecraft:container`), its board id (the item's
      `modularcomputers:serial`, so Bluetooth links still find it) and its bus order
      (`modularcomputers:bus_order`, like NVRAM), copied by the loot table
      (`copy_components`) from `collectImplicitComponents`; placing it puts them back
      (`applyImplicitComponents`). It comes back off, with its buffer empty until a battery
      fills it. Explosions drop it too (no survives-explosion condition), so parts aren't lost.
      Screens, hubs, robotic pistons and paint guns do the same with their cards and ids. An
      empty one still has its id, so it stacks with new ones only once crafted alone.
      Its hint lists what's inside, one line a part (`Hint.contents`): a computer's bus devices
      in bus order ("ROM: EEPROM", "SATA 1: ...", "EXP 2: ..."), then "CPU", "RAM n", "UPS n";
      a holder's cards by slot, then "Radio". Vanilla's own container list is hidden
      (`TooltipDisplay` on the block items, `ModBlocks.holderProperties`).
  - **Case tiers** (user design, 2026-10-11; `machine/CaseTier`; blocks `tier1_case`..`tier3_case`,
    the same `AllInOneBlock`/`AllInOneBlockEntity` as the all-in-one). Case == board. **The
    all-in-one is now just a tier 3 case** (the dev kit; may become a creative pre-filled block):
    no built-in terminal (user: it shouldn't show a screen without one in the slot); its front
    and screen come from the gold slot like any case. Bus places: 1 ROM, 2-5 SATA, 6-9 EXP.
    - **One board for every tier**, like a fab leaving features out of cheaper boards: the case
      view draws the same art, and where a tier has no slot it shows the board as left
      unpopulated (user: "cut but not printed"): `case_board_bare.png`, the same board with each
      part's footprint as a dashed silkscreen outline and tinned pads, copied in over the
      missing slot (`CaseScreen.extractTierMarks`); nothing moves. Missing slots are inactive
      (`PartSlot.isActive`): no hover highlight, no clicks. A lower tier has the first slots
      of each kind (`CaseMenu.available`): battery bays 1/2/3, RAM 1/2/4, SATA 1/2/4, expansion
      2/3/4 (the user's suggestion). The CPU socket and ROM are on every tier.
    - **Looks: each tier is an era of the PC** (user's pick, "direction C", 2026-10-12; drafts at
      a private draft page), and a case and a screen of one tier
      match: tier 1 the 80s beige box with a **green-phosphor** terminal (its glass is green and
      its text glows green: `ColorDepth.glow`/`glass`, drawing only, as phosphor is the glass's);
      tier 2 a 90s platinum tower with a red MHz display, and a color monitor with an RGB badge;
      tier 3 a modern graphite case with a diamond light bar and dark keys, and a graphite
      monitor. Every tier's sides keep tier 1's line across them, and **only the back shows the
      board** (user: a side showing it would send players looking for the power button to the
      wrong face). Shapes don't change between tiers:
      the glass where text is drawn, the back's window and the **power button on the back** (still
      right-clickable, `BackPanel`) stay put. `scripts/art/case_tier_textures.py` recolors the
      beige originals for tiers 2 and 3 and writes the case models (`tier{N}_case[_screen][_on][_faded]`),
      blockstates and item models (the all-in-one uses tier 3's); `cabled_block_textures.py`
      draws each tier's screen faces (`tier{N}_screen_front/side/back/top`). The screen view's
      bezel takes the block's era too (`DisplayScreen.Bezel`).
    - **Power buffer:** 0.0025, 0.005, 0.01 of a day of a running all-in-one (3, 6, 12 seconds)
      (`CaseTier.bufferCapacity`, `PowerBuffer(capacity)`).
    - **The gold slot** (tiers 2 and 3, user design): the first expansion slot is gold-trimmed and
      also takes a screen (a terminal: display and keyboard), like an AGP slot took graphics. The
      screen is then the case's terminal, on the bus at `exp:0` (with its own id), of the
      screen's tier; the case's front shows it (block state `screen`; models `case[_screen]...`)
      and right-clicking the front opens its screen view. Without one, the front is blank
      (`all_in_one_case_front`; a tier 1, which can't take a screen, has a plain front with no
      recess, `case_plain*` models) and right-clicking opens the case view; the case view shows
      no screen-view toggle (and the server refuses the switch). Taking the screen out
      takes the terminal off the bus (dark).
    - `CaseMenu.stillValid` accepts any case block (a tier case's view closed at once before).
  - **Two views, one block:**
    - **Right-click** opens the **screen view** (`DisplayScreen`, the user's "modal mode").
      The display sits in a beige **bezel** (`DisplayLayout.bezel`, 6 GUI units) that matches
      the screen surround on the block's front.
    - A **view toggle** button switches to the **case view** and back. In the world, the
      **window into the case** across the top of the back (texels 3..12 by 2..7, showing the
      motherboard) opens the case view directly.
    - **The case view** (`CaseScreen` + `CaseMenu`, an `ExtendedMenuType` that carries the
      position) is the user's chosen design: mockup "E+ round 3". The mockup page is
      a private draft page (private to the user).
      - **Layout:** a printed-circuit **motherboard** above the player's (vanilla gray)
        inventory, and the **bus order panel** to its left.
      - **Static art:** `textures/gui/case_board.png`, `bus_panel.png` and `case_icons.png`,
        generated by `scripts/art/case_gui_textures.py`.
        - Labels and numbers are drawn at runtime with Minecraft's font; no font is baked
          into textures.
        - `CaseArtTest` checks that every slot position in `CaseMenu.SLOT_POSITIONS` sits on
          a socket cavity in the board texture.
      - **Labels** sit above their parts: CPU, ROM, UPS, RAM, SATA, EXP. The parts themselves
        have no numbers.
      - **Live indicators**, drawn over the art from the slots' contents:
        - CPU in: the 4 power-stage dots left of the socket light up. The user wants to use
          those dots for more status later.
        - RAM in: the connector stays black. A thin gold contact line runs down it, and the
          latches close in (6 wide instead of the open 8). The user rejected a green fill as
          too thick and too much like the RAM itself.
        - Hard disk in: a small green cable plug in its SATA connector. The user rejected red
          and an activity light.
        - Card in: the expansion connector's left clip engages (silver).
        - UPS charge meter: 6 bars filling left to right (`power/ChargeMeter`). No battery:
          all off. An empty battery shows one red bar. 2–3 bars are yellow, 4–6 green.
        - Empty slots show faint hint icons.
      - **Bus order panel:** one row per device on the bus, however many there are (no empty
        rows).
        - Up to 10 fit (`CaseScreen.BUS_VISIBLE_ROWS`). With more, the mouse wheel scrolls,
          and a scrollbar with a proportional thumb appears (`display/ScrollWindow`). There's
          no scrollbar when everything fits.
        - The backing (`bus_panel.png`) is plain board; each row is the `bus_row.png` sprite.
        - Each position number is etched into the board to the left of its row and stays
          with the position when devices move.
        - Each row has the device's icon in a socket, then a dark display with its name and
          location ("built-in", "ROM", "UPS", "SATA 1", "EXP 2", or "cable, 5m": meters along
          the connection, so a screen right against the computer is 1m; the user rejected
          "adjacent").
        - **Linked hover:** hovering a part slot outlines its bus row, and hovering a row
          outlines its slot (`BusEntry.slot`). Built-ins have no slot.
        - The ↑/↓ arrows send `ReorderBusPayload`; they're greyed out at the ends.
        - **The line under the title:** "bus: 3 of 64 (2 internal, 1 external)", then ", missing 5"
          when offline devices are listed (", missing 0" is left off), or ", overflow 2" while
          devices wait; the two can't happen at once, since a waiting device takes a missing
          one's place. It counts the case's own devices too, since they use the CPU's capacity
          (the user found "external devices: 0 of 62" confusing, 2026-10-11). No CPU: "no CPU:
          no bus". It
          shrinks to fit the panel when the numbers get long (`CaseScreen.busSummary`).
        - Order lives in `bus/BusOrder` (user design, 2026-10-11, replacing "first found on top"):
          - **The case's devices come first, fixed in their slots' order:** built-ins, ROM, UPS,
            SATA 1-4, EXP 1-4. No arrows: a slot's position is its address, like real hardware.
            Removed parts drop out.
          - **External devices (cabled and Bluetooth, mixed) follow, in a saved list the player
            reorders**, like UEFI's BootOrder: kept by id, so a device late to load (its chunk)
            keeps its place. Absent ones stay listed, dimmed, reading "offline", with a red X
            **forget** button where the plug was (`ForgetDevicePayload`). New ones go last.
            Offline rows keep their last-known name, icon and position (`known`, saved).
          - **Bus capacity comes entirely from the CPU** (user, 2026-10-11; `CpuTier`: tier 1 4,
            tier 2 16, tier 3 64; `Motherboard.busCapacity`). **The case's own devices count
            against it as much as external ones**, but only those installed: empty slots reserve
            nothing, and external devices get what's left (`BusStatus.room`). A tier 1's 4 hold
            the essentials: an EEPROM, a disk, a card, and one more card or external device. **No CPU, no bus:** nothing is on
            it, the panel lists nothing ("no CPU: no bus"), and the saved external order waits
            untouched. The case's devices come first, in their slots' order; a CPU too small for
            even those leaves the last of them waiting too (overflow). A smaller CPU going in makes missing devices make room
            first, as when the list is full. Tests can set `Motherboard.overrideBusCapacity`
            (saved as `bus_capacity_override` so clients see it).
          - **Full:** a new external device bumps the one missing longest (each entry keeps
            `lastSeen`). With none missing it **waits**: not on the bus. The bus is then in its
            warning state: an amber LED top right of the panel, the summary line saying
            "overflow N", and a deep, rough, sagging hum (`bus_overflow`) when a device starts
            waiting.
          - **A soft hum beat** (`device_connect`, two low tones 4 Hz apart, the user's
            favorite) plays whenever an external device joins the bus: wired up, plugged back in
            from the panel, or online after its chunk loads late. Leaving is silent. The full hum
            is the same idea deeper (72 Hz, beating 7 Hz, sagging 15%), to sound like an error.
            Both are made by `scripts/art/part_sounds.py`; neither is checked by a test. Programs will read it from `Bus.status()` (`BusStatus`: capacity,
            devices, saved, missing, waiting, overflow).
          - **Numbers are fixed places** (user, after looking at PCI/SATA/USB): 1 the built-in
            terminal, 2 ROM, 3-6 SATA, 7-10 EXP, then external devices from 11 in their
            order. Empty case slots leave gaps (like `lspci`); only present devices get rows.
          - Parts and hub cards are known by a serial number (`ModComponents.SERIAL`, stamped when
            a bus part first goes into a computer or a card into a hub), so moving a hub card to
            another slot keeps its place.
          - It's saved (`bus_external`, `bus_disconnected`), and the machine's `Bus` follows it.
        - **Cabled devices** (screens) get two more buttons left of the arrows:
          - The **bulb** outlines the device in the world for 10 seconds, pulsing yellow and
            seen through walls (`client/Identify`, drawn by `DisplayRenderer`). It's
            client-side: only that player sees it. Clicking it again stops it. It's never
            saved, and leaving the world clears it.
          - The **plug** connects or disconnects it (`ConnectDevicePayload`). Plugged in it's
            green; pulled out it's grey, and the row's name dims. A disconnected device stays
            listed in its place but is off the bus. Built-ins and parts can't be unplugged.
        - The panel is left out on windows narrower than about 382 GUI units.
      - **Part sounds:** putting a part in or taking it out plays its own sound, server side,
        from the slot changes (`AllInOneBlockEntity.playPartSounds`).
        - RAM: a slide and latch clicks. CPU: a drop and the lever. EEPROM: a chip press.
          Disk: a slide and a clunk. Card: a slide, a seat and a clip. UPS battery: a tiny
          zap on insert.
        - They're synthesized by `scripts/art/cabled_block_textures.py draws the screen, cable, expansion hub, robotic piston and paint gun textures (Pillow); PNGs are committed
scripts/art/part_sounds.py`. The user wanted them soft, not
          Minecraft's abrasive click.
        - Slides are sparse grains ringing a resonance (a plastic rasp). The first version's
          noise slides sounded like breathing into a mic. The user called the current set
          "perfect" (2026-10-11).
        - Loading a save plays nothing.
      - **Fitting hints** (`SlotHints`, case view and card holder views): carrying an item, or
        hovering one in the inventory, outlines the slots it fits; hovering a slot outlines the
        inventory items that fit it (user requests, 2026-10-11).
      - Clicking the bus panel or the buttons doesn't count as clicking outside the window,
        so it never drops the carried item (`hasClickedOutside`).
    - Switching sends `SwitchViewPayload`. The server either opens the menu, or closes it and
      sends `OpenDisplayPayload`.
      - Going back to the screen view, the server closes the menu quietly (`doCloseContainer`)
        and the client swaps one screen for the other. Closing it on the client first would
        pass through "no screen", which grabs the mouse and recenters the cursor (the user
        noticed).
    - Both views show the same button column to the right (`ComputerButtons`): the power
      button on top, the view toggle below. The toggle shows a motherboard
      (`textures/gui/case_button.png`, the same art as the back's window) for "case", and a
      tiny screen for "screen".
  - **Power button** (a press button, not a toggle switch). A press (`Machine.pressPower`)
    turns the machine on or off (`Machine.isPowered`, block state `powered`). Rules the user
    chose (plus: it needs a charged battery first; see Batteries above):
    - **The machine is only ever on with a CPU.**
      - Pressing power without one is refused: a stone-button click, one **error beep**, and
        the power light **flashes faded green for `AllInOneBlock.ATTEMPT_TICKS` (4)**, then
        returns to faded red. It stays off.
        - The flash sets block state `powered=true`; a scheduled block tick
          (`AllInOneBlock.tick`) resets it from the machine's real state.
        - So for a CPU-less block, `powered` means "the light shows green", not "on".
      - **Error beep:** our own sound, `modularcomputers:error_beep` (`ModSounds`,
        `sounds.json`).
        - It's a 170 ms band-limited square wave at a steady 180 Hz: low, buzzy, not musical.
          The user rejected the note block as too musical, with a synthesizer wobble.
        - It's generated by `scripts/art/case_gui_textures.py     draws the case view's board, bus panel and icons (Pillow); PNGs are committed
scripts/art/error_beep.py` into
          `assets/modularcomputers/sounds/error_beep.ogg` (committed).
      - Pulling the CPU out of a running machine beeps the same way and cuts power (the
        display goes dark).
      - Inserting a CPU never turns the machine on; the player presses power.
    - **On the back, bottom right:** a dark housing at texels 9..13, holding a 3x3 button that
      sticks out half a texel. A plain right-click on the housing presses it. `BackPanel.hit`
      maps back-face clicks to its regions: `POWER` and `CASE`.
    - **In both GUI views:** left or right click sends `PressPowerPayload`, and the server
      checks reach.
    - **Sneak + right-click** anywhere also still presses it.
    - **Look:**
      - Ready, with a CPU and case power (block state `ready=true`), the button is lit (an
        emissive model element): red when off, green when on.
      - Otherwise it's **faded and unlit**, and red, since the machine can't be on.
        - In the GUIs it also shows faded green while the mouse is held down on it.
        - It's back to faded red on release (`ComputerButtons`, `powerHeld`).
      - There are four block models (`all_in_one[_on][_faded]`) on two base models (emissive,
        and plain), and four frames in `textures/gui/power_button.png`.
  - **Boot results:**
    - CPU and EEPROM: **mcos** (`Mcos`; user's name, 2026-10-12: the Modular Computers OS, Java
      version, a Lua version to follow), which the user keeps changing
      to try things out). Today it's **a tiny OS and shell, after OpenOS and busybox, all in that
      one class** (user, 2026-10-12: "keep all functions for the os just in that one class"):
      - a VFS with a mount table; `/` is a tmpfs in memory (like an initramfs) with `/mnt` and
        `/tmp`; every hfs disk on the bus is mounted automatically, like udisks (`sata:N` at
        `/mnt/sata/N`, others at `/mnt/id/<uuid>`), following the bus's interrupt; disks that
        leave are unmounted;
      - the shell runs on the console, editing lines itself as readline does in raw mode
        (Backspace, Ctrl+C/U/L, Up/Down history), with quotes, backslashes, `;`, `>` and `>>`;
      - commands: help, ls [-l], cd, pwd, cat, echo [-n], touch, mkdir [-p], rm [-r], cp, mv
        (across disks: copy and remove), mount, umount, df, mkfs [-t hfs|raw], lsdev, free, acpi,
        uptime, clear, halt.
      The screen-labelling experiment is gone; every terminal shows the shell.
    - CPU, no EEPROM: the soft gray **ABRT** screen, `ABRT: no EEPROM installed`.
    - No CPU: the machine won't turn on.
    - Part changes take effect at the next power-on, except that removing the CPU cuts power.
  - **Display:**
    - It shows on the block face's glass (`AllInOneRenderer`) and in the screen view.
    - Cells have foreground and background colors. In the world, backgrounds are drawn with
      `submitTextBackground`.
    - The screen view sizes itself from the resolution in `OpenDisplayPayload`, so it's right
      from the first frame: 90% of the window, keeping the aspect ratio, over the dimmed
      world.
      - User requirement: no OC-style placeholder box, and not 100% of the window.
      - It doesn't pause the game, and it closes when the block is gone or out of reach.
    - Resolution is 50x16 cells of 8x16, so text is readable on a one-block face.
    - Text uses Minecraft's proportional font, centered per cell. A monospace bitmap font is
      future work.
  - Power, parts, display contents and colors, and the console state are saved and synced.
    Toggling plays a stone-button click.
- **Item hints** (user design, 2026-10-11; `hint/`, `client/hint/`): every mod item's tooltip
  shows, under its name, short data (a summary), then "Hold [Shift] for details". With Shift:
  how it works, then "Crafting:" and a 3x3 crafting grid, an arrow, and the result.
  - Built on Fabric and vanilla, after checking what exists: an item's `getTooltipImage` returns
    a `HintTooltip`, and Fabric's `ClientTooltipComponentCallback` turns it into
    `HintComponent`, which draws text, icon rows (`Hint.Icons`) and the grid. Vanilla puts a
    tooltip image right under the name, so the hint draws its own text lines too, and mod
    items add no `appendHoverText` lines.
  - Every mod item is a `HintedItem` or `HintedBlockItem` (`Hinted.summary` / `details`). The
    hint content is plain data (`Hint.of(stack, details)`), testable on the server.
  - **Grids come from the real recipes:** the mod asks Fabric to synchronize shaped recipes to
    clients (`RecipeSynchronization.synchronizeRecipeSerializer(ShapedRecipe.SERIALIZER)`), and
    `ClientRecipes` keeps the ones that make mod items. A tag cell cycles its items each
    second. The battery upgrade's recipe type is synchronized too. Items without a recipe (the
    all-in-one) show an empty grid and "No recipe yet"; broken things (the broken computer) show
    no grid: they're found, never made (user). A blast furnace recipe (silicon) shows as its input
    over a blast furnace (`ClientRecipes`; `BlastingRecipe`'s serializer is synchronized too).
- **Chips** (user design, 2026-10-12): nether quartz smelts to **silicon** in a blast furnace only
  (`recipe/silicon.json`); a **photomask** (`PhotomaskBlock`, `PhotomaskBlockEntity`, `PhotomaskMenu`,
  `PhotomaskScreen`) prints silicon into **chips**: a wafer takes `WORK` (8) turns and gives
  `CHIPS` (4). **Right-click opens its slots; crouch + right-click turns its crank** (user,
  2026-10-12), and its brass gear turns an eighth on its front
  (`PhotomaskRenderer`, a quad drawn with `submitCustomGeometry`, lit from the block in front;
  clients learn of a turn by a block event, `SPIN_EVENT`, and ease the gear round); a charged
  battery in its bay runs it: with power it's on (gear spinning, block state `working`), idling
  at 5 units a tick with nothing to print (like an idle tier 3 CPU; user 2026-10-12), and working
  a turn every 5 ticks for 100 units more while there's silicon. Chips go to its output slot. **It's also a bus device** (user, 2026-10-12; `device/Photomask`, type
  `fabricator`, no methods yet: an API comes later): cabled on every side but its gear face, at
  `id:<its id>`; while a running computer has it on its bus it runs on the bus's power first
  (idle draw 5, plus 100 a turn charged to the computers supplying it), else on its battery. Right-click with silicon or a battery puts it in. Its slots light up
  for silicon and batteries, as the case view's do (`SlotHints`). Its window is the machine: a
  riveted steel plate with the mask's glass and chrome traces behind the slots, an arrow that
  lights as a wafer prints, and empty slots showing a ghost of what goes in them (silicon, a
  battery, chips), named by a tooltip (user: pictures, not words). It keeps its contents when broken, like the holders. Its recipe needs chips (iron,
  glass pane, two chips, amethyst lens, copper, redstone): an intentional circle, so the first
  photomask is found (an ancient site) or bought (a villager), not yet built. Art:
  `scripts/art/photomask_textures.py`. Items without code of their own can explain themselves
  with Shift through `hint.modularcomputers.details.<item>` in the lang file.
- **The first photomask** (user design, 2026-10-12) is found or bought, never built first:
  - **The ancient lab**, a world-gen ruin (`worldgen/structure/ancient_lab.json`, a one-piece
    jigsaw on the surface, every ~40 chunks in plains, forests, taiga, savanna, meadow, hills):
    a copper-trimmed stone lab with its front roof fallen in, workbenches with **broken
    computers** (`BrokenCaseBlock`, a cracked-open model: lid askew, bezel fallen forward, the
    board inside showing; decoration; broken blocks break to nothing, no drops, user 2026-10-12), cables along the floor, a chest of parts
    (`loot_table/chests/ancient_lab.json`: cables, chips, silicon, copper, gold, redstone, now and
    then an EEPROM or RAM; chance drops, weighted; and in about one chest in twenty, a flat tier 1
    battery: its own pool with a 5% chance, user 2026-10-12) and a **working photomask** on its bench. Weathering (moss,
    cracks, missing cables and windows) comes from the processor list at generation. The template
    is written by `scripts/data/ancient_lab_structure.py` (26.3 structure palettes use `id` and
    `properties`, not `Name`/`Properties`; processor `output_state` is a plain id).
  - **A toolsmith at expert level** sells one for 24 emeralds and 4 glass panes (villager trades
    are data in 26.3: `villager_trade/toolsmith/4/emerald_photomask.json`, added to the tag
    `minecraft:toolsmith/level_4`).
  - Programs from ruins and traders, and books with programs, come later (user).
- **Recipes** (user design, 2026-10-12; `data/modularcomputers/recipe/`, checked grid by grid by
  `RecipeGameTest`; the draft page with game textures: a private draft page):
  real computer materials from vanilla, few custom components, each grid laid out like the part
  (CPU lid on top, pins below). **Chips** take the place of silicon in every part (they replaced
  quartz, user 2026-10-12; see "Chips" below),
  amethyst the CPU's clock crystal, copper boards and cable cores, gold pins and contacts, iron
  frames; tiers are iron, gold, diamond. **Every tier above 1 is built around the tier below, in
  the center** (user: an upgrade keeps the old part from being junk); a used part (with an id)
  upgrades too, and a battery keeps its charge. Notable choices: every radio (WLAN, Bluetooth)
  takes an ender pearl and an emerald (beryl: beryllia under a transmitter's amplifier), Bluetooth
  adds lapis; cable is dried kelp around copper (8); the case's note block is its speaker; a
  terminal's keyboard is chiseled stone bricks; screens are glass, glowstone from tier 2,
  prismarine crystals at tier 3; the audio card a jukebox; the hub four comparators around quartz;
  the paint gun red, green and blue dye over a dispenser. Cases need something earned: tier 2
  breeze rods (trial chambers; its fans), tier 3 echo shards (ancient cities; memory) and a heart
  of the sea (buried treasure; power). The robotic piston and paint gun take no radio parts (their
  radio is a card). The all-in-one has no recipe (dev kit).
  - The Shift check reads the real key (`Minecraft.hasShiftDown`), which client tests can't
    press; tests set `HintComponent.detailsOverride`.
- **Settings** (`config/ModConfig`, user request 2026-10-11): `config/modularcomputers.properties`
  in the game folder, `key: value` lines (Java properties).
  - The file is written with commented defaults the first time the game starts. Settings a
    file doesn't have yet (it's from an older version) are appended to it, so they show up to
    edit. Bad values fall back to the defaults, with a warning in the log.
  - Settings: `battery_tier1_duration`, `battery_tier2_duration`, `battery_tier3_duration`
    (days, 0 = never runs out), `battery_tier2_recharge_rate` (0.5).
  - `/modularcomputers reload` rereads it (operator permission). A server sends its settings
    to joining players and after a reload (`SettingsPayload`), and a client goes back to its
    own file on disconnect.
  - Where it is: a launcher install's `.minecraft/config/`, the dev client's `run/config/`
    (`./gradlew runClient`), or `build/run/productionClientGameTest/config/` for the tests.
    The tests run with the defaults.
- **Lua runtime:** the user is building their own Lua runtime as a separate project, and it
  isn't ready. Leave VM/runtime work alone and keep building the computer's other features.
  Machine behavior stays a stand-in (like the "hello world" greeting) until the runtime lands.
- **Screens and cables** (built 2026-10-11, the user's request):
  - **Screen** (`ScreenBlock`, `ScreenBlockEntity`): a monitor block, 50x16 like the built-in
    display. It faces the player who places it.
    - The face is one plain pane of glass, 14x9 texels across the middle: the same shape as
      the screen view. It sits in an even beige frame. The user rejected a chin with a badge
      and light under the glass: it looked like a blurry keyboard.
    - Right-click opens its screen view: just the display, with no power button and no view
      toggle. The user wants the power button **only on the case**, not on every screen on
      the network (2026-10-11).
    - Each screen has a UUID (its holder id, saved), its bus order key `id:<uuid>`. Broken and
      placed again, it's the same screen (the id goes with its item); a newly crafted one is new.
    - It saves its display and sends changes to clients from its own tick (`TextBuffer.changes`).
  - **Cable** (`CableBlock`, a vanilla `PipeBlock`, 4 pixels thick): joins any neighbor that
    takes a cable on that side, and its multipart model shows each join.
    - Cables join other cables, and screens and computers on **any side but the front**
      (`Cabling.hasPort`; the front is glass or keyboard).
    - **Screens connect like a cable** (user, 2026-10-11; future blocks may too). A row of
      screens is one network, and a screen against a computer's side is connected
      ("adjacent" in the bus order). Each step still needs a port on both sides: never
      through glass or a computer's front.
      - A screen another computer has **disconnected** still passes the connection on (user):
        the plug is per computer, and other computers see the screen as connected.
    - Computers don't pass it on (a computer isn't a hub), and two non-relaying blocks
      touching aren't connected.
  - **Discovery** (`cable/CableWalk`, plain Java, breadth-first, nearest first; `Kind.RELAY`
    blocks are found as devices and carry the walk on; "cable, Nm" counts every block between):
    - A computer walks its cables in its server tick when any cable, screen or computer was
      placed or removed anywhere (`Cabling.changes()`), and every 20 ticks anyway, for chunks
      loading and unloading.
    - It never looks into unloaded chunks.
    - Only screens are cabled devices so far. Another computer's built-in display is its own.
  - **Expansion hub** (`ExpansionHubBlock`, `ExpansionHubBlockEntity`): 4 card slots on the
    network, like an external expansion chassis. It takes a cable on every side and passes
    the connection on.
    - Each card in it is a cabled device of every computer wired to it. The key is
      `id:<card serial>`, and the location reads "hub 2, 3m".
    - Each card can be unplugged on its own.
    - A card works where it's installed: a redstone card in a hub will emit and detect
      redstone at the hub (user, 2026-10-11). Nothing works yet.
  - **Robotic piston** (`RoboticPistonBlock`, a `DirectionalBlock`, placeholder): its `FACING`
    is its one cable port. It will push on every other face.
    - It's placed with the port toward the block it was put against.
    - It has one slot inside, for a Bluetooth card instead of a cable. It's a cabled device
      (`ACTUATOR`) and doesn't pass the connection on.
  - **Paint gun** (`PaintGunBlock`, a `DirectionalBlock`, placeholder; user idea): it will
    shoot paint colors at nearby wool. Its nozzle is its `FACING`, set where the player looks,
    and it takes a cable on every other face. It's a cabled device (`ACTUATOR`), with an id
    and no slots (`PaintGunBlockEntity`).
  - **Bluetooth** (`block/Bluetooth`, `item/BluetoothCardItem`; user design 2026-10-11):
    - **A card links to a board, not to another card** (user, 2026-10-11): every block that
      can hold cards has a board id (a computer's `board_id`, a holder's own id), and a card's
      `BLUETOOTH_LINK` names one. Moving a card to another machine doesn't take its links along;
      on real hardware you pair with the machine, and the card is just the radio.
      - A link only works while **both** blocks have a Bluetooth card in (the radio). Any card
        will do: take one out and the links drop, put any in and they're back.
      - Links are many-to-one (many cards to one board), and any board can be the target, a
        hub's included, so cards daisy-chain (piston → hub → computer).
    - **Right-click** a computer, hub, screen, piston or paint gun that has a Bluetooth card,
      with a card in hand: the card in hand links to that block's board. Without a card there,
      the click goes on to the block, which opens its slots.
    - **Sneak + right-click** anywhere clears the card in hand's link.
    - A link joins the two blocks like a cable. The walk follows links both ways, from the
      start and from relays (hubs). A device at the end of a link doesn't pass it on.
      - Distance is the straight line in meters, and the row reads "bluetooth, 5m".
      - `CableWalk` is a nearest-first shortest-path search now, since links weigh more than
        one block.
    - Each side keeps a registry of loaded blocks holding Bluetooth cards. Holders update it
      on `setLevel`, content changes and loads, and leave it on `setRemoved`.
    - **While you hold a linked card**, the block with its target board gets a steady blue
      wireframe, seen through walls (`client/BluetoothTarget`, `Outline.BLUETOOTH`).
    - There's no range limit yet.
  - **The radio slot** (user design, 2026-10-11; first called the "pairing slot", renamed by the
    user since it's just a radio): every network block has a slot GUI with one slot for a
    Bluetooth card, its **radio**, and it's the only place such a block takes one (a hub's
    expansion slots refuse Bluetooth cards). Computers keep theirs in an expansion slot.
    - Where the slot is:
      - the screen: its only slot, opened by right-clicking its back, or with the view
        toggle in its screen view. The slot view has the toggle back, as the all-in-one's
        case view does; the user asked for it for convenience. With no power button, the
        toggle sits at the top.
      - the hub: a fifth slot, apart from its four expansion slots, drawn blue, labeled
        "Radio";
      - the robotic piston: its one slot;
      - the paint gun: its one slot, opened by right-click.
    - **An unlinked radio just receives:** other cards can link to the block's board, and the
      block stays shared.
    - **A linked radio pairs the block with that board** (a computer or a hub; the user likes
      devices pairing to the hub, not to the computer using it). A computer sees a paired block
      if it's paired with that computer, or with a hub the computer sees, and so on through
      hubs (`AllInOneBlockEntity.visibleDevices`, `Bluetooth.pairedTo`). So hubs daisy-chain:
      hub 1's radio linked to hub 2 means a computer seeing either sees both, and what's past
      them. Another computer's pairings never pass through. Both ways on purpose: the user
      first meant one direction, but chose two-way as easier for players to understand.
      - Its row reads "paired" (or "hub 2, paired"). It still carries the cable for everything
        else.
      - This is deterministic: it's saved in the cards, so it doesn't depend on which chunk
        loads first. The user found first-wins and last-wins hopeless for that reason.
    - **Unpaired devices are shared** by every computer that reaches them. That's racy by
      nature; the user calls it undefined behavior and accepts it.
  - The screen, hub, piston and paint gun share `CardHolderBlockEntity`: an id, card slots
    with a radio slot, save and sync, keeping cards and id in the dropped item, card sounds, Bluetooth registration, and
    `Cabling.changed()` when their cards change. They also share `CardHolderMenu` and
    `CardHolderScreen`. Hubs, pistons and paint guns use `OutlineRenderer` for the outlines.
  - **One screen, many computers:** a screen can be wired to several computers, and each
    has it on its bus.
    - Each writes to it; the last write wins, and powering one off blanks it.
    - Proper sharing waits for the "virtual multicast text buffer" design (below).
  - **The console writes to every display in its group** (see the console's group under
    "Mock runtime"); the EEPROM chooses by unbinding displays.
- **Bus order and gaps:**
  - See the bus order panel above for the rules (case fixed, external saved by id, capacity,
    bumping, waiting, forget).
  - The bus walk the machine sees is **gapless**: disconnected and absent devices are left out
    of `Bus.devices()`. Whether firmware should see gaps is still open (`docs/PLAN.md`).
- **Roadmap and ideas: `docs/PLAN.md`.** Record new plans and decided-but-unbuilt ideas there.
  Move items into this section as they're built.
- Design rule: machine logic stays in plain Java (`machine/`, `bus/`, `console/`, `device/`,
  `display/`) and is unit-tested. Minecraft glue goes in `block/`, `item/`, `menu/` and
  `network/`, and rendering goes in `client/`.

### Machine architecture (agreed with the user, modeled on real PCs)

- **CPU (`Cpu`):** an installable part with almost no logic of its own. At power-on it loads
  the program from the motherboard's **EEPROM slot** and executes it.
  - The slot is a fixed place, like a real CPU's reset vector, so the CPU never walks a bus to
    find firmware.
  - The CPU's only behavior of its own is the **abort screen**, like a kernel panic. With an
    empty slot, or when the program throws, it does this and halts, with power staying on:
    writes to the console, as escape sequences: colors `ABORT_FOREGROUND` on `ABORT_BACKGROUND`
    (soft gray; the user doesn't want blue), clear, cursor home, then `ABRT: <reason>`
    (`Cpu.NO_EEPROM`, `Cpu.CRASHED` plus the message, or `Cpu.RETURNED`).
- **EEPROM (`Eeprom`):** holds **plain text, whatever the CPU's architecture** (user design,
  2026-10-12). Today's CPUs are Java machines: the text's first line names a `Program` class with
  a public no-argument constructor, which the CPU loads (`Cpu.load`; looked at before it's
  initialized, so naming anything else runs nothing). A Lua CPU will read the same text as Lua.
  Blank: `ABRT: EEPROM is blank`; not a Java program: `ABRT: EEPROM: exec format error` (Linux's
  ENOEXEC). A new EEPROM comes flashed with mcos (`Mcos`'s class name is
  the item's default `eeprom_image`). **Crafting one alone wipes it** (user): no id, blank
  (`ClearIdRecipe`); its hint shows what it holds ("Holds: ...", "Blank"). Tests attach Java
  program objects with `Eeprom.flash(Program)`, like an in-circuit programmer; the CPU runs an
  attached program in place of the image.
- **Mock runtime** (`runtime/`, built 2026-10-11 after a design discussion with the user, modeled
  on OC's `machine.lua` + `component.invoke` but redesigned; OC is never named in code):
  - **Names:** `bus` and `device`, never "component" (user rule, code and docs).
  - **The motherboard API (`runtime/Board`)** is everything a program can do: `list()` (the
    bus's devices in bus order, each a `DeviceInfo(address, id, type)`, a table in Lua), `status()` (`BusStatus`), `type`, `methods`, `doc`,
    `invoke(address, method, args...)`, `waitForInterrupt(seconds)`, `uptime()`, and what the
    firmware reports (user, 2026-10-12): `power()` (ACPI: the case's store, each battery by bay
    and tier, the load: `PowerInfo`), `hardware()` (SMBIOS and `/proc/meminfo`: case tier, CPU
    tier and bus capacity, each RAM stick's slot, tier and size, memory total and used; used is
    0 until the Lua runtime counts it: `HardwareInfo`) and `info(address)` (like `udevadm info`:
    address, id, type, label, then what the kind adds: a tier, a size, a terminal's width, height
    and colors; `Device.info`). The board reads the case through `machine/Chassis` (the case's
    `CaseChassis`; `Chassis.BENCH` for a bare board: tier 0, endless power). **Labels** are what
    the player named the item on an anvil (`ModComponents.label`), like a sticker; programs can't
    set them yet. Screen blocks have none yet. Addresses are
    the bus keys, three kinds as on a real system (user design, 2026-10-11):
    - **Paths** name slots, fixed by the slot: `eeprom`, `sata:0`, `exp:2`.
    - **Ids** name a device however it's connected: `id:<uuid>`. **Every device has one**
      (user, 2026-10-11: nothing is excluded): a screen's or block's id, a part's or card's
      `SERIAL`, the built-in terminal's `terminal_id`. The user rejected `cable:`: a program
      shouldn't care whether a device is cabled or Bluetooth, or in a hub. A part in a slot
      answers to both its path and its id (like Linux's `by-path` and `by-id`;
      `Bus.setDevices(devices, aliases)`, `Bus.id`); `list()` gives both.
    - **The built-in terminal has no name of its own** (user, 2026-10-11): its address is its
      id. A program that wants it takes the first terminal listed, since the connectors come
      before external devices, as UEFI and Linux take the first display found as the primary.
      A case without one simply has none. (`edp`/`kbd` were dropped: eDP carries only the
      picture, and `tty` names every terminal, not the built-in one.)
    - **Virtual devices** are bare names, fixed by convention: `console` (the user: something as
      simple as `/dev/console` might as well be just `console`). The console is UEFI's
      ConSplitter, so it already is the "virtual multicast text buffer". **The console is the
      group's multicast device for input and output both** (user, 2026-10-11): it writes to
      every terminal's screen and reads key presses from every terminal, merged
      (`console.read()`), as UEFI's ConSplitter does `ConOut` and `ConIn` and Linux's
      `/dev/console` (`console=tty0 console=ttyS0`) does. Each terminal stays readable on its
      own too.
    - **Terminals** (`device/Terminal`, type `terminal`; user decision 2026-10-11): a screen and
      a keyboard are one device, like a VT100, so there's no separate keyboard device (the
      user left that complexity out). The screen block and the all-in-one's front are
      terminals.
      - **They're VT100/xterm decoders** (user, 2026-10-11: "vt102, but really what modern pty
        terminal emulators support"): programs never touch cells or positions, they only
        `write(text)` with escape sequences, and the terminal decodes them
        (`display/VtDecoder`: cursor moves, erase, insert/delete, scroll region, SGR with 16,
        256 and 24-bit colors, new line mode `LNM`, autowrap, cursor visibility, the alternate
        screen, reports `5n`/`6n`/`c`; unknown sequences ignored). Methods: `write`, `read`,
        `getSize` (like the pty window size, `TIOCGWINSZ`). Direct writes are raw: LF only
        moves down unless the program sets `ESC[20h`. The decoder's state (cursor, colors,
        modes) is saved with the cells (`display_state`). Nothing draws a cursor yet.
      - **Three tiers** (user design, 2026-10-11; `device/TerminalTier`, `display/ColorDepth`;
        blocks `tier1_screen`..`tier3_screen`, `ScreenItem`): the same decoder, showing what it
        can of the colors it's sent. Tier 1 is text only (every cell in the default colors, no
        reverse either), tier 2 shows the 16 basic colors (256-color and 24-bit map to the
        nearest), tier 3 every color (pixel graphics planned for it). Each is a real DEC model
        (2026-10-12), named in its tooltip, and answers Device Attributes (`ESC[c`) as that model
        did: tier 1 a **VT102** on green phosphor (`?6c`), tier 2 a **VT525**, DEC's color text
        terminal (`?65;22c`), tier 3's tooltip says "Sixel" (user, 2026-10-12: the plan, not
        built yet) and it answers `?63;22c` (a VT340's level; it adds `;4`, sixel, once sixel is
        built, so programs never send pixels to a terminal that would drop them). The front's frame shows the tier as 1-3 dots, iron, gold and diamond. The
        all-in-one's built-in terminal is tier 3.
      - **Input:** keys wait in its queue until `read()` (as do its answers to reports); while
        any wait, its interrupt is on, or the console's if it's in the console's group.
      - **Only the terminal the player has open gets the keys** (user, 2026-10-11): typing in a
        screen view (`DisplayScreen`) sends `TerminalInputPayload` to that terminal.
        Characters come from `charTyped`; other keys send what an xterm keyboard sends
        (`client/screen/TerminalKeys`): Return CR, Backspace DEL, Tab, Ctrl+letter a control
        character, arrows `ESC[A`..., Home/End, Insert/Delete, PageUp/PageDown. Escape still
        closes the view. The server checks reach.
      - **SDL only sends typed characters while text input is on** (26.3 gotcha; the user found
        typing did nothing): vanilla turns it on for a focused text box. `DisplayScreen` turns
        it on in `init` (`Minecraft.onTextInputFocusChange(this, true)`) and off in `removed`.
        The client test's `typeChars` injects characters directly, so it checks the flag too
        (`TextInputManager.textInputEnabled`, by reflection).
    - **The console's group** (user design, 2026-10-11): `console.terminals()` lists the bound
      terminals by address in bus order; `console.unbind(address)` takes one out (the console
      stops drawing on it and reading from it, what's on it stays, the program owns it through
      its address); `console.bind(address)` puts it back and redraws the console on it. Names
      from Linux's vtconsole `bind`; the user rejected "graphics mode" and numbered VTs. Not a
      terminal on the bus: a soft failure. Every terminal is bound by default. The console's
      size is the smallest bound terminal.
    - **No graphics card, one console buffer** (user, 2026-10-11, real-world "example 2", like a
      USB display adapter, not a GPU with a monitor on a cable): a screen that leaves the bus
      loses what it showed, and one that joins, new or plugged back in, is a new arrival, bound,
      and gets the console's copy drawn on it. Unbinding lasts only while the device stays on
      the bus (`ConsoleSplitter.busChanged`); power-off rebinds everything. GPUs may come later
      (then a screen's picture would live on the board and survive a replug).
  - **A program ends with `Board.halt()`** (user, 2026-10-11), like `cli; hlt` or Linux's
    `reboot(HALT)`: it never returns, the CPU stops, **power stays on** and the screens keep
    what they show (and the console still redraws onto screens plugged in later). **Falling off
    the end is a panic**, as when Linux's init exits ("Attempted to kill init!"): the CPU's ABRT
    screen, `ABRT: EEPROM returned without halting`. A program that wants its text to stay up prints once, then halts.
  - **Programs** (`Program.run(Board)`) are plain Java that reads like Lua. Each runs as a
    coroutine on a virtual thread (`ProgramRunner`); every board call is handed to the host and
    carried out on the server thread, so device code never runs off-thread.
  - **Scheduling is the host's** (user): calls are immediate, but a machine gets
    `Machine.DEFAULT_CALLS_PER_TICK` (20) calls a tick, then is paused inside its next call until
    the next tick. Power-on runs the first turn at once. Pure-Java loops that never call can't
    be preempted (accepted for mocks). **TODO:** running machines off-thread, asynchronously,
    and sharing the tick; not built.
  - **Device methods** are public `Result name(Arguments)` methods annotated `@Method(doc)`
    (`DeviceMethods` finds them). **Nothing a device does can stop the machine** (user rule,
    like a status bit a driver reads): unknown device or method, bad arguments ("bad argument #1
    (number expected, got string)") and device exceptions all come back as `Result.fail`.
    Only the program's own uncaught error stops it (the CPU's ABRT screen).
  - **Values** (`Values`) are Lua's: nil, boolean, Long and Double kept apart, String, List
    (sequence), Map (table), and **handles** (`runtime/Handle`, Lua's userdata): a value a device
    hands a program, with methods of its own, called with `Board.invoke(handle, method, args...)`;
    a closed one fails softly ("closed handle"). Strings are bytes for device data (files go one
    character per byte, ISO-8859-1).
  - **Disks** (user design, 2026-10-12; `device/Disk`): data in the world save by the disk's id
    (`AllInOneBlockEntity.diskFolder`: `modularcomputers/disks/<id>`), so it goes with the disk to
    any computer. Two modes, set like formatting (`setMode`, which erases it; `getMode`): **hfs**
    (host filesystem, how a disk comes; the user's name): the disk serves files, real files in
    that folder, like Plan 9's 9P where a device serves files itself: `open(path, mode)` (C's
    fopen modes) gives a `Disk.FileHandle`, a proxy holding the disk and the real host file, with
    `read(n)` (at most 4096), `write(data)`, `seek(whence, offset)`, `close()`; and `list`
    (directories end in /), `stat`, `mkdir`, `remove`, `rename`, `getCapacity`, `getUsed`.
    **Every write goes to the host file at once** (user: no deferred writes; OC buffered). Paths
    can't climb out of the disk. Space counts bytes plus 512 for each file and directory. At most
    16 open files a disk; they close when the disk loses power or leaves the bus (and, once there's
    a Lua runtime, when the handle is garbage-collected). **raw**: sectors of 512 bytes in one
    image file (`<id>.img`): `getSectorSize`, `readSector(n)`, `writeSector(n, data)`. A disk is
    another device when its id changes; ids of things that hold data (EEPROMs, disks) can't be
    cleared by crafting (their data goes by it). `Arguments.check*` converts like Lua (2.0 is an integer; numbers
    are strings).
  - **Interrupts** are per device and level-triggered (user design): `Device.interruptPending()`
    is on while the device has data for the program; the data stays in the device until read.
    The bus itself raises `Board.BUS` when its devices changed since the program last listed
    them. `waitForInterrupt(seconds, sources...)` waits only on the named sources (all when
    none are named), like `poll()` on the files a program cares about, and returns those
    pending (bus first, then bus order), or none on a timeout. Since interrupts are
    level-triggered, a program that waits on everything must read everything, or it spins
    (an interrupt storm); waiting on just what it reads avoids that (user, 2026-10-11).
  - **Interrupts wake the CPU at once** (user asked to cut keystroke latency, 2026-10-11): a
    device with an `InterruptLine` (terminals) raises it when data arrives (`Terminal.press`),
    and every computer that has it on its bus (wired in `Machine.supply`/`release`) runs a
    turn right then, between ticks, if its program is waiting (`Machine.wake`), as a real
    interrupt pulls a CPU out of `HLT`. Wakes share the tick's call budget (`callsThisTick`), so
    mashing keys buys no extra calls; power counts at most one CPU tick a game tick. After any
    turn, bus devices `flush()`: a terminal's block sends its screen to players right away
    instead of at its next tick. This took out the 0-50 ms wait for the tick.
  - **Only changed rows go to players** (user, 2026-10-11; `ScreenRowsPayload`, `ScreenRows`):
    the server's terminal is the only decoder, and players' copies just take cells (the glass
    shows what the device shows). `TextBuffer` marks changed rows (`takeDirtyRows`); after a turn
    or on the block's tick, those rows (characters, colors as 3-byte RGB) go to the players
    tracking the block. A typed key: 363 bytes, where the whole block entity was 15,385
    (measured by a game test). The full block data still goes on first load and other
    changes. The user turned down a client-side decoder (sending the bytes, decoding on both
    sides): a client-only bug could show a screen the server never decoded.
  - **Saving a screen keeps the decoder's whole state** (`block/ScreenSave`, used for terminals
    and the console's copy): cells, cursor, colors, modes, the saved cursor, a half-received
    escape sequence and the main screen behind the alternate one, so a reload carries on
    exactly.
  - **Motherboard supervision** (`Machine.tick`, every tick): CPU gone or power gone, and the
    machine dies with the error beep, like a socket's CPU-present pin or a PSU's power-good.
    The event-driven checks (pulling a part) still act at once.
  - **Not saveable** (user): a running program lives in its thread. Unloading stops it
    (`Machine.unload`); after a load a powered machine's CPU is halted. Save/restore waits for
    the real Lua runtime.
  - **Tests bring their own programs** (user, 2026-10-11): never assert on `Mcos`,
    which changes. Unit tests build their own `Program`s; game tests flash one onto the EEPROM
    after inserting it (`Eeprom.flash`, like reflashing firmware; `gametest/TestPrograms`).
  - Device methods so far: the console (`write`, `read`, `getSize`, `getModes`, `setMode`,
    `terminals`, `bind`, `unbind`), terminals (`write`, `read`, `getSize`), disks
    (below) and the EEPROM (`get`, `set`: its image, no size limit for now (user, 2026-10-12; EEPROMs will
    later hold a bootloader, with the OS installed on a disk), like
    `flashrom -r`/`-w`; kept on the item as `ModComponents.EEPROM_IMAGE`; another chip is another
    `Eeprom`, by serial. The CPU still runs the Java stand-in whatever the image holds, until the
    Lua runtime).
- **Motherboard (`Motherboard`):** holds the soldered parts (`Hardware`/`Part`, memory), the
  CPU socket, the EEPROM slot, and two buses.
  - **The bus is the motherboard's firmware, not the case's** (user, 2026-10-11: "the all in one
    HAS a motherboard"; new case types will come). `Motherboard` owns the connectors and their
    addresses (`CONNECTORS`: the built-in `TERMINAL` (no path: its address is its id), `eeprom`,
    `sata:0-3`, `exp:0-3`, and their fixed places 1-10), the `BusOrder`, `id:` aliases, bus positions, and building the bus
    (`plug(connector, device, id)`, `setExternal(found)`, `layout(now)`; `Machine.rebuildBus`
    applies it with hot-plug, `Motherboard.rebuildBus` without, for clients). A case only wires
    things up: the all-in-one plugs its terminal into `TERMINAL` with its id, turns slot
    items into devices, and finds cabled devices. Keep it that way: no bus logic in block
    entities.
  - **Physical bus (`bus/Bus`):** the real devices in bus order (`BusOrder`: the connectors'
    devices first, in their fixed order, then external ones in the player's order). Each describes itself
    (`Device#type()`, the class code) and is used through its own Java type, the capability
    (like UEFI's GOP).
    - There are no drivers: plug and play.
    - The CPU and memory are not on it, as on a real PC.
  - **Virtual bus (`bus/VirtualBus`):** devices that always exist, found by fixed name, not by
    walking (like Linux's virtual devices or UEFI's `ConOut`). Today it holds only `console`.
- **Machine (`Machine`):** the power state.
  - `pressPower()` returns `ON`, `OFF` or `NO_CPU`; `NO_CPU` means refused.
  - `cpuRemoved()` cuts power if the CPU was pulled while running.
  - On means a CPU is installed and was handed control.
- **Console (`console/ConsoleSplitter`):** modeled on UEFI's console splitter and Linux's tty
  layer for `/dev/console`.
  - It's never null. It sends the same text to every terminal in its group (raw multicast,
    like `console=tty0 console=ttyS0`) and drops output when there are none. Each terminal
    decodes it at its own size.
  - **Its modes are termios's, by name** (user, 2026-10-11): `onlcr` (on by default, as on
    Linux) sends `\n` as `\r\n`; `setMode("onlcr", false)` makes it literal. Direct
    `id:<terminal>` writes have no tty layer: always literal. Power-off restores the
    defaults. Input-side modes (`icrnl`, `echo`, `icanon`) are undecided.
  - Its size is the smallest among its terminals. With none it reports `HEADLESS` (a real
    size, never 0×0, like Linux's dummycon).
  - Like a Linux virtual console, it keeps **its own copy of what it shows**: it decodes its
    own output onto `ConsoleSplitter.screen()` (saved with the computer, with the decoder's
    state). `redraw(terminal)` paints it onto a terminal that joins, as escape sequences
    (`VtDecoder.redraw`), the way tmux redraws an attaching terminal.
- **Hot-plug (`Machine.setDevices`):** the bus changes when a part goes in or out, a cable is
  plugged or unplugged, or the player reorders. While the machine runs:
  - devices that left get `powerOff()` (an unplugged screen goes blank);
  - displays that joined get the console redrawn onto them (like fbcon taking over a new
    framebuffer).
  - Clients only replace their `Bus` list; they never run devices.
  - A minimal EEPROM just prints to it. Advanced code can walk the physical bus instead.
- **Power-off:** every device gets `powerOff()` (displays blank), and the console resets.
- **Saved and synced:** the block entity saves and syncs the switch, the parts, the display
  (characters and colors) and the console state. Clients never run programs; they show the
  synced display contents. `Machine.restorePowered` sets the switch without booting.

### Versions in use (`gradle.properties`)

| Thing | Version | Notes |
|---|---|---|
| Minecraft | 26.3 | newest stable on 2026-10-09; 26.4 is in snapshots |
| Fabric Loader | 0.19.5 | |
| Fabric API | 0.162.0+26.3 | |
| Loom (`net.fabricmc.fabric-loom`) | 1.18.3 | latest stable; the template uses 1.18-SNAPSHOT |
| Gradle | 9.8.1 (wrapper) | |
| Java | 25 (Temurin, provisioned by Gradle) | |
| JUnit | 6.1.3 | |
| Mockito | 5.24.0 | |
| AssertJ | 3.27.7 | |
| foojay-resolver-convention | 1.0.0 | |

To check for updates: https://fabricmc.net/develop, `https://meta.fabricmc.net/v2/versions/game`,
`/loader`, and the official template https://github.com/FabricMC/fabric-example-mod (compare
its `build.gradle`/`gradle.properties` when porting).

### Settled decisions

- **Java package / maven group:** `com.payonel.modularcomputers`. The mod id is `modularcomputers`.
- **License:** MIT, the same as OC. It's in the `LICENSE` file and `fabric.mod.json`, and
  `build.gradle` copies `LICENSE` into the jar as `LICENSE_modularcomputers`.
- **Branch:** `master` is the mainline branch. Don't use `main` or `mainline`.

### Open decisions for the user (ask; don't assume)

1. **Mod icon and `contact` URLs** in `fabric.mod.json` are empty. There's no repo URL
   because the repo is local only for now.

## 4. Versioning scheme (decided)

```
MAJOR.MINOR.PATCH+MCVERSION        e.g. 0.1.0+26.3   ->  modularcomputers-0.1.0+26.3.jar
```

- `mod_version` (SemVer) is in `gradle.properties`. `build.gradle` appends
  `+${minecraft_version}` as SemVer build metadata.
- This is the Fabric ecosystem convention: Fabric API itself ships as `0.162.0+26.3`.
  Modrinth's guidance also accepts embedding the Minecraft version.
- Fabric Loader **ignores build metadata when comparing versions**. So `1.2.0+26.3` and
  `1.2.0+26.4` are the same feature set built for two games, and addon dependency ranges
  like `>=1.2.0` keep working across Minecraft versions.
- `fabric.mod.json` declares `"minecraft": "${minecraft_version_range}"`, currently `~26.3`
  (any 26.3.x hotfix), so the jar refuses to load on the wrong game.
- Bump rules:
  - PATCH: fixes.
  - MINOR: compatible features.
  - MAJOR: breaks saves, configs, or the addon API.
  - `0.x`: pre-release, anything goes.
  - Porting to a new Minecraft version alone changes only the `+MCVERSION` part.
- Tags: `v0.1.0+26.3`.
- Release branches, when the user asks: e.g. `release/mc26.3`.
- Details are in `docs/VERSIONING.md`.

## 5. What changed in Minecraft modding (context the user asked for)

- **Minecraft numbering is now `YY.D.H`** (year, drop, hotfix) since 2026: 26.1, 26.1.1,
  26.1.2, 26.2, 26.3, and so on.
  - Three or four drops come out per year.
  - Drops are usually binary-incompatible for mods: build one jar per drop.
  - Hotfixes usually stay compatible.
  - The last old-style version was 1.21.11.
- **Minecraft is unobfuscated since 26.1.**
  - Code uses Mojang's official names; there is no remapping step.
  - Yarn mappings are no longer officially supported.
  - Mods from 1.21.11 or older need at least a recompile.
- **Loom changes:**
  - The plugin id is `net.fabricmc.fabric-loom` (non-remapping).
  - Use plain `implementation`/`compileOnly`, not `modImplementation`.
  - Use the `jar` task, not `remapJar`.
- **Fabric API names now follow Mojang's**, e.g. `ItemGroupEvents` became
  `CreativeModeTabEvents`. `HudRenderCallback` was replaced by `HudElementRegistry`.
- **Vanilla renames we hit:**
  - The id class is `net.minecraft.resources.Identifier` (formerly `ResourceLocation`).
    Construct ids with `Identifier.fromNamespaceAndPath`, `Identifier.withDefaultNamespace`,
    or `Identifier.parse`.
  - `ServerLevel#getSharedSpawnPos` no longer exists.
  - `Block#codec()` is gone, so blocks don't declare a `MapCodec`.
  - GUI: `GuiGraphics` became `GuiGraphicsExtractor`, and `Screen#render` /
    `renderBackground` became `extractRenderState` / `extractBackground`.
    `graphics.pose()` is a JOML `Matrix3x2fStack` (`pushMatrix`, `translate`, `scale`).
    Draw text with `graphics.text(...)`.
  - The current screen moved off `Minecraft`: use `minecraft.gui.setScreen(...)` and
    `minecraft.gui.screen()`. Hide the HUD (F1) with `minecraft.gui.hud.toggle()` and check it
    with `isHidden()`.
  - `PoseStack#mulPose(Quaternionf)` is gone. Use `pose.rotateDegrees(Axis.YP, deg)` or
    `rotate(Quaternionfc)`.
  - Block entity renderers use render states. Implement `createRenderState`,
    `extractRenderState` (copy what you need off the block entity) and `submit(state, pose,
    collector, camera)`, and register with `BlockEntityRenderers.register`.
    - Text goes through `collector.submitText(pose, x, y, FormattedCharSequence, shadow,
      Font.DisplayMode, light, color, background, outline)`.
    - Full brightness is `LightCoordsUtil.FULL_BRIGHT`.
  - Block entity save/load uses `saveAdditional(ValueOutput)` / `loadAdditional(ValueInput)`.
  - Loot table pools use `"condition": {"type": ...}`, not `"conditions": [{"condition": ...}]`.
  - Model elements accept `"light_emission": 15` (and `"shade": false`) for glowing parts.
    A child model can't add elements to its parent's, so `all_in_one_base` holds the cube and
    the button, and the off/on models only swap the `#button` texture.
  - Container screens: `AbstractContainerScreen(menu, inventory, title, width, height)`.
    `imageWidth` and `imageHeight` are final. Draw the panel in `extractBackground`, after
    calling `super`.
  - Fabric menus that send data on open use `ExtendedMenuType<Menu, Data>(factory,
    StreamCodec)`, plus a provider that implements `ExtendedMenuProvider<Data>`. Open one with
    `player.openMenu(provider)`. Register the screen with `MenuScreens.register`.
  - Drop a block entity's contents in `BlockEntity#preRemoveSideEffects(pos, state)` with
    `Containers.dropContents`.
  - `submitTextBackground(pose, x0, y0, x1, y1, argb, mode, light)` fills a rectangle in text
    space (the four floats are corners). Use it for colored cell backgrounds on a block face.
  - Draw an item icon in a GUI with `graphics.item(stack, x, y)`.
  - GLFW isn't on the compile classpath. Use `InputConstants.KEY_*` for key codes, e.g. in
    client tests.
  - **Mouse buttons use SDL numbering: left = 1, middle = 2, right = 3.** GLFW's old numbering
    was left = 0, right = 1.
    - Always compare `MouseButtonEvent#button()` against `InputConstants.MOUSE_BUTTON_LEFT` /
      `_RIGHT` / `_MIDDLE`.
    - Pass those same constants to `TestInput.pressMouse`.
    - Hard-coding 0 or 1 once broke right-click for the user while the test still passed,
      because the test made the same mistake.
- **Rendering:** Minecraft 26.3 bundles LWJGL Vulkan and SDL libraries, but on the dev host it picked
  the **OpenGL** backend ("Using graphics backend OpenGL ... Mesa"). Keep an eye on this if
  Mojang changes the default backend.
- **Java 25 is required**, and IntelliJ 2025.3+ is needed for mixin support.
- **Fabric's official test harness:**
  - `fabric-loader-junit` for unit tests
  - server `@GameTest`s
  - `fabric-client-gametest-api-v1`, which boots the real client, creates worlds or dedicated
    servers, simulates input, clicks buttons, and takes or compares screenshots

Sources:
- https://fabricmc.net/2026/03/14/261.html
- https://docs.fabricmc.net/develop/automatic-testing
- https://support.modrinth.com/en/articles/8793369-version-number-recommendations

## 6. Build and test

### Commands

```sh
./gradlew assemble                  # compile + jars only
./gradlew build                     # compile + jars + unit tests: the fast loop (seconds, no Minecraft)
./gradlew runGameTest               # server game tests (boots a headless dedicated server)
./gradlew testAll                   # everything: build + server game tests + client game tests on Xvfb
./gradlew runProductionClientGameTest   # just the release-jar client tests (Loom starts Xvfb itself)
scripts/headless.sh ./gradlew runClientGameTest   # client tests on the dev classpath, under xvfb-run
scripts/headless.sh ./gradlew runClient           # run the game on a virtual display
scripts/setup-headless-host.sh      # one-time: install Xvfb (sudo or ~/.local) and verify OpenGL
```

### Test layers (pick the lowest that can express the test; see `docs/TESTING.md`)

1. **Unit tests** in `src/test/java`: JUnit 5/6 plus Mockito and AssertJ.
   - Call `SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();` to get vanilla
     registries.
   - **Gotcha:** in 26.x, `new ItemStack(...)` fails after Bootstrap alone ("Components not
     bound yet"). Tests that need stacks go in game tests until we build a helper that binds
     components.
   - **Architecture rule:** keep core logic (machine, VM bridge, component/signal routing,
     data formats) in plain Java behind small interfaces so it can be unit-tested with fakes.
     Keep the Minecraft glue thin.
2. **Server game tests** in `src/gametest/java`: methods annotated with
   `net.fabricmc.fabric.api.gametest.v1.GameTest` that take a
   `net.minecraft.gametest.framework.GameTestHelper`.
   - They run headless via `runGameTest`, which is part of `testAll` but **not** of `build`.
     Loom adds `runGameTest` to `check` by name; `build.gradle` removes it so `build` stays fast
     (the user wants `build` quick and iterative).
   - The run reports "All 56 required tests passed": 55 are ours (`AllInOneGameTest`,
     `CableGameTest`, `CardHolderGameTest`, `BluetoothGameTest`, `BatteryGameTest`), and one is
     of unknown origin (not investigated).
   - Check recipes with `level.recipeAccess().getRecipeFor(RecipeType.CRAFTING,
     CraftingInput.of(w, h, stacks), level)` (see `BatteryGameTest.craft`).
   - Test computers need a charged battery to power on (`ModItems.TIER1_BATTERY.charged()`).
   - `helper.useBlock` always goes to the block, even when sneaking. The real game skips the
     block when sneaking with an item in hand, so test sneak-use of an item with
     `stack.useOn(new UseOnContext(...))`.
   - `helper.destroyBlock` doesn't drop the block itself (its contents do drop). Check a
     block's own drop with `Block.getDrops`.
   - Cable and screen tests wait for the computer's tick to scan
     (`helper.succeedWhen`/`startSequence().thenWaitUntil`).
   - **A test times out after about 20 ticks by default.** A test that waits longer (for
     example `thenIdle` past a 20-tick rescan) needs `@GameTest(maxTicks = ...)`.
   - `helper.makeMockPlayer(GameType)` and `helper.useBlock(pos, player)` exercise real
     right-click handling. Call `player.setShiftKeyDown(true)` for sneak-use.
3. **Client game tests** in `src/gametest/java`, implementing `FabricClientGameTest`.
   - The current test:
     - asserts both entrypoints ran
     - takes a title-screen screenshot
     - creates a singleplayer world and waits for chunks to render
     - takes a screenshot
     - runs `/setblock` on the integrated server
     - asserts the block on the server and on the client
     - closes the world
   - The game then exits.
   - Useful API: `context.worldBuilder().create()`, `singleplayer.getServer().runCommand/
     runOnServer/computeOnServer`, `singleplayer.getConnection().getServerPlayer()/
     getClientLevel()/waitForChunksRender()`, `context.waitFor(...)`, `context.getInput()`,
     `context.clickScreenButton(...)`, `context.takeScreenshot(...)`,
     `context.assertScreenshotEquals(...)`.
   - Throwing (e.g. `AssertionError`) fails the run with exit code 1. This was verified with a
     deliberately failing assertion.

### Gradle wiring (`build.gradle`)

- `loom.splitEnvironmentSourceSets()`: `src/main` holds common code and `src/client` holds
  client-only code.
- `fabricApi.configureTests { createSourceSet, modId "modularcomputers-gametest",
  enableGameTests, enableClientGameTests, eula }` creates the `gametest` source set (its own
  `fabric.mod.json` lives in `src/gametest/resources`) and the `runGameTest` and
  `runClientGameTest` tasks.
- `runProductionClientGameTest` (Loom `ClientProductionRunTask`):
  - **Mods:** our `jar`, a `gametestJar`, and the `productionRuntimeMods` configuration.
    That configuration holds fabric-api plus **`fabric-gametest-api-v1` and
    `fabric-client-gametest-api-v1`, which the release fabric-api jar does NOT bundle.**
    Without them the client idles on the title screen forever.
  - **Display:** `useXVFB = true`.
  - **Run dir:** `build/run/productionClientGameTest`. The default would be `./run` in the
    repo root.
  - **Timeout:** 10 minutes.
- `tasks.withType(ProcessResources)` expands `${version}`, `${minecraft_version_range}` and
  `${loader_version}` in **all** `fabric.mod.json` files, including the gametest one.
- `testAll` depends on `build` and `runProductionClientGameTest`.
- **JDK provisioning:**
  - `settings.gradle` applies foojay, and `gradle/gradle-daemon-jvm.properties` (generated by
    `./gradlew updateDaemonJvm --jvm-version=25 --jvm-vendor=adoptium`) makes Gradle download
    Temurin 25 into `~/.gradle/jdks`.
  - No system JDK is needed.
- `gradle.properties` turns on parallel builds, the build cache and the configuration cache,
  with `-Xmx4G`.

### Client test worlds and gotchas

- **Test worlds have cheats off by default**, so the player can't run operator commands. A
  command the player isn't allowed to use answers "Unknown or incomplete command".
  `AllInOneClientGameTest` turns cheats on with
  `.adjustSettings(settings -> settings.setAllowCommands(true))`.
- `context.worldBuilder()` uses "consistent settings" by default, and the test spells this out
  with `setUseConsistentSettings(true)`:
  - the superflat preset, with seed `1`
  - time and weather frozen
- Teleports: format block centers as `getX() + 0.5`, never `"%d.5"`. With `"%d.5"`, block -4
  becomes `-4.5`, which is inside block -5.
- `context.getInput().setCursorPos(x, y)` takes window pixels (GUI coordinates times
  `getWindow().getGuiScale()`), and `pressMouse(0)` left-clicks.
- **`pressMouse` sends no modifier flags**, and 26.3 reads Shift from the click event's flags.
  So `holdShift()` + `pressMouse()` is a plain click.
  - To shift-click, call `screen.mouseClicked(new MouseButtonEvent(x, y,
    new MouseButtonInfo(MOUSE_BUTTON_LEFT, InputConstants.MOD_SHIFT)), false)` on the client.
    That still runs the real container click path.
- **Wait about 6 ticks between clicks.** Clicks less than 250 ms apart count as a
  double-click, which, for example, swallows "place the carried item".
- `lookAt(yaw, pitch)` aims at an exact point. Compute yaw as `atan2(-dx, dz)` and pitch as
  `-atan2(dy, horizontal)`.

- **The test input can't move the system pointer under Xvfb**, and every `setScreen`
  re-reads it (`resyncMousePosition`). So a test can't watch the real cursor. To catch an
  accidental recenter, check that no mouse grab happened: `MouseGrabs.count`, counted by the
  gametest-only mixin `gametest/mixin/MouseGrabCounter`.

### Pixel assertions in client tests

`AllInOneClientGameTest` reads screenshots with `ImageIO` and counts near-white pixels in
regions: the block face, and each display row.
- It hides the HUD first, so the crosshair and hand stay out of the frame.
- It was checked against a deliberate break: with the renderer disabled, it fails.
- Use the same approach for new visual features rather than golden-image comparisons, which
  break whenever vanilla's art or lighting changes.

### Outputs

- Jars: `build/libs/modularcomputers-<ver>.jar` and `-sources.jar`
- Unit reports: `build/reports/tests/test/`; JUnit XML: `build/test-results/`
- Screenshots: `build/run/clientGameTest/screenshots/` and
  `build/run/productionClientGameTest/screenshots/`.
  **Look at them** (with the image Read tool) when a change affects rendering.
- Game logs: `build/run/*/logs/latest.log`

### CI

`.github/workflows/build.yml` runs on ubuntu-24.04: Temurin 25, apt-installs xvfb, runs
`./gradlew testAll`, and uploads reports, screenshots, logs and jars. It is unused until a
GitHub remote exists.

## 7. Reference: the OpenComputers clone

- Location: `~/code/OpenComputers`, cloned from https://github.com/MightyPirates/OpenComputers.
- The checked-out branch is `master-MC1.7.10` (the repo default).
- The two actively maintained lines are `master-MC1.12` (newest, last commit 2026-08-04) and
  `master-MC1.7.10` (2026-08-04).
  - Use `git -C ~/code/OpenComputers show origin/master-MC1.12:<path>`, or check that branch
    out, for the 1.12 code.
  - Older and abandoned ports: `master-MC1.16` (2021), 1.8–1.11 branches, and `OC1.x-MC*`
    release branches. This shows OC's own branching model, which we are *not* copying.
- Layout:
  - mostly Scala (~790 files) in `src/main/scala/li/cil/oc`, plus Java (~240 files; the API is
    in `src/main/java/li/cil/oc/api`)
  - Lua (~270 files, OpenOS etc.) and the in-game manual (`doc`) under
    `src/main/resources/assets/opencomputers/` (`lua`, `doc`, `lang`, `recipes`, `textures`,
    `font.hex`, `loot`, `robot.names`)
- OC used Forge, Scala, `build.properties`-style versioning (`oc.version=1.8.10-snapshot`) and
  JNLua/LuaJ VMs. None of that carries over automatically; each choice gets re-decided for
  ModularComputers.

## 8. The host machine

- Linux Mint 22.2 (Ubuntu 24.04 base), kernel 6.17, 32 cores, 125 GB RAM, 1.8 TB disk.
- **Headless.** No X server runs, and `DISPLAY` is unset.
- **`sudo` requires a password**, so agents cannot apt-install. Ask the user to run sudo
  commands if a system package is truly needed.
- **Java:** the system has only OpenJDK 21/25 **JREs** (no `javac`). Gradle supplies the JDK.
- **Xvfb:** the system `xvfb` package is installed (`/usr/bin/Xvfb`, `/usr/bin/xvfb-run`).
  The earlier unpacked copy in `~/.local` has been removed. `scripts/setup-headless-host.sh`
  keeps its `~/.local` fallback for hosts without sudo.
- **OpenGL:** Mesa 25.2.8 llvmpipe (software) provides **OpenGL 4.5 core** under Xvfb, which
  is plenty for Minecraft. `mesa-vulkan-drivers` (lavapipe) is installed too.
- **GPUs:** the host was set up to pass GPUs through to VMs for other projects that have
  since been retired.
  - The 2× GTX 960 are bound to `vfio-pci`, and the GT 710 runs on `nouveau`.
  - That's why `nvidia-smi` fails even though `nvidia-driver-580` is installed. It's
    intentional, not broken.
  - Nothing here needs a GPU: tests render with Mesa llvmpipe under Xvfb.
- **Window manager:** none is installed or needed; client tests drive the game directly.
- **Audio tools:**
  - `ffmpeg`/`ffprobe` are installed (by the user, 2026-10-10). Use them to inspect sounds,
    e.g. `showspectrumpic` to check a beep has a steady pitch.
  - libsndfile, libogg and libvorbisenc are present, and `error_beep.py` encodes with them.
  - There's no pip; use the system Python with ctypes or Pillow.
- **Gradle:** a standalone Gradle 9.8.1 is in `~/.local/opt/gradle-9.8.1` (used only to
  generate the wrapper). Use `./gradlew`.
- **Git:** this repo's local config sets `user.name=payonel` and
  `user.email=payonel@hotmail.com`. No global identity is configured.
- **Harmless log noise:**
  - Realms/authlib 401 errors ("Failed to fetch user properties")
  - "Illegal option value 0 for Anisotropic Filtering"

## 9. Repo layout

```
AGENTS.md / CLAUDE.md        this handoff (CLAUDE.md just imports AGENTS.md)
LICENSE                      MIT
README.md
docs/TESTING.md              test layers, outputs, headless setup
docs/VERSIONING.md           version format, bump rules, branches
docs/PLAN.md                 roadmap and design ideas (record user plans here)
build.gradle, settings.gradle, gradle.properties, gradle/ (wrapper + daemon JVM criteria)
scripts/setup-headless-host.sh, scripts/headless.sh
scripts/art/all_in_one_textures.py   draws the block, GUI and item textures (Pillow); PNGs are committed
scripts/art/case_gui_textures.py     draws the case view's board, bus panel and icons (Pillow); PNGs are committed
scripts/art/error_beep.py            synthesizes the error beep as Ogg Vorbis; the .ogg is committed
scripts/art/case_tier_textures.py    tier 2 and 3 case textures (eras) from the beige ones, plus case models and blockstates
scripts/art/photomask_textures.py    silicon, chip, the photomask's faces and its gear
scripts/art/ancient_site_textures.py the broken computer's faces (from the beige case's)
scripts/data/ancient_lab_structure.py writes the ancient lab's structure template (.nbt)
scripts/art/power_down.py            synthesizes the power-down sound (the beep sagging and fading)
scripts/art/part_sounds.py           synthesizes the part insert/remove sounds (slides, clicks, thumps, a zap)
scripts/art/oggwrite.py              shared: writes mono Ogg Vorbis via the system's libsndfile (ctypes)
src/main/java/.../sound/ModSounds.java   sound events (error_beep)
.github/workflows/build.yml
src/main/java/com/payonel/modularcomputers/
  ModularComputers.java            MOD_ID, LOGGER, id(), INIT_MARKER; registers content + packets
  machine/                         plain Java: Machine (power), Cpu, Motherboard, Eeprom, Program, Mcos, Hardware, Part, PartKind
  bus/                             plain Java: Bus (physical), VirtualBus, Device, DeviceType
  console/ConsoleSplitter.java     the always-present console on the virtual bus
  device/                          Terminal (screen + keyboard), Disk, Card, UpsBattery (bus devices)
  display/                         plain Java: Resolution, TextBuffer, Viewport, DisplayLayout (GUI layout math)
  cable/CableWalk.java             plain Java: follows cables out from a block to the devices at the ends
  ModCreativeTab.java              the "Modular Computers" creative tab
  item/ModItems.java               the parts, cards and batteries
  item/BatteryItem.java            a battery tier: charge bar, tooltip, tier 2's self-charging
  item/BatteryChargingRecipe.java  charging a battery with glowing materials in the crafting grid
  power/                           plain Java: BatteryTier, BatteryCharge (drain, rest, charge), ChargeMeter
  menu/                            CaseMenu (part slots + inventory), ModMenus
  block/                           AllInOneBlock, AllInOneBlockEntity, BackPanel (back-face hit regions), ModBlocks (registrations),
                                   ScreenBlock, ScreenBlockEntity, CableBlock, Cabling (ports, walking, change counter), DisplayHost,
                                   CardHolderBlockEntity, ExpansionHubBlock(Entity), RoboticPistonBlock(Entity), PaintGunBlock(Entity)
  menu/CardHolderMenu.java         a hub's or piston's card slots
  command/ModCommands.java         /modularcomputers giveall (cheat: one of every mod item)
  network/OpenDisplayPayload.java  server->client "open this display"
  network/PressPowerPayload.java  client->server "power button clicked in a GUI"
  network/SwitchViewPayload.java   client->server "view toggle clicked" (screen view <-> case view)
  network/ReorderBusPayload.java   client->server "move this bus device up/down"
  network/ConnectDevicePayload.java client->server "plug this cabled device in / out"
src/client/java/com/payonel/modularcomputers/client/
  ModularComputersClient.java      registers renderer + packet receiver
  render/                          DisplayRenderer (text on a screen's glass, both blocks; the bulb's outline), TextGrid (cell layout)
  Identify.java                    the bulb: which devices are outlined, until when
  screen/DisplayScreen.java        the screen view (right-click; "modal mode")
  screen/CaseScreen.java           the case view (slots)
  screen/ComputerButtons.java      power button + view toggle, shared by both views
src/main/resources/                fabric.mod.json, assets (blockstates, models, items, textures, lang), data (loot table, pickaxe tag)
src/test/java/...                  unit tests (ModularComputersTest, machine/, display/)
src/gametest/java/.../gametest/    ModularComputersGameTest + AllInOneGameTest + CableGameTest + BatteryGameTest (server),
                                   ModularComputersClientGameTest + AllInOneClientGameTest + CableClientGameTest (client),
                                   MouseGrabs + mixin/MouseGrabCounter (counts mouse grabs for the client test)
src/gametest/resources/fabric.mod.json                                   test mod (entrypoints fabric-gametest, fabric-client-gametest)
```

## 10. Working conventions for agents

- Use `./gradlew build` (seconds) while iterating. Before claiming something works, and before
  every commit, run `./gradlew testAll`. Report failures with their output.
- Every new feature ships with tests at the lowest workable layer, plus a client game test
  when it has a visible or interactive part. Check screenshots when rendering is involved.
- Verify Minecraft and Fabric API names against the actual jars instead of from memory; many
  names changed with unobfuscation:
  - `javap` is at `~/.gradle/jdks/eclipse_adoptium-25-amd64-linux.2/bin/javap`
  - the Minecraft jar is at
    `.gradle/loom-cache/minecraftMaven/net/minecraft/minecraft-common-*/26.3/`
  - Fabric API module jars are under `~/.gradle/caches/modules-2/files-2.1/net.fabricmc.fabric-api/`
- Keep `gradle.properties` as the single place for versions.
- Match the existing code style: tabs, `final` entrypoint classes, short doc comments.
- Update this file when decisions are made or gotchas are discovered.
