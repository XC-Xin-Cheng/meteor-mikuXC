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

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.Arrays;
import java.util.List;

public interface MikuConstant {

    List<Block> FUC_BLOCKS = Arrays.asList(
        // 容器类
        Blocks.CHEST, Blocks.TRAPPED_CHEST, Blocks.ENDER_CHEST, Blocks.BARREL,
        Blocks.HOPPER, Blocks.DROPPER, Blocks.DISPENSER,
        MikuBlocks.get("white_shulker_box"), MikuBlocks.get("orange_shulker_box"), MikuBlocks.get("magenta_shulker_box"),
        MikuBlocks.get("light_blue_shulker_box"), MikuBlocks.get("yellow_shulker_box"), MikuBlocks.get("lime_shulker_box"),
        MikuBlocks.get("pink_shulker_box"), MikuBlocks.get("gray_shulker_box"), MikuBlocks.get("cyan_shulker_box"),
        MikuBlocks.get("purple_shulker_box"), MikuBlocks.get("blue_shulker_box"), MikuBlocks.get("brown_shulker_box"),
        MikuBlocks.get("green_shulker_box"), MikuBlocks.get("red_shulker_box"), MikuBlocks.get("black_shulker_box"),

        // 功能方块
        Blocks.CRAFTING_TABLE, Blocks.ENCHANTING_TABLE, Blocks.ANVIL,
        Blocks.CHIPPED_ANVIL, Blocks.DAMAGED_ANVIL,
        Blocks.BREWING_STAND, Blocks.FURNACE, Blocks.BLAST_FURNACE, Blocks.SMOKER,
        Blocks.STONECUTTER, Blocks.GRINDSTONE, Blocks.LOOM, Blocks.SMITHING_TABLE,
        Blocks.COMPOSTER, Blocks.BEACON, Blocks.LECTERN, Blocks.BELL,

        // 红石交互
        Blocks.LEVER,
        Blocks.STONE_BUTTON, Blocks.OAK_BUTTON, Blocks.SPRUCE_BUTTON, Blocks.BIRCH_BUTTON,
        Blocks.JUNGLE_BUTTON, Blocks.ACACIA_BUTTON, Blocks.DARK_OAK_BUTTON,
        Blocks.MANGROVE_BUTTON, Blocks.CHERRY_BUTTON, Blocks.BAMBOO_BUTTON,
        Blocks.WARPED_BUTTON, Blocks.CRIMSON_BUTTON,
        Blocks.NOTE_BLOCK,

        // 门与活板门类
        Blocks.OAK_DOOR, Blocks.SPRUCE_DOOR, Blocks.BIRCH_DOOR, Blocks.JUNGLE_DOOR,
        Blocks.ACACIA_DOOR, Blocks.DARK_OAK_DOOR, Blocks.MANGROVE_DOOR,
        Blocks.CHERRY_DOOR, Blocks.BAMBOO_DOOR, Blocks.WARPED_DOOR, Blocks.CRIMSON_DOOR,
        Blocks.OAK_TRAPDOOR, Blocks.SPRUCE_TRAPDOOR, Blocks.BIRCH_TRAPDOOR,
        Blocks.JUNGLE_TRAPDOOR, Blocks.ACACIA_TRAPDOOR, Blocks.DARK_OAK_TRAPDOOR,
        Blocks.MANGROVE_TRAPDOOR, Blocks.CHERRY_TRAPDOOR, Blocks.BAMBOO_TRAPDOOR,
        Blocks.WARPED_TRAPDOOR, Blocks.CRIMSON_TRAPDOOR,

        // 建筑/杂项
        Blocks.SCAFFOLDING,
        Blocks.OAK_SIGN, Blocks.OAK_WALL_SIGN,
        Blocks.OAK_HANGING_SIGN, Blocks.OAK_WALL_HANGING_SIGN
    );

}
