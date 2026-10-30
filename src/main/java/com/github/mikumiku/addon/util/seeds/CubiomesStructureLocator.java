/*
 * 本文件属于 meteor-miku 项目：https://github.com/mikumiku7/meteor-miku
 * meteor-miku 是 meteor-miku 在 Minecraft 26.1 / Fabric 上的移植版本。
 *
 * Copyright (C) 2024-2026 XC_XinCheng
 *
 * SPDX-License-Identifier: GPL-3.0-only
 *
 * 本程序是自由软件：你可以依据 GNU 通用公共许可证（GNU GPL）第 3 版的条款
 * 重新发布和/或修改它。
 *
 * 本程序基于“有用”的期望分发，但不提供任何担保，甚至不提供适销性或特定
 * 用途适用性的默示担保。详见 GNU 通用公共许可证。
 *
 * 你应当已随本程序收到一份 GNU 通用公共许可证副本；如果没有，请见
 * <https://www.gnu.org/licenses/>。
 */
package com.github.mikumiku.addon.util.seeds;

import dev.xpple.cubiomes.Cubiomes;
import dev.xpple.cubiomes.Generator;
import dev.xpple.cubiomes.Pos;
import dev.xpple.cubiomes.StructureConfig;
import dev.xpple.cubiomes.StrongholdIter;

import java.io.InputStream;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.BooleanSupplier;

/**
 * 结构定位库 cubiomes（{@code dev.xpple:cubiomes}）的接入层。
 *
 * <p><a href="https://www.seedmap.app">seedmap.app</a> 与 SeedMapper 模组用的都是
 * cubiomes（Cubitect 的 C 库，xpple 维护的分支），这里用的是它发布到 Maven 的 Java
 * 绑定构件：类在 {@code dev.xpple.cubiomes}（由 jextract 生成，走
 * {@link java.lang.foreign} 的 FFM API），原生库有 Linux x86/arm、Windows x86/arm 与
 * macOS（通用二进制）五种，都随本模组的 jar 一起分发。
 *
 * <p>结构搜索优先走这里：版本表、结构放置算法、频率约简（废弃矿井 / 埋藏的宝藏 /
 * 掠夺者前哨站）、排除区（前哨站避开村庄 10 区块）、要塞同心环以及各结构的生物群系
 * 与地形判定都由 cubiomes 原样完成，结果与 seedmap.app 一致。cubiomes 不支持或原生库
 * 加载失败时 {@link #tryCreate(SeedVersion, long)} 返回 {@code null}，调用方退回原有算法。
 *
 * <p>版本口径：cubiomes 的 {@code MCVersion} 把同一发行版的补丁号合并成“最新补丁”
 * 一个常量（例如 {@code MC_1_20} 代表 1.20.6、{@code MC_1_16} 代表 1.16.5），
 * {@link #mcFor(SeedVersion)} 按它自己的版本表把本模组的版本号映射到最接近的常量；
 * {@code 1.21} 系列里单独列出的补丁号（1.21.1 / 1.21.3 / 1.21.4 / 1.21.5 / 1.21.6 /
 * 1.21.9 / 1.21.11）以及 26.1 / 26.2 都各有对应的常量，直接精确映射。
 */
public final class CubiomesStructureLocator {

    /** 一个命中的结构：显示名、方块坐标与路径点高度。 */
    public record Found(String display, int blockX, int blockZ, int y) {
    }

    /** cubiomes 里一个可定位结构：显示名、结构类型常量、维度、路径点 Y。 */
    private record Spec(String display, int type, int dim, int y) {
    }

    /** 要塞数量（原版 1.9+ 固定 128 个）。 */
    private static final int STRONGHOLD_COUNT = 128;

    /** 各结构的路径点高度（与原生定位器的口径一致）。 */
    private static final int SURFACE_Y = 100;
    private static final int STRONGHOLD_Y = 32;
    private static final int ANCIENT_CITY_Y = -51;
    private static final int NETHER_FORTRESS_Y = 64;
    private static final int NETHER_BASTION_Y = 33;
    private static final int NETHER_FOSSIL_Y = 32;
    private static final int END_CITY_Y = 64;

    // ------------------------------------------------------------------
    // 原生库加载：整个进程只做一次，失败后不再重试
    // ------------------------------------------------------------------

    private static final Object LOAD_LOCK = new Object();
    private static volatile boolean loadAttempted;
    private static volatile boolean nativeLoaded;
    private static volatile String loadMessage = "尚未尝试加载";

