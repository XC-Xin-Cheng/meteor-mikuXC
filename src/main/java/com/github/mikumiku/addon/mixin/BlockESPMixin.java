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

import com.github.mikumiku.addon.util.MeteorXrayBridge;
import meteordevelopment.meteorclient.systems.modules.render.blockesp.ESPChunk;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * Mixin
 */
@Mixin(ESPChunk.class)
public abstract class BlockESPMixin {
    // 上游用 lombok @Slf4j，此处等价展开为显式字段
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(BlockESPMixin.class);


    @Inject(method = "searchChunk",
        at = @At("HEAD"), cancellable = true)
    private static void injectSearchChunk(ChunkAccess chunk, List<Block> blocks, CallbackInfoReturnable<ESPChunk> cir) {
        try {
            ESPChunk schunk = new ESPChunk(chunk.getPos().x(), chunk.getPos().z());
            if (schunk.shouldBeDeleted()) {
                cir.setReturnValue(schunk);
                return;
            }

            BlockPos.MutableBlockPos blockPos = new BlockPos.MutableBlockPos();
            BlockPos playerPos = mc.player.blockPosition();

            // 性能：1) blocks 转 HashSet，避免每个方块做一次线性查找；
            //       2) 高度图对象与区块边界提到循环外，避免每列重复查询；
            //       3) 水源距离改用整数平方距离，去掉每格两个 Vec3 的分配（与 distanceToSqr 等值）。
            Set<Block> blockSet = new HashSet<>(blocks);
            Heightmap surface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE);
            int minX = chunk.getPos().getMinBlockX();
            int minZ = chunk.getPos().getMinBlockZ();
            int maxX = chunk.getPos().getMaxBlockX();
            int maxZ = chunk.getPos().getMaxBlockZ();
            int minY = mc.level.getMinY();
            int px = playerPos.getX(), py = playerPos.getY(), pz = playerPos.getZ();

            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    int height = surface.getFirstAvailable(x - minX, z - minZ);

                    for (int y = minY; y < height; y++) {
                        blockPos.set(x, y, z);
                        BlockState bs = chunk.getBlockState(blockPos);
                        Block block = bs.getBlock();

                        if (blockSet.contains(block)) {
                            if (block instanceof LiquidBlock) {
                                Integer level = bs.getValue(LiquidBlock.LEVEL);
                                if (level != null && level == 0) {
                                    // 水源距离限制
                                    if (block == Blocks.WATER) {
                                        long dx = (long) x - px;
                                        long dy = (long) y - py;
                                        long dz = (long) z - pz;
                                        if (dx * dx + dy * dy + dz * dz > (64L * 64L)) continue; // 跳过渲染
                                    }
                                    schunk.add(blockPos, false);
                                }
                            } else {
                                schunk.add(blockPos, false);
                            }
                        }
                    }
                }
            }

            // 种子矿透同步过来的矿位也一并带上：本体重扫这个区块时不会把它们冲掉。
            MeteorXrayBridge.addOverlayTo(chunk.getPos(), schunk);

            cir.setReturnValue(schunk);
        } catch (Exception e) {
            log.error("Miku BlockESPMixin.injectSearchChunk err: " + e);
        }
    }
}
