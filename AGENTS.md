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
  - **Do** use OC as the reference for design questions. The user will often ask "how did OC
    do X?". Answer from the local clone (section 7).
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
  - CPU, memory and disk are soldered in, with no limits. There's no EEPROM, no inventory
    and no slots.
  - **Power button** (a press button, not a toggle switch):
    - **On the back, bottom right:** a dark housing at texels 9..13, holding a 3x3 button that
      sticks out half a texel. It's an emissive model element, so it glows: red when off,
      green when on. A plain right-click on the housing toggles power (`PowerButton.isHit`).
    - **In the display GUI:** to the right of the display, level with its top edge, never
      covering it. It's drawn from `textures/gui/power_button.png` with the same pixel colors.
      A left or right click sends `TogglePowerPayload`, and the server checks reach before toggling.
    - **Sneak + right-click** anywhere also still toggles power.
    - The `running` block-state property mirrors the machine's power and picks the
      off/on model.
  - User's future plan for power:
    - Once screens are separate blocks, the GUI power button only shows when the screen is
      connected to a case, because cases provide the power toggle.
    - Lighting will depend on available power: faded red/green when unpowered, fully lit when
      powered. Only a fully lit green button actually runs the PC.
  - The power state is saved and synced to clients. Toggling plays a stone-button click.
  - There is **no CPU emulation, Lua or programs yet**. Turning a computer on just writes
    `Machine.GREETING` ("hello world") to row 0 of its text buffer. Turning it off blanks the
    buffer, and a blank buffer renders black.
  - The text renders on the block face's glass (`AllInOneRenderer`) and in the display GUI.
  - **Right-click** opens the display GUI (`DisplayScreen`). The server sends
    `OpenDisplayPayload` with the position and resolution, so the GUI has its final size from
    the first frame. It's a black rectangle at 90% of the window, keeping the buffer's aspect
    ratio, over the dimmed world.
    - User requirement: no OC-style placeholder box, and not 100% of the window.
    - The GUI doesn't pause the game, and it closes when the block is gone or out of reach.
  - Resolution is 50x16 cells of 8x16 (OC tier 1), so text is readable on a one-block face.
  - Text uses Minecraft's proportional font, centered per cell. A real monospace bitmap font
    (OC used unifont `font.hex`) is future work.
- **Lua runtime:** the user is building their own Lua runtime as a separate project, and it
  isn't ready. Leave VM/runtime work alone and keep building the computer's other features.
  Machine behavior stays a stand-in (like the "hello world" greeting) until the runtime lands.
- Planned (the user's roadmap, not started):
  - computer cases with slots, plus tiers and power
  - parts: CPUs, RAM, disks, EEPROM
  - external devices: separate screens, keyboards, cables
- Design rule: machine logic stays in plain Java (`machine/`, `display/`) and is unit-tested.
  Minecraft glue goes in `block/` and `network/`, and rendering goes in `client/`.

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
  - GLFW isn't on the compile classpath. Use `InputConstants.KEY_*` for key codes, e.g. in
    client tests.
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
./gradlew build                     # compile + unit tests + server game tests (no display needed)
./gradlew testAll                   # + client game tests against the release jar on Xvfb
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
   - They run headless inside `./gradlew build` via `runGameTest`.
   - The run reports "All 7 required tests passed": 6 are ours, and one is of unknown origin
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

- `context.worldBuilder()` uses "consistent settings" by default, and the test spells this out
  with `setUseConsistentSettings(true)`:
  - the superflat preset, with seed `1`
  - time and weather frozen
- Teleports: format block centers as `getX() + 0.5`, never `"%d.5"`. With `"%d.5"`, block -4
  becomes `-4.5`, which is inside block -5.
- `context.getInput().setCursorPos(x, y)` takes window pixels (GUI coordinates times
  `getWindow().getGuiScale()`), and `pressMouse(0)` left-clicks.
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
build.gradle, settings.gradle, gradle.properties, gradle/ (wrapper + daemon JVM criteria)
scripts/setup-headless-host.sh, scripts/headless.sh
scripts/art/all_in_one_textures.py   draws the all-in-one textures (Pillow); PNGs are committed
.github/workflows/build.yml
src/main/java/com/payonel/modularcomputers/
  ModularComputers.java            MOD_ID, LOGGER, id(), INIT_MARKER; registers content + packets
  machine/                         plain Java: Machine (power, display buffer), Hardware, Part, PartKind
  display/                         plain Java: Resolution, TextBuffer, Viewport, DisplayLayout (GUI layout math)
  ModCreativeTab.java              the "Modular Computers" creative tab
  block/                           AllInOneBlock, AllInOneBlockEntity, PowerButton (hit test), ModBlocks (registrations)
  network/OpenDisplayPayload.java  server->client "open this display"
  network/TogglePowerPayload.java  client->server "power button clicked in the display"
src/client/java/com/payonel/modularcomputers/client/
  ModularComputersClient.java      registers renderer + packet receiver
  render/                          AllInOneRenderer (text on the block face), TextGrid (cell layout)
  screen/DisplayScreen.java        the right-click display GUI
src/main/resources/                fabric.mod.json, assets (blockstates, models, items, textures, lang), data (loot table, pickaxe tag)
src/test/java/...                  unit tests (ModularComputersTest, machine/, display/)
src/gametest/java/.../gametest/    ModularComputersGameTest + AllInOneGameTest (server),
                                   ModularComputersClientGameTest + AllInOneClientGameTest (client)
src/gametest/resources/fabric.mod.json                                   test mod (entrypoints fabric-gametest, fabric-client-gametest)
```

## 10. Working conventions for agents

- Before claiming something works, run `./gradlew testAll` (or `build` when no client-side
  code changed). Report failures with their output.
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