    private static void ensureNativeLoaded() {
        if (loadAttempted) return;
        synchronized (LOAD_LOCK) {
            if (loadAttempted) return;
            loadAttempted = true;
            try {
                String libraryName = nativeLibraryName();
                Path target = Files.createTempFile("meteor-miku-cubiomes-", suffixOf(libraryName));
                try (InputStream in = CubiomesStructureLocator.class.getResourceAsStream("/" + libraryName)) {
                    if (in == null) {
                        loadMessage = "模组 jar 里没有原生库 " + libraryName;
                        return;
                    }
                    Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                }
                target.toFile().deleteOnExit();
                System.load(target.toAbsolutePath().toString());
                nativeLoaded = true;
                loadMessage = "已加载 " + libraryName;
            } catch (Throwable t) {
                loadMessage = "加载原生库失败: " + t;
            }
        }
    }

    /** 当前平台对应的原生库文件名（与构件里分发的五个文件一一对应）。 */
    private static String nativeLibraryName() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        boolean arm = arch.contains("aarch64") || arch.contains("arm");
        if (os.contains("win")) {
            return arm ? "cubiomes_arm.dll" : "cubiomes_x86.dll";
        }
        if (os.contains("mac") || os.contains("darwin")) {
            return "libcubiomes.dylib";
        }
        return arm ? "libcubiomes_arm.so" : "libcubiomes_x86.so";
    }

    /** 原样的扩展名（含点），用于临时文件；System.load 在 Windows 上要靠它识别格式。 */
    private static String suffixOf(String libraryName) {
        int dot = libraryName.lastIndexOf('.');
        return dot < 0 ? "" : libraryName.substring(dot);
    }

    /** 原生库是否已成功加载。 */
    public static boolean isNativeLoaded() {
        return nativeLoaded;
    }

    /** 原生库加载结果，用于排查启动时的问题。 */
    public static String loadMessage() {
        return loadMessage;
    }

    // ------------------------------------------------------------------
    // 结构表
    // ------------------------------------------------------------------

    private static volatile List<Spec> specs;

    private static List<Spec> specs() {
        List<Spec> local = specs;
        if (local != null) return local;
        synchronized (CubiomesStructureLocator.class) {
            if (specs == null) specs = buildSpecs();
            return specs;
        }
    }

    /**
     * 本模组结构搜索模块里列出的全部结构，按 cubiomes 的结构类型建表。
     * 顺序固定，保证聊天栏与路径点的输出顺序稳定。
     */
    private static List<Spec> buildSpecs() {
        int overworld = Cubiomes.DIM_OVERWORLD();
        int nether = Cubiomes.DIM_NETHER();
        int end = Cubiomes.DIM_END();
        List<Spec> list = new ArrayList<>();
        list.add(new Spec("村庄", Cubiomes.Village(), overworld, SURFACE_Y));
        list.add(new Spec("沉船", Cubiomes.Shipwreck(), overworld, SURFACE_Y));
        list.add(new Spec("埋藏的宝藏", Cubiomes.Treasure(), overworld, SURFACE_Y));
        list.add(new Spec("海底废墟", Cubiomes.Ocean_Ruin(), overworld, SURFACE_Y));
        list.add(new Spec("掠夺者前哨站", Cubiomes.Outpost(), overworld, SURFACE_Y));
        list.add(new Spec("沙漠神殿", Cubiomes.Desert_Pyramid(), overworld, SURFACE_Y));
        list.add(new Spec("雪屋", Cubiomes.Igloo(), overworld, SURFACE_Y));
        list.add(new Spec("丛林神庙", Cubiomes.Jungle_Temple(), overworld, SURFACE_Y));
        list.add(new Spec("林地府邸", Cubiomes.Mansion(), overworld, SURFACE_Y));
        list.add(new Spec("废弃矿井", Cubiomes.Mineshaft(), overworld, SURFACE_Y));
        list.add(new Spec("海底神殿", Cubiomes.Monument(), overworld, SURFACE_Y));
        list.add(new Spec("废弃传送门", Cubiomes.Ruined_Portal(), overworld, SURFACE_Y));
        list.add(new Spec("沼泽小屋", Cubiomes.Swamp_Hut(), overworld, SURFACE_Y));
        list.add(new Spec("下界要塞", Cubiomes.Fortress(), nether, NETHER_FORTRESS_Y));
        list.add(new Spec("堡垒遗迹", Cubiomes.Bastion(), nether, NETHER_BASTION_Y));
        list.add(new Spec("下界化石", Cubiomes.Nether_Fossil(), nether, NETHER_FOSSIL_Y));
        list.add(new Spec("末地城", Cubiomes.End_City(), end, END_CITY_Y));
        list.add(new Spec("古城", Cubiomes.Ancient_City(), overworld, ANCIENT_CITY_Y));
        list.add(new Spec("要塞", Cubiomes.Stronghold(), overworld, STRONGHOLD_Y));
        return list;
    }

    // ------------------------------------------------------------------
    // 版本映射
    // ------------------------------------------------------------------

    /**
     * 把本模组的版本号映射到 cubiomes 的 {@code MCVersion} 常量；超出 cubiomes 版本表
     * 时返回 0。映射表按各版本世界生成的实际差异来分：例如 1.16.1 与 1.16.5 的堡垒遗迹
     * 不同（cubiomes 也是两个常量），1.19 / 1.19.1 / 1.19.2 共享 {@code MC_1_19_2}。
     */
    private static int mcFor(SeedVersion version) {
        if (version == null) return 0;
        return switch (version) {
            case v26_2 -> Cubiomes.MC_26_2();
            case v26_1_2, v26_1_1, v26_1 -> Cubiomes.MC_26_1();
            case v1_21_11 -> Cubiomes.MC_1_21_11();
            case v1_21_10, v1_21_9 -> Cubiomes.MC_1_21_9();
            case v1_21_8, v1_21_7, v1_21_6 -> Cubiomes.MC_1_21_6();
            case v1_21_5 -> Cubiomes.MC_1_21_5();
            case v1_21_4 -> Cubiomes.MC_1_21_4();
            case v1_21_3, v1_21_2 -> Cubiomes.MC_1_21_3();
            case v1_21_1, v1_21 -> Cubiomes.MC_1_21_1();
            case v1_20_6, v1_20_5, v1_20_4, v1_20_3, v1_20_2, v1_20_1, v1_20 -> Cubiomes.MC_1_20_6();
            case v1_19_4, v1_19_3 -> Cubiomes.MC_1_19_4();
            case v1_19_2, v1_19_1, v1_19 -> Cubiomes.MC_1_19_2();
            case v1_18_2, v1_18_1, v1_18 -> Cubiomes.MC_1_18_2();
            case v1_17_1, v1_17 -> Cubiomes.MC_1_17_1();
            case v1_16_5, v1_16_4, v1_16_3, v1_16_2 -> Cubiomes.MC_1_16_5();
            case v1_16_1, v1_16 -> Cubiomes.MC_1_16_1();
            case v1_15_2, v1_15_1, v1_15 -> Cubiomes.MC_1_15_2();
            case v1_14_4, v1_14_3, v1_14_2, v1_14_1, v1_14 -> Cubiomes.MC_1_14_4();
            case v1_13_2, v1_13_1, v1_13 -> Cubiomes.MC_1_13_2();
            case v1_12_2, v1_12_1, v1_12 -> Cubiomes.MC_1_12_2();
            default -> 0;
        };
    }

    // ------------------------------------------------------------------
    // 实例
    // ------------------------------------------------------------------

    private final int mc;
    private final long seed;
    private final String versionLabel;

    private CubiomesStructureLocator(int mc, long seed, String versionLabel) {
        this.mc = mc;
        this.seed = seed;
        this.versionLabel = versionLabel;
    }

    /**
     * 为指定种子与版本建立定位器。
     *
     * @return cubiomes 版本表里没有该版本，或当前平台的原生库加载失败时返回 {@code null}，
     *         调用方应退回原有算法
     */
    public static CubiomesStructureLocator tryCreate(SeedVersion version, long seed) {
        if (version == null) return null;
        ensureNativeLoaded();
        if (!nativeLoaded) return null;
        try {
            int mc = mcFor(version);
            if (mc == 0) return null;
            String label = Cubiomes.mc2str(mc).getString(0);
            return new CubiomesStructureLocator(mc, seed, label);
        } catch (Throwable t) {
            loadMessage = "读取 cubiomes 版本表失败: " + t;
            return null;
        }
    }

    /** cubiomes 实际使用的版本名，例如 {@code 1.20}、{@code 26.2}。 */
    public String versionLabel() {
        return versionLabel;
    }

    /**
     * 扫描中心区块周围半径内的结构。
     *
     * @param centerChunkX 中心区块 X
     * @param centerChunkZ 中心区块 Z
     * @param radiusChunks 半径（区块）
     * @param wanted       需要的中文结构名集合；{@code null} 表示全部
     * @param skipped      收集“该版本下 cubiomes 不支持”的结构名（可以为 {@code null}）
     * @param running      返回 false 时立即停止（模块被关闭或取消搜索）
     * @return 命中的结构位置列表（未按距离排序）
     */
    public List<Found> scan(int centerChunkX, int centerChunkZ, int radiusChunks,
                            Set<String> wanted, List<String> skipped, BooleanSupplier running) {
        List<Found> result = new ArrayList<>();
        if (radiusChunks < 0) return result;

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment overworld = generator(arena, Cubiomes.DIM_OVERWORLD());
            MemorySegment nether = generator(arena, Cubiomes.DIM_NETHER());
            MemorySegment end = generator(arena, Cubiomes.DIM_END());

            for (Spec spec : specs()) {
                if (wanted != null && !wanted.contains(spec.display())) continue;
                if (running != null && !running.getAsBoolean()) return result;

                if (spec.type() == Cubiomes.Stronghold()) {
                    scanStrongholds(arena, overworld, centerChunkX, centerChunkZ, radiusChunks, spec, result, running);
                    continue;
                }

                scanRandomSpread(arena, spec, overworld, nether, end,
                    centerChunkX, centerChunkZ, radiusChunks, skipped, result, running);
            }
        }
        return result;
    }

    /** 给指定维度建立生物群系生成器（cubiomes 的 setupGenerator + applySeed）。 */
    private MemorySegment generator(Arena arena, int dim) {
        MemorySegment generator = Generator.allocate(arena);
        Cubiomes.setupGenerator(generator, mc, 0);
        Cubiomes.applySeed(generator, dim, seed);
        return generator;
    }

    /**
     * 一个 random_spread（或 cubiomes 里同类的按区域放置）结构：按区域大小遍历候选区域，
     * 用 {@code getStructurePos} 取生成尝试点，再用 {@code isViableStructurePos} 过生物
     * 群系 / 地形判定。频率约简与排除区都由 cubiomes 在这两个函数里完成。
     */
    private void scanRandomSpread(Arena arena, Spec spec, MemorySegment overworld, MemorySegment nether,
                                  MemorySegment end, int centerChunkX, int centerChunkZ, int radiusChunks,
                                  List<String> skipped, List<Found> result, BooleanSupplier running) {
        MemorySegment config = StructureConfig.allocate(arena);
        if (Cubiomes.getStructureConfig(spec.type(), mc, config) == 0) {
            if (skipped != null) skipped.add(spec.display() + "（" + versionLabel + " 里没有这个结构）");
            return;
        }

        MemorySegment generator;
        if (spec.dim() == Cubiomes.DIM_NETHER()) {
            generator = nether;
        } else if (spec.dim() == Cubiomes.DIM_END()) {
            generator = end;
        } else {
            generator = overworld;
        }

        int region = Math.max(1, Byte.toUnsignedInt(StructureConfig.regionSize(config)));
        int minRegionX = Math.floorDiv(centerChunkX - radiusChunks, region);
        int maxRegionX = Math.floorDiv(centerChunkX + radiusChunks, region);
        int minRegionZ = Math.floorDiv(centerChunkZ - radiusChunks, region);
        int maxRegionZ = Math.floorDiv(centerChunkZ + radiusChunks, region);

        MemorySegment pos = Pos.allocate(arena);
        for (int regionX = minRegionX; regionX <= maxRegionX; regionX++) {
            for (int regionZ = minRegionZ; regionZ <= maxRegionZ; regionZ++) {
                if (running != null && !running.getAsBoolean()) return;

                if (Cubiomes.getStructurePos(spec.type(), mc, seed, regionX, regionZ, pos) == 0) continue;

                int blockX = Pos.x(pos);
                int blockZ = Pos.z(pos);
                int chunkX = blockX >> 4;
                int chunkZ = blockZ >> 4;
                if (Math.abs(chunkX - centerChunkX) > radiusChunks
                    || Math.abs(chunkZ - centerChunkZ) > radiusChunks) {
                    continue;
                }
                if (Cubiomes.isViableStructurePos(spec.type(), generator, blockX, blockZ, 0) == 0) continue;

                result.add(new Found(spec.display(), blockX, blockZ, spec.y()));
            }
        }
    }

    /**
     * 要塞：cubiomes 用与游戏相同的同心环算法迭代出 128 个位置。
     * {@code initFirstStronghold} 只做准备，之后每调用一次 {@code nextStronghold} 就
     * 前进到下一个要塞，返回值为“后面还剩几个”，减到 0 即结束。
     */
    private void scanStrongholds(Arena arena, MemorySegment overworld, int centerChunkX, int centerChunkZ,
                                 int radiusChunks, Spec spec, List<Found> result, BooleanSupplier running) {
        MemorySegment iterator = StrongholdIter.allocate(arena);
        Cubiomes.initFirstStronghold(arena, iterator, mc, seed);

        int found = 0;
        while (found < STRONGHOLD_COUNT && Cubiomes.nextStronghold(iterator, overworld) > 0) {
            found++;
            if (running != null && !running.getAsBoolean()) return;

            MemorySegment pos = StrongholdIter.pos(iterator);
            int blockX = Pos.x(pos);
            int blockZ = Pos.z(pos);
            if (Math.abs((blockX >> 4) - centerChunkX) > radiusChunks
                || Math.abs((blockZ >> 4) - centerChunkZ) > radiusChunks) {
                continue;
            }
            result.add(new Found(spec.display(), blockX, blockZ, spec.y()));
        }
    }
}
