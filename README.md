# Meteor-MikuXC

A [Meteor Client](https://github.com/MeteorDevelopment/meteor-client) addon, ported and extended from [meteor-rejects](https://github.com/MeteorDevelopment/meteor-rejects) / [meteor-miku](https://github.com/mikumiku7/meteor-miku) for **Minecraft 26.1 / Fabric**.

- Loader: Fabric `0.19.5`
- Minecraft: `26.1` (this branch)
- Java: `25`

See the [branch table](#branches) for other supported Minecraft versions.

## Required dependencies

**All of the following are hard dependencies** — they are listed in this mod's `fabric.mod.json` `depends`, and Fabric will refuse to start if any of them is missing.

| Dependency | mod id | For 26.1 | For 26.2 (see `26.2` branch) | Used for |
| --- | --- | --- | --- | --- |
| [Meteor Client](https://github.com/MeteorDevelopment/meteor-client) | `meteor-client` | `26.1.2` | `26.2` | this mod is its addon |
| [Fabric API](https://modrinth.com/mod/fabric-api) | `fabric-api` | `0.145.1+26.1` | `0.161.0+26.2` | core API |
| Fabric Loader | — | `0.19.5` | `0.19.5` | loader |
| [Baritone](https://github.com/cabaletta/baritone) (Meteor-packaged, mod id `baritone-meteor`) | `baritone-meteor` | `26.1.2` | `baritone-meteor-26.2` | pathing, seed-based ore ESP targets |
| [Xaero's Minimap](https://modrinth.com/mod/xaeros-minimap) | `xaerominimap` | `26.5.0` | `26.5.1` | minimap coordinate overlay |
| [Xaero's World Map](https://modrinth.com/mod/xaeros-world-map) | `xaeroworldmap` | `1.46.0` | `1.46.1` | world map coordinate overlay |
| [XaeroPlus](https://modrinth.com/mod/xaeroplus) | `xaeroplus` | `2.36.4` | `2.36.4` | Xaero map enhancements |
| [Litematica](https://modrinth.com/mod/litematica) | `litematica` | `0.27.14` | `0.28.8` | schematics |
| [MiniHUD](https://modrinth.com/mod/minihud) | `minihud` | `0.39.11` | `0.40.7` | HUD coordinate line overlay |
| [MaLiLib](https://modrinth.com/mod/malilib) | `malilib` | `0.28.12` | `0.29.6` | Litematica / MiniHUD library |

XaeroPlus versions must match the Minimap / World Map versions: `2.36.4` pairs with Minimap `26.5.0`/`26.5.1` and World Map `1.46.0`/`1.46.1`. Newer XaeroPlus releases (e.g. `2.36.5`) require Minimap `26.6.0` + World Map `1.47.0` — upgrade them together.

The Orbit event bus ships with Meteor Client at runtime and does not need to be installed separately.

## Branches

| Branch | Minecraft |
| --- | --- |
| `main` | 26.1 |
| `26.2` | 26.2 |
| `1.21.11` | 1.21.11 |
| `1.21.1` | 1.21.1 |

Releases attach one jar per supported Minecraft version.

## Highlights

- **AutoMiner ("dig sand, dig everything")** — automatic mining of configurable blocks with full shulker-box storage management (tool box / storage box marking by keybind or coordinates, four full-box detection modes, extracting non-target items, fill delay + jitter, colored box status rendering), Baritone integration, packet-mining mode
- **AutoMinecart** — boards minecarts that come within range and re-boards immediately after dismounting, using vanilla rotation packets (Grim-safe)
- **AutoFollowPlayer (elytra chase)** — while gliding with an elytra, continuously locks your view onto a target, with air-only targeting, anti-ground pitch, target rendering (wireframe/fill/both) and optional humanized view jitter
- **ItemSorterModule** — shulker box / chest sorting
- …and 80+ more modules: combat, movement, rendering, seed-based ore/structure finding, chat automation and utilities

## Seed-based modules

`SeedMine` (ore ESP), `StructureFinder`, `EnchantedAppleFinder`, `ElytraFinder` and `AutoElytraHarvester` share one version parser: the "MC version / game version" field in each module is a free-text input (`26.2`, `1.21.4`, `1.20.1`, `1.12.2`, …). Input outside `1.12.2`–`26.2`, or unrecognized text, is treated as blank — nothing is written to the seed store and no search starts. Versions are never silently substituted with a nearby known version.

Structure positions prefer [cubiomes](https://github.com/Cubitect/cubiomes) (the same library used by seedmap.app; the `dev.xpple:cubiomes` fork is bundled with native libraries for Windows / Linux / macOS). When native library loading fails, the mod falls back to native world-generation data for 26.x (structure sets, `random_spread` candidate chunks, biome filters, strongholds ring algorithm, a dedicated `AncientCityLocator`) and to the bundled [seedfinding](https://github.com/kabicin/seedfinding) jars for versions the library covers. Versions covered by neither source are explicitly skipped.

The `util/seeds/SeedVersion` class extends seedfinding's `MCVersion` enum with the 26.x versions and all 1.21.x patch releases so users can enter their exact version.

## Building

The build expects dependency jars in `libs/` (not included in this repository):

- `meteor-client-26.1.2.jar`, `baritone-26.1.2.jar`, `orbit-0.2.4.jar`
- `xaerominimap-fabric-26.1.2-26.5.0.jar`, `xaeroworldmap-fabric-26.1.2-1.46.0.jar`
- `litematica-fabric-26.1.2-0.27.14.jar`, `minihud-fabric-26.1.2-0.39.11.jar`
- Seedfinding: `mc_core.jar`, `mc_math.jar`, `mc_seed.jar`, `mc_noise.jar`, `mc_biome.jar`, `mc_terrain.jar`, `mc_feature.jar`
- `cubiomes-0.0.0-2026.10.2.1790944595+4f04235f3e6e25491a02a830e4f500f68045be17.jar`

Then:

```bash
gradlew build
```

The output jar is written to `build/libs/` — drop it into your `mods/` folder (client-side only).

## License

Copyright (C) 2024-2026 XC_XinCheng

This program is free software: you can redistribute it and/or modify it under the terms of the **GNU General Public License v3.0**.

```
SPDX-License-Identifier: GPL-3.0-only
```

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY. See the included [LICENSE](LICENSE) for the full GNU GPL v3.0 text.

### Why GPL-3.0

All runtime dependencies and upstream sources are GPL-3.0 licensed:

- [Meteor Client](https://github.com/MeteorDevelopment/meteor-client): GPL-3.0
- [meteor-rejects](https://github.com/MeteorDevelopment/meteor-rejects): GPL-3.0
- [Baritone](https://github.com/cabaletta/baritone): LGPL-3.0

As a derivative work, this mod is therefore released under GPL-3.0 with upstream copyright notices preserved. Full attribution and licenses of upstream projects: [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md).

### Source availability

GPL-3.0 requires distributing the complete corresponding source with binaries. The full source of this mod is published at:

<https://github.com/XC-XinCheng/meteor-mikuXC>

The released jar also bundles `LICENSE` and `THIRD-PARTY-NOTICES.md` under `META-INF/`.

---

Original mod: https://github.com/mikumiku7/meteor-miku
