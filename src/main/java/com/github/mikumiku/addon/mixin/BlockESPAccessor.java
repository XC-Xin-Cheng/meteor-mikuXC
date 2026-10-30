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
package com.github.mikumiku.addon.mixin;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.systems.modules.render.blockesp.BlockESP;
import meteordevelopment.meteorclient.systems.modules.render.blockesp.ESPChunk;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/**
 * 借出 Meteor 本体 block-esp 的区块表与方块列表：
 * 前者用来把种子矿位直接塞进本体的渲染队列，后者用来把矿石加进本体的搜索/配色列表。
 * 两个字段在本体里都是私有的。
 */
@Mixin(BlockESP.class)
public interface BlockESPAccessor {
    @Accessor("chunks")
    Long2ObjectMap<ESPChunk> getMeteorMikuChunks();

    @Accessor("blocks")
    Setting<List<Block>> getMeteorMikuBlocks();
}
