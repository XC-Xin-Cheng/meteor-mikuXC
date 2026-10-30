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
package com.github.mikumiku.addon.modules;

import com.github.mikumiku.addon.BaseModule;
import com.github.mikumiku.addon.util.WaypointUtils;
import com.github.mikumiku.addon.util.seeds.AncientCityLocator;
import com.github.mikumiku.addon.util.seeds.CubiomesStructureLocator;
import com.github.mikumiku.addon.util.seeds.NativeStructureLocator;
import com.github.mikumiku.addon.util.seeds.Seed;
import com.github.mikumiku.addon.util.seeds.SeedVersion;
import com.github.mikumiku.addon.util.seeds.Seeds;
import com.github.mikumiku.addon.util.seeds.StructureSupport;
import com.seedfinding.mcbiome.source.BiomeSource;
import com.seedfinding.mccore.rand.ChunkRand;
import com.seedfinding.mccore.state.Dimension;
import com.seedfinding.mccore.util.pos.BPos;
import com.seedfinding.mccore.util.pos.CPos;
import com.seedfinding.mccore.version.MCVersion;
import com.seedfinding.mcfeature.structure.*;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.level.ChunkPos;
import xaero.hud.minimap.waypoint.set.WaypointSet;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;

import static meteordevelopment.meteorclient.utils.world.Dimension.Overworld;

public class StructureFinder extends BaseModule {
    // 上游用 lombok @Slf4j，此处等价展开为显式字段
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(StructureFinder.class);

    // 设置组
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgSeed = settings.createGroup("种子设置");
    private final SettingGroup sgStructures = settings.createGroup("结构设置");
    // 通用设置
    private final Setting<Integer> searchRadius = sgGeneral.add(new IntSetting.Builder()
        .name("搜索半径")
        .description("以玩家为中心的搜索半径（区块）。")
        .defaultValue(100)
        .min(10)
        .max(20000)
        .sliderMin(10)
        .sliderMax(20000)
        .build()
    );

