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
//import meteordevelopment.meteorclient.events.render.Render3DEvent;
//import meteordevelopment.meteorclient.settings.ColorSetting;
//import meteordevelopment.meteorclient.systems.modules.Module;
//import meteordevelopment.meteorclient.utils.render.RenderUtils;
//import meteordevelopment.meteorclient.utils.render.color.Color;
//import net.minecraft.world.level.block.state.BlockState;
//import net.minecraft.world.level.block.Blocks;
//import net.minecraft.world.level.block.LiquidBlock;
//import net.minecraft.core.BlockPos;
//
//@Deprecated
//public class WaterSourceESP extends Module {
//    private final ColorSetting waterColor = new ColorSetting.Builder()
//        .name("water-color")
//        .description("Color for water source blocks.")
//        .defaultValue(new Color(0, 0, 255, 50))
//        .build();
//
//    private final ColorSetting lavaColor = new ColorSetting.Builder()
//        .name("lava-color")
//        .description("Color for lava source blocks.")
//        .defaultValue(new Color(255, 100, 0, 50))
//        .build();
//
//    public WaterSourceESP() {
//        super(MikuMikuAddon.CATEGORY, "water-esp", "Renders water and lava source blocks.");
//    }
//
//    @Override
//    public void onRender3D(Render3DEvent event) {
//        // 循环渲染玩家周围范围
//        BlockPos playerPos = mc.player.getBlockPos();
//        int range = 10; // 自定义渲染范围
//
//        for (int x = -range; x <= range; x++) {
//            for (int y = -range; y <= range; y++) {
//                for (int z = -range; z <= range; z++) {
//                    BlockPos pos = playerPos.add(x, y, z);
//                    BlockState state = mc.world.getBlockState(pos);
//
//                    // 判断是否为 FluidBlock
//                    if (state.getBlock() instanceof FluidBlock) {
//                        Integer level = state.get(FluidBlock.LEVEL);
//                        if (level != null && level == 0) {
//                            // 判断水源
//                            if (state.isOf(Blocks.WATER)) {
//                                RenderUtils.drawBoxOutline(pos, waterColor.get(), 1);
//                            }
//
//                            // 判断岩浆源
//                            if (state.isOf(Blocks.LAVA)) {
//                                RenderUtils.drawBoxOutline(pos, lavaColor.get(), 1);
//                            }
//                        }
//                    }
//                }
//            }
//        }
//    }
//}
