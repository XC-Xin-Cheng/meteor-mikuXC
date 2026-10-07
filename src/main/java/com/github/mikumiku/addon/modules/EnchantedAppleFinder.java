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
import com.github.mikumiku.addon.util.seeds.NativeStructureLocator;
import com.github.mikumiku.addon.util.seeds.NativeStructureLoot;
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
import com.seedfinding.mcfeature.loot.ChestContent;
import com.seedfinding.mcfeature.loot.ILoot;
import com.seedfinding.mcfeature.loot.item.Items;
import com.seedfinding.mcfeature.structure.BuriedTreasure;
import com.seedfinding.mcfeature.structure.DesertPyramid;
import com.seedfinding.mcfeature.structure.EndCity;
import com.seedfinding.mcfeature.structure.RegionStructure;
import com.seedfinding.mcfeature.structure.RuinedPortal;
import com.seedfinding.mcfeature.structure.Shipwreck;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.ChunkPos;
import xaero.hud.minimap.waypoint.set.WaypointSet;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static meteordevelopment.meteorclient.utils.world.Dimension.Overworld;

/**
 * 「附魔金标记」：融合 SeedMapper 的做法，用种子定位结构并模拟容器战利品，
 * 找出含附魔金苹果的容器，并在 Xaero 上用黄色路径点「附魔金」标出容器坐标。
 *
 * <p>两条计算路线：
 * <ul>
 *   <li><b>本版原生（26.x 默认）</b>：位置由 {@link NativeStructureLocator} 读本版
 *       注册表里的结构集精确算出；容器内容由 {@link NativeStructureLoot} 直接调用
 *       本版原版结构生成算法把结构真正搭一遍，逐箱读取游戏写下的真实战利品种子
 *       再掷一次，和 SeedMapper 是同一套思路；</li>
 *   <li><b>种子库（1.12.2 ~ 1.21）</b>：所选版本被种子库确实收录时，容器位置与内容
 *       依旧交给 {@code mc_feature.loot} 模拟，绝不拿别的版本的数据顶替。</li>
 * </ul>
 *
 * <p>古城（远古城市）按最新要求只标记结构位置（{@link AncientCityLocator} 定位 +
 * 深暗之域过滤，路径点取结构中心 Y=-51），不再查询容器内容。
 */
public class EnchantedAppleFinder extends BaseModule {
    // 上游用 lombok @Slf4j，此处等价展开为显式字段
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(EnchantedAppleFinder.class);

    /** 路径点名称与地图像素符号。 */
    private static final String WAYPOINT_NAME = "附魔金";
    private static final String WAYPOINT_SYMBOL = "附魔金";

    // 设置组
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgSeed = settings.createGroup("种子设置");
    private final SettingGroup sgStructures = settings.createGroup("结构设置");

    // 通用设置
    private final Setting<Integer> searchRadius = sgGeneral.add(new IntSetting.Builder()
        .name("搜索半径")
        .description("以玩家为中心的搜索半径（区块）。")
        .defaultValue(128)
        .min(16)
        .max(2000)
        .sliderMin(16)
        .sliderMax(2000)
        .build()
    );

    private final Setting<Integer> waypointY = sgGeneral.add(new IntSetting.Builder()
        .name("路径点高度")
        .description("种子库只给出容器的 X/Z，多数结构的容器 Y 返回 0；这些容器用此高度生成路径点。沉船、末地城等能给出真实 Y 的容器优先用真实值。")
        .defaultValue(63)
        .min(-64)
        .max(320)
        .sliderMin(-64)
        .sliderMax(320)
        .build()
    );

    private final Setting<Integer> maxStructures = sgGeneral.add(new IntSetting.Builder()
        .name("最多模拟结构数")
        .description("单次搜索最多模拟多少个结构的战利品（一个结构可能含多个容器），防止搜索半径过大导致长时间卡顿。")
        .defaultValue(300)
        .min(1)
        .max(5000)
        .sliderMin(1)
        .sliderMax(5000)
        .build()
    );

