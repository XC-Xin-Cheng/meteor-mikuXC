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

import com.github.mikumiku.addon.util.seeds.NativeStructureLocator.Dim;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.RandomSource;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.level.biome.TheEndBiomeSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.util.datafix.DataFixers;

import java.io.Reader;
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 原生（SeedMapper 式）容器战利品预测器。
 *
 * <p>这是与 <a href="https://github.com/cev-api/VoxelMap-x-SeedMapper">VoxelMap x
 * SeedMapper</a> 同一套思路的实现：不看种子库对结构的“重新实现”，而是直接让本版
 * 游戏自己把结构搭一遍，它搭到哪个箱子、写进什么战利品种子，我们就照单全收。
 * 相比种子库的做法有两个好处：
 *
 * <ul>
 *   <li><b>结构位置更准</b>：候选区块与结构变体由 {@link NativeStructureLocator}
 *       用本版注册表里的结构集、权重、生物群系白名单、频率约简与排除区原样算出，
 *       和游戏/SeedMapper 是一致的；</li>
 *   <li><b>容器更好拿</b>：结构本体交给原版的 {@link Structure#generate} /
 *       {@link StructureStart#placeInChunk} 真正生成一次，箱子位置、战利品表、
 *       战利品种子全部来自游戏自己的代码，不再需要为每种结构单独写生成器。</li>
 * </ul>
 *
 * <p>关键在于 {@link FakeWorldGenLevel}：客户端在多人服务器上没有 {@code ServerLevel}，
 * 原版的 {@code WorldGenRegion} 用不了，所以这里用一个只存方块的假世界顶上，
 * 让原版的结构生成代码“以为”自己在正常世界里搭结构。
 *
 * <p>战利品种子的复刻与游戏 {@code ChunkGenerator.applyBiomeDecoration} 对齐：
 * 每个区块先用 {@code setDecorationSeed(世界种子, 区块X*16, 区块Z*16)} 初始化装饰种子，
 * 再对该结构所在生成步骤调用 {@code setFeatureSeed(装饰种子, 结构序号, 步骤序号)}，
 * 然后逐个区块把结构放下去；结构片段在放置箱子时会从这个随机源取真实种子。
 */
public final class NativeStructureLoot {

    /** 主世界可建造高度：minY=-64、高度 384。 */
    private static final int OVERWORLD_MIN_Y = -64;
    private static final int OVERWORLD_HEIGHT = 384;

    /** 下界与末地的可建造高度：minY=0、高度 256。 */
    private static final int OTHER_MIN_Y = 0;
    private static final int OTHER_HEIGHT = 256;

    /** 懒加载的 {@code LootContext} 私有构造器（客户端没有 ServerLevel，走不了公开 Builder）。 */
    private static Constructor<LootContext> lootContextConstructor;

    /** 一个预测出的容器。 */
    public record Chest(int x, int y, int z, String lootTable, boolean enchantedGoldenApple) {
    }

    /** 一个维度对应的世界生成数据。 */
    private record DimData(RandomState randomState, ChunkGenerator chunkGenerator, BiomeSource biomeSource,
                           ResourceKey<Level> levelKey, int minY, int height) {
    }

    private final RegistryAccess registries;
    private final ResourceManager resources;
    private final long seed;
    private final StructureTemplateManager templateManager;
    private final Map<Dim, DimData> dimensions = new EnumMap<>(Dim.class);
    private final Map<String, int[]> stepIndexCache = new HashMap<>();
    private final Map<String, Optional<LootTable>> lootTableCache = new HashMap<>();

    private NativeStructureLoot(RegistryAccess registries, ResourceManager resources, long seed,
                                StructureTemplateManager templateManager) {
        this.registries = registries;
        this.resources = resources;
        this.seed = seed;
        this.templateManager = templateManager;
    }

    /**
     * 建立预测器。注册表与资源管理器必须从客户端主线程取得。
     *
     * @return 建立成功返回预测器；注册表/资源管理器缺失、或拿不到结构模板管理器时返回 {@code null}
     */
    public static NativeStructureLoot create(RegistryAccess registries, ResourceManager resources, long seed) {
        if (registries == null || resources == null) return null;
        try {
            StructureTemplateManager templateManager = buildTemplateManager(registries, resources);
            if (templateManager == null) return null;

            NativeStructureLoot predictor = new NativeStructureLoot(registries, resources, seed, templateManager);
            if (!predictor.buildDimensions()) return null;
            return predictor;
        } catch (Throwable t) {
            return null;
        }
    }

    /** 建立主世界 / 下界 / 末地三套区块生成器与随机状态。 */
    private boolean buildDimensions() {
        try {
            RegistryAccess registryAccess = registries;
            HolderGetter<net.minecraft.world.level.levelgen.synth.NormalNoise.NoiseParameters> noises =
                registryAccess.lookupOrThrow(Registries.NOISE);

            MultiNoiseBiomeSource overworldSource = MultiNoiseBiomeSource.createFromPreset(
                registryAccess.lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
                    .getOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD));
            NoiseGeneratorSettings overworldSettings = registryAccess.lookupOrThrow(Registries.NOISE_SETTINGS)
                .getOrThrow(NoiseGeneratorSettings.OVERWORLD).value();
            Holder<NoiseGeneratorSettings> overworldSettingsHolder = registryAccess.lookupOrThrow(Registries.NOISE_SETTINGS)
                .getOrThrow(NoiseGeneratorSettings.OVERWORLD);
            dimensions.put(Dim.OVERWORLD, new DimData(
                RandomState.create(overworldSettings, noises, seed),
                new NoiseBasedChunkGenerator(overworldSource, overworldSettingsHolder),
                overworldSource, Level.OVERWORLD, OVERWORLD_MIN_Y, OVERWORLD_HEIGHT));

            MultiNoiseBiomeSource netherSource = MultiNoiseBiomeSource.createFromPreset(
                registryAccess.lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
                    .getOrThrow(MultiNoiseBiomeSourceParameterLists.NETHER));
            NoiseGeneratorSettings netherSettings = registryAccess.lookupOrThrow(Registries.NOISE_SETTINGS)
                .getOrThrow(NoiseGeneratorSettings.NETHER).value();
            Holder<NoiseGeneratorSettings> netherSettingsHolder = registryAccess.lookupOrThrow(Registries.NOISE_SETTINGS)
                .getOrThrow(NoiseGeneratorSettings.NETHER);
            dimensions.put(Dim.NETHER, new DimData(
                RandomState.create(netherSettings, noises, seed),
                new NoiseBasedChunkGenerator(netherSource, netherSettingsHolder),
                netherSource, Level.NETHER, OTHER_MIN_Y, OTHER_HEIGHT));

            TheEndBiomeSource endSource = TheEndBiomeSource.create(registryAccess.lookupOrThrow(Registries.BIOME));
            NoiseGeneratorSettings endSettings = registryAccess.lookupOrThrow(Registries.NOISE_SETTINGS)
                .getOrThrow(NoiseGeneratorSettings.END).value();
            Holder<NoiseGeneratorSettings> endSettingsHolder = registryAccess.lookupOrThrow(Registries.NOISE_SETTINGS)
                .getOrThrow(NoiseGeneratorSettings.END);
            dimensions.put(Dim.END, new DimData(
                RandomState.create(endSettings, noises, seed),
                new NoiseBasedChunkGenerator(endSource, endSettingsHolder),
                endSource, Level.END, OTHER_MIN_Y, OTHER_HEIGHT));
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** 某个维度是否可用（三门都有；异常时缺哪个跳过哪个）。 */
    public boolean supports(Dim dim) {
        return dimensions.containsKey(dim);
    }

    /**
     * 生成指定区块上的结构，并逐箱预测战利品。
     *
     * @param structureId 原版结构 id，例如 {@code minecraft:desert_pyramid}
     * @return 生成成功返回箱子列表（可能为空）；结构生成失败返回 {@code null}
     */
    public List<Chest> predict(String structureId, ChunkPos startChunk, Dim dim) {
        DimData data = dimensions.get(dim);
        if (data == null || structureId == null || startChunk == null) return null;

        try {
            Holder<Structure> holder = structureHolder(structureId);
            if (holder == null) return null;

            int[] stepIndex = stepIndex(holder);
            if (stepIndex == null) return null;

            Structure structure = holder.value();
            LevelHeightAccessor heightAccessor = LevelHeightAccessor.create(data.minY(), data.height());
            StructureStart start = structure.generate(holder, data.levelKey(), registries,
                data.chunkGenerator(), data.biomeSource(), data.randomState(), templateManager,
                seed, startChunk, 0, heightAccessor, biome -> true);
            if (start == null || !start.isValid()) return List.of();

            FakeWorldGenLevel level = new FakeWorldGenLevel(seed, registries, data.chunkGenerator(),
                data.randomState(), data.biomeSource(), data.minY(), data.height());

            BoundingBox box = start.getBoundingBox();
            int minChunkX = box.minX() >> 4;
            int maxChunkX = box.maxX() >> 4;
            int minChunkZ = box.minZ() >> 4;
            int maxChunkZ = box.maxZ() >> 4;

            for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                    BoundingBox area = new BoundingBox(chunkX << 4, data.minY() + 1, chunkZ << 4,
                        (chunkX << 4) + 15, data.minY() + data.height() - 1, (chunkZ << 4) + 15);

                    WorldgenRandom random = new WorldgenRandom(new XoroshiroRandomSource(0L));
                    long decorationSeed = random.setDecorationSeed(seed, chunkX << 4, chunkZ << 4);
                    random.setFeatureSeed(decorationSeed, stepIndex[0], stepIndex[1]);
                    level.setRandom(random);

                    try {
                        start.placeInChunk(level, null, data.chunkGenerator(), random, area,
                            new ChunkPos(chunkX, chunkZ));
                    } catch (Throwable t) {
                        // 单个区块放不下去不影响其它区块里的箱子
                    }
                }
            }

            List<Chest> result = new ArrayList<>();
            for (BlockEntity blockEntity : level.placedBlockEntities()) {
                if (!(blockEntity instanceof RandomizableContainer container)) continue;
                ResourceKey<LootTable> lootTable = container.getLootTable();
                if (lootTable == null) continue;

                String lootTableId = lootTable.identifier().toString();
                long lootSeed = container.getLootTableSeed();
                boolean enchantedApple = rollContainsEnchantedApple(lootTableId, lootSeed);
                result.add(new Chest(blockEntity.getBlockPos().getX(), blockEntity.getBlockPos().getY(),
                    blockEntity.getBlockPos().getZ(), lootTableId, enchantedApple));
            }
            return result;
        } catch (Throwable t) {
            return null;
        }
    }

    /** 按 id 从本版注册表取结构；取不到返回 {@code null}。 */
    private Holder<Structure> structureHolder(String structureId) {
        try {
            Registry<Structure> registry = registries.lookupOrThrow(Registries.STRUCTURE);
            return registry.getOrThrow(ResourceKey.create(Registries.STRUCTURE, Identifier.parse(structureId)));
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * 该结构在 {@code ChunkGenerator.applyBiomeDecoration} 里的序号对
     * {@code [组内序号, 步骤序号]}：原版把结构注册表按 {@code step().ordinal()} 分组，
     * 组内顺序就是注册表遍历顺序，特征种子用的就是组内序号。
     */
    private int[] stepIndex(Holder<Structure> holder) {
        String id = holder.unwrapKey().map(key -> key.identifier().toString()).orElse(null);
        if (id == null) return null;
        int[] cached = stepIndexCache.get(id);
        if (cached != null) return cached;

        try {
            Structure target = holder.value();
            int step = target.step().ordinal();
            int index = 0;
            for (Structure structure : registries.lookupOrThrow(Registries.STRUCTURE)) {
                if (structure.step().ordinal() != step) continue;
                if (structure == target) {
                    int[] result = {index, step};
                    stepIndexCache.put(id, result);
                    return result;
                }
                index++;
            }
        } catch (Throwable t) {
            return null;
        }
        return null;
    }

    /** 用给定种子掷一次战利品表，判断结果里有没有附魔金苹果。 */
    private boolean rollContainsEnchantedApple(String lootTableId, long lootSeed) {
        LootTable table = loadLootTable(lootTableId);
        if (table == null) return false;
        LootContext context = createLootContext(lootSeed);
        if (context == null) return false;

        boolean[] found = {false};
        try {
            table.getRandomItemsRaw(context, (ItemStack stack) -> {
                if (stack.is(Items.ENCHANTED_GOLDEN_APPLE)) found[0] = true;
            });
        } catch (Throwable t) {
            return false;
        }
        return found[0];
    }

    /** 取战利品表：优先用注册表（单人是存档服务端的），否则解析客户端数据包里的原始 JSON。 */
    private LootTable loadLootTable(String lootTableId) {
        Optional<LootTable> cached = lootTableCache.get(lootTableId);
        if (cached != null) return cached.orElse(null);

        Optional<LootTable> loaded = Optional.empty();
        try {
            Identifier id = Identifier.parse(lootTableId);
            ResourceKey<LootTable> key = ResourceKey.create(Registries.LOOT_TABLE, id);
            Optional<Registry<LootTable>> registry = registries.lookup(Registries.LOOT_TABLE);
            if (registry.isPresent()) {
                Optional<Holder.Reference<LootTable>> holder = registry.get().get(key);
                if (holder.isPresent()) {
                    loaded = Optional.of(holder.get().value());
                }
            }
            if (loaded.isEmpty()) {
                Identifier resourceId = Identifier.fromNamespaceAndPath(id.getNamespace(),
                    "loot_table/" + id.getPath() + ".json");
                Optional<Resource> resource = resources.getResource(resourceId);
                if (resource.isPresent()) {
                    try (Reader reader = resource.get().openAsReader()) {
                        JsonElement json = JsonParser.parseReader(reader);
                        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, registries);
                        loaded = Optional.of(LootTable.DIRECT_CODEC.parse(ops, json).getOrThrow());
                    }
                }
            }
        } catch (Throwable t) {
            loaded = Optional.empty();
        }
        lootTableCache.put(lootTableId, loaded);
        return loaded.orElse(null);
    }

    /** 用反射构造 {@code LootContext}：客户端没有 ServerLevel，走不了公开的 Builder。 */
    private LootContext createLootContext(long lootSeed) {
        try {
            Constructor<LootContext> constructor = lootContextConstructor();
            if (constructor == null) return null;
            ContextMap contextMap = new ContextMap.Builder().create(LootContextParamSets.EMPTY);
            LootParams params = new LootParams(null, contextMap, Map.of(), 0.0F);
            return constructor.newInstance(params, RandomSource.create(lootSeed), registries);
        } catch (Throwable t) {
            return null;
        }
    }

    private static synchronized Constructor<LootContext> lootContextConstructor() {
        if (lootContextConstructor == null) {
            try {
                Constructor<LootContext> constructor = LootContext.class.getDeclaredConstructor(
                    LootParams.class, RandomSource.class, HolderGetter.Provider.class);
                constructor.setAccessible(true);
                lootContextConstructor = constructor;
            } catch (Throwable t) {
                return null;
            }
        }
        return lootContextConstructor;
    }

    /**
     * 拿结构模板管理器：单人优先用集成服务端（含存档数据包），否则用客户端资源管理器
     * 加一个临时目录自建一个（只读客户端自带的原版结构模板）。
     */
    private static StructureTemplateManager buildTemplateManager(RegistryAccess registries, ResourceManager resources) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) {
            try {
                if (minecraft.getSingleplayerServer() != null) {
                    StructureTemplateManager manager = minecraft.getSingleplayerServer().getStructureManager();
                    if (manager != null) return manager;
                }
            } catch (Throwable ignored) {
                // 单人存档不可用就退到自建管理器
            }
        }

        LevelStorageSource.LevelStorageAccess access = createTemporaryStorage(minecraft);
        if (access == null) return null;
        try {
            return new StructureTemplateManager(resources, access, DataFixers.getDataFixer(),
                registries.lookupOrThrow(Registries.BLOCK));
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * 结构模板管理器构造时需要一份 {@code LevelStorageAccess}（只用来定位“存档生成目录”，
     * 读取原版模板其实走资源管理器）。这里反射构造一份指向临时目录的访问对象。
     */
    private static LevelStorageSource.LevelStorageAccess createTemporaryStorage(Minecraft minecraft) {
        if (minecraft == null) return null;
        try {
            LevelStorageSource source = minecraft.getLevelSource();
            if (source == null) return null;
            Path directory = Files.createTempDirectory("blockforge-native-loot");
            Constructor<LevelStorageSource.LevelStorageAccess> constructor =
                LevelStorageSource.LevelStorageAccess.class.getDeclaredConstructor(
                    LevelStorageSource.class, String.class, Path.class);
            constructor.setAccessible(true);
            return constructor.newInstance(source, "blockforge-temporary", directory);
        } catch (Throwable t) {
            return null;
        }
    }
}
