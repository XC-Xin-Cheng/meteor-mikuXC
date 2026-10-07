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

import com.github.mikumiku.addon.mixin.BlockESPAccessor;
import com.github.mikumiku.addon.mixin.XrayAccessor;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.Xray;
import meteordevelopment.meteorclient.systems.modules.render.blockesp.BlockESP;
import meteordevelopment.meteorclient.systems.modules.render.blockesp.ESPChunk;
import meteordevelopment.meteorclient.utils.world.Dimension;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 把「种子矿透」算出来的矿位喂给 Meteor 本体，让本体的透视功能也能画出来。
 *
 * <p>服务器开了反矿透（把矿石在发包时换成石头）时，客户端世界里根本没有矿石方块，
 * 本体 Xray / block-esp 自然什么都找不到。这里做的是渲染层的桥接，不去改客户端区块数据：
 *
 * <ul>
 *   <li><b>位置叠加层</b>：种子算出的矿位与它对应的矿石方块状态放在一张只读快照表里，
 *       {@code ESPBlock} 读取方块状态时优先用它，于是本体的 block-esp 会用矿石去画框、
 *       按矿石连线、按矿石取颜色，而不是把那个位置当成石头。</li>
 *   <li><b>注入 block-esp</b>：把矿位塞进本体 block-esp 的区块表，由本体的渲染器绘制。</li>
 *   <li><b>Xray 白名单</b>：把选中的矿石加进本体 Xray 的白名单，免得本体 Xray 把它们一起透明掉。</li>
 * </ul>
 *
 * <p>并发：叠加层用「整表构建完成后一次 volatile 赋值」的方式发布，读的时候不加锁；
 * 只有注入本体 block-esp 的区块表时才会去抢它自己的监视器锁，和本体保持同一把锁。
 */
public final class MeteorXrayBridge {

    /** 位置 → 该位置应当被当成哪种矿石方块。发布后不再修改，读侧无锁。 */
    private static volatile Long2ObjectMap<BlockState> overrides = Long2ObjectMaps.emptyMap();

    /** 区块 → 该区块里被同步的矿位，供 block-esp 扫描时补齐。同上，发布后只读。 */
    private static volatile Long2ObjectMap<LongOpenHashSet> byChunk = Long2ObjectMaps.emptyMap();

    /** 本轮同步想要的矿位集合，用于判断注入进本体里的位置是否已经过期。 */
    private static volatile LongOpenHashSet wanted = new LongOpenHashSet();

    /**
     * 上一次同步时希望出现在本体 block-esp 里的矿位。
     *
     * <p>清理不能只看 {@link #injectedPositions}：本体重扫区块时会走 {@code addOverlayTo}
     * 把叠加层矿位补进去，那条路不记账，于是被挖走后会一直留在框里。按上一轮的矿位表来清，
     * 两种来源都盖得住。
     */
    private static volatile LongOpenHashSet previousOverlay = new LongOpenHashSet();

    /** 我们注入进本体 block-esp 的矿位，退出/关闭时按它清理。 */
    private static final LongOpenHashSet injectedPositions = new LongOpenHashSet();

    /** 我们加进本体设置里、需要还原的条目（用户自己加的不会被记进来）。 */
    private static final Set<Block> addedEspBlocks = new HashSet<>();
    private static final Set<Block> addedXrayBlocks = new HashSet<>();

    private MeteorXrayBridge() {
    }

    // ====================================
    // 给 mixin 用的只读查询
    // ====================================

    /**
     * 这个坐标是否应当被当成种子算出的矿石。没有就返回 null，调用方照旧读世界。
     * 热路径，刻意不产生任何对象。
     */
    public static BlockState stateAt(int x, int y, int z) {
        return overrides.get(BlockPos.asLong(x, y, z));
    }

    /**
     * 本体 block-esp 扫描某个区块时，把该区块里的种子矿位一并补进去。
     * 这样即使本体稍后重新扫描覆盖了区块，矿位也不会丢。
     */
    public static void addOverlayTo(ChunkPos chunkPos, ESPChunk chunk) {
        LongOpenHashSet positions = byChunk.get(chunkPos.pack());
        if (positions == null || positions.isEmpty()) return;

        LongIterator it = positions.iterator();
        while (it.hasNext()) {
            long packed = it.nextLong();
            int x = BlockPos.getX(packed);
            int y = BlockPos.getY(packed);
            int z = BlockPos.getZ(packed);
            // 本体自己扫到的方块不要覆盖，否则它在分组里会留下一个悬空的旧框。
            if (chunk.get(x, y, z) == null) chunk.add(new BlockPos(x, y, z));
        }
    }

