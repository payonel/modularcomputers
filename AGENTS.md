# Modular Computers: agent handoff

This file is the full context for any agent session working on this repo. `CLAUDE.md` imports it
(Claude Code reads `CLAUDE.md`, not `AGENTS.md`; other agent tools read `AGENTS.md`). Keep it
current: when a decision is made or a gotcha is found, record it here.

Last updated: 2026-10-09 (project bootstrap session; host notes revised).

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
4. **Commits:** commit **autonomously**, without asking, every time a new addition is done
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
- Gameplay so far: one block, the **all-in-one computer** (`modularcomputers:all_in_one`).
  - It's a full cube in beige retro style, with a screen above a keyboard on the front face.
    It faces the player who places it.
  - It's listed in the mod's own creative tab, "Modular Computers" (`ModCreativeTab`). Fabric
    puts mod tabs on **page 2** of the creative inventory (the ▶ arrow by the tab title).
    It's also in creative search, and `/give @s modularcomputers:all_in_one` works.
  - **Parts:**
    - The display and keyboard are built in. Everything else is an item (`ModItems`) in the
      **case view**'s 15 slots (`CaseMenu`):
      - CPU, EEPROM (labeled ROM), UPS battery, 4 RAM, 4 SATA (hard disks) and 4 expansion
        (WLAN cards).
      - Only the CPU and EEPROM affect the machine so far.
      - Hard disks and WLAN cards become bus devices (`Disk`, `NetworkCard`).
      - RAM and the UPS battery are placeholders: the machine still uses the soldered
        `Hardware` memory.
      - UPS batteries carry a charge (`ModComponents.CHARGE`, 0..100); new ones come full.
    - A newly placed computer is empty. Get the parts from the creative tab, or with the
      cheat command **`/modularcomputers giveall`**.
      - It gives one of every item registered under the mod's namespace, so new items are
        included automatically (`ModCommands`).
      - It needs operator permission, like `/give`: cheats on in singleplayer.
    - Breaking the block drops its parts.
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
          location ("built-in", "SATA 1", "EXP 2"; a cabled device will read "cable, 5m").
        - **Linked hover:** hovering a part slot outlines its bus row, and hovering a row
          outlines its slot (`BusEntry.slot`). Built-ins have no slot.
        - The ↑/↓ arrows send `ReorderBusPayload`; they're greyed out at the ends.
        - Order lives in `bus/BusOrder`: devices keep their place, new ones go last,
          removed ones drop out. It's saved, and the machine's `Bus` follows it.
        - The panel is left out on windows narrower than about 382 GUI units.
        - The **light bulb** (highlight a cabled device in the world) waits for cabled
          devices.
      - **Part sounds:** putting a part in or taking it out plays its own sound, server side,
        from the slot changes (`AllInOneBlockEntity.playPartSounds`).
        - RAM: a slide and latch clicks. CPU: a drop and the lever. EEPROM: a chip press.
          Disk: a slide and a clunk. Card: a slide, a seat and a clip. UPS battery: a tiny
          zap on insert.
        - They're synthesized by `scripts/art/part_sounds.py`. The user wanted them soft, not
          Minecraft's abrasive click.
        - Loading a save plays nothing.
      - Clicking the bus panel or the buttons doesn't count as clicking outside the window,
        so it never drops the carried item (`hasClickedOutside`).
    - Switching sends `SwitchViewPayload`. The server either opens the menu, or closes it and
      sends `OpenDisplayPayload`.
    - Both views show the same button column to the right (`ComputerButtons`): the power
      button on top, the view toggle below. The toggle shows a motherboard
      (`textures/gui/case_button.png`, the same art as the back's window) for "case", and a
      tiny screen for "screen".
  - **Power button** (a press button, not a toggle switch). A press (`Machine.pressPower`)
    turns the machine on or off (`Machine.isPowered`, block state `powered`). Rules the user
    chose:
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
      - With a CPU (block state `cpu=true`), the button is lit (an emissive model element):
        red when off, green when on.
      - With no CPU it's **faded and unlit**, and red, since the machine can't be on.
        - In the GUIs it also shows faded green while the mouse is held down on it.
        - It's back to faded red on release (`ComputerButtons`, `powerHeld`).
      - There are four block models (`all_in_one[_on][_faded]`) on two base models (emissive,
        and plain), and four frames in `textures/gui/power_button.png`.
  - **Boot results:**
    - CPU and EEPROM: "hello world" (the stand-in program).
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
- **Lua runtime:** the user is building their own Lua runtime as a separate project, and it
  isn't ready. Leave VM/runtime work alone and keep building the computer's other features.
  Machine behavior stays a stand-in (like the "hello world" greeting) until the runtime lands.
- **Cabled devices and bus-order memory** (connect/disconnect toggle, gaps in the bus walk,
  remembering positions per case, chunk unloads) are designed in `docs/PLAN.md`, and parts
  are **undecided: a documented gap**. Ask the user before building cabled devices.
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
    1. sets the console colors to `ABORT_FOREGROUND` on `ABORT_BACKGROUND` (soft gray; the
       user doesn't want blue);
    2. clears the console;
    3. prints `ABRT: <reason>` (`Cpu.NO_EEPROM`, or `Cpu.CRASHED` plus the message).
- **EEPROM (`Eeprom`):** holds a `Program`. Today that's Java (`HelloWorldProgram`, a
  stand-in). Later it's Lua code run by the user's Lua runtime. The CPU's side doesn't
  change.
- **Motherboard (`Motherboard`):** holds the soldered parts (`Hardware`/`Part`, memory), the
  CPU socket, the EEPROM slot, and two buses.
  - **Physical bus (`bus/Bus`):** the real devices in the player's discovery order
    (`BusOrder`; built-ins first by default). Each describes itself
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
- **Console (`console/ConsoleSplitter`):** modeled on UEFI's console splitter.
  - It's never null. It writes to every display on the physical bus and drops output when
    there are none.
  - Its size is the smallest columns × rows among the displays. With no displays it reports
    `HEADLESS` (a real size, never 0×0, like Linux's dummycon).
  - It owns the cursor and the current colors. It wraps text at the right edge, `clear()`
    fills its area, and it scrolls only its own area.
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
   - The run reports "All 22 required tests passed": 21 are ours, and one is of unknown origin
     (not investigated).
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
scripts/art/part_sounds.py           synthesizes the part insert/remove sounds (slides, clicks, thumps, a zap)
scripts/art/oggwrite.py              shared: writes mono Ogg Vorbis via the system's libsndfile (ctypes)
src/main/java/.../sound/ModSounds.java   sound events (error_beep)
.github/workflows/build.yml
src/main/java/com/payonel/modularcomputers/
  ModularComputers.java            MOD_ID, LOGGER, id(), INIT_MARKER; registers content + packets
  machine/                         plain Java: Machine (power), Cpu, Motherboard, Eeprom, Program, HelloWorldProgram, Hardware, Part, PartKind
  bus/                             plain Java: Bus (physical), VirtualBus, Device, DeviceType
  console/ConsoleSplitter.java     the always-present console on the virtual bus
  device/                          Keyboard, Disk (bus devices; no behavior yet)
  display/                         plain Java: Display (bus device), Resolution, TextBuffer, Viewport, DisplayLayout (GUI layout math)
  ModCreativeTab.java              the "Modular Computers" creative tab
  item/ModItems.java               CPU and EEPROM items
  menu/                            CaseMenu (part slots + inventory), ModMenus
  block/                           AllInOneBlock, AllInOneBlockEntity, BackPanel (back-face hit regions), ModBlocks (registrations)
  command/ModCommands.java         /modularcomputers giveall (cheat: one of every mod item)
  network/OpenDisplayPayload.java  server->client "open this display"
  network/PressPowerPayload.java  client->server "power button clicked in a GUI"
  network/SwitchViewPayload.java   client->server "view toggle clicked" (screen view <-> case view)
src/client/java/com/payonel/modularcomputers/client/
  ModularComputersClient.java      registers renderer + packet receiver
  render/                          AllInOneRenderer (text on the block face), TextGrid (cell layout)
  screen/DisplayScreen.java        the screen view (right-click; "modal mode")
  screen/CaseScreen.java           the case view (slots)
  screen/ComputerButtons.java      power button + view toggle, shared by both views
src/main/resources/                fabric.mod.json, assets (blockstates, models, items, textures, lang), data (loot table, pickaxe tag)
src/test/java/...                  unit tests (ModularComputersTest, machine/, display/)
src/gametest/java/.../gametest/    ModularComputersGameTest + AllInOneGameTest (server),
                                   ModularComputersClientGameTest + AllInOneClientGameTest (client)
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
