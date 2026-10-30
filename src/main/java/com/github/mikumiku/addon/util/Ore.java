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
package com.github.mikumiku.addon.util;

import com.github.mikumiku.addon.mixin.CountPlacementModifierAccessor;
import com.github.mikumiku.addon.mixin.HeightRangePlacementModifierAccessor;
import com.github.mikumiku.addon.mixin.RarityFilterPlacementModifierAccessor;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.world.Dimension;
import net.minecraft.client.Minecraft;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.data.worldgen.placement.OrePlacements;
import net.minecraft.world.level.levelgen.feature.ScatteredOreFeature;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.biome.FeatureSorter;
import net.minecraft.world.level.levelgen.heightproviders.HeightProvider;
import net.minecraft.world.level.levelgen.placement.CountPlacement;
import net.minecraft.world.level.levelgen.placement.HeightRangePlacement;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.placement.RarityFilter;

import java.util.*;


public class Ore {

    /**
     * 矿石所在层。浅层 = 石头变种（Y≥0），深层 = 深板岩变种（Y<0），
     * 下界 = 下界岩变种（下界没有深板岩）。选择界面按它分段。
     */
    public enum OreLayer {
        SHALLOW("浅层矿石（Y≥0，石头）", "浅层"),
        DEEP("深层矿石（Y<0，深板岩）", "深层"),
        NETHER("下界矿石", "下界");

        public final String displayName;
        /** 写进选择文件（{@code Select-Ore.txt}）的短名，例如「深层 绿宝石 on on」。 */
        public final String shortName;

        OreLayer(String displayName, String shortName) {
            this.displayName = displayName;
            this.shortName = shortName;
        }

        /** 按文件里的短名（浅层/深层/下界）、枚举名或界面显示名查找；认不出返回 null。 */
        public static OreLayer byToken(String token) {
            if (token == null) return null;
            String key = token.trim();
            if (key.isEmpty()) return null;
            for (OreLayer layer : values()) {
                if (layer.shortName.equals(key)
                    || layer.name().equalsIgnoreCase(key)
                    || layer.displayName.equalsIgnoreCase(key)) {
                    return layer;
                }
            }
            return null;
        }
    }

    /** 矿石种类。显示名、baritone 名称与颜色保持和旧版逐个开关完全一致。 */
    public enum OreType {
        COAL("煤炭", "coal_ore", new Color(47, 44, 54), OreLayer.SHALLOW, OreLayer.DEEP),
        IRON("铁", "iron_ore", new Color(236, 173, 119), OreLayer.SHALLOW, OreLayer.DEEP),
        COPPER("铜", "copper_ore", new Color(239, 151, 0), OreLayer.SHALLOW, OreLayer.DEEP),
        EMERALD("绿宝石", "emerald_ore", new Color(27, 209, 45), OreLayer.SHALLOW, OreLayer.DEEP),
        GOLD("金", "gold_ore", new Color(247, 229, 30), OreLayer.SHALLOW, OreLayer.DEEP, OreLayer.NETHER),
        REDSTONE("红石", "redstone_ore", new Color(245, 7, 23), OreLayer.SHALLOW, OreLayer.DEEP),
        LAPIS("青金石", "lapis_ore", new Color(8, 26, 189), OreLayer.SHALLOW, OreLayer.DEEP),
        DIAMOND("钻石", "diamond_ore", new Color(33, 244, 255), OreLayer.SHALLOW, OreLayer.DEEP),
        QUARTZ("石英", "nether_quartz_ore", new Color(205, 205, 205), OreLayer.NETHER),
        DEBRIS("远古残骸", "ancient_debris", new Color(209, 27, 245), OreLayer.NETHER);

        public final String displayName;
        /** Baritone 的 mineByName 名称。 */
        public final String mineName;
        public final Color color;

        private final EnumSet<OreLayer> layers;

        OreType(String displayName, String mineName, Color color, OreLayer... layers) {
            this.displayName = displayName;
            this.mineName = mineName;
            this.color = color;
            this.layers = EnumSet.noneOf(OreLayer.class);
            Collections.addAll(this.layers, layers);
        }

        public boolean hasLayer(OreLayer layer) {
            return layers.contains(layer);
        }

        public EnumSet<OreLayer> layers() {
            return layers;
        }

        /** 该层里可能出现的矿石种类。 */
        public static List<OreType> inLayer(OreLayer layer) {
            List<OreType> list = new ArrayList<>();
            for (OreType type : values()) {
                if (type.hasLayer(layer)) list.add(type);
            }
            return list;
        }

        /** 按 baritone 名称、中文名或枚举名查找（忽略大小写与首尾空格）。 */
        public static OreType byId(String id) {
            if (id == null) return null;
            String key = id.trim().toLowerCase(Locale.ROOT);
            if (key.isEmpty()) return null;
            for (OreType type : values()) {
                if (type.mineName.equals(key)
                    || type.displayName.equalsIgnoreCase(key)
                    || type.name().toLowerCase(Locale.ROOT).equals(key)) {
                    return type;
                }
            }
            return null;
        }
    }

