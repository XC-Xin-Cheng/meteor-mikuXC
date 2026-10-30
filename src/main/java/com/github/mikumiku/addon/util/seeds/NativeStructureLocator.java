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

import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.level.biome.TheEndBiomeSource;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;

/**
 * 本版原生结构定位器（26.x 用）。
 *
 * <p>种子库（seedfinding）的 {@code MCVersion} 只收录到 1.21，26.x 在库里没有
 * 自己的数据。按“拿不到种子库数据就在本地运算、绝不用别的版本顶替”的口径，这里
 * 直接读客户端世界注册表里本版自己的世界生成数据来定位结构：
 *
 * <ul>
 *   <li><b>主世界 / 下界 / 末地的 random_spread 结构</b>：从
 *       {@code worldgen/structure_set} 取出结构集，用原版
 *       {@link RandomSpreadStructurePlacement#getPotentialStructureChunk}
 *       算候选区块；同一结构集里的多个变体（村庄的五种、废弃传送门的多变体、
 *       下界要塞与堡垒遗迹共用的 {@code nether_complexes} 等）按原版权重抽取，
 *       用同一个 {@link WorldgenRandom}（种子 0 的 {@link LegacyRandomSource}
 *       + {@code setLargeFeatureSeed}）精确复刻；再按该结构集所属维度的生物群系源
 *       过滤（结构集属于哪个维度由它全部变体的生物群系白名单投票判定），没有展示给
 *       用户的结构也留在抽签池里，保证权重抽取顺序与原版一致；候选区块还要过原版的
 *       {@link StructurePlacement#isStructureChunk} 一关——频率约简（废弃矿井 0.004、
 *       埋藏的宝藏 0.01、掠夺者前哨站 0.2）与排除区（前哨站避开村庄 10 区块）都在
 *       这一步完成，这里按维度构造原版 {@link ChunkGeneratorStructureState} 交给它判定；</li>
 *   <li><b>要塞</b>：结构集里是 {@link ConcentricRingsStructurePlacement}，
 *       按原版 {@code ChunkGeneratorStructureState.generateRingPositions} 的同心环
 *       算法复刻（同样的种子、同样的角度与环半径公式、同样的
 *       {@link BiomeSource#findBiomeHorizontal} 偏好生物群系搜索），得到与游戏一致
 *       的 128 个要塞区块。</li>
 * </ul>
 *
 * <p>生物群系判定的高度：主世界取海平面，下界要塞与下界化石按各自生成高度，
 * 堡垒遗迹按它的起始高度，末地取地表附近。原版是在结构实际生成点的高度判定，
 * 这里是同一套参数在固定高度的近似，位置与游戏一致、生物群系边界可能有极少数
 * 误差。
 *
 * <p>古城走单独的 {@link AncientCityLocator}（要在地板高度 Y=-51 判深暗之域，
 * 不能用地表采样）。
 */
public final class NativeStructureLocator {

    /** 主世界地表结构判定生物群系时采样的高度（主世界海平面）。 */
    public static final int SURFACE_Y = 63;

    /** random_spread 结构的路径点高度（地表附近，和种子库路径一致）。 */
    public static final int WAYPOINT_Y = 100;

    /** 下界要塞生成点在 Y=64，生物群系判定用同一个高度。 */
    public static final int NETHER_FORTRESS_Y = 64;

    /** 堡垒遗迹起始高度为 Y=33（{@code start_height.absolute}）。 */
    public static final int NETHER_BASTION_Y = 33;

    /** 下界化石生成高度下限 Y=32，取这个高度判定灵魂沙峡谷。 */
    public static final int NETHER_FOSSIL_Y = 32;

    /** 末地城按末地地表附近高度判定生物群系。 */
    public static final int END_Y = 64;

    /** 要塞路径点高度（地下结构）。 */
    public static final int STRONGHOLD_Y = 32;

    /** 要塞同心环找偏好生物群系时的水平搜索半径（原版为 112）。 */
    private static final int STRONGHOLD_SEARCH_RADIUS = 112;

    /** 生物群系缓存上限，超过就清空重建，避免大半径搜索时占满内存。 */
    private static final int BIOME_CACHE_LIMIT = 200_000;

    /** 下界生物群系（用于判断一个结构属于哪个维度）。 */
    private static final Set<String> NETHER_BIOMES = Set.of(
        "nether_wastes", "soul_sand_valley", "crimson_forest", "warped_forest", "basalt_deltas");

