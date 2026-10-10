# Versioning and branches

## Version format

```
MAJOR.MINOR.PATCH+MCVERSION        e.g. 0.1.0+26.3
```

- `MAJOR.MINOR.PATCH` is the mod's own [SemVer](https://semver.org) version (`mod_version` in
  `gradle.properties`).
- `+MCVERSION` is SemVer *build metadata* naming the Minecraft version the jar was built for
  (`minecraft_version`). It is appended automatically in `build.gradle`.

This is the convention the Fabric ecosystem itself uses (Fabric API ships as e.g.
`0.162.0+26.3`). Why it works well:

- Users see the Minecraft version in the file name: `modularcomputers-0.1.0+26.3.jar`.
- Fabric Loader parses mod versions as SemVer and **ignores build metadata when comparing**, so
  `1.2.0+26.3` and `1.2.0+26.4` are the same feature set built for two games, and dependency
  ranges written by other mods (`"modularcomputers": ">=1.2.0"`) keep working across Minecraft
  versions.
- Modrinth and CurseForge accept it as-is; the Minecraft version is also set as upload metadata.

The jar also refuses to load on the wrong game: `fabric.mod.json` declares
`"minecraft": "${minecraft_version_range}"` (`~26.3` = any 26.3.x hotfix).

### Minecraft's own numbering (since 2026)

Mojang now numbers Java Edition `YY.D.H`: year, drop within the year, hotfix. 2026 went
26.1, 26.2, 26.3; hotfixes are 26.1.1, 26.1.2, ... Drops are usually binary-incompatible for
mods (target one per jar); hotfixes usually are not, hence `~26.3`.

### Bumping the mod version

- PATCH: bug fixes only, no save/API/behavior changes players need to know about.
- MINOR: new features, backwards compatible with existing worlds and the public API.
- MAJOR: breaks worlds/saves, configs, or the public API for addons.
- `0.x` while pre-release: anything may change.
- Porting to a new Minecraft version alone does not bump the mod version; the `+MCVERSION` changes.

## Branches

- `master` (mainline): all current development, always targets the newest Minecraft version.
- Release branches are cut from `master` only when a release is declared, e.g.
  `release/mc26.3` for a Minecraft version we keep maintaining after `master` moves on.
- Tags mark releases: `v0.1.0+26.3`.
- No feature branches unless explicitly requested.
