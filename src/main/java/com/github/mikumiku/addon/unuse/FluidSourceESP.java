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
//package com.github.mikumiku.addon.modules;
//
//
//import com.github.mikumiku.addon.MikuMikuAddon;
//import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
//import meteordevelopment.meteorclient.events.render.Render3DEvent;
//import meteordevelopment.meteorclient.events.world.BlockUpdateEvent;
//import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
//import meteordevelopment.meteorclient.settings.ColorSetting;
//import meteordevelopment.meteorclient.settings.Setting;
//import meteordevelopment.meteorclient.settings.SettingGroup;
//import meteordevelopment.meteorclient.systems.modules.Categories;
//import meteordevelopment.meteorclient.systems.modules.Module;
//import meteordevelopment.meteorclient.utils.Utils;
//import meteordevelopment.meteorclient.utils.render.RenderUtils;
//import meteordevelopment.meteorclient.utils.render.color.SettingColor;
//import meteordevelopment.orbit.EventHandler;
//import net.minecraft.world.level.block.state.BlockState;
//import net.minecraft.world.level.block.Blocks;
//import net.minecraft.world.level.block.LiquidBlock;
//import net.minecraft.core.BlockPos;
//import net.minecraft.world.level.ChunkPos;
//import net.minecraft.world.level.chunk.ChunkAccess;
//
//import java.util.concurrent.ConcurrentLinkedQueue;
//import java.util.concurrent.ExecutorService;
//import java.util.concurrent.Executors;
//
//@Deprecated
//public class FluidSourceESP extends Module {
//    private final SettingGroup sgGeneral = settings.getDefaultGroup();
//
//    private final Setting<SettingColor> waterColor = sgGeneral.add(new ColorSetting.Builder()
//        .name("water-color")
//        .description("Color for water source blocks.")
//        .defaultValue(new SettingColor(0, 0, 255, 80))
//        .build()
//    );
//
//    private final Setting<SettingColor> lavaColor = sgGeneral.add(new ColorSetting.Builder()
//        .name("lava-color")
//        .description("Color for lava source blocks.")
//        .defaultValue(new SettingColor(255, 100, 0, 80))
//        .build()
//    );
//
//    private final ExecutorService workerThread = Executors.newSingleThreadExecutor();
//    private final Long2ObjectOpenHashMap<BlockPos[]> chunkSources = new Long2ObjectOpenHashMap<>();
//
//    public FluidSourceESP() {
//        super(MikuMikuAddon.CATEGORY, "fluid-source-esp", "Renders water and lava source blocks through walls.");
//    }
//
//    @Override
//    public void onActivate() {
//        chunkSources.clear();
//
//        for (Chunk chunk : Utils.chunks()) {
//            searchChunk(chunk);
//        }
//    }
//
//    @Override
//    public void onDeactivate() {
//        chunkSources.clear();
//    }
//
//    private void searchChunk(Chunk chunk) {
//        workerThread.submit(() -> {
//            if (!isActive()) return;
//
//            ChunkPos pos = chunk.getPos();
//            Long key = pos.toLong();
//
//            ConcurrentLinkedQueue<BlockPos> sources = new ConcurrentLinkedQueue<>();
//
//            for (int x = 0; x < 16; x++) {
//                for (int y = mc.world.getBottomY(); y < mc.world.getTopY(); y++) {
//                    for (int z = 0; z < 16; z++) {
//                        BlockPos bp = new BlockPos(pos.getStartX() + x, y, pos.getStartZ() + z);
//                        BlockState state = chunk.getBlockState(bp);
//
//                        if (state.getBlock() instanceof FluidBlock) {
//                            Integer level = state.get(FluidBlock.LEVEL);
//                            if (level != null && level == 0) {
//                                if (state.isOf(Blocks.WATER) || state.isOf(Blocks.LAVA)) {
//                                    sources.add(bp);
//                                }
//                            }
//                        }
//                    }
//                }
//            }
//
//            if (!sources.isEmpty()) {
//                chunkSources.put(key, sources.toArray(new BlockPos[0]));
//            }
//        });
//    }
//
//    @EventHandler
//    private void onChunkLoad(ChunkDataEvent event) {
//        searchChunk(event.chunk());
//    }
//
//    @EventHandler
//    private void onBlockUpdate(BlockUpdateEvent event) {
//        BlockPos pos = event.pos;
//        Chunk chunk = mc.world.getChunk(pos);
//        searchChunk(chunk);
//    }
//
//    @EventHandler
//    private void onRender(Render3DEvent event) {
//        synchronized (chunkSources) {
//            chunkSources.forEach((key, positions) -> {
//                for (BlockPos bp : positions) {
//                    BlockState state = mc.world.getBlockState(bp);
//                    if (!(state.getBlock() instanceof FluidBlock)) continue;
//
//                    Integer level = state.get(FluidBlock.LEVEL);
//                    if (level == null || level != 0) continue;
//
//                    if (state.isOf(Blocks.WATER)) {
//                        RenderUtils.drawItem(event, bp, waterColor.get(), true);
//                    }
//
//                    if (state.isOf(Blocks.LAVA)) {
//                        RenderUtils.drawBox(event, bp, lavaColor.get(), true);
//                    }
//                }
//            });
//        }
//    }
//}