    /** 末地生物群系（用于判断一个结构属于哪个维度）。 */
    private static final Set<String> END_BIOMES = Set.of(
        "the_end", "end_highlands", "end_midlands", "small_end_islands", "end_barrens");

    private final long seed;
    private final List<Placement> placements;
    private final List<RingPlacement> rings;
    /** 每个维度按原版构造的结构生成状态，用于复刻频率约简与排除区判定。 */
    private final Map<Dim, ChunkGeneratorStructureState> structureStates;
    /** 要塞同心环位置只算一次，扫描时复用。 */
    private List<ChunkPos> strongholdPositions;

    private NativeStructureLocator(long seed, List<Placement> placements, List<RingPlacement> rings,
                                   Map<Dim, ChunkGeneratorStructureState> structureStates) {
        this.seed = seed;
        this.placements = placements;
        this.rings = rings;
        this.structureStates = structureStates;
    }

    /**
     * 用世界注册表和种子建立定位器。注册表必须从客户端世界主线程取得
     * （{@code mc.level.registryAccess()}）。
     *
     * @return 建立成功返回定位器；注册表缺失或没有任何可用结构集时返回 {@code null}
     */
    public static NativeStructureLocator create(RegistryAccess registryAccess, long seed) {
        if (registryAccess == null) return null;
        try {
            HolderGetter<NormalNoise.NoiseParameters> noises = registryAccess.lookupOrThrow(Registries.NOISE);

            BiomeSource overworldSource = overworldBiomeSource(registryAccess);
            RandomState overworldRandom = randomState(registryAccess, noises, seed, NoiseGeneratorSettings.OVERWORLD);
            BiomeSource netherSource = netherBiomeSource(registryAccess);
            RandomState netherRandom = randomState(registryAccess, noises, seed, NoiseGeneratorSettings.NETHER);
            BiomeSource endSource = TheEndBiomeSource.create(registryAccess.lookupOrThrow(Registries.BIOME));
            RandomState endRandom = randomState(registryAccess, noises, seed, NoiseGeneratorSettings.END);

            Climate.Sampler overworldSampler = overworldRandom == null ? null : overworldRandom.sampler();
            Climate.Sampler netherSampler = netherRandom == null ? null : netherRandom.sampler();
            Climate.Sampler endSampler = endRandom == null ? null : endRandom.sampler();

            Registry<StructureSet> structureSets = registryAccess.lookupOrThrow(Registries.STRUCTURE_SET);
            List<Placement> placements = new ArrayList<>();
            List<RingPlacement> rings = new ArrayList<>();

            // 原版的「频率约简」（frequency / frequency_reduction_method，例如废弃矿井 0.004、
            // 埋藏的宝藏 0.01、掠夺者前哨站 0.2）与「排除区」（exclusion_zone，例如前哨站要
            // 避开村庄 10 区块）都实现在 StructurePlacement#isStructureChunk 里，而这些字段
            // 是 protected 的。这里按维度构造原版的 ChunkGeneratorStructureState（构造函数只做
            // 字段赋值，不触发同心环等重活），扫描候选区块时把这两项判定原样交给它，避免把
            // 每个候选区块都当成结构报出来。
            Map<Dim, ChunkGeneratorStructureState> structureStates = new EnumMap<>(Dim.class);
            putStructureState(structureStates, Dim.OVERWORLD, overworldRandom, overworldSource, seed, structureSets);
            putStructureState(structureStates, Dim.NETHER, netherRandom, netherSource, seed, structureSets);
            putStructureState(structureStates, Dim.END, endRandom, endSource, seed, structureSets);

            for (Map.Entry<ResourceKey<StructureSet>, StructureSet> entry : structureSets.entrySet()) {
                StructureSet set = entry.getValue();
                if (set == null) continue;

                StructurePlacement placement = set.placement();

                // 要塞：同心环放置，单独走原版的环算法。
                if (placement instanceof ConcentricRingsStructurePlacement ring) {
                    if (hasStructure(set, "stronghold")
                        && ring.preferredBiomes() != null && ring.preferredBiomes().isBound()
                        && overworldSource != null && overworldSampler != null) {
                        rings.add(new RingPlacement(ring, overworldSource, overworldSampler));
                    }
                    continue;
                }

                if (!(placement instanceof RandomSpreadStructurePlacement randomSpread)) continue;

                // 原版是按「结构集所属维度」的生物群系源给结构集里每个变体判生物群系
                // （主世界结构集里的下界变体会因生物群系不合格被剔除后重抽），所以这里对
                // 整个结构集投票定维度，而不是逐个变体定；这样才能保留完整的权重抽签池。
                Dim dim = classifySet(set);
                BiomeSource source = switch (dim) {
                    case NETHER -> netherSource;
                    case END -> endSource;
                    default -> overworldSource;
                };
                Climate.Sampler sampler = switch (dim) {
                    case NETHER -> netherSampler;
                    case END -> endSampler;
                    default -> overworldSampler;
                };
                if (source == null || sampler == null) continue;

                List<Variant> variants = new ArrayList<>();
                boolean unbound = false;
                boolean reportable = false;
                for (StructureSet.StructureSelectionEntry selection : set.structures()) {
                    Holder<Structure> holder = selection.structure();
                    HolderSet<Biome> biomes = holder.value().biomes();
                    // 客户端拿不到生物群系标签时 HolderSet 未绑定，contains 判定不可信，
                    // 整个结构集跳过，避免用错误的抽签池算出错位置。
                    if (biomes == null || !biomes.isBound()) {
                        unbound = true;
                        break;
                    }
                    String id = holder.unwrapKey().map(key -> key.identifier().toString()).orElse("");
                    String path = holder.unwrapKey().map(key -> key.identifier().getPath()).orElse("");
                    // 没有展示给用户的结构（例如下界废弃传送门）也留在抽签池里，只是
                    // display 为 null：这样权重抽取顺序与原版一致，不会算错其它变体。
                    String display = displayName(path);
                    if (display != null) reportable = true;
                    variants.add(new Variant(display, id, biomes, Math.max(1, selection.weight()),
                        source, sampler, sampleY(path, dim), dim));
                }
                if (unbound || variants.isEmpty() || !reportable) continue;

                placements.add(new Placement(randomSpread, variants, Math.max(1, randomSpread.spacing()), dim));
            }

            if (placements.isEmpty() && rings.isEmpty()) return null;
            return new NativeStructureLocator(seed, placements, rings, structureStates);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * 本版注册表里实际能定位的结构中文名集合。调用方用它判断用户勾选的结构
     * 有没有被本版世界生成数据覆盖，没覆盖的才明确跳过。
     */
    public Set<String> supportedDisplays() {
        Set<String> result = new HashSet<>();
        for (Placement placement : placements) result.addAll(placement.displays);
        if (!rings.isEmpty()) result.add("要塞");
        return result;
    }

    /** 主世界的地表生物群系源；注册表缺预设时返回 {@code null}。 */
    private static BiomeSource overworldBiomeSource(RegistryAccess registryAccess) {
        try {
            Holder<MultiNoiseBiomeSourceParameterList> preset = registryAccess
                .lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
                .getOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD);
            return MultiNoiseBiomeSource.createFromPreset(preset);
        } catch (Throwable t) {
            return null;
        }
    }

    /** 下界的生物群系源；注册表缺预设时返回 {@code null}。 */
    private static BiomeSource netherBiomeSource(RegistryAccess registryAccess) {
        try {
            Holder<MultiNoiseBiomeSourceParameterList> preset = registryAccess
                .lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
                .getOrThrow(MultiNoiseBiomeSourceParameterLists.NETHER);
            return MultiNoiseBiomeSource.createFromPreset(preset);
        } catch (Throwable t) {
            return null;
        }
    }

    /** 某个维度噪声设置对应的随机状态（生物群系采样器与结构生成状态都用它）。 */
    private static RandomState randomState(RegistryAccess registryAccess,
                                           HolderGetter<NormalNoise.NoiseParameters> noises,
                                           long seed, ResourceKey<NoiseGeneratorSettings> key) {
        try {
            NoiseGeneratorSettings settings = registryAccess.lookupOrThrow(Registries.NOISE_SETTINGS)
                .getOrThrow(key).value();
            return RandomState.create(settings, noises, seed);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * 按维度构造原版的 {@link ChunkGeneratorStructureState}，用来复刻原版的频率约简与
     * 排除区判定。构造失败时该维度不做这两项过滤（退回到只按候选区块与生物群系判定）。
     */
    private static void putStructureState(Map<Dim, ChunkGeneratorStructureState> target, Dim dim,
                                          RandomState randomState, BiomeSource source, long seed,
                                          HolderLookup<StructureSet> structureSets) {
        if (randomState == null || source == null) return;
        try {
            target.put(dim, ChunkGeneratorStructureState.createForNormal(randomState, seed, source, structureSets));
        } catch (Throwable ignored) {
            // 构造不出来就不用它，调用方会在 state 为 null 时跳过这两项过滤
        }
    }

    /** 结构集里是否含有指定路径的结构（例如 {@code stronghold}）。 */
    private static boolean hasStructure(StructureSet set, String path) {
        for (StructureSet.StructureSelectionEntry selection : set.structures()) {
            String name = selection.structure().unwrapKey()
                .map(key -> key.identifier().getPath()).orElse("");
            if (path.equals(name)) return true;
        }
        return false;
    }

    /**
     * 扫描一个矩形范围内的所有本版原生结构（主世界 / 下界 / 末地的 random_spread，
     * 以及要塞同心环）。
     *
     * @param centerChunkX 中心区块 X
     * @param centerChunkZ 中心区块 Z
     * @param radiusChunks 半径（区块）
     * @param wanted       需要的中文结构名集合；{@code null} 表示全部
     * @param running      返回 false 时立即停止（模块被关闭或取消搜索）
     * @return 命中的结构位置列表（未按距离排序）
     */
    public List<Found> scan(int centerChunkX, int centerChunkZ, int radiusChunks,
                            Set<String> wanted, BooleanSupplier running) {
        List<Found> result = new ArrayList<>();
        Map<Long, Holder<Biome>> biomeCache = new HashMap<>();

        for (Placement placement : placements) {
            if (!placement.intersects(wanted)) continue;

            int spacing = placement.spacing;
            int minRegionX = Math.floorDiv(centerChunkX - radiusChunks, spacing);
            int maxRegionX = Math.floorDiv(centerChunkX + radiusChunks, spacing);
            int minRegionZ = Math.floorDiv(centerChunkZ - radiusChunks, spacing);
            int maxRegionZ = Math.floorDiv(centerChunkZ + radiusChunks, spacing);

            for (int regionX = minRegionX; regionX <= maxRegionX; regionX++) {
                for (int regionZ = minRegionZ; regionZ <= maxRegionZ; regionZ++) {
                    if (running != null && !running.getAsBoolean()) return result;

                    ChunkPos pos;
                    try {
                        pos = placement.placement.getPotentialStructureChunk(seed, regionX * spacing, regionZ * spacing);
                    } catch (Throwable t) {
                        continue;
                    }
                    if (pos == null) continue;

                    int chunkX = pos.x();
                    int chunkZ = pos.z();
                    if (Math.abs(chunkX - centerChunkX) > radiusChunks
                        || Math.abs(chunkZ - centerChunkZ) > radiusChunks) {
                        continue;
                    }

                    // 原版的频率约简与排除区判定：候选区块只是「可能生成」，
                    // 还要过 isStructureChunk 这一关（废弃矿井 0.004、埋藏的宝藏 0.01、
                    // 掠夺者前哨站 0.2 并避开村庄 10 区块）。缺状态时退回旧行为。
                    ChunkGeneratorStructureState state = structureStates.get(placement.dim);
                    if (state != null) {
                        boolean allowed;
                        try {
                            allowed = placement.placement.isStructureChunk(state, chunkX, chunkZ);
                        } catch (Throwable t) {
                            allowed = false;
                        }
                        if (!allowed) continue;
                    }

                    Found found = null;
                    try {
                        found = pick(placement, chunkX, chunkZ, wanted, biomeCache);
                    } catch (Throwable t) {
                        continue;
                    }
                    if (found != null) result.add(found);
                }
            }
        }

        if (wanted == null || wanted.contains("要塞")) {
            result.addAll(scanStrongholds(centerChunkX, centerChunkZ, radiusChunks, running));
        }
        return result;
    }

    /**
     * 复刻原版 {@code ChunkGenerator} 的来源结构变体选择：按 {@code WorldgenRandom}
     * 权重抽取，抽中的变体生物群系不匹配时移除它、再抽下一个，直到抽中或抽完。
     * 唯一抽中的变体生物群系匹配即认为该区块会生成这个结构。
     */
    private Found pick(Placement placement, int chunkX, int chunkZ,
                       Set<String> wanted, Map<Long, Holder<Biome>> biomeCache) {
        List<Variant> candidates = new ArrayList<>(placement.variants);
        WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(0L));
        random.setLargeFeatureSeed(seed, chunkX, chunkZ);

        int totalWeight = 0;
        for (Variant variant : candidates) totalWeight += variant.weight;

        while (!candidates.isEmpty()) {
            int index = 0;
            if (candidates.size() > 1) {
                int roll = random.nextInt(totalWeight);
                for (Variant variant : candidates) {
                    roll -= variant.weight;
                    if (roll < 0) break;
                    index++;
                }
            }

            Variant chosen = candidates.get(index);
            if (matchesBiome(chosen, chunkX, chunkZ, biomeCache)) {
                if (chosen.display != null && (wanted == null || wanted.contains(chosen.display))) {
                    return new Found(chosen.display, chosen.id, chunkX, chunkZ,
                        (chunkX << 4) + 8, (chunkZ << 4) + 8, WAYPOINT_Y, chosen.dim);
                }
                // 这个区块被一个未展示或用户没勾选的结构占了，同结构集里就不会再有别的结构
                return null;
            }

            candidates.remove(index);
            totalWeight -= chosen.weight;
        }
        return null;
    }

    /** 候选区块中心在对应维度采样高度处的生物群系是否在该结构的白名单里。 */
    private boolean matchesBiome(Variant variant, int chunkX, int chunkZ,
                                 Map<Long, Holder<Biome>> cache) {
        long key = biomeKey(chunkX, chunkZ, variant.sampleY, variant.dim);
        Holder<Biome> biome = cache.get(key);
        if (biome == null) {
            if (cache.size() >= BIOME_CACHE_LIMIT) cache.clear();
            int blockX = (chunkX << 4) + 8;
            int blockZ = (chunkZ << 4) + 8;
            biome = variant.biomeSource.getNoiseBiome(blockX >> 2, variant.sampleY >> 2, blockZ >> 2, variant.sampler);
            if (biome != null) cache.put(key, biome);
        }
        return biome != null && variant.biomes.contains(biome);
    }

    /** 生物群系缓存键：区块坐标 + 采样高度 + 维度，避免不同维度/高度互相覆盖。 */
    private static long biomeKey(int chunkX, int chunkZ, int sampleY, Dim dim) {
        return ((chunkX & 0x3FFFFFFL) << 36)
            | ((chunkZ & 0x3FFFFFFL) << 10)
            | ((sampleY & 0xFFL) << 2)
            | (dim.ordinal() & 0x3L);
    }

    /**
     * 用本版原生的同心环算法算要塞位置。与
     * {@code ChunkGeneratorStructureState.generateRingPositions} 逐步对齐：
     * 同一个种子初始化 {@link RandomSource}，同样的角度与半径公式，同样用
     * {@link BiomeSource#findBiomeHorizontal} 在 112 格内找偏好生物群系。
     */
    private List<Found> scanStrongholds(int centerChunkX, int centerChunkZ, int radiusChunks,
                                        BooleanSupplier running) {
        List<Found> result = new ArrayList<>();
        for (ChunkPos pos : strongholdPositions()) {
            if (running != null && !running.getAsBoolean()) return result;
            if (Math.abs(pos.x() - centerChunkX) > radiusChunks
                || Math.abs(pos.z() - centerChunkZ) > radiusChunks) {
                continue;
            }
            result.add(new Found("要塞", "minecraft:stronghold", pos.x(), pos.z(),
                (pos.x() << 4) + 8, (pos.z() << 4) + 8, STRONGHOLD_Y, Dim.OVERWORLD));
        }
        return result;
    }

    /** 只算一次的要塞同心环位置。 */
    private List<ChunkPos> strongholdPositions() {
        if (strongholdPositions != null) return strongholdPositions;
        List<ChunkPos> result = new ArrayList<>();
        for (RingPlacement ring : rings) {
            try {
                result.addAll(computeRingPositions(ring));
            } catch (Throwable ignored) {
                // 单个结构集的环算不出来时跳过它，不影响其它结构
            }
        }
        strongholdPositions = result;
        return result;
    }

    private List<ChunkPos> computeRingPositions(RingPlacement ring) {
        ConcentricRingsStructurePlacement placement = ring.placement;
        int distance = placement.distance();
        int count = placement.count();
        if (count <= 0) return List.of();

        int spread = placement.spread();
        HolderSet<Biome> preferred = placement.preferredBiomes();
        List<ChunkPos> result = new ArrayList<>(count);

        RandomSource random = RandomSource.create();
        random.setSeed(seed);
        double angle = random.nextDouble() * Math.PI * 2.0;
        int inRing = 0;
        int ringIndex = 0;

        for (int i = 0; i < count; i++) {
            double d = 4.0 * distance + distance * ringIndex * 6.0
                + (random.nextDouble() - 0.5) * distance * 2.5;
            int chunkX = (int) Math.round(Math.cos(angle) * d);
            int chunkZ = (int) Math.round(Math.sin(angle) * d);

            RandomSource fork = random.fork();
            result.add(nearestBiomeChunk(ring, chunkX, chunkZ, preferred, fork));

            angle += Math.PI * 2.0 / spread;
            inRing++;
            if (inRing == spread) {
                ringIndex++;
                inRing = 0;
                spread = spread + (2 * spread) / (ringIndex + 1);
                spread = Math.min(spread, count - i);
                angle += random.nextDouble() * Math.PI * 2.0;
            }
        }
        return result;
    }

    /** 在给定区块中心的 112 格内找偏好生物群系；找不到就退回原候选区块。 */
    private ChunkPos nearestBiomeChunk(RingPlacement ring, int chunkX, int chunkZ,
                                       HolderSet<Biome> preferred, RandomSource random) {
        try {
            int blockX = SectionPos.sectionToBlockCoord(chunkX, 8);
            int blockZ = SectionPos.sectionToBlockCoord(chunkZ, 8);
            Pair<BlockPos, Holder<Biome>> pair = ring.biomeSource.findBiomeHorizontal(
                blockX, 0, blockZ, STRONGHOLD_SEARCH_RADIUS, preferred::contains, random, ring.sampler);
            if (pair != null && pair.getFirst() != null) {
                BlockPos found = pair.getFirst();
                return new ChunkPos(
                    SectionPos.blockToSectionCoord(found.getX()),
                    SectionPos.blockToSectionCoord(found.getZ()));
            }
        } catch (Throwable ignored) {
            // 采样失败就用原候选区块
        }
        return new ChunkPos(chunkX, chunkZ);
    }

    /**
     * 按整个结构集的生物群系白名单投票判断它属于哪个维度。
     *
     * <p>原版是用「该维度」的生物群系源给结构集里每个变体判生物群系的，所以维度是
     * 结构集的属性、不是单个变体的属性。例如主世界的 {@code ruined_portals} 结构集里
     * 也含一个下界变体，但它在主世界会因为生物群系不合格被剔除后重抽，整个结构集仍
     * 属于主世界。按维度投票（而不是逐个变体判）才能和原版保留同一个抽签池。
     */
    private static Dim classifySet(StructureSet set) {
        int nether = 0;
        int end = 0;
        int overworld = 0;
        for (StructureSet.StructureSelectionEntry selection : set.structures()) {
            HolderSet<Biome> biomes = selection.structure().value().biomes();
            if (biomes == null || !biomes.isBound()) continue;
            try {
                for (Holder<Biome> holder : biomes) {
                    String path = holder.unwrapKey().map(key -> key.identifier().getPath()).orElse("");
                    if (NETHER_BIOMES.contains(path)) nether++;
                    else if (END_BIOMES.contains(path)) end++;
                    else overworld++;
                }
            } catch (Throwable ignored) {
                // 判不出来按主世界处理
            }
        }
        if (nether > overworld && nether >= end) return Dim.NETHER;
        if (end > overworld && end > nether) return Dim.END;
        return Dim.OVERWORLD;
    }

    /** 每种结构判定生物群系时采样高度的选择（与原版结构的生成高度对齐）。 */
    private static int sampleY(String path, Dim dim) {
        return switch (dim) {
            case NETHER -> {
                if (path.startsWith("bastion")) yield NETHER_BASTION_Y;
                if (path.startsWith("nether_fossil")) yield NETHER_FOSSIL_Y;
                yield NETHER_FORTRESS_Y;
            }
            case END -> END_Y;
            default -> SURFACE_Y;
        };
    }

    /**
     * 把原版结构 id 映射成本模组「结构搜索」里的中文名；不处理的结构返回 {@code null}。
     *
     * <p>古城返回 null，交给 {@link AncientCityLocator}；试炼密室、古迹废墟等
     * 没有对应设置的返回 null。
     */
    private static String displayName(String path) {
        if (path == null || path.isEmpty()) return null;

        if (path.startsWith("village")) return "村庄";
        if (path.startsWith("ocean_ruin")) return "海底废墟";
        if (path.startsWith("mineshaft")) return "废弃矿井";
        if (path.startsWith("ruined_portal")) {
            return path.equals("ruined_portal_nether") ? null : "废弃传送门";
        }

        switch (path) {
            case "desert_pyramid":
                return "沙漠神殿";
            case "jungle_pyramid":
                return "丛林神庙";
            case "swamp_hut":
                return "沼泽小屋";
            case "igloo":
                return "雪屋";
            case "monument":
                return "海底神殿";
            case "pillager_outpost":
                return "掠夺者前哨站";
            case "shipwreck":
            case "shipwreck_beached":
                return "沉船";
            case "buried_treasure":
                return "埋藏的宝藏";
            case "mansion":
                return "林地府邸";
            case "fortress":
                return "下界要塞";
            case "bastion_remnant":
                return "堡垒遗迹";
            case "nether_fossil":
                return "下界化石";
            case "end_city":
                return "末地城";
            default:
                return null;
        }
    }

    /** 结构所属维度。 */
    public enum Dim {
        OVERWORLD,
        NETHER,
        END
    }

    /** 一个结构集里的一个变体：中文名 + 原版结构 id + 生物群系白名单 + 权重 + 所在维度信息。 */
    private static final class Variant {
        final String display;
        final String id;
        final HolderSet<Biome> biomes;
        final int weight;
        final BiomeSource biomeSource;
        final Climate.Sampler sampler;
        final int sampleY;
        final Dim dim;

        Variant(String display, String id, HolderSet<Biome> biomes, int weight,
                BiomeSource biomeSource, Climate.Sampler sampler, int sampleY, Dim dim) {
            this.display = display;
            this.id = id;
            this.biomes = biomes;
            this.weight = weight;
            this.biomeSource = biomeSource;
            this.sampler = sampler;
            this.sampleY = sampleY;
            this.dim = dim;
        }
    }

    /** 一个 random_spread 结构集：放置参数 + 变体列表 + 所属维度。 */
    private static final class Placement {
        final RandomSpreadStructurePlacement placement;
        final List<Variant> variants;
        final int spacing;
        final Set<String> displays;
        final Dim dim;

        Placement(RandomSpreadStructurePlacement placement, List<Variant> variants, int spacing, Dim dim) {
            this.placement = placement;
            this.variants = variants;
            this.spacing = spacing;
            this.dim = dim;
            Set<String> names = new HashSet<>();
            for (Variant variant : variants) {
                if (variant.display != null) names.add(variant.display);
            }
            this.displays = names;
        }

        /** 用户勾选的结构里有没有落在这个结构集上的；没有就整个跳过，省去无谓扫描。 */
        boolean intersects(Set<String> wanted) {
            if (wanted == null) return true;
            for (String display : wanted) {
                if (displays.contains(display)) return true;
            }
            return false;
        }
    }

    /** 一个同心环结构集：放置参数 + 用于找偏好生物群系的主世界生物群系源与采样器。 */
    private static final class RingPlacement {
        final ConcentricRingsStructurePlacement placement;
        final BiomeSource biomeSource;
        final Climate.Sampler sampler;

        RingPlacement(ConcentricRingsStructurePlacement placement, BiomeSource biomeSource,
                      Climate.Sampler sampler) {
            this.placement = placement;
            this.biomeSource = biomeSource;
            this.sampler = sampler;
        }
    }

    /** 一个命中：中文结构名 + 原版结构 id + 维度 + 区块坐标 + 结构中心方块坐标 + 路径点高度。 */
    public static final class Found {
        public final String display;
        /** 原版结构 id，例如 {@code minecraft:desert_pyramid}；用于按注册表取回结构本体。 */
        public final String id;
        public final Dim dim;
        public final int chunkX;
        public final int chunkZ;
        public final int blockX;
        public final int blockZ;
        public final int y;

        Found(String display, String id, int chunkX, int chunkZ, int blockX, int blockZ, int y, Dim dim) {
            this.display = display;
            this.id = id;
            this.dim = dim;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.blockX = blockX;
            this.blockZ = blockZ;
            this.y = y;
        }
    }
}
