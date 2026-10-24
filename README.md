# Modular Computers

Programmable computers for Minecraft, for Fabric and NeoForge. A spiritual successor to
[OpenComputers](https://github.com/MightyPirates/OpenComputers), written from scratch.

- Minecraft **26.3**, Java 25
- Fabric: Loader 0.19.5, Fabric API 0.162.0+26.3
- NeoForge: 26.3.0.95
- Mod id: `modularcomputers`
- License: [MIT](LICENSE)

## Build

```sh
./gradlew build      # compile + unit tests -> fabric/build/libs/, neoforge/build/libs/ (seconds; doesn't start Minecraft)
./gradlew testAll    # everything: + server game tests on both loaders, headless client tests (needs Xvfb, see .agents/TESTING.md)
```

You do not need to install a JDK: Gradle downloads JDK 25 on first run.

## Layout

- `common/`: the mod, written against the plain game (no loader's API)
- `fabric/`, `neoforge/`: each loader's entry point and its services for the common code

## Docs

- [.agents/TESTING.md](.agents/TESTING.md): test layers and headless setup
- [.agents/VERSIONING.md](.agents/VERSIONING.md): version numbers and branches