    private final Setting<Boolean> autoSearch = sgGeneral.add(new BoolSetting.Builder()
        .name("自动搜索")
        .description("启用模块时自动开始搜索")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> startSearch = sgGeneral.add(new BoolSetting.Builder()
        .name("开始搜索")
        .description("开始寻找含附魔金苹果的容器")
        .defaultValue(false)
        .onChanged(this::onStartSearchChanged)
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
        .description("手动输入 Minecraft 版本，支持 1.12.2 ~ 26.2（例如 26.2、1.21.4、1.20.1、1.12.2）。低于 1.12.2、高于 26.2 或认不出来的写法按空白处理，不会进行搜索。只要版本有效，就用该版本自己的数据：种子库收录的版本走种子库对应的库版本；26.x 与本版同族时走本版原生世界生成。种子库没收录又没有本版原生数据的版本会明确跳过，绝不拿别的版本的数据顶替。")
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

    // 结构设置：26.x 走本版原生结构生成，其余版本走种子库模拟
    private final Setting<Boolean> findDesertPyramid = sgStructures.add(new BoolSetting.Builder()
        .name("沙漠神殿")
        .description("模拟沙漠神殿的容器战利品。战利品表含附魔金苹果（26.x 用本版 chests/desert_pyramid，其余版本用种子库表）。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> findRuinedPortal = sgStructures.add(new BoolSetting.Builder()
        .name("废弃传送门")
        .description("模拟废弃传送门的容器战利品。战利品表含附魔金苹果（26.x 用本版 chests/ruined_portal，其余版本用种子库表）。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> findShipwreck = sgStructures.add(new BoolSetting.Builder()
        .name("沉船")
        .description("模拟沉船的容器战利品。容器能给出真实 Y，但本版与种子库的战利品表都不含附魔金苹果，默认关闭。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> findBuriedTreasure = sgStructures.add(new BoolSetting.Builder()
        .name("埋藏的宝藏")
        .description("模拟埋藏的宝藏容器。逐区块扫描较慢，且本版与种子库的战利品表都不含附魔金苹果，默认关闭。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> findEndCity = sgStructures.add(new BoolSetting.Builder()
        .name("末地城")
        .description("模拟末地城的容器（坐标按末地算，和「结构搜索」一致）。本版与种子库的战利品表都不含附魔金苹果，默认关闭。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> findAncientCity = sgStructures.add(new BoolSetting.Builder()
        .name("古城")
        .description("查找古城（远古城市，1.19+）并只标记结构位置：用本版原生的 random_spread 参数算候选区块，再按深暗之域过滤，路径点取结构中心（Y=-51）。按最新要求，古城不再查询容器内容。只有所选版本与本版运行版本同族（都是 26.x）时才能用本版世界生成数据定位；不同族时直接跳过，不会用本版数据顶替。低于 1.19 的版本会自动跳过。")
        .defaultValue(true)
        .build()
    );

    // 内部变量
    private boolean isSearching = false;
    private CompletableFuture<Void> searchTask = null;
    /** 本次搜索因所选版本过老、种子库没有生成参数而被跳过的结构说明。 */
    private final List<String> skippedByVersion = new ArrayList<>();
    /** 本次搜索因种子库没收录所选版本而被跳过的容器结构名（逐个列出）。 */
    private final List<String> libraryMissing = new ArrayList<>();

    public EnchantedAppleFinder() {
        super(BaseModule.CATEGORY, "附魔金标记", "[标记附魔金苹果位置(1.12.2-26.2)]");
    }

    @Override
    public void onActivate() {
        super.onActivate();
        if (autoSearch.get()) {
            startAppleSearch();
        }
        info("附魔金标记模块已启用");
    }

    @Override
    public void onDeactivate() {
        if (searchTask != null && !searchTask.isDone()) {
            searchTask.cancel(true);
            isSearching = false;
            info("搜索已取消");
        }
        info("附魔金标记模块已禁用");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (searchTask != null && searchTask.isDone()) {
            isSearching = false;
            searchTask = null;
            startSearch.set(false);
        }
    }

    private void onStartSearchChanged(boolean value) {
        if (value && !isSearching) {
            startAppleSearch();
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
        if (seed.version.hasExactLibraryData()) {
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

    private void startAppleSearch() {
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

        Seed worldSeed = resolveSeed();
        if (worldSeed == null) {
            error("没有可用的种子：请在「种子」里输入种子，或先点「应用种子」把种子写进种子库");
            return;
        }

        if (worldSeed.seed == null || worldSeed.version == null) {
            error("种子数据无效，请重新设置种子");
            return;
        }

        reportSeedLibraryInfo(worldSeed);

        if (PlayerUtils.getDimension() != Overworld) {
            warning("建议在主世界使用此功能以获得最准确的结果");
        }

        isSearching = true;
        BlockPos playerPos = mc.player.blockPosition();
        // 古城走原版世界生成算法，需要客户端世界的注册表和资源管理器；在主线程取好后交给后台任务
        RegistryAccess registryAccess = mc.level != null ? mc.level.registryAccess() : null;
        ResourceManager resourceManager = mc.getResourceManager();

        info("开始寻找含附魔金苹果的容器... 半径: " + searchRadius.get() + " 区块");

        searchTask = CompletableFuture.runAsync(() -> {
            try {
                searchApples(worldSeed, playerPos, registryAccess, resourceManager);
            } catch (Exception e) {
                error("搜索过程中发生错误: " + e.getMessage());
                log.error("附魔金苹果搜索过程中发生错误: ", e);
                isSearching = false;
            }
        });
    }

    private void searchApples(Seed worldSeed, BlockPos playerPos, RegistryAccess registryAccess, ResourceManager resourceManager) {
        List<AppleLocation> hits = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        int[] checked = {0};
        boolean[] truncated = {false};

        try {
            SeedVersion selected = worldSeed.version;
            long seed = worldSeed.seed;
            // 只有种子库确实收录了这个版本（版本号与库版本号一致）时，库里的结构/战利品
            // 才是该版本自己的数据；26.x 对应的是 1.21 的库数据，不能拿来顶替。
            boolean libraryData = selected.hasExactLibraryData();
            MCVersion version = selected.toMCVersion();
            String runningVersionName = net.minecraft.SharedConstants.getCurrentVersion().name();
            // 与本版运行版本同族（都是 26.x）时走 SeedMapper 式原生路线：结构位置与
            // 容器内容全部由本版自己的世界生成数据算出，最准也最好拿。
            boolean nativeLoot = registryAccess != null && selected.sharesNativeWorldgen(runningVersionName);

            info("使用种子: " + seed + ", 版本: " + selected.name
                + (nativeLoot ? "（本版原生世界生成，SeedMapper 式容器预测）"
                    : (libraryData ? "（种子库版本 " + version.name + "）" : "（种子库没有该版本的数据）")));

            int radius = searchRadius.get();
            int max = maxStructures.get();
            int playerChunkX = playerPos.getX() >> 4;
            int playerChunkZ = playerPos.getZ() >> 4;

            ChunkRand decorRand = new ChunkRand();
            ChunkRand lootRand = new ChunkRand();

            // 两类跳过的原因分开记：版本过老逐个写「结构名（需 X+）」，库没收录则把所选
            // 的结构逐个列出来，末尾统一说明原因。
            skippedByVersion.clear();
            libraryMissing.clear();
            boolean wantDesertPyramid = findDesertPyramid.get();
            boolean wantRuinedPortal = findRuinedPortal.get();
            boolean wantShipwreck = findShipwreck.get();
            boolean wantBuriedTreasure = findBuriedTreasure.get();
            boolean wantEndCity = findEndCity.get();

            if (nativeLoot) {
                // 路线一（SeedMapper 式）：位置用本版注册表里的结构集精确算，容器内容
                // 由原版结构生成算法把结构真正搭一遍、再读游戏写下的真实战利品种子。
                Set<String> nativeWanted = new HashSet<>();
                if (wantDesertPyramid) nativeWanted.add("沙漠神殿");
                if (wantRuinedPortal) nativeWanted.add("废弃传送门");
                if (wantShipwreck) nativeWanted.add("沉船");
                if (wantBuriedTreasure) nativeWanted.add("埋藏的宝藏");
                if (wantEndCity) nativeWanted.add("末地城");
                if (!nativeWanted.isEmpty()) {
                    scanNativeContainers(hits, seen, seed, registryAccess, resourceManager, nativeWanted,
                        playerPos.getX(), playerPos.getZ(), playerChunkX, playerChunkZ,
                        radius, max, checked, truncated);
                }

                // 古城按最新要求只标记结构位置，不再查询容器内容。
                if (findAncientCity.get()
                    && StructureSupport.check(version, StructureSupport.MIN_ANCIENT_CITY, "古城", skippedByVersion)) {
                    markAncientCities(hits, seen, seed, registryAccess,
                        playerPos.getX(), playerPos.getZ(), playerChunkX, playerChunkZ,
                        radius, "古城", max, checked, truncated);
                }
            } else {
                // 路线二：种子库。只有库确实收录了所选版本时才算，绝不拿别的版本的数据顶替。
                if (!libraryData) {
                    if (wantDesertPyramid) libraryMissing.add("沙漠神殿");
                    if (wantRuinedPortal) libraryMissing.add("废弃传送门");
                    if (wantShipwreck) libraryMissing.add("沉船");
                    if (wantBuriedTreasure) libraryMissing.add("埋藏的宝藏");
                    if (wantEndCity) libraryMissing.add("末地城");
                }

                boolean doDesertPyramid = libraryData && wantDesertPyramid
                    && StructureSupport.check(version, StructureSupport.MIN_LEGACY, "沙漠神殿", skippedByVersion);
                boolean doRuinedPortal = libraryData && wantRuinedPortal
                    && StructureSupport.check(version, StructureSupport.MIN_NETHER_116, "废弃传送门", skippedByVersion);
                boolean doShipwreck = libraryData && wantShipwreck
                    && StructureSupport.check(version, StructureSupport.MIN_OCEAN, "沉船", skippedByVersion);
                boolean doBuriedTreasure = libraryData && wantBuriedTreasure
                    && StructureSupport.check(version, StructureSupport.MIN_OCEAN, "埋藏的宝藏", skippedByVersion);
                boolean doEndCity = libraryData && wantEndCity
                    && StructureSupport.check(version, StructureSupport.MIN_END_CITY, "末地城", skippedByVersion);

                boolean scanOverworld = doDesertPyramid || doRuinedPortal || doShipwreck || doBuriedTreasure;
                if (scanOverworld) {
                    BiomeSource overworld = BiomeSource.of(Dimension.OVERWORLD, version, seed);
                    if (overworld == null) {
                        error("当前游戏版本 " + worldSeed.version.name + "（结构库 " + version.name
                            + "）过老，种子库无法建立生物群系源，容器搜索无法进行，请改选 1.8 及以上的版本。");
                        return;
                    }

                    if (doDesertPyramid) {
                        scanStructureLoot(hits, seen, new DesertPyramid(version), seed, overworld, decorRand, lootRand,
                            playerPos.getX(), playerPos.getZ(), playerChunkX, playerChunkZ, radius,
                            "沙漠神殿", max, checked, truncated);
                    }
                    if (doRuinedPortal) {
                        scanStructureLoot(hits, seen, new RuinedPortal(Dimension.OVERWORLD, version), seed, overworld, decorRand, lootRand,
                            playerPos.getX(), playerPos.getZ(), playerChunkX, playerChunkZ, radius,
                            "废弃传送门", max, checked, truncated);
                    }
                    if (doShipwreck) {
                        scanStructureLoot(hits, seen, new Shipwreck(version), seed, overworld, decorRand, lootRand,
                            playerPos.getX(), playerPos.getZ(), playerChunkX, playerChunkZ, radius,
                            "沉船", max, checked, truncated);
                    }
                    if (doBuriedTreasure) {
                        scanStructureLoot(hits, seen, new BuriedTreasure(version), seed, overworld, decorRand, lootRand,
                            playerPos.getX(), playerPos.getZ(), playerChunkX, playerChunkZ, radius,
                            "埋藏的宝藏", max, checked, truncated);
                    }
                }

                if (doEndCity) {
                    BiomeSource end = BiomeSource.of(Dimension.END, version, seed);
                    if (end == null) {
                        warning("当前游戏版本过老，种子库无法建立末地生物群系源，已跳过末地城。");
                    } else {
                        scanStructureLoot(hits, seen, new EndCity(version), seed, end, decorRand, lootRand,
                            playerPos.getX(), playerPos.getZ(), playerChunkX, playerChunkZ, radius,
                            "末地城", max, checked, truncated);
                    }
                }

                // 古城只有本版原生数据能算，这条路一律跳过，不拿别版本数据顶替。
                if (findAncientCity.get()) {
                    warning("古城需要所选版本（" + selected.name + "）自己的世界生成数据；本版运行的是 "
                        + runningVersionName + "，与所选版本不同族，已跳过古城，不会用本版数据顶替。");
                }
            }

            if (!libraryMissing.isEmpty()) {
                skippedByVersion.add(String.join("、", libraryMissing) + "（种子库没有 "
                    + selected.name + " 的数据）");
            }

            if (!skippedByVersion.isEmpty()) {
                warning("当前游戏版本 " + selected.name + " 下这些结构已跳过："
                    + StructureSupport.describeSkipped(skippedByVersion));
            }

            if (truncated[0]) {
                warning("已达到「最多模拟结构数」上限（" + max + "），结果可能不完整，可调小搜索半径或调大上限。");
            }

            if (hits.isEmpty()) {
                warning("在指定范围内没有找到含附魔金苹果的容器");
                return;
            }

            // 路径点写回客户端主线程，避免和 Xaero 的渲染线程抢同一份路径点集合
            mc.execute(() -> displayResults(hits));
        } catch (Exception e) {
            error("搜索过程中发生错误, 建议调小搜索半径: " + e.getMessage());
            log.error("附魔金苹果搜索过程中发生错误: ", e);
        } finally {
            isSearching = false;
        }
    }

    /**
     * 在搜索半径内遍历某个结构的候选位置，逐个模拟容器战利品，把含附魔金苹果的容器收进 {@code hits}。
     *
     * @param seen     已记录的容器 X/Z，用来去重（同坐标只留一个路径点）
     * @param checked  单元素计数器：已模拟的容器数（跨结构累计）
     * @param truncated 单元素标记：是否因为达到上限而提前结束
     */
    private void scanStructureLoot(List<AppleLocation> hits, Set<Long> seen, RegionStructure<?, ?> structure,
                                   long seed, BiomeSource biomeSource, ChunkRand decorRand, ChunkRand lootRand,
                                   int playerBlockX, int playerBlockZ, int playerChunkX, int playerChunkZ,
                                   int radius, String structureName, int max, int[] checked, boolean[] truncated) {
        int spacing = structure.getSpacing();
        if (spacing <= 0) {
            warning("结构 " + structureName + " 间距无效: " + spacing);
            return;
        }
        if (!(structure instanceof ILoot loot)) return;

        int minRegionX = Math.floorDiv(playerChunkX - radius, spacing);
        int maxRegionX = Math.floorDiv(playerChunkX + radius, spacing);
        int minRegionZ = Math.floorDiv(playerChunkZ - radius, spacing);
        int maxRegionZ = Math.floorDiv(playerChunkZ + radius, spacing);

        for (int regionX = minRegionX; regionX <= maxRegionX; regionX++) {
            for (int regionZ = minRegionZ; regionZ <= maxRegionZ; regionZ++) {
                if (!isSearching) return;
                if (checked[0] >= max) {
                    truncated[0] = true;
                    return;
                }

                CPos pos;
                try {
                    pos = structure.getInRegion(seed, regionX, regionZ, decorRand);
                } catch (Exception e) {
                    continue;
                }
                if (pos == null) continue;

                double chunkDistance = Math.sqrt(Math.pow(pos.getX() - playerChunkX, 2)
                    + Math.pow(pos.getZ() - playerChunkZ, 2));
                if (chunkDistance > radius) continue;

                if (!structure.canSpawn(pos, biomeSource)) continue;

                checked[0]++;

                List<ChestContent> contents;
                try {
                    contents = loot.getLootAtPos(seed, pos, lootRand, false);
                } catch (Throwable t) {
                    warning(String.format("模拟 %s (%d,%d) 的容器时出错: %s",
                        structureName, pos.getX(), pos.getZ(), t.getMessage()));
                    continue;
                }
                if (contents == null) continue;

                for (ChestContent content : contents) {
                    if (!content.contains(Items.ENCHANTED_GOLDEN_APPLE)) continue;

                    BPos chest = content.getPos();
                    long key = ((long) chest.getX() << 32) ^ (chest.getZ() & 0xFFFFFFFFL);
                    if (!seen.add(key)) continue;

                    double distance = Math.sqrt(Math.pow(chest.getX() - playerBlockX, 2)
                        + Math.pow(chest.getZ() - playerBlockZ, 2));
                    hits.add(new AppleLocation(chest.getX(), chest.getY(), chest.getZ(), structureName, distance));
                }
            }
        }
    }

    /**
     * SeedMapper 式的原生容器预测：位置由 {@link NativeStructureLocator} 用本版注册表
     * 精确算出，容器内容交 {@link NativeStructureLoot} 调用原版结构生成算法把结构真正
     * 搭一遍，再读游戏写下的真实战利品种子，只留下确定含附魔金苹果的容器。
     *
     * <p>原生预测器建立失败、或某个结构生成不出来时，退回标记该结构中心：结构位置本身
     * 仍然是本版世界生成算出来的，比拿别的版本的数据顶替更可靠。
     */
    private void scanNativeContainers(List<AppleLocation> hits, Set<Long> seen, long seed,
                                      RegistryAccess registryAccess, ResourceManager resourceManager,
                                      Set<String> wanted, int playerBlockX, int playerBlockZ,
                                      int playerChunkX, int playerChunkZ, int radius,
                                      int max, int[] checked, boolean[] truncated) {
        NativeStructureLocator locator = NativeStructureLocator.create(registryAccess, seed);
        if (locator == null) {
            warning("读不到本版世界生成数据，无法用原生算法定位容器结构，已跳过容器搜索。");
            return;
        }

        NativeStructureLoot loot = NativeStructureLoot.create(registryAccess, resourceManager, seed);
        if (loot == null) {
            warning("原生容器预测器建立失败（结构模板或战利品表不可用），改为只标记结构位置。");
        }

        List<NativeStructureLocator.Found> found =
            locator.scan(playerChunkX, playerChunkZ, radius, wanted, () -> isSearching);
        int failed = 0;
        for (NativeStructureLocator.Found structure : found) {
            if (!isSearching) return;
            if (checked[0] >= max) {
                truncated[0] = true;
                break;
            }
            checked[0]++;

            if (loot != null) {
                List<NativeStructureLoot.Chest> chests;
                try {
                    chests = loot.predict(structure.id, new ChunkPos(structure.chunkX, structure.chunkZ), structure.dim);
                } catch (Throwable t) {
                    chests = null;
                }
                if (chests != null) {
                    for (NativeStructureLoot.Chest chest : chests) {
                        if (!chest.enchantedGoldenApple()) continue;
                        long key = ((long) chest.x() << 32) ^ (chest.z() & 0xFFFFFFFFL);
                        if (!seen.add(key)) continue;
                        double distance = Math.sqrt(Math.pow(chest.x() - playerBlockX, 2)
                            + Math.pow(chest.z() - playerBlockZ, 2));
                        hits.add(new AppleLocation(chest.x(), chest.y(), chest.z(),
                            structure.display + "附魔金箱", distance));
                    }
                    continue;
                }
                failed++;
            }

            // 预测不可用或这个结构生成失败：退回标记结构中心，位置依然是本版算法算的
            long key = ((long) structure.blockX << 32) ^ (structure.blockZ & 0xFFFFFFFFL);
            if (!seen.add(key)) continue;
            double distance = Math.sqrt(Math.pow(structure.blockX - playerBlockX, 2)
                + Math.pow(structure.blockZ - playerBlockZ, 2));
            hits.add(new AppleLocation(structure.blockX, structure.y, structure.blockZ,
                structure.display, distance));
        }

        if (failed > 0) {
            warning(String.format("有 %d 个结构没能用原版算法生成，已改为标记结构中心，其余结果不受影响。", failed));
        }
    }

    /**
     * 用本版原生的 random_spread 参数搜索古城，按最新要求只标记结构位置（不再查询容器内容）：
     * 候选区块用结构的 spacing/separation/salt 直接算，再按深暗之域过滤，路径点取结构中心
     * 与古城地板高度（Y=-51）。
     */
    private void markAncientCities(List<AppleLocation> hits, Set<Long> seen, long seed,
                                   RegistryAccess registryAccess, int playerBlockX, int playerBlockZ,
                                   int playerChunkX, int playerChunkZ, int radius, String structureName,
                                   int max, int[] checked, boolean[] truncated) {
        AncientCityLocator locator = AncientCityLocator.create(registryAccess, seed);
        if (locator == null) {
            warning("无法用本版世界生成算法建立古城定位器，已跳过古城。");
            return;
        }

        int spacing = AncientCityLocator.SPACING;
        int minRegionX = Math.floorDiv(playerChunkX - radius, spacing);
        int maxRegionX = Math.floorDiv(playerChunkX + radius, spacing);
        int minRegionZ = Math.floorDiv(playerChunkZ - radius, spacing);
        int maxRegionZ = Math.floorDiv(playerChunkZ + radius, spacing);

        int cities = 0;
        for (int regionX = minRegionX; regionX <= maxRegionX; regionX++) {
            for (int regionZ = minRegionZ; regionZ <= maxRegionZ; regionZ++) {
                if (!isSearching) return;
                if (checked[0] >= max) {
                    truncated[0] = true;
                    return;
                }

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

                checked[0]++;

                int centerX = (chunkX << 4) + 8;
                int centerZ = (chunkZ << 4) + 8;
                long key = ((long) centerX << 32) ^ (centerZ & 0xFFFFFFFFL);
                if (!seen.add(key)) continue;

                double distance = Math.sqrt(Math.pow(centerX - playerBlockX, 2)
                    + Math.pow(centerZ - playerBlockZ, 2));
                hits.add(new AppleLocation(centerX, AncientCityLocator.FLOOR_Y, centerZ, structureName, distance));
                cities++;
            }
        }

        if (cities > 0) {
            info(String.format("古城按新口径只标记结构位置：共标记 %d 座古城（Y=%d）", cities, AncientCityLocator.FLOOR_Y));
        }
    }

    private void displayResults(List<AppleLocation> hits) {
        hits.sort(Comparator.comparingDouble(location -> location.distance));

        WaypointSet waypointSet = WaypointUtils.getWaypointSet();
        if (waypointSet == null) {
            warning("无法获取 Xaero 路径点集合，请确保已安装 Xaero 小地图模组");
            return;
        }

        int added = 0;
        for (AppleLocation hit : hits) {
            // 种子库多数只给 X/Z（Y=0），这类容器用设置里的高度；能给出真实 Y 的
            // （沉船、末地城等）直接用；古城这类地下结构给出的是负数的真实高度。
            int y = hit.y != 0 ? hit.y : waypointY.get();
            if (WaypointUtils.addToWaypoints(hit.x, y, hit.z, WAYPOINT_NAME, WAYPOINT_SYMBOL, WaypointUtils.COLOR_YELLOW)) {
                added++;
            }
        }

        info(String.format("搜索完成！共 %d 个目标（含附魔金苹果的容器 / 古城结构标记），已添加 %d 个黄色路径点「%s」",
            hits.size(), added, WAYPOINT_NAME));
    }

    // 内部类：一个待标记的目标（含附魔金苹果的容器，或古城结构中心）
    private static final class AppleLocation {
        final int x;
        final int y;
        final int z;
        final String structureName;
        final double distance;

        AppleLocation(int x, int y, int z, String structureName, double distance) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.structureName = structureName;
            this.distance = distance;
        }
    }
}
