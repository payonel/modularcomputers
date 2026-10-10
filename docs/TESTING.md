# Testing

The goal: almost nothing should need a human to launch the game. Every layer below runs from
Gradle and exits with a pass/fail code.

| Layer | Where | Runs | Needs | Speed |
|---|---|---|---|---|
| Unit tests | `src/test/java` | `./gradlew test` (part of `build`) | nothing | seconds |
| Server game tests | `src/gametest/java`, `@GameTest` | `./gradlew runGameTest` (part of `testAll`, not `build`) | nothing (headless dedicated server) | ~20s |
| Client game tests (dev) | `src/gametest/java`, `FabricClientGameTest` | `scripts/headless.sh ./gradlew runClientGameTest` | Xvfb | ~30s |
| Client game tests (release jar) | same | `./gradlew runProductionClientGameTest` | Xvfb | ~20s |
| Everything | | `./gradlew testAll` | Xvfb | ~1 min |

`./gradlew build` (compile, jars, unit tests) is the fast inner loop and never starts
Minecraft. Loom adds the server game tests to `check`, and `build.gradle` takes them back out.
Run `./gradlew testAll` before committing.

## Where to put a test

Prefer the lowest layer that can express it.

1. **Unit tests**: logic that doesn't need a running world. Keep core logic (machine state,
   component/signal routing, the VM bridge, data formats) in plain Java behind small
   interfaces so it can be tested here with fakes/Mockito. `fabric-loader-junit` puts Minecraft
   on the classpath; call `SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();` to use
   vanilla registries. (In 26.x, `new ItemStack(...)` additionally needs data components bound
   and does not work after Bootstrap alone.)
2. **Server game tests**: anything involving blocks, block entities, ticking, redstone,
   networking between blocks, persistence. Runs in a real server with no rendering.
3. **Client game tests**: rendering, screens/GUIs, input, client/server sync. They boot the
   real client, can create singleplayer worlds or dedicated servers, simulate input
   (`context.getInput()`), click screen buttons, and take/compare screenshots.

## Outputs

- Unit test reports: `build/reports/tests/test/`
- Server game test log: Gradle console; JUnit XML in `build/test-results/`
- Client screenshots: `build/run/clientGameTest/screenshots/`,
  `build/run/productionClientGameTest/screenshots/`
- Client logs: `build/run/*/logs/latest.log`

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
