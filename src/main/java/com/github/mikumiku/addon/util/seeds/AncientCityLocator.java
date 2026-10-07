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

import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

/**
 * 古城（远古城市，Ancient City）定位器。
 *
 * <p>种子库（seedfinding）的结构表只收录到 1.16 那批结构，既没有古城的结构类，
 * 也没有古城容器战利品表，更缺 1.17 之后（含深暗之域 deep_dark）的生物群系数据，
 * 所以古城无法像沙漠神殿那样用 {@code mc_feature} 计算。这里改用本版游戏原生的
 * 世界生成算法：
 *
 * <ul>
 *   <li>位置：古城的结构集 {@code minecraft:ancient_cities} 是
 *       {@code random_spread}，spacing=24、separation=8、salt=20083232、linear，
 *       用原版 {@link RandomSpreadStructurePlacement#getPotentialStructureChunk}
 *       直接算候选区块，和 1.19 ~ 现版本的游戏完全一致；</li>
 *   <li>过滤：古城只生成在深暗之域。用原版 {@link RandomState} 的
 *       {@link Climate.Sampler} 配合 {@link MultiNoiseBiomeSource} 采样该区块
 *       中心、古城地板高度（Y=-51）处的生物群系，判断是否为
 *       {@link Biomes#DEEP_DARK}。</li>
 * </ul>
 *
 * <p>这两步都走原版注册表，所以只需要客户端已经加载的世界注册表，不依赖存档
 * 是否被探索过，也不依赖玩家所在位置。
 *
 * <p>注意：判定生物群系用的是本版（26.1）的原生参数表。古城自 1.19 加入，
 * 1.19 ~ 现版本的深暗之域分布基本一致，跨版本使用时可能有极少数边缘误差。
 */
public final class AncientCityLocator {

    /** 古城结构集的 spacing。 */
    public static final int SPACING = 24;

    /** 古城结构集的 separation。 */
    public static final int SEPARATION = 8;

    /** 古城结构集的 salt。 */
    public static final int SALT = 20083232;

    /** 古城地板固定生成的高度，深暗之域判定与路径点默认高度都用它。 */
    public static final int FLOOR_Y = -51;

    private final RandomSpreadStructurePlacement placement;
    private final MultiNoiseBiomeSource biomeSource;
    private final Climate.Sampler sampler;

    private AncientCityLocator(RandomSpreadStructurePlacement placement,
                               MultiNoiseBiomeSource biomeSource,
                               Climate.Sampler sampler) {
        this.placement = placement;
        this.biomeSource = biomeSource;
        this.sampler = sampler;
    }

    /**
     * 用世界注册表和种子建立定位器。注册表必须从客户端世界主线程取得
     * （{@code mc.level.registryAccess()}）。
     *
     * @return 建立成功返回定位器；注册表缺失或结构不完整时返回 {@code null}
     */
    public static AncientCityLocator create(RegistryAccess registryAccess, long seed) {
        if (registryAccess == null) return null;
        try {
            NoiseGeneratorSettings settings = registryAccess.lookupOrThrow(Registries.NOISE_SETTINGS)
                .getOrThrow(NoiseGeneratorSettings.OVERWORLD).value();
            HolderGetter<NormalNoise.NoiseParameters> noises = registryAccess.lookupOrThrow(Registries.NOISE);
            RandomState randomState = RandomState.create(settings, noises, seed);

            Holder<MultiNoiseBiomeSourceParameterList> preset = registryAccess
                .lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
                .getOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD);
            MultiNoiseBiomeSource biomeSource = MultiNoiseBiomeSource.createFromPreset(preset);

            return new AncientCityLocator(
                new RandomSpreadStructurePlacement(SPACING, SEPARATION, RandomSpreadType.LINEAR, SALT),
                biomeSource, randomState.sampler());
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * 计算某个区域内的古城候选区块（原版 random_spread 逻辑，不管生物群系）。
     *
     * @param regionX 区域 X（区块坐标 / spacing）
     * @param regionZ 区域 Z
     */
    public ChunkPos potentialChunk(long seed, int regionX, int regionZ) {
        return placement.getPotentialStructureChunk(seed, regionX * SPACING, regionZ * SPACING);
    }

    /** 该区块中心在地板高度处是否是深暗之域（决定古城会不会真的生成）。 */
    public boolean isDeepDarkAt(int chunkX, int chunkZ) {
        int blockX = (chunkX << 4) + 8;
        int blockZ = (chunkZ << 4) + 8;
        Holder<Biome> biome = biomeSource.getNoiseBiome(
            blockX >> 2, FLOOR_Y >> 2, blockZ >> 2, sampler);
        return biome != null && biome.is(Biomes.DEEP_DARK);
    }
}
