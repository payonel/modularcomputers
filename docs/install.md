---
title: Installing
section: Start
order: 1
---
Modular Computers is not released yet. Until it is, you build it from source.

## What you need

| | |
|---|---|
| Minecraft | 26.3 |
| Java | 25 (Gradle downloads it for you) |
| Fabric | Fabric Loader 0.19.5 or newer, and Fabric API 0.162.0+26.3 |
| NeoForge | 26.3.0.95 |

Pick the jar for your loader. A jar refuses to load on the wrong Minecraft version.

## Building

```sh
git clone https://github.com/payonel/modularcomputers.git
cd modularcomputers
./gradlew build
```

The jars are in `fabric/build/libs/` and `neoforge/build/libs/`, named
`modularcomputers-<loader>-<version>+<minecraft version>.jar`. Put the one for your loader in your
`mods` folder, along with Fabric API if you use Fabric.

## What next

Build your [first computer](/tutorials/first-computer/).
