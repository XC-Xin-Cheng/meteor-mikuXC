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

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.attribute.EnvironmentAttributeReader;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.ticks.LevelTickAccess;
import net.minecraft.world.ticks.ScheduledTick;
import net.minecraft.world.ticks.TickPriority;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * 一个只存在于内存里的 {@link WorldGenLevel}，用来在客户端把结构真正“搭”一遍。
 *
 * <p>这是融合 SeedMapper 做法的关键一环：客户端在多人大服上没有 {@code ServerLevel}，
 * 原版 {@code WorldGenRegion} 用不了，所以结构生成需要的世界接口只能自己实现一份。
 * 这里只把结构生成真正会用到的部分做成真实行为，其余接口按空实现处理：
 *
 * <ul>
 *   <li><b>方块读写</b>：{@link #getBlockState} / {@link #setBlock} 存在一张坐标表里；
 *       写入带方块实体的方块（箱子、木桶等）时按 {@link EntityBlock#newBlockEntity}
 *       顺手把方块实体建出来，这样原版 {@code StructurePiece.createChest} 与
 *       {@code StructureTemplate.placeInWorld} 里“先 setBlock 再 getBlockEntity”的写法
 *       才能拿到真实的箱子对象，也就顺手记下了真实的战利品表与战利品种子；</li>
 *   <li><b>地形高度</b>：{@link #getHeight} 直接问噪声区块生成器
 *       {@link ChunkGenerator#getBaseHeight}，与结构放下去时原版高度图一致，
 *       沙漠神殿这类“先找最低地面再整体下沉”的结构才能落到正确的 Y；</li>
 *   <li><b>随机数</b>：{@link #getRandom} 返回调用方为当前区块准备的那个
 *       {@code WorldgenRandom}，保证 {@code nextLong()} 序列与游戏一致；</li>
 *   <li>其余（音效、粒子、实体、计划刻、维度数据等）全部空实现——结构生成本来
 *       也不会去动它们。</li>
 * </ul>
 *
 * <p>所有坐标只在本对象内部有效，不会写进真正的存档，也不依赖玩家是否到过那里。
 */
final class FakeWorldGenLevel implements WorldGenLevel {

    private final long seed;
    private final RegistryAccess registries;
    private final int minY;
    private final int height;
    private final ChunkGenerator chunkGenerator;
    private final RandomState randomState;
    private final BiomeSource biomeSource;

    private final Map<Long, BlockState> blocks = new HashMap<>();
    private final Map<Long, BlockEntity> blockEntities = new HashMap<>();
    private RandomSource random = RandomSource.create(0L);
    private ProtoChunk dummyChunk;

    FakeWorldGenLevel(long seed, RegistryAccess registries, ChunkGenerator chunkGenerator,
                      RandomState randomState, BiomeSource biomeSource, int minY, int height) {
        this.seed = seed;
        this.registries = registries;
        this.chunkGenerator = chunkGenerator;
        this.randomState = randomState;
        this.biomeSource = biomeSource;
        this.minY = minY;
        this.height = height;
    }

    /** 换一个结构重新开始时清空所有方块与方块实体。 */
    void reset() {
        blocks.clear();
        blockEntities.clear();
    }

    /** 当前区块/结构使用的随机源（与传给结构放置方法的是同一个对象）。 */
    void setRandom(RandomSource random) {
        this.random = random == null ? RandomSource.create(0L) : random;
    }

    /** 结构搭完后，这里就是生成出来的所有带战利品的容器。 */
    Collection<BlockEntity> placedBlockEntities() {
        return new ArrayList<>(blockEntities.values());
    }

    private static long key(BlockPos pos) {
        return pos.asLong();
    }

    // ---- 世界基本属性 ----

    @Override
    public long getSeed() {
        return seed;
    }

    @Override
    public void setCurrentlyGenerating(Supplier<String> supplier) {
        // 只用于调试名，忽略
    }

    @Override
    public int getMinY() {
        return minY;
    }

    @Override
    public int getHeight() {
        return height;
    }

    @Override
    public int getSeaLevel() {
        try {
            return chunkGenerator.getSeaLevel();
        } catch (Throwable t) {
            return 63;
        }
    }

    @Override
    public RegistryAccess registryAccess() {
        return registries;
    }

    @Override
    public FeatureFlagSet enabledFeatures() {
        return FeatureFlags.DEFAULT_FLAGS;
    }

    @Override
    public LevelLightEngine getLightEngine() {
        return null;
    }

    @Override
    public net.minecraft.world.level.border.WorldBorder getWorldBorder() {
        return null;
    }

    @Override
    public EnvironmentAttributeReader environmentAttributes() {
        return EnvironmentAttributeReader.EMPTY;
    }

    @Override
    public boolean isClientSide() {
        return true;
    }

    @Override
    public DimensionType dimensionType() {
        return null;
    }

    @Override
    public int getSkyDarken() {
        return 0;
    }

    @Override
    public BiomeManager getBiomeManager() {
        return null;
    }

    @Override
    public Holder<Biome> getBiome(BlockPos pos) {
        return getNoiseBiome(pos.getX() >> 2, pos.getY() >> 2, pos.getZ() >> 2);
    }

    @Override
    public Holder<Biome> getNoiseBiome(int x, int y, int z) {
        return biomeSource.getNoiseBiome(x, y, z, randomState.sampler());
    }

    @Override
    public Holder<Biome> getUncachedNoiseBiome(int x, int y, int z) {
        return getNoiseBiome(x, y, z);
    }

    @Override
    public long nextSubTickCount() {
        return 0L;
    }

    @Override
    public ServerLevel getLevel() {
        return null;
    }

    @Override
    public LevelData getLevelData() {
        return null;
    }

    @Override
    public MinecraftServer getServer() {
        return null;
    }

    @Override
    public ChunkSource getChunkSource() {
        return null;
    }

    @Override
    public DifficultyInstance getCurrentDifficultyAt(BlockPos pos) {
        return new DifficultyInstance(Difficulty.PEACEFUL, 0L, 0L, 0.0F);
    }

    @Override
    public RandomSource getRandom() {
        return random;
    }

    // ---- 方块读写 ----

    @Override
    public BlockState getBlockState(BlockPos pos) {
        BlockState state = blocks.get(key(pos));
        return state != null ? state : Blocks.AIR.defaultBlockState();
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        return getBlockState(pos).getFluidState();
    }

    @Override
    public boolean isFluidAtPosition(BlockPos pos, Predicate<FluidState> predicate) {
        return predicate.test(getFluidState(pos));
    }

    @Override
    public boolean isStateAtPosition(BlockPos pos, Predicate<BlockState> predicate) {
        return predicate.test(getBlockState(pos));
    }

    @Override
    public BlockEntity getBlockEntity(BlockPos pos) {
        return blockEntities.get(key(pos));
    }

    @Override
    public boolean setBlock(BlockPos pos, BlockState state, int flags, int recursionLeft) {
        BlockPos immutable = pos.immutable();
        long k = key(immutable);
        BlockState old = blocks.get(k);
        if (old != null && old.hasBlockEntity()) {
            blockEntities.remove(k);
        }
        blocks.put(k, state);
        if (state.hasBlockEntity() && state.getBlock() instanceof EntityBlock entityBlock) {
            BlockEntity blockEntity = entityBlock.newBlockEntity(immutable, state);
            if (blockEntity != null) {
                blockEntities.put(k, blockEntity);
            }
        }
        return true;
    }

    @Override
    public boolean removeBlock(BlockPos pos, boolean movedByPiston) {
        long k = key(pos);
        BlockState old = blocks.remove(k);
        if (old != null && old.hasBlockEntity()) {
            blockEntities.remove(k);
        }
        return old != null;
    }

    @Override
    public boolean destroyBlock(BlockPos pos, boolean dropBlock, Entity entity, int recursionLeft) {
        return removeBlock(pos, false);
    }

    @Override
    public boolean addFreshEntity(Entity entity) {
        return true;
    }

    @Override
    public void addFreshEntityWithPassengers(Entity entity) {
        // 结构生成偶尔会放怪，这里不需要真的实体
    }

    // ---- 地形高度 ----

    @Override
    public int getHeight(Heightmap.Types type, int x, int z) {
        try {
            return chunkGenerator.getBaseHeight(x, z, type, this, randomState);
        } catch (Throwable t) {
            return getSeaLevel();
        }
    }

    // ---- 区块 ----

    @Override
    public ChunkAccess getChunk(int chunkX, int chunkZ, ChunkStatus status, boolean requireChunk) {
        if (dummyChunk == null) {
            dummyChunk = new ProtoChunk(new ChunkPos(chunkX, chunkZ), UpgradeData.EMPTY,
                this, PalettedContainerFactory.create(registries), null);
        }
        return dummyChunk;
    }

    @Override
    public boolean hasChunk(int chunkX, int chunkZ) {
        return true;
    }

    // ---- 计划刻（结构生成用不到，全部空实现） ----

    @Override
    public <T> ScheduledTick<T> createTick(BlockPos pos, T type, int delay, TickPriority priority) {
        return null;
    }

    @Override
    public <T> ScheduledTick<T> createTick(BlockPos pos, T type, int delay) {
        return null;
    }

    @Override
    public LevelTickAccess<Block> getBlockTicks() {
        return noopBlockTicks;
    }

    @Override
    public LevelTickAccess<Fluid> getFluidTicks() {
        return noopFluidTicks;
    }

    /** 什么都不排的计划刻表：结构里的水/岩浆只要不炸就行。 */
    private static final LevelTickAccess<Block> noopBlockTicks = new LevelTickAccess<>() {
        @Override
        public void schedule(ScheduledTick<Block> tick) {
        }

        @Override
        public boolean hasScheduledTick(BlockPos pos, Block type) {
            return false;
        }

        @Override
        public int count() {
            return 0;
        }

        @Override
        public boolean willTickThisTick(BlockPos pos, Block type) {
            return false;
        }
    };

    private static final LevelTickAccess<Fluid> noopFluidTicks = new LevelTickAccess<>() {
        @Override
        public void schedule(ScheduledTick<Fluid> tick) {
        }

        @Override
        public boolean hasScheduledTick(BlockPos pos, Fluid type) {
            return false;
        }

        @Override
        public int count() {
            return 0;
        }

        @Override
        public boolean willTickThisTick(BlockPos pos, Fluid type) {
            return false;
        }
    };

    // ---- 声音 / 粒子 / 事件（空实现） ----

    @Override
    public void playSound(Entity entity, BlockPos pos, net.minecraft.sounds.SoundEvent sound,
                          net.minecraft.sounds.SoundSource source, float volume, float pitch) {
        // 空实现
    }

    @Override
    public void addParticle(net.minecraft.core.particles.ParticleOptions particle,
                            double x, double y, double z, double dx, double dy, double dz) {
        // 空实现
    }

    @Override
    public void levelEvent(Entity entity, int type, BlockPos pos, int data) {
        // 空实现
    }

    @Override
    public void gameEvent(Holder<net.minecraft.world.level.gameevent.GameEvent> event, Vec3 pos,
                          net.minecraft.world.level.gameevent.GameEvent.Context context) {
        // 空实现
    }

    // ---- 实体查询（结构生成不需要，返回空） ----

    @Override
    public List<Entity> getEntities(Entity except, AABB box, Predicate<? super Entity> predicate) {
        return List.of();
    }

    @Override
    public <T extends Entity> List<T> getEntities(EntityTypeTest<Entity, T> typeTest, AABB box,
                                                  Predicate<? super T> predicate) {
        return List.of();
    }

    @Override
    public List<? extends Player> players() {
        return List.of();
    }
}