    /**
     * 一个被勾选的“矿石 + 层”组合，例如深层钻石 = (DIAMOND, DEEP)。
     * 实例全部预先建好，渲染时 {@link #of(OreType, OreLayer)} 不产生新对象。
     */
    public static final class OreKey {
        /** “矿石 + 层”组合的总数，便于用数组按下标查表。 */
        public static final int COUNT = OreType.values().length * OreLayer.values().length;
        private static final OreKey[] CACHE = new OreKey[COUNT];

        static {
            for (OreType type : OreType.values()) {
                for (OreLayer layer : OreLayer.values()) {
                    CACHE[type.ordinal() * OreLayer.values().length + layer.ordinal()] = new OreKey(type, layer);
                }
            }
        }

        public final OreType type;
        public final OreLayer layer;

        private OreKey(OreType type, OreLayer layer) {
            this.type = type;
            this.layer = layer;
        }

        public static OreKey of(OreType type, OreLayer layer) {
            return CACHE[type.ordinal() * OreLayer.values().length + layer.ordinal()];
        }

        /** 在 {@link #CACHE} 里的下标，等价于 {@code type.ordinal() * 层数 + layer.ordinal()}。 */
        public int index() {
            return type.ordinal() * OreLayer.values().length + layer.ordinal();
        }

        /** 配置里保存的形式，例如 diamond_ore@deep。 */
        public String id() {
            return type.mineName + "@" + layer.name().toLowerCase(Locale.ROOT);
        }

