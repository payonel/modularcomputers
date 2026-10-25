# Testing

The goal: almost nothing should need a human to launch the game. Every layer below runs from
Gradle and exits with a pass/fail code.

| Layer | Where | Runs | Needs | Speed |
|---|---|---|---|---|
| Unit tests | `common/src/test/java` | `./gradlew :common:test` (part of `build`) | nothing | seconds |
| Server game tests | `common/src/gametest/java`, `@GameTest` (the mod's own, `GameTests`) | `./gradlew :fabric:runGameTest` and `:neoforge:runGameTestServer` (part of `testAll`, not `build`) | nothing (headless dedicated server) | ~30s each |
| Client game tests (dev) | `fabric/src/gametest/java`, `FabricClientGameTest` | `scripts/headless.sh ./gradlew :fabric:runClientGameTest` | Xvfb | ~30s |
| Client game tests (release jar) | same | `./gradlew :fabric:runProductionClientGameTest` | Xvfb | ~20s |
| Everything | | `./gradlew testAll` | Xvfb | ~2 min |
| Without a display (what GitHub Actions runs) | | `./gradlew testServer` | nothing | ~1 min |

`./gradlew build` (compile, jars, unit tests) is the fast inner loop and never starts
Minecraft. Loom adds the server game tests to `check`, and `fabric/build.gradle` takes them back out.
Run `./gradlew testAll` before committing.

**Two loaders, one set of server tests.** The server game tests are loader-neutral: each loader's
test mod registers the same `GameTests` (Fabric: `FabricGameTests`; NeoForge: `NeoForgeGameTests`),
so every server test runs on both. The client tests run on Fabric only: NeoForge has no client test
harness (user's call, 2026-10-20; the one unofficial port found was too young to depend on). A
NeoForge client that doesn't start would go unnoticed by the tests.

## Where to put a test

Prefer the lowest layer that can express it.

1. **Unit tests**: logic that doesn't need a running world. Keep core logic (machine state,
   component/signal routing, the VM bridge, data formats) in plain Java behind small
   interfaces so it can be tested here with fakes/Mockito. The plain game (NeoForm) is on the
   classpath; call `SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();` to use
   vanilla registries. The loader's services are `TestPlatform`'s. (In 26.x, `new ItemStack(...)` additionally needs data components bound
   and does not work after Bootstrap alone.)
2. **Server game tests**: anything involving blocks, block entities, ticking, redstone,
   networking between blocks, persistence. Runs in a real server with no rendering.
3. **Client game tests**: rendering, screens/GUIs, input, client/server sync. They boot the
   real client, can create singleplayer worlds or dedicated servers, simulate input
   (`context.getInput()`), click screen buttons, and take/compare screenshots.

## Outputs

- Unit test reports: `common/build/reports/tests/test/`
- Server game test log: Gradle console; Fabric's JUnit XML in `fabric/build/test-results/`
- Client screenshots: `fabric/build/run/clientGameTest/screenshots/`,
  `fabric/build/run/productionClientGameTest/screenshots/`
- Client logs: `fabric/build/run/*/logs/latest.log`

Startup markers logged by the mod (`ModularComputers.INIT_MARKER`, `CLIENT_INIT_MARKER`) are
asserted directly by the client game test via `ModularComputers.isInitialized()`.

## Headless host setup

`scripts/setup-headless-host.sh` installs Xvfb (system-wide with passwordless sudo, otherwise
into `~/.local`) and checks that Mesa's llvmpipe provides OpenGL. No GPU and no window
manager are needed. The JDK is provisioned by Gradle (`gradle/gradle-daemon-jvm.properties`).

## Things that bite

- `runProductionClientGameTest` must be given `fabric-client-gametest-api-v1` explicitly; the
  release `fabric-api` jar does not bundle it, and without it the client idles on the title
  screen. The task has a 10-minute timeout as a backstop.
