# 第三方声明（Third-Party Notices）

`meteor-miku` 是下列开源项目的衍生作品或使用了它们的构件。各项目的版权与许可
归其各自作者所有，本文件仅做转载与归属说明。

## 1. Meteor Client

- 项目：<https://github.com/MeteorDevelopment/meteor-client>
- 版权：Copyright (c) 2021 Meteor Development
- 许可：GNU General Public License v3.0（GPL-3.0）
- 用途：本模组的运行前置与 API 依赖；本模组作为其 addon 运行。

## 2. meteor-rejects

- 项目：<https://github.com/MeteorDevelopment/meteor-rejects>
- 版权：Copyright (c) Meteor Development 及 meteor-rejects 贡献者
- 许可：GNU General Public License v3.0（GPL-3.0）
- 用途：本模组的源码即由该项目移植到 Minecraft 26.1 而来，属其衍生作品。

### 2.1 AntiCope 的 meteor-rejects 分支

- 项目：<https://github.com/AntiCope/meteor-rejects>
- 版权：Copyright (c) AntiCope 及 meteor-rejects 贡献者
- 许可：GNU General Public License v3.0（GPL-3.0）
- 用途：本项目“种子矿透”（`SeedMine`）模块与 `Ore`、`Seeds`、`Seed` 工具类，
  移植自该分支的 `OreSim` 及其配套实现；矿位模拟与空气判定逻辑按其行为对齐，
  并针对 Minecraft 26.1 改写了随机源与访问器 mixin。

## 3. Baritone

- 项目：<https://github.com/cabaletta/baritone>
- 版权：Copyright (c) Baritone contributors（leijurv 等）
- 许可：GNU Lesser General Public License v3.0（LGPL-3.0）
- 用途：运行期强制前置（由玩家的 `mods/` 目录提供），本模组不打包其代码；
  Meteor 打包版的 mod id 是 `baritone-meteor`。

## 4. SeedFinding 系列库（mc_core / mc_math / mc_seed / mc_noise / mc_biome / mc_terrain / mc_feature）

- 项目：<https://github.com/SeedFinding>
- 版权：Copyright (c) KaptainWutax 及 SeedFinding 贡献者
- 许可：MIT License
- 用途：这些库的 class 文件通过 Gradle `zipTree` 直接解包并入本模组的 jar，
  供 `SeedMine`、`ElytraFinder` 等模块在运行期使用。

以下为 MIT 许可证全文，依其要求随本模组一并保留：

```
MIT License

Copyright (c) KaptainWutax and the SeedFinding contributors

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

## 5. cubiomes 与它的 Java 绑定（`dev.xpple:cubiomes`）

- 项目：<https://github.com/Cubitect/cubiomes>（原始项目，MIT）
  、<https://github.com/xpple/cubiomes>（本项目实际使用的维护分支）
- 版权：Copyright (c) 2020 Cubitect；Java 绑定与 xpple 分支的修改
  Copyright (c) xpple 及贡献者；内置战利品表库 Copyright (c) 2024 ScriptLine（MIT）
- 许可：C 库本体 MIT License；xpple 发布的 Java 绑定构件（`dev.xpple:cubiomes`）
  以 GNU Lesser General Public License v3.0（LGPL-3.0）发布，LGPL-3.0 允许在
  GPL-3.0 项目中再链接与分发
- 用途：「结构搜索」模块的结构定位直接调用该库：版本表、结构放置、频率约简、
  排除区、要塞同心环与生物群系 / 地形判定都由 cubiomes 完成，结果与
  <https://www.seedmap.app>（同样使用 cubiomes）一致。该库的 FFM 绑定 class 与
  Linux / Windows / macOS 五份原生库（`libcubiomes_x86.so`、`libcubiomes_arm.so`、
  `libcubiomes.dylib`、`cubiomes_x86.dll`、`cubiomes_arm.dll`）通过 Gradle `zipTree`
  解包并入本模组的 jar；构件附带的 `LICENSE`（LGPL-3.0）、`LICENSE_cubiomes.txt`
  与 `LICENSE_loot_library.h.txt` 也随 jar 一并保留。
- 对应源码：Java 绑定在 <https://github.com/xpple/cubiomes/tree/java-bindings>；
  Maven 也提供同一版本的源码包
  （<https://maven.xpple.dev/maven2/dev/xpple/cubiomes/> 下的
  `cubiomes-<版本>-sources.jar`）。

cubiomes 本体与内置战利品表库的 MIT 许可证全文：

```
MIT License

Copyright (c) 2020 Cubitect
Copyright (c) 2024 ScriptLine

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

## 6. 仅在编译期使用、不随本模组分发的强制前置模组

下列模组是本模组 `fabric.mod.json` 中声明的强制前置：玩家必须自行安装，
但本模组只把它们作为 `compileOnly` 依赖用于编译，不会打包或再分发它们的代码，
各模组由其发行方提供许可：

| 项目 | 许可 |
| --- | --- |
| [Xaero's Minimap](https://chocolateminecraft.com/minimap2.php) | All Rights Reserved |
| [Xaero's World Map](https://chocolateminecraft.com/worldmap.php) | All Rights Reserved |
| [XaeroPlus](https://github.com/rfresh2/XaeroPlus) | MIT |
| [Litematica](https://github.com/maruohon/litematica) | LGPL-3.0 |
| [MiniHUD](https://github.com/maruohon/minihud) | LGPL-3.0 |
| [MaLiLib](https://github.com/maruohon/malilib) | LGPL-3.0 |
| [Orbit](https://github.com/MeteorDevelopment/orbit)（事件总线，由 Meteor Client 运行期提供） | GPL-3.0 |

## 7. Minecraft 与 Fabric

Minecraft 及其官方类库、以及 Fabric Loader / Fabric API 各自遵循 Mojang 与
Fabric 项目的许可条款，不在本模组的授权范围内。
