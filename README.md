# Modular Computers

Programmable computers for Minecraft, built on Fabric. A spiritual successor to
[OpenComputers](https://github.com/MightyPirates/OpenComputers), written from scratch.

- Minecraft **26.3**, Fabric Loader 0.19.5, Fabric API 0.162.0+26.3, Java 25
- Mod id: `modularcomputers`
- License: [MIT](LICENSE)

## Build

```sh
./gradlew build      # compile + unit tests -> build/libs/ (seconds; doesn't start Minecraft)
./gradlew testAll    # everything: + server and headless client game tests (needs Xvfb, see docs/TESTING.md)
```

You do not need to install a JDK: Gradle downloads JDK 25 on first run.

## Docs

- [docs/TESTING.md](docs/TESTING.md): test layers and headless setup
- [docs/VERSIONING.md](docs/VERSIONING.md): version numbers and branches