    private final Setting<Boolean> includeDistance = sgGeneral.add(new BoolSetting.Builder()
        .name("路径点包含距离")
        .description("在路径点名称中包含距离信息")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> autoSearch = sgGeneral.add(new BoolSetting.Builder()
        .name("自动搜索")
        .description("启用模块时自动开始搜索")
        .defaultValue(true)
        .build()
    );

    // 种子设置
    private final Setting<String> seedInput = sgSeed.add(new StringSetting.Builder()
        .name("种子")
        .description("输入服务器种子")
        .defaultValue("")
        .build()
    );

    private final Setting<String> mcVersionInput = sgSeed.add(new StringSetting.Builder()
        .name("游戏版本")
        .description("手动输入 Minecraft 版本，支持 1.12.2 ~ 26.2（例如 26.2、1.21.4、1.20.1、1.12.2）。低于 1.12.2、高于 26.2 或认不出来的写法按空白处理，不会进行搜索。结构位置优先交给 cubiomes（seedmap.app 与 SeedMapper 用的同一套结构库）：版本表、放置算法、频率约简、排除区、要塞同心环与生物群系判定都由它完成，位置与 seedmap.app 一致；它会按版本判定某个结构存不存在（例如 1.12.2 没有埋藏的宝藏与前哨站，1.18 没有古城），不存在的会被跳过并提示。原生库加载失败时自动退回本版原生 / 种子库算法。")
        .defaultValue(SeedVersion.MAX_INPUT_VERSION)
        .build()
    );

    private final Setting<Boolean> applySeed = sgSeed.add(new BoolSetting.Builder()
        .name("应用种子")
        .description("应用手动输入的种子设置")
        .defaultValue(false)
        .onChanged(this::onApplySeedChanged)
        .build()
    );

    private final Setting<Boolean> readSeedLibrary = sgSeed.add(new BoolSetting.Builder()
        .name("读取种子库信息")
        .description("从种子库读取当前世界的种子与版本并显示在聊天栏：单人是存档真实种子，服务器上是之前“应用种子”保存下来的种子。")
        .defaultValue(false)
        .onChanged(this::onReadSeedLibraryChanged)
        .build()
    );

    private final Setting<Boolean> startSearch = sgGeneral.add(new BoolSetting.Builder()
        .name("开始搜索")
        .description("开始搜索结构位置")
        .defaultValue(false)
        .onChanged(this::onStartSearchChanged)
        .build()
    );

    // 结构设置
    private final Setting<Boolean> findVillage = sgStructures.add(new BoolSetting.Builder()
        .name("村庄")
        .description("查找村庄结构")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> findShipwreck = sgStructures.add(new BoolSetting.Builder()
        .name("沉船")
        .description("查找沉船结构")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> findBuriedTreasure = sgStructures.add(new BoolSetting.Builder()
        .name("埋藏的宝藏")
        .description("查找埋藏的宝藏结构")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> findOceanRuin = sgStructures.add(new BoolSetting.Builder()
        .name("海底废墟")
        .description("查找海底废墟结构")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> findPillagerOutpost = sgStructures.add(new BoolSetting.Builder()
        .name("掠夺者前哨站")
        .description("查找掠夺者前哨站结构")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> findNetherFortress = sgStructures.add(new BoolSetting.Builder()
        .name("下界要塞")
        .description("查找下界要塞结构")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> findBastionRemnant = sgStructures.add(new BoolSetting.Builder()
        .name("堡垒遗迹")
        .description("查找堡垒遗迹结构")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> findEndCity = sgStructures.add(new BoolSetting.Builder()
        .name("末地城")
        .description("查找末地城结构")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> findDesertPyramid = sgStructures.add(new BoolSetting.Builder()
        .name("沙漠神殿")
        .description("查找沙漠神殿结构")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> findIgloo = sgStructures.add(new BoolSetting.Builder()
        .name("雪屋")
        .description("查找雪屋结构")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> findJunglePyramid = sgStructures.add(new BoolSetting.Builder()
        .name("丛林神庙")
        .description("查找丛林神庙结构")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> findMansion = sgStructures.add(new BoolSetting.Builder()
        .name("林地府邸")
        .description("查找林地府邸结构")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> findMineshaft = sgStructures.add(new BoolSetting.Builder()
        .name("废弃矿井")
        .description("查找废弃矿井结构")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> findMonument = sgStructures.add(new BoolSetting.Builder()
        .name("海底神殿")
        .description("查找海底神殿结构")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> findNetherFossil = sgStructures.add(new BoolSetting.Builder()
        .name("下界化石")
        .description("查找下界化石结构")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> findRuinedPortal = sgStructures.add(new BoolSetting.Builder()
        .name("废弃传送门")
        .description("查找废弃传送门结构")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> findStronghold = sgStructures.add(new BoolSetting.Builder()
        .name("要塞")
        .description("查找要塞结构")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> findSwampHut = sgStructures.add(new BoolSetting.Builder()
        .name("沼泽小屋")
        .description("查找沼泽小屋结构")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> findAncientCity = sgStructures.add(new BoolSetting.Builder()
        .name("古城")
        .description("查找古城（远古城市，1.19+）。走 cubiomes 的 random_spread（spacing=24、separation=8、salt=20083232）+ 深暗之域判定，标记古城位置（Y=-51）；低于 1.19 的版本会由 cubiomes 判定为不存在并跳过。cubiomes 不可用时退回本版游戏原生的算法。")
        .defaultValue(false)
        .build()
    );

    // 内部变量
    private boolean isSearching = false;
    private CompletableFuture<Void> searchTask = null;
    /** 本次搜索因所选版本过老、种子库没有生成参数而被跳过的结构说明。 */
    private final List<String> skippedByVersion = new ArrayList<>();

    public StructureFinder() {
        super(BaseModule.CATEGORY, "结构搜索", "[标记指定结构位置(1.12.2-26.2)]");
    }

    @Override
    public void onActivate() {
        super.onActivate();
        if (autoSearch.get()) {
            startStructureSearch();
        }

        info("结构搜索模块已启用");
    }

    @Override
    public void onDeactivate() {
        if (searchTask != null && !searchTask.isDone()) {
            searchTask.cancel(true);
            isSearching = false;
            info("搜索已取消");
        }
        info("结构搜索模块已禁用");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        // 检查搜索任务状态
        if (searchTask != null && searchTask.isDone()) {
            isSearching = false;
            searchTask = null;
            startSearch.set(false);
        }
    }

    /** 当前「游戏版本」输入解析出的版本；输入为空或不在 1.12.2 ~ 26.2 内时返回 null（空白）。 */
    private SeedVersion resolvedVersion() {
        return SeedVersion.parseInput(mcVersionInput.get());
    }

    private void onApplySeedChanged(boolean value) {
        if (value) {
            SeedVersion version = resolvedVersion();
            if (version == null) {
                // 版本输入为空或超出 1.12.2 ~ 26.2：按空白处理，不写种子库也不输出
                applySeed.set(false);
                return;
            }
            String seed = seedInput.get().trim();
            if (!seed.isEmpty()) {
                try {
                    Seeds.get().setSeed(seed, version);
                    info("已设置种子: " + seed + " 版本: " + version.name);
                } catch (Exception e) {
                    error("设置种子失败: " + e.getMessage());
                }
            } else {
                error("请先输入种子");
            }
            applySeed.set(false);
        }
    }

    private void onReadSeedLibraryChanged(boolean value) {
        if (!value) return;

        Seed librarySeed = Seeds.get().getSeed();
        if (librarySeed == null || librarySeed.seed == null) {
            warning("种子库里还没有这个世界的种子，请先在“种子”里输入并点“应用种子”。");
        } else {
            reportSeedLibraryInfo(librarySeed);
        }
        readSeedLibrary.set(false);
    }

    /**
     * 取这次搜索要用的种子与版本：版本一律用「游戏版本」输入框里输入的（没有有效输入则不搜索）；
     * 种子优先用「种子」输入框里输入的，输入为空时才用种子库兜底
     * （单人是存档真实种子，服务器上是按世界名保存的种子）。
     */
    private Seed resolveSeed() {
        SeedVersion version = resolvedVersion();
        if (version == null) return null;

        String manual = seedInput.get().trim();
        if (!manual.isEmpty()) {
            info("使用输入的种子: " + manual + "，版本: " + version.name);
            return new Seed(Seeds.toSeed(manual), version);
        }

        Seed librarySeed = Seeds.get().getSeed();
        if (librarySeed != null && librarySeed.seed != null) {
            info("“种子”输入为空，改用种子库记录的种子: " + librarySeed.seed + "，版本: " + version.name);
            return new Seed(librarySeed.seed, version);
        }

        return null;
    }

    /** 把种子库里的信息报到聊天栏：种子、游戏版本，以及这个版本的数据来源。 */
    private void reportSeedLibraryInfo(Seed seed) {
        if (seed == null || seed.seed == null || seed.version == null) return;

        String runningVersion = net.minecraft.SharedConstants.getCurrentVersion().name();
        String source;
        // 结构位置首选 cubiomes，所以能建出定位器时数据来源就是 cubiomes，而不是种子库 /
        // 本版原生；只有 cubiomes 不可用时才按下面原有的口径回报。
        CubiomesStructureLocator locator = CubiomesStructureLocator.tryCreate(seed.version, seed.seed);
        if (locator != null) {
            source = "cubiomes " + locator.versionLabel() + "（与 https://www.seedmap.app 同一套结构库）";
        } else if (seed.version.hasExactLibraryData()) {
            source = "种子库版本 " + seed.version.toMCVersion().name;
        } else if (seed.version.sharesNativeWorldgen(runningVersion)) {
            source = "本版原生世界生成（" + runningVersion + "，" + seed.version.name + " 与本版同族）";
        } else {
            source = "无：种子库没收录 " + seed.version.name + "，本版运行版本（" + runningVersion
                + "）也不是它的同族，该版本涉及的结构会跳过，不会用别的版本的数据顶替";
        }
        info(String.format("种子库信息：种子=%d，游戏版本=%s，数据来源=%s",
            seed.seed, seed.version.name, source));
    }

    private void onStartSearchChanged(boolean value) {
        if (value && !isSearching) {
            startStructureSearch();
        }
    }

    private void startStructureSearch() {
        if (isSearching) {
            warning("搜索正在进行中...");
            return;
        }

        if (mc.player == null) {
            error("玩家不存在");
            return;
        }

        // 「游戏版本」输入为空或不在 1.12.2 ~ 26.2 之内：按空白处理，不搜索也不输出
        if (resolvedVersion() == null) return;

        // 获取种子：优先用「种子」输入框，输入为空才回退种子库
        Seed worldSeed = resolveSeed();
        if (worldSeed == null) {
            error("没有可用的种子：请在「种子」里输入种子，或先点「应用种子」把种子写进种子库");
            return;
        }

        // 验证种子数据
        if (worldSeed.seed == null || worldSeed.version == null) {
            error("种子数据无效，请重新设置种子");
            return;
        }

        reportSeedLibraryInfo(worldSeed);

        // 检查是否在主世界
        if (PlayerUtils.getDimension() != Overworld) {
            warning("建议在主世界使用此功能以获得最准确的结果");
        }

        isSearching = true;
        BlockPos playerPos = mc.player.blockPosition();
        // 古城走原版世界生成算法，需要客户端世界的注册表；在主线程取好后交给后台任务
        RegistryAccess registryAccess = mc.level != null ? mc.level.registryAccess() : null;

        info("开始搜索结构位置... 半径: " + searchRadius.get() + " 区块");

        // 异步搜索
        searchTask = CompletableFuture.runAsync(() -> {
            try {
                searchStructureLocations(worldSeed, playerPos, registryAccess);
            } catch (Exception e) {
                error("搜索过程中发生错误: " + e.getMessage());
                log.error("搜索过程中发生错误: ", e);  // 在开发环境中启用
                isSearching = false;
            }
        });
    }

    private void searchStructureLocations(Seed worldSeed, BlockPos playerPos, RegistryAccess registryAccess) {
        List<StructureLocation> structureLocations = new ArrayList<>();

        try {
            // 验证种子数据
            if (worldSeed == null || worldSeed.seed == null || worldSeed.version == null) {
                error("种子数据无效");
                return;
            }

            long seed = worldSeed.seed;
            SeedVersion selected = worldSeed.version;
            // 只有种子库确实收录了这个版本（游戏版本号与库版本号一致）时，库里的数据才是
            // 该版本自己的数据；26.x 对应的库常量是 1.21，不能拿来顶替 26.x。
            boolean libraryData = selected.hasExactLibraryData();
            MCVersion version = selected.toMCVersion();
            String runningVersionName = net.minecraft.SharedConstants.getCurrentVersion().name();

            // 结构库优先用 cubiomes——https://www.seedmap.app 与 SeedMapper 用的就是
            // 这一套库：版本表、放置算法、频率约简、排除区、要塞同心环与生物群系判定
            // 全部原样交给它，位置与 seedmap.app 一致。原生库加载失败或版本表里没有该
            // 版本时返回 null，下面会退回本版原生 / 种子库算法。
            CubiomesStructureLocator cubiomes = CubiomesStructureLocator.tryCreate(selected, seed);

            info("使用种子: " + seed + ", 版本: " + selected.name
                + (cubiomes != null
                    ? "（cubiomes " + cubiomes.versionLabel() + "，与 seedmap.app 同一套库）"
                    : (libraryData ? "（种子库版本 " + version.name + "）" : "（本版原生世界生成）")));

            skippedByVersion.clear();

            BiomeSource overworldBiomeSource = libraryData
                ? BiomeSource.of(Dimension.OVERWORLD, version, seed)
                : null;
            if (libraryData && overworldBiomeSource == null) {
                error("当前游戏版本 " + selected.name + "（种子库版本 " + version.name
                    + "）过老，种子库无法建立生物群系源，结构搜索无法进行，请改选 1.8 及以上的版本。");
                return;
            }
            ChunkRand rand = new ChunkRand();

            int radius = searchRadius.get();
            // 安全检查搜索半径
            if (radius > 500) {
                warning("搜索半径过大 (" + radius + ")，可能导致性能问题或错误。 ");
            }

            int playerChunkX = playerPos.getX() >> 4;
            int playerChunkZ = playerPos.getZ() >> 4;

            // 本次勾选的结构（中文名）。cubiomes 与两套后备算法共用同一份口径。
            Set<String> requested = new HashSet<>();
            if (findVillage.get()) requested.add("村庄");
            if (findShipwreck.get()) requested.add("沉船");
            if (findBuriedTreasure.get()) requested.add("埋藏的宝藏");
            if (findOceanRuin.get()) requested.add("海底废墟");
            if (findPillagerOutpost.get()) requested.add("掠夺者前哨站");
            if (findDesertPyramid.get()) requested.add("沙漠神殿");
            if (findIgloo.get()) requested.add("雪屋");
            if (findJunglePyramid.get()) requested.add("丛林神庙");
            if (findMansion.get()) requested.add("林地府邸");
            if (findMineshaft.get()) requested.add("废弃矿井");
            if (findMonument.get()) requested.add("海底神殿");
            if (findRuinedPortal.get()) requested.add("废弃传送门");
            if (findSwampHut.get()) requested.add("沼泽小屋");
            if (findNetherFortress.get()) requested.add("下界要塞");
            if (findBastionRemnant.get()) requested.add("堡垒遗迹");
            if (findNetherFossil.get()) requested.add("下界化石");
            if (findEndCity.get()) requested.add("末地城");
            if (findStronghold.get()) requested.add("要塞");
            if (findAncientCity.get()) requested.add("古城");

            // 首选 cubiomes：与 seedmap.app 逐条对齐，且版本表覆盖 1.12.2 ~ 26.2 的全部
            // 可选版本，所以正常情况下这里就会直接算完并返回；下面两套算法只在原生库
            // 加载失败（不支持的平台等）时兜底。
            if (cubiomes != null) {
                info("结构计算方式：cubiomes " + cubiomes.versionLabel()
                    + "（seedmap.app 同款结构库，自带版本表、频率约简、排除区与要塞同心环）");
                List<String> skippedByCubiomes = new ArrayList<>();
                searchCubiomesStructures(structureLocations, cubiomes, playerChunkX, playerChunkZ,
                    radius, requested, skippedByCubiomes);
                if (!skippedByCubiomes.isEmpty()) {
                    warning("当前游戏版本 " + selected.name + "（cubiomes " + cubiomes.versionLabel()
                        + "）里没有这些结构，已跳过：" + StructureSupport.describeSkipped(skippedByCubiomes));
                }
                displayResults(structureLocations, playerPos);
                isSearching = false;
                return;
            }
            if (!CubiomesStructureLocator.isNativeLoaded()) {
                warning("cubiomes 原生库不可用（" + CubiomesStructureLocator.loadMessage()
                    + "），这次退回原有算法。");
            }

            // 主世界结构是否被勾选：用于在没有本版原生数据也没有库数据时给出提示。
            boolean anyOverworldRequested = findVillage.get() || findShipwreck.get()
                || findBuriedTreasure.get() || findOceanRuin.get() || findPillagerOutpost.get()
                || findDesertPyramid.get() || findIgloo.get() || findJunglePyramid.get()
                || findMansion.get() || findMineshaft.get() || findMonument.get()
                || findRuinedPortal.get() || findSwampHut.get();

            // 26.x 在种子库里只有 1.21 的数据，不能拿来顶替：只有本版原生的世界生成
            // 数据才是 26.x 自己的数据。主世界、下界、末地的结构与要塞都能用本版
            // 注册表里的结构集 / 同心环算出来。
            boolean nativeMode = selected.nativeWorldgen && registryAccess != null;
            Set<String> nativeWanted = new HashSet<>();
            if (nativeMode) {
                if (findVillage.get()) nativeWanted.add("村庄");
                if (findShipwreck.get()) nativeWanted.add("沉船");
                if (findBuriedTreasure.get()) nativeWanted.add("埋藏的宝藏");
                if (findOceanRuin.get()) nativeWanted.add("海底废墟");
                if (findPillagerOutpost.get()) nativeWanted.add("掠夺者前哨站");
                if (findDesertPyramid.get()) nativeWanted.add("沙漠神殿");
                if (findIgloo.get()) nativeWanted.add("雪屋");
                if (findJunglePyramid.get()) nativeWanted.add("丛林神庙");
                if (findMansion.get()) nativeWanted.add("林地府邸");
                if (findMineshaft.get()) nativeWanted.add("废弃矿井");
                if (findMonument.get()) nativeWanted.add("海底神殿");
                if (findRuinedPortal.get()) nativeWanted.add("废弃传送门");
                if (findSwampHut.get()) nativeWanted.add("沼泽小屋");
                if (findNetherFortress.get()) nativeWanted.add("下界要塞");
                if (findBastionRemnant.get()) nativeWanted.add("堡垒遗迹");
                if (findNetherFossil.get()) nativeWanted.add("下界化石");
                if (findEndCity.get()) nativeWanted.add("末地城");
                if (findStronghold.get()) nativeWanted.add("要塞");
            }
            boolean anyNativeRequested = !nativeWanted.isEmpty();

            // 注册表读不到时 nativeLocator 为 null：26.x 没有可用的库数据，下面的
            // libraryData 分支不会执行，会明确跳过而不是退回 1.21。
            NativeStructureLocator nativeLocator = (nativeMode && anyNativeRequested)
                ? NativeStructureLocator.create(registryAccess, seed)
                : null;

            // 搜索结构。所选版本比某个结构的最低版本还老时跳过它：种子库的配置表
            // 里没有那个版本，硬算会得到错误结果甚至抛异常（见 StructureSupport）。
            if (nativeLocator != null) {
                info("结构计算方式：本版原生算法（用本版自己的结构集、生物群系白名单与同心环）");
                searchNativeStructures(structureLocations, nativeLocator, playerChunkX, playerChunkZ, radius, nativeWanted);
            } else if (libraryData) {
                if (findVillage.get() && StructureSupport.check(version, StructureSupport.MIN_LEGACY, "村庄", skippedByVersion)) {
                    searchRegionStructure(structureLocations, new Village(version), seed, overworldBiomeSource, rand, playerChunkX, playerChunkZ, radius, "村庄");
                }

                if (findShipwreck.get() && StructureSupport.check(version, StructureSupport.MIN_OCEAN, "沉船", skippedByVersion)) {
                    searchRegionStructure(structureLocations, new Shipwreck(version), seed, overworldBiomeSource, rand, playerChunkX, playerChunkZ, radius, "沉船");
                }

                if (findBuriedTreasure.get() && StructureSupport.check(version, StructureSupport.MIN_OCEAN, "埋藏的宝藏", skippedByVersion)) {
                    searchRegionStructure(structureLocations, new BuriedTreasure(version), seed, overworldBiomeSource, rand, playerChunkX, playerChunkZ, radius, "埋藏的宝藏");
                }

                if (findOceanRuin.get() && StructureSupport.check(version, StructureSupport.MIN_OCEAN, "海底废墟", skippedByVersion)) {
                    searchRegionStructure(structureLocations, new OceanRuin(version), seed, overworldBiomeSource, rand, playerChunkX, playerChunkZ, radius, "海底废墟");
                }

                if (findPillagerOutpost.get() && StructureSupport.check(version, StructureSupport.MIN_OUTPOST, "掠夺者前哨站", skippedByVersion)) {
                    searchRegionStructure(structureLocations, new PillagerOutpost(version), seed, overworldBiomeSource, rand, playerChunkX, playerChunkZ, radius, "掠夺者前哨站");
                }

                if (findDesertPyramid.get() && StructureSupport.check(version, StructureSupport.MIN_LEGACY, "沙漠神殿", skippedByVersion)) {
                    searchRegionStructure(structureLocations, new DesertPyramid(version), seed, overworldBiomeSource, rand, playerChunkX, playerChunkZ, radius, "沙漠神殿");
                }

                if (findIgloo.get() && StructureSupport.check(version, StructureSupport.MIN_END_CITY, "雪屋", skippedByVersion)) {
                    searchRegionStructure(structureLocations, new Igloo(version), seed, overworldBiomeSource, rand, playerChunkX, playerChunkZ, radius, "雪屋");
                }

                if (findJunglePyramid.get() && StructureSupport.check(version, StructureSupport.MIN_LEGACY, "丛林神庙", skippedByVersion)) {
                    searchRegionStructure(structureLocations, new JunglePyramid(version), seed, overworldBiomeSource, rand, playerChunkX, playerChunkZ, radius, "丛林神庙");
                }

                if (findMansion.get() && StructureSupport.check(version, StructureSupport.MIN_MANSION, "林地府邸", skippedByVersion)) {
                    searchRegionStructure(structureLocations, new Mansion(version), seed, overworldBiomeSource, rand, playerChunkX, playerChunkZ, radius, "林地府邸");
                }

                if (findMineshaft.get() && StructureSupport.check(version, StructureSupport.MIN_LEGACY, "废弃矿井", skippedByVersion)) {
                    searchStructure(structureLocations, new Mineshaft(version), seed, overworldBiomeSource, rand, playerChunkX, playerChunkZ, radius, "废弃矿井");
                }

                if (findMonument.get() && StructureSupport.check(version, StructureSupport.MIN_LEGACY, "海底神殿", skippedByVersion)) {
                    searchRegionStructure(structureLocations, new Monument(version), seed, overworldBiomeSource, rand, playerChunkX, playerChunkZ, radius, "海底神殿");
                }

                if (findRuinedPortal.get() && StructureSupport.check(version, StructureSupport.MIN_NETHER_116, "废弃传送门", skippedByVersion)) {
                    searchRegionStructure(structureLocations, new RuinedPortal(Dimension.OVERWORLD, version), seed, overworldBiomeSource, rand, playerChunkX, playerChunkZ, radius, "废弃传送门");
                }

                if (findSwampHut.get() && StructureSupport.check(version, StructureSupport.MIN_LEGACY, "沼泽小屋", skippedByVersion)) {
                    searchRegionStructure(structureLocations, new SwampHut(version), seed, overworldBiomeSource, rand, playerChunkX, playerChunkZ, radius, "沼泽小屋");
                }
            }

            // 要塞、下界要塞、堡垒遗迹、下界化石、末地城：种子库收录的版本走种子库，
            // 26.x 这类没有库数据的版本走本版原生世界生成（要塞是同心环，下界/末地是
            // random_spread）。两条路都没有该版本自己的数据时明确跳过，绝不用别的版本顶替。

            // 要塞是同心环放置，种子库版本由 seedfinding 算
            if (libraryData && findStronghold.get() && StructureSupport.check(version, StructureSupport.MIN_STRONGHOLD, "要塞", skippedByVersion)) {
                searchStructure(structureLocations, new Stronghold(version), seed, overworldBiomeSource, rand, playerChunkX, playerChunkZ, radius, "要塞");
            }

            // 搜索下界结构
            if (libraryData && findNetherFortress.get() && StructureSupport.check(version, StructureSupport.MIN_LEGACY, "下界要塞", skippedByVersion)) {
                // 下界要塞搜索
                searchNetherRegionStructure(structureLocations, new Fortress(version), seed, version, playerChunkX, playerChunkZ, radius, "下界要塞");
            }

            if (libraryData && findBastionRemnant.get() && StructureSupport.check(version, StructureSupport.MIN_NETHER_116, "堡垒遗迹", skippedByVersion)) {
                // 堡垒遗迹搜索
                searchNetherRegionStructure(structureLocations, new BastionRemnant(version), seed, version, playerChunkX, playerChunkZ, radius, "堡垒遗迹");
            }

            if (libraryData && findNetherFossil.get() && StructureSupport.check(version, StructureSupport.MIN_NETHER_116, "下界化石", skippedByVersion)) {
                // 下界化石搜索
                searchNetherRegionStructure(structureLocations, new NetherFossil(version), seed, version, playerChunkX, playerChunkZ, radius, "下界化石");
            }

            // 搜索末地结构
            if (libraryData && findEndCity.get() && StructureSupport.check(version, StructureSupport.MIN_END_CITY, "末地城", skippedByVersion)) {
                searchEndRegionStructure(structureLocations, new EndCity(version), seed, version, playerChunkX, playerChunkZ, radius, "末地城");
            }

            // 这些结构没有被本版原生数据覆盖时（没有库数据、也没读到本版结构集）
            // 逐个列出并跳过，不拿别的版本的数据顶替。
            if (!libraryData) {
                Set<String> supported = nativeLocator != null
                    ? nativeLocator.supportedDisplays() : Set.of();
                List<String> uncovered = new ArrayList<>();
                if (findStronghold.get() && !supported.contains("要塞")) uncovered.add("要塞");
                if (findNetherFortress.get() && !supported.contains("下界要塞")) uncovered.add("下界要塞");
                if (findBastionRemnant.get() && !supported.contains("堡垒遗迹")) uncovered.add("堡垒遗迹");
                if (findNetherFossil.get() && !supported.contains("下界化石")) uncovered.add("下界化石");
                if (findEndCity.get() && !supported.contains("末地城")) uncovered.add("末地城");
                if (!uncovered.isEmpty()) {
                    String reason = nativeLocator != null
                        ? "（本版注册表里没有这些结构集）"
                        : "（读不到本版世界生成数据，种子库也没有 " + selected.name + " 的数据）";
                    skippedByVersion.add(String.join("、", uncovered) + reason);
                }
            }

            // 古城：种子库没有它的结构类与生物群系数据，只能用本版原生世界生成；所选版本与
            // 本版运行版本不同族时，本版数据并不是该版本自己的数据，按需求跳过。
            if (findAncientCity.get() && StructureSupport.check(version, StructureSupport.MIN_ANCIENT_CITY, "古城", skippedByVersion)) {
                if (selected.sharesNativeWorldgen(runningVersionName)) {
                    searchAncientCities(structureLocations, seed, registryAccess, selected, playerChunkX, playerChunkZ, radius, "古城");
                } else {
                    warning("古城的位置也要用 " + selected.name + " 自己的世界生成数据：本版运行的是 "
                        + runningVersionName + "，与所选版本不同族，已跳过古城，不会用本版数据顶替。");
                }
            }

            if (!skippedByVersion.isEmpty()) {
                warning("当前游戏版本 " + selected.name + " 下这些结构没有该版本自己的数据，已跳过："
                    + StructureSupport.describeSkipped(skippedByVersion));
            }

        } catch (Exception e) {
            error("搜索过程中发生错误, 建议调小搜索半径: " + e.getMessage());
            return;
        }

        // 显示结果
        displayResults(structureLocations, playerPos);
        isSearching = false;
    }

    /**
     * 用本版注册表里的世界生成数据搜索结构（26.x）：主世界/下界/末地的
     * random_spread 结构各有自己的路径点高度，要塞是同心环。定位器已经在主线程
     * 之外建立好了，这里只负责把结果换算成路径点坐标。
     */
    private void searchNativeStructures(List<StructureLocation> locations, NativeStructureLocator locator,
                                        int playerChunkX, int playerChunkZ, int radius, Set<String> wanted) {
        BooleanSupplier running = () -> isSearching;
        List<NativeStructureLocator.Found> found = locator.scan(playerChunkX, playerChunkZ, radius, wanted, running);

        for (NativeStructureLocator.Found hit : found) {
            BPos pos = new BPos(hit.blockX, hit.y, hit.blockZ);
            double distance = Math.sqrt(Math.pow(pos.getX() - playerChunkX * 16, 2)
                + Math.pow(pos.getZ() - playerChunkZ * 16, 2));
            locations.add(new StructureLocation(pos, distance, hit.display, hit.y));
        }
    }

    /**
     * 用 cubiomes 搜索结构：定位、频率约简、排除区与生物群系判定都在库内部完成，
     * 这里只负责把命中的方块坐标换算成与其它算法一致的 {@link StructureLocation}。
     */
    private void searchCubiomesStructures(List<StructureLocation> locations, CubiomesStructureLocator locator,
                                          int playerChunkX, int playerChunkZ, int radius, Set<String> wanted,
                                          List<String> skipped) {
        BooleanSupplier running = () -> isSearching;
        List<CubiomesStructureLocator.Found> found =
            locator.scan(playerChunkX, playerChunkZ, radius, wanted, skipped, running);

        int playerX = playerChunkX * 16;
        int playerZ = playerChunkZ * 16;
        for (CubiomesStructureLocator.Found hit : found) {
            BPos pos = new BPos(hit.blockX(), hit.y(), hit.blockZ());
            double distance = Math.sqrt(Math.pow(hit.blockX() - playerX, 2)
                + Math.pow(hit.blockZ() - playerZ, 2));
            locations.add(new StructureLocation(pos, distance, hit.display(), hit.y()));
        }
    }

    private void searchStructure(List<StructureLocation> locations, Structure structure, long seed, BiomeSource biomeSource, ChunkRand rand, int playerChunkX, int playerChunkZ, int radius, String structureName) {
        try {
            int minChunkX = Math.max(playerChunkX - radius, Integer.MIN_VALUE / 2);
            int maxChunkX = Math.min(playerChunkX + radius, Integer.MAX_VALUE / 2);
            int minChunkZ = Math.max(playerChunkZ - radius, Integer.MIN_VALUE / 2);
            int maxChunkZ = Math.min(playerChunkZ + radius, Integer.MAX_VALUE / 2);

            for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                    if (!isSearching) return; // 检查是否被取消

                    try {
                        CPos pos = new CPos(chunkX, chunkZ);

                        // 检查是否在搜索范围内
                        double chunkDistance = Math.sqrt(Math.pow(pos.getX() - playerChunkX, 2) +
                            Math.pow(pos.getZ() - playerChunkZ, 2));
                        if (chunkDistance > radius) continue;

                        if (structure.canSpawn(pos, biomeSource)) {
                            BPos blockPos = new BPos(pos.getX() * 16, 0, pos.getZ() * 16);
                            double distance = Math.sqrt(Math.pow(blockPos.getX() - playerChunkX * 16, 2) +
                                Math.pow(blockPos.getZ() - playerChunkZ * 16, 2));

                            locations.add(new StructureLocation(blockPos, distance, structureName, 100));
                        }
                    } catch (Exception e) {
                        warning(String.format("处理区块 (%d,%d) 时出错: %s", chunkX, chunkZ, e.getMessage()));
                        continue;
                    }
                }
            }
        } catch (Exception e) {
            warning("搜索结构 " + structureName + " 时出错: " + e.getMessage());
        }
    }

    private void searchRegionStructure(List<StructureLocation> locations, RegionStructure<?, ?> structure, long seed, BiomeSource biomeSource, ChunkRand rand, int playerChunkX, int playerChunkZ, int radius, String structureName) {
        try {
            int spacing = structure.getSpacing();
            if (spacing <= 0) {
                warning("结构 " + structureName + " 间距无效: " + spacing);
                return;
            }

            int minChunkX = Math.max(playerChunkX - radius, Integer.MIN_VALUE / 2);
            int maxChunkX = Math.min(playerChunkX + radius, Integer.MAX_VALUE / 2);
            int minChunkZ = Math.max(playerChunkZ - radius, Integer.MIN_VALUE / 2);
            int maxChunkZ = Math.min(playerChunkZ + radius, Integer.MAX_VALUE / 2);

            int minRegionX = minChunkX / spacing;
            int maxRegionX = maxChunkX / spacing;
            int minRegionZ = minChunkZ / spacing;
            int maxRegionZ = maxChunkZ / spacing;

            for (int regionX = minRegionX; regionX <= maxRegionX; regionX++) {
                for (int regionZ = minRegionZ; regionZ <= maxRegionZ; regionZ++) {
                    if (!isSearching) return; // 检查是否被取消

                    try {
                        CPos pos = structure.getInRegion(seed, regionX, regionZ, rand);
                        if (pos == null) {
                            continue;
                        }
                        // 检查是否在搜索范围内
                        double chunkDistance = Math.sqrt(Math.pow(pos.getX() - playerChunkX, 2) +
                            Math.pow(pos.getZ() - playerChunkZ, 2));
                        if (chunkDistance > radius) continue;

                        if (structure.canSpawn(pos, biomeSource)) {
                            BPos blockPos = new BPos(pos.getX() * 16, 0, pos.getZ() * 16);

                            double distance = Math.sqrt(Math.pow(blockPos.getX() - playerChunkX * 16, 2) +
                                Math.pow(blockPos.getZ() - playerChunkZ * 16, 2));

                            locations.add(new StructureLocation(blockPos, distance, structureName, 100));
                        }
                    } catch (Exception e) {
                        warning(String.format("处理区域 (%d,%d) 时出错: %s", regionX, regionZ, e.getMessage()));
                        continue;
                    }
                }
            }
        } catch (Exception e) {
            warning("搜索结构 " + structureName + " 时出错: " + e.getMessage());
        }
    }

    private void searchNetherRegionStructure(List<StructureLocation> locations, RegionStructure<?, ?> structure, long seed, MCVersion version, int playerChunkX, int playerChunkZ, int radius, String structureName) {
        try {
            // 下界结构需要使用下界维度
            BiomeSource netherBiomeSource = BiomeSource.of(Dimension.NETHER, version, seed);

            int spacing = structure.getSpacing();
            if (spacing <= 0) {
                warning("结构 " + structureName + " 间距无效: " + spacing);
                return;
            }

            int minChunkX = Math.max(playerChunkX - radius, Integer.MIN_VALUE / 2);
            int maxChunkX = Math.min(playerChunkX + radius, Integer.MAX_VALUE / 2);
            int minChunkZ = Math.max(playerChunkZ - radius, Integer.MIN_VALUE / 2);
            int maxChunkZ = Math.min(playerChunkZ + radius, Integer.MAX_VALUE / 2);

            int minRegionX = minChunkX / spacing;
            int maxRegionX = maxChunkX / spacing;
            int minRegionZ = minChunkZ / spacing;
            int maxRegionZ = maxChunkZ / spacing;

            ChunkRand rand = new ChunkRand();

            for (int regionX = minRegionX; regionX <= maxRegionX; regionX++) {
                for (int regionZ = minRegionZ; regionZ <= maxRegionZ; regionZ++) {
                    if (!isSearching) return; // 检查是否被取消

                    try {
                        CPos pos = structure.getInRegion(seed, regionX, regionZ, rand);

                        // 检查是否在搜索范围内
                        double chunkDistance = Math.sqrt(Math.pow(pos.getX() - playerChunkX, 2) +
                            Math.pow(pos.getZ() - playerChunkZ, 2));
                        if (chunkDistance > radius) continue;

                        if (structure.canSpawn(pos, netherBiomeSource)) {
                            BPos blockPos = new BPos(pos.getX() * 16, 0, pos.getZ() * 16);
                            double distance = Math.sqrt(Math.pow(blockPos.getX() - playerChunkX * 16, 2) +
                                Math.pow(blockPos.getZ() - playerChunkZ * 16, 2));

                            locations.add(new StructureLocation(blockPos, distance, structureName, 100));
                        }
                    } catch (Exception e) {
                        warning(String.format("处理区域 (%d,%d) 时出错: %s", regionX, regionZ, e.getMessage()));
                        continue;
                    }
                }
            }
        } catch (Exception e) {
            warning("搜索下界结构 " + structureName + " 时出错: " + e.getMessage());
        }
    }

    private void searchEndRegionStructure(List<StructureLocation> locations, RegionStructure<?, ?> structure, long seed, MCVersion version, int playerChunkX, int playerChunkZ, int radius, String structureName) {
        try {
            // 末地结构需要使用末地维度
            BiomeSource endBiomeSource = BiomeSource.of(Dimension.END, version, seed);

            int spacing = structure.getSpacing();
            if (spacing <= 0) {
                warning("结构 " + structureName + " 间距无效: " + spacing);
                return;
            }

            int minChunkX = Math.max(playerChunkX - radius, Integer.MIN_VALUE / 2);
            int maxChunkX = Math.min(playerChunkX + radius, Integer.MAX_VALUE / 2);
            int minChunkZ = Math.max(playerChunkZ - radius, Integer.MIN_VALUE / 2);
            int maxChunkZ = Math.min(playerChunkZ + radius, Integer.MAX_VALUE / 2);

            int minRegionX = minChunkX / spacing;
            int maxRegionX = maxChunkX / spacing;
            int minRegionZ = minChunkZ / spacing;
            int maxRegionZ = maxChunkZ / spacing;

            ChunkRand rand = new ChunkRand();

            for (int regionX = minRegionX; regionX <= maxRegionX; regionX++) {
                for (int regionZ = minRegionZ; regionZ <= maxRegionZ; regionZ++) {
                    if (!isSearching) return; // 检查是否被取消

                    try {
                        CPos pos = structure.getInRegion(seed, regionX, regionZ, rand);

                        // 检查是否在搜索范围内
                        double chunkDistance = Math.sqrt(Math.pow(pos.getX() - playerChunkX, 2) +
                            Math.pow(pos.getZ() - playerChunkZ, 2));
                        if (chunkDistance > radius) continue;

                        if (structure.canSpawn(pos, endBiomeSource)) {
                            BPos blockPos = new BPos(pos.getX() * 16, 0, pos.getZ() * 16);
                            double distance = Math.sqrt(Math.pow(blockPos.getX() - playerChunkX * 16, 2) +
                                Math.pow(blockPos.getZ() - playerChunkZ * 16, 2));

                            locations.add(new StructureLocation(blockPos, distance, structureName, 100));
                        }
                    } catch (Exception e) {
                        warning(String.format("处理区域 (%d,%d) 时出错: %s", regionX, regionZ, e.getMessage()));
                        continue;
                    }
                }
            }
        } catch (Exception e) {
            warning("搜索末地结构 " + structureName + " 时出错: " + e.getMessage());
        }
    }

    /**
     * 用本版游戏原生的世界生成算法搜索古城：先按 random_spread 算出每个区域的
     * 候选区块，再用深暗之域过滤。古城只生成在深暗之域，这一步不能省。
     *
     * <p>只有所选版本与本版运行版本同族时才调用这里——那时本版的原生世界生成数据
     * 就是该版本自己的数据；不同族时调用方会直接跳过，不会用本版数据顶替。
     */
    private void searchAncientCities(List<StructureLocation> locations, long seed, RegistryAccess registryAccess,
                                     SeedVersion selectedVersion,
                                     int playerChunkX, int playerChunkZ, int radius, String structureName) {
        AncientCityLocator locator = AncientCityLocator.create(registryAccess, seed);
        if (locator == null) {
            warning("无法用本版世界生成算法建立古城定位器，已跳过古城。");
            return;
        }

        String runningVersion = net.minecraft.SharedConstants.getCurrentVersion().name();
        if (selectedVersion == null || !selectedVersion.sharesNativeWorldgen(runningVersion)) {
            // 双保险：正常情况下调用方已经挡掉了不同族的版本。
            warning("古城需要所选版本自己的世界生成数据；本版运行的是 " + runningVersion
                + "，与所选版本不同族，已跳过古城，不会用本版数据顶替。");
            return;
        }

        int spacing = AncientCityLocator.SPACING;
        int minRegionX = Math.floorDiv(playerChunkX - radius, spacing);
        int maxRegionX = Math.floorDiv(playerChunkX + radius, spacing);
        int minRegionZ = Math.floorDiv(playerChunkZ - radius, spacing);
        int maxRegionZ = Math.floorDiv(playerChunkZ + radius, spacing);

        for (int regionX = minRegionX; regionX <= maxRegionX; regionX++) {
            for (int regionZ = minRegionZ; regionZ <= maxRegionZ; regionZ++) {
                if (!isSearching) return;

                ChunkPos pos;
                try {
                    pos = locator.potentialChunk(seed, regionX, regionZ);
                } catch (Exception e) {
                    continue;
                }
                if (pos == null) continue;

                int chunkX = pos.x();
                int chunkZ = pos.z();

                double chunkDistance = Math.sqrt(Math.pow(chunkX - playerChunkX, 2)
                    + Math.pow(chunkZ - playerChunkZ, 2));
                if (chunkDistance > radius) continue;

                try {
                    if (!locator.isDeepDarkAt(chunkX, chunkZ)) continue;
                } catch (Exception e) {
                    continue;
                }

                BPos blockPos = new BPos((chunkX << 4) + 8, AncientCityLocator.FLOOR_Y, (chunkZ << 4) + 8);
                double distance = Math.sqrt(Math.pow(blockPos.getX() - playerChunkX * 16, 2)
                    + Math.pow(blockPos.getZ() - playerChunkZ * 16, 2));

                locations.add(new StructureLocation(blockPos, distance, structureName, AncientCityLocator.FLOOR_Y));
            }
        }
    }

    private void displayResults(List<StructureLocation> locations, BlockPos playerPos) {
        if (locations.isEmpty()) {
            warning("在指定范围内未找到任何结构");
            return;
        }

        // 按距离排序
        locations.sort((a, b) -> Double.compare(a.distance, b.distance));

        // 获取Xaero路径点集合
        WaypointSet waypointSet = WaypointUtils.getWaypointSet();
        if (waypointSet == null) {
            warning("无法获取Xaero路径点集合，请确保已安装Xaero地图模组");
            return;
        }

        int addedCount = 0;

        // 添加路径点到Xaero地图
        for (int i = 0; i < locations.size(); i++) {
            StructureLocation loc = locations.get(i);
            BPos pos = loc.position;

            // 创建路径点名称，包含距离信息
            String waypointName;
            if (includeDistance.get()) {
                waypointName = String.format("%s #%d (%.0fm)", loc.structureType, i + 1, loc.distance);
            } else {
                waypointName = String.format("%s #%d", loc.structureType, i + 1);
            }

            // 添加到Xaero路径点（结构在地表附近用 Y=100，古城等地下结构用真实高度）
            WaypointUtils.addToWaypoints(pos.getX(), loc.y, pos.getZ(), waypointName, "结构");
            addedCount++;
        }

        info(String.format("搜索完成！找到 %d 个结构，已添加到Xaero路径点", addedCount));
    }

    // 内部类存储结构位置信息
    private static class StructureLocation {
        final BPos position;
        final double distance;
        final String structureType;
        /** 路径点高度：地表结构用 100，古城这类地下结构用真实高度。 */
        final int y;

        StructureLocation(BPos position, double distance, String structureType, int y) {
            this.position = position;
            this.distance = distance;
            this.structureType = structureType;
            this.y = y;
        }
    }
}