        public static OreKey byId(String id) {
            if (id == null) return null;
            String key = id.trim().toLowerCase(Locale.ROOT);
            int at = key.indexOf('@');
            if (at < 0) return null;

            OreType type = OreType.byId(key.substring(0, at));
            if (type == null) return null;

            for (OreLayer layer : OreLayer.values()) {
                if (layer.name().toLowerCase(Locale.ROOT).equals(key.substring(at + 1))
                    && type.hasLayer(layer)) {
                    return of(type, layer);
                }
            }
            return null;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof OreKey other)) return false;
            return type == other.type && layer == other.layer;
        }

        @Override
        public int hashCode() {
            return type.ordinal() * 31 + layer.ordinal();
        }

        @Override
        public String toString() {
            return id();
        }
    }

    /**
     * 每个“矿石 + 层”的边框样式：是否让相邻同类矿石的边框连成一体，
     * 以及用哪个颜色画。默认不连接、颜色沿用该矿石的内置颜色。
     */
    public static final class OreStyle {
        public boolean connected;
        public final Color color;

        public OreStyle(Color defaultColor) {
            this.connected = false;
            this.color = new Color(defaultColor);
        }
    }

    public static Map<ResourceKey<Biome>, List<Ore>> getRegistry(Dimension dimension) {

        HolderLookup.Provider registry = VanillaRegistries.createLookup();
        HolderLookup.RegistryLookup<PlacedFeature> features = registry.lookupOrThrow(Registries.PLACED_FEATURE);
        var reg = registry.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.NORMAL).value().createWorldDimensions().dimensions();

        var dim = switch (dimension) {
            case Overworld -> reg.get(LevelStem.OVERWORLD);
            case Nether -> reg.get(LevelStem.NETHER);
            case End -> reg.get(LevelStem.END);
        };
        ChunkGenerator generator = dim.generator();
        var biomes = generator.getBiomeSource().possibleBiomes();
        var biomes1 = biomes.stream().toList();

        List<FeatureSorter.StepFeatureData> indexer = FeatureSorter.buildFeaturesPerStep(
            biomes1, biomeEntry -> biomeEntry.value().getGenerationSettings().features(), true
        );


        Map<PlacedFeature, Ore> featureToOre = new HashMap<>();
        registerOre(featureToOre, indexer, features, OrePlacements.ORE_COAL_LOWER, 6, OreType.COAL, generator);
        registerOre(featureToOre, indexer, features, OrePlacements.ORE_COAL_UPPER, 6, OreType.COAL, generator);
        registerOre(featureToOre, indexer, features, OrePlacements.ORE_IRON_MIDDLE, 6, OreType.IRON, generator);
        registerOre(featureToOre, indexer, features, OrePlacements.ORE_IRON_SMALL, 6, OreType.IRON, generator);
        registerOre(featureToOre, indexer, features, OrePlacements.ORE_IRON_UPPER, 6, OreType.IRON, generator);
        registerOre(featureToOre, indexer, features, OrePlacements.ORE_GOLD, 6, OreType.GOLD, generator);
        registerOre(featureToOre, indexer, features, OrePlacements.ORE_GOLD_LOWER, 6, OreType.GOLD, generator);
        registerOre(featureToOre, indexer, features, OrePlacements.ORE_GOLD_EXTRA, 6, OreType.GOLD, generator);
        registerOre(featureToOre, indexer, features, OrePlacements.ORE_GOLD_NETHER, 7, OreType.GOLD, generator);
        registerOre(featureToOre, indexer, features, OrePlacements.ORE_GOLD_DELTAS, 7, OreType.GOLD, generator);
        registerOre(featureToOre, indexer, features, OrePlacements.ORE_REDSTONE, 6, OreType.REDSTONE, generator);
        registerOre(featureToOre, indexer, features, OrePlacements.ORE_REDSTONE_LOWER, 6, OreType.REDSTONE, generator);
        registerOre(featureToOre, indexer, features, OrePlacements.ORE_DIAMOND, 6, OreType.DIAMOND, generator);
        registerOre(featureToOre, indexer, features, OrePlacements.ORE_DIAMOND_BURIED, 6, OreType.DIAMOND, generator);
        registerOre(featureToOre, indexer, features, OrePlacements.ORE_DIAMOND_LARGE, 6, OreType.DIAMOND, generator);
        registerOre(featureToOre, indexer, features, OrePlacements.ORE_DIAMOND_MEDIUM, 6, OreType.DIAMOND, generator);
        registerOre(featureToOre, indexer, features, OrePlacements.ORE_LAPIS, 6, OreType.LAPIS, generator);
        registerOre(featureToOre, indexer, features, OrePlacements.ORE_LAPIS_BURIED, 6, OreType.LAPIS, generator);
        registerOre(featureToOre, indexer, features, OrePlacements.ORE_COPPER, 6, OreType.COPPER, generator);
        registerOre(featureToOre, indexer, features, OrePlacements.ORE_COPPER_LARGE, 6, OreType.COPPER, generator);
        registerOre(featureToOre, indexer, features, OrePlacements.ORE_EMERALD, 6, OreType.EMERALD, generator);
        registerOre(featureToOre, indexer, features, OrePlacements.ORE_QUARTZ_NETHER, 7, OreType.QUARTZ, generator);
        registerOre(featureToOre, indexer, features, OrePlacements.ORE_QUARTZ_DELTAS, 7, OreType.QUARTZ, generator);
        registerOre(featureToOre, indexer, features, OrePlacements.ORE_ANCIENT_DEBRIS_SMALL, 7, OreType.DEBRIS, generator);
        registerOre(featureToOre, indexer, features, OrePlacements.ORE_ANCIENT_DEBRIS_LARGE, 7, OreType.DEBRIS, generator);


        Map<ResourceKey<Biome>, List<Ore>> biomeOreMap = new HashMap<>();

        biomes1.forEach(biome -> {
            biomeOreMap.put(biome.unwrapKey().get(), new ArrayList<>());
            biome.value().getGenerationSettings().features().stream()
                .flatMap(HolderSet::stream)
                .map(Holder::value)
                .filter(featureToOre::containsKey)
                .forEach(feature -> {
                    biomeOreMap.get(biome.unwrapKey().get()).add(featureToOre.get(feature));
                });
        });
        return biomeOreMap;
    }

    private static void registerOre(
        Map<PlacedFeature, Ore> map,
        List<FeatureSorter.StepFeatureData> indexer,
        HolderLookup.RegistryLookup<PlacedFeature> oreRegistry,
        ResourceKey<PlacedFeature> oreKey,
        int genStep,
        OreType type,
        ChunkGenerator generator
    ) {
        var orePlacement = oreRegistry.getOrThrow(oreKey).value();

        int index = indexer.get(genStep).indexMapping().applyAsInt(orePlacement);

        Ore ore = new Ore(orePlacement, genStep, index, type, generator);

        map.put(orePlacement, ore);
    }

    public int step;
    public int index;
    public final OreType type;
    public IntProvider count = ConstantInt.of(1);
    public HeightProvider heightProvider;
    public WorldGenerationContext heightContext;
    public float rarity = 1;
    public float discardOnAirChance;
    public int size;
    public Color color;
    public boolean scattered;

    private Ore(PlacedFeature feature, int step, int index, OreType type, ChunkGenerator generator) {
        this.step = step;
        this.index = index;
        this.type = type;
        this.color = type.color;
        int bottom = Minecraft.getInstance().level.getMinY();
        int height = Minecraft.getInstance().level.dimensionType().logicalHeight();
        this.heightContext = new WorldGenerationContext(generator, LevelHeightAccessor.create(bottom, height));

        for (PlacementModifier modifier : feature.placement()) {
            if (modifier instanceof CountPlacement) {
                this.count = ((CountPlacementModifierAccessor) modifier).getCount();

            } else if (modifier instanceof HeightRangePlacement) {
                this.heightProvider = ((HeightRangePlacementModifierAccessor) modifier).getHeight();

            } else if (modifier instanceof RarityFilter) {
                this.rarity = ((RarityFilterPlacementModifierAccessor) modifier).getChance();
            }
        }

        FeatureConfiguration featureConfig = feature.feature().value().config();

        if (featureConfig instanceof OreConfiguration oreFeatureConfig) {
            this.discardOnAirChance = oreFeatureConfig.discardChanceOnAirExposure;
            this.size = oreFeatureConfig.size;
        } else {
            throw new IllegalStateException("config for " + feature + "is not OreConfiguration.class");
        }

        if (feature.feature().value().feature() instanceof ScatteredOreFeature) {
            this.scattered = true;
        }
    }
}