    // ====================================
    // 同步
    // ====================================

    /**
     * 用当前这批种子矿位重建叠加层，并按开关同步到本体。
     *
     * @param selected       用户勾选的「矿石 + 层」
     * @param dimension      当前算矿位用的维度
     * @param chunkRenderers 种子矿透算好的区块矿位
     * @param blockEsp       是否同步到本体 block-esp
     * @param xray           是否同步到本体 Xray 白名单
     */
    public static void sync(Set<Ore.OreKey> selected, Dimension dimension,
                            Map<Long, Map<Ore, Set<Vec3>>> chunkRenderers,
                            boolean blockEsp, boolean xray) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || selected == null || chunkRenderers == null) return;

        Long2ObjectMap<BlockState> states = new Long2ObjectOpenHashMap<>();
        Long2ObjectMap<LongOpenHashSet> chunks = new Long2ObjectOpenHashMap<>();
        LongOpenHashSet wantedPositions = new LongOpenHashSet();
        Set<Block> oreBlocks = new HashSet<>();

        for (Map.Entry<Long, Map<Ore, Set<Vec3>>> chunkEntry : chunkRenderers.entrySet()) {
            long chunkKey = chunkEntry.getKey();
            Map<Ore, Set<Vec3>> ores = chunkEntry.getValue();
            if (ores == null) continue;

            for (Map.Entry<Ore, Set<Vec3>> oreEntry : ores.entrySet()) {
                Ore.OreType type = oreEntry.getKey().type;
                for (Vec3 pos : oreEntry.getValue()) {
                    Ore.OreLayer layer = layerOf(dimension, pos.y);
                    if (!selected.contains(Ore.OreKey.of(type, layer))) continue;

                    Block block = blockOf(type, layer);
                    if (block == null) continue;

                    long packed = BlockPos.asLong((int) pos.x, (int) pos.y, (int) pos.z);
                    states.put(packed, block.defaultBlockState());
                    wantedPositions.add(packed);

                    LongOpenHashSet chunkPositions = chunks.get(chunkKey);
                    if (chunkPositions == null) {
                        chunkPositions = new LongOpenHashSet();
                        chunks.put(chunkKey, chunkPositions);
                    }
                    chunkPositions.add(packed);

                    oreBlocks.add(block);
                }
            }
        }

        overrides = states;
        byChunk = chunks;
        wanted = wantedPositions;

        if (blockEsp) injectBlockEsp(oreBlocks);
        else {
            // 关掉方块透视同步：把上一轮放进本体的矿位都收回来，包括本体重扫区块时
            // 通过叠加层补进去、没有记在 injectedPositions 里的那些。
            removeInjectedBlockEsp();
            removeOverlayFromBlockEsp(previousOverlay);
            restoreEspBlocks();
        }

        // 记下这一轮的矿位表，下一轮据此清掉被挖走 / 取消勾选的。
        previousOverlay = wantedPositions;

        if (xray) applyXrayWhitelist(oreBlocks);
        else restoreXrayBlocks();
    }

    /** 关闭联动：清空叠加层，并把注入到本体里的东西收回来。 */
    public static void disable() {
        overrides = Long2ObjectMaps.emptyMap();
        byChunk = Long2ObjectMaps.emptyMap();
        wanted = new LongOpenHashSet();

        removeInjectedBlockEsp();
        removeOverlayFromBlockEsp(previousOverlay);
        previousOverlay = new LongOpenHashSet();
        restoreEspBlocks();
        restoreXrayBlocks();
    }

    // ====================================
    // block-esp
    // ====================================

    private static void injectBlockEsp(Set<Block> oreBlocks) {
        BlockESP blockEsp = Modules.get() == null ? null : Modules.get().get(BlockESP.class);
        if (blockEsp == null) return;

        // 把矿石加进本体的方块列表：一来本体才会去扫这些方块，二来用户能在本体界面里
        // 给每种矿石单独配颜色/形状/连线。只记我们自己加的，退出时只删这些。
        Setting<List<Block>> blocksSetting = ((BlockESPAccessor) blockEsp).getMeteorMikuBlocks();
        List<Block> blockList = blocksSetting.get();
        boolean listChanged = false;
        for (Block block : oreBlocks) {
            if (!blockList.contains(block)) {
                blockList.add(block);
                addedEspBlocks.add(block);
                listChanged = true;
            }
        }
        // 列表变了才让本体重扫一次，避免每轮同步都触发整片重扫。
        if (listChanged && blockEsp.isActive()) {
            blockEsp.onActivate();
            // onActivate 会把本体的区块表清空，之前注入的位置也一起没了，记账要跟着清，
            // 否则下面会以为它们还在，不再补进去。
            injectedPositions.clear();
        }

        if (!blockEsp.isActive()) return;

        Long2ObjectMap<ESPChunk> chunkMap = ((BlockESPAccessor) blockEsp).getMeteorMikuChunks();
        synchronized (chunkMap) {
            // 1) 先清掉这一轮不需要了的叠加层矿位：上一轮同步过、这一轮不再想要的，
            //    加上记账里剩下的。按上一轮的矿位表来清，才盖得住本体重扫区块时
            //    通过 addOverlayTo 补进去、没有被 injectedPositions 记账的位置。
            LongOpenHashSet stale = new LongOpenHashSet();
            LongIterator prevIt = previousOverlay.iterator();
            while (prevIt.hasNext()) {
                long packed = prevIt.nextLong();
                if (!wanted.contains(packed)) stale.add(packed);
            }
            LongIterator injectedIt = injectedPositions.iterator();
            while (injectedIt.hasNext()) {
                long packed = injectedIt.nextLong();
                if (!wanted.contains(packed)) stale.add(packed);
            }
            LongIterator staleIt = stale.iterator();
            while (staleIt.hasNext()) {
                long packed = staleIt.nextLong();
                injectedPositions.remove(packed);
                removeFromChunk(chunkMap, packed);
            }

            // 2) 再把这一轮的矿位补进去。
            for (Map.Entry<Long, LongOpenHashSet> entry : byChunk.entrySet()) {
                long chunkKey = entry.getKey();
                LongOpenHashSet positions = entry.getValue();
                if (positions.isEmpty()) continue;

                ESPChunk chunk = chunkMap.get(chunkKey);
                if (chunk == null) {
                    chunk = new ESPChunk(ChunkPos.getX(chunkKey), ChunkPos.getZ(chunkKey));
                    if (chunk.shouldBeDeleted()) continue;
                    chunkMap.put(chunkKey, chunk);
                }

                LongIterator positionsIt = positions.iterator();
                while (positionsIt.hasNext()) {
                    long packed = positionsIt.nextLong();
                    if (injectedPositions.contains(packed)) continue;

                    int x = BlockPos.getX(packed);
                    int y = BlockPos.getY(packed);
                    int z = BlockPos.getZ(packed);
                    // 本体自己已经扫到同一格就跳过，省得覆盖它、把旧框留在分组里。
                    if (chunk.get(x, y, z) != null) continue;

                    chunk.add(new BlockPos(x, y, z));
                    injectedPositions.add(packed);
                }
            }
        }
    }

    private static void removeInjectedBlockEsp() {
        if (injectedPositions.isEmpty()) return;

        BlockESP blockEsp = Modules.get() == null ? null : Modules.get().get(BlockESP.class);
        if (blockEsp == null) {
            injectedPositions.clear();
            return;
        }

        Long2ObjectMap<ESPChunk> chunkMap = ((BlockESPAccessor) blockEsp).getMeteorMikuChunks();
        synchronized (chunkMap) {
            LongIterator it = injectedPositions.iterator();
            while (it.hasNext()) removeFromChunk(chunkMap, it.nextLong());
        }
        injectedPositions.clear();
    }

    /**
     * 把一批叠加层矿位从本体 block-esp 的区块表里去掉。用于关掉方块透视同步或整个联动时：
     * 这时不看 {@code wanted}，上一轮放进去的全部收回，免得本体界面里还留着种子矿透的框。
     */
    private static void removeOverlayFromBlockEsp(LongOpenHashSet positions) {
        if (positions.isEmpty()) return;

        BlockESP blockEsp = Modules.get() == null ? null : Modules.get().get(BlockESP.class);
        if (blockEsp == null || !blockEsp.isActive()) return;

        Long2ObjectMap<ESPChunk> chunkMap = ((BlockESPAccessor) blockEsp).getMeteorMikuChunks();
        synchronized (chunkMap) {
            LongIterator it = positions.iterator();
            while (it.hasNext()) removeFromChunk(chunkMap, it.nextLong());
        }
    }

    private static void removeFromChunk(Long2ObjectMap<ESPChunk> chunkMap, long packed) {
        int x = BlockPos.getX(packed);
        int y = BlockPos.getY(packed);
        int z = BlockPos.getZ(packed);
        ESPChunk chunk = chunkMap.get(ChunkPos.pack(x >> 4, z >> 4));
        if (chunk != null) chunk.remove(new BlockPos(x, y, z));
    }

    private static void restoreEspBlocks() {
        if (addedEspBlocks.isEmpty()) return;

        BlockESP blockEsp = Modules.get() == null ? null : Modules.get().get(BlockESP.class);
        if (blockEsp != null) {
            List<Block> blockList = ((BlockESPAccessor) blockEsp).getMeteorMikuBlocks().get();
            blockList.removeAll(addedEspBlocks);
        }
        addedEspBlocks.clear();
    }

    // ====================================
    // Xray 白名单
    // ====================================

    private static void applyXrayWhitelist(Set<Block> oreBlocks) {
        Xray xray = Modules.get() == null ? null : Modules.get().get(Xray.class);
        if (xray == null) return;

        List<Block> whitelist = ((XrayAccessor) xray).getMeteorMikuBlocks().get();
        boolean changed = false;
        for (Block block : oreBlocks) {
            if (!whitelist.contains(block)) {
                whitelist.add(block);
                addedXrayBlocks.add(block);
                changed = true;
            }
        }
        // 白名单改了要让它重建区块渲染，否则要等下次进世界才生效。
        if (changed && xray.isActive() && Minecraft.getInstance().levelRenderer != null) {
            MikuCompat.reloadRenderer();
        }
    }

    private static void restoreXrayBlocks() {
        if (addedXrayBlocks.isEmpty()) return;

        Xray xray = Modules.get() == null ? null : Modules.get().get(Xray.class);
        if (xray != null) {
            List<Block> whitelist = ((XrayAccessor) xray).getMeteorMikuBlocks().get();
            whitelist.removeAll(addedXrayBlocks);
        }
        addedXrayBlocks.clear();
    }

    // ====================================
    // 矿石 → 方块
    // ====================================

    /** 与「矿石」选择器同一套深浅层规则：下界是下界岩变种，主世界按 y 分浅层/深层。 */
    public static Ore.OreLayer layerOf(Dimension dimension, double y) {
        if (dimension == Dimension.Nether) return Ore.OreLayer.NETHER;
        return y < 0 ? Ore.OreLayer.DEEP : Ore.OreLayer.SHALLOW;
    }

    /** 「矿石 + 层」对应的真实方块；该层不该出现这种矿石时返回 null。 */
    public static Block blockOf(Ore.OreType type, Ore.OreLayer layer) {
        return switch (layer) {
            case NETHER -> switch (type) {
                case GOLD -> Blocks.NETHER_GOLD_ORE;
                case QUARTZ -> Blocks.NETHER_QUARTZ_ORE;
                case DEBRIS -> Blocks.ANCIENT_DEBRIS;
                default -> null;
            };
            case SHALLOW -> switch (type) {
                case COAL -> Blocks.COAL_ORE;
                case IRON -> Blocks.IRON_ORE;
                case COPPER -> Blocks.COPPER_ORE;
                case EMERALD -> Blocks.EMERALD_ORE;
                case GOLD -> Blocks.GOLD_ORE;
                case REDSTONE -> Blocks.REDSTONE_ORE;
                case LAPIS -> Blocks.LAPIS_ORE;
                case DIAMOND -> Blocks.DIAMOND_ORE;
                default -> null;
            };
            case DEEP -> switch (type) {
                case COAL -> Blocks.DEEPSLATE_COAL_ORE;
                case IRON -> Blocks.DEEPSLATE_IRON_ORE;
                case COPPER -> Blocks.DEEPSLATE_COPPER_ORE;
                case EMERALD -> Blocks.DEEPSLATE_EMERALD_ORE;
                case GOLD -> Blocks.DEEPSLATE_GOLD_ORE;
                case REDSTONE -> Blocks.DEEPSLATE_REDSTONE_ORE;
                case LAPIS -> Blocks.DEEPSLATE_LAPIS_ORE;
                case DIAMOND -> Blocks.DEEPSLATE_DIAMOND_ORE;
                default -> null;
            };
        };
    }
}
