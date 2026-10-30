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

import meteordevelopment.meteorclient.utils.world.BlockUtils;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.CartographyTableBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;

import static meteordevelopment.meteorclient.MeteorClient.mc;
import static meteordevelopment.meteorclient.utils.world.BlockUtils.isClickable;

@Mixin(value = BlockUtils.class, remap = false)
public class BlockUtilsMixin {
    @Inject(method = "isClickable", at = @At("HEAD"), cancellable = true)
    private static void injectedIsClickable(Block block, CallbackInfoReturnable<Boolean> cir) {
        if (block instanceof CartographyTableBlock) {
            cir.setReturnValue(true);
        }
    }

    //Fixing meteors garbo code
    @Inject(method = "getPlaceSide", at = @At("HEAD"), cancellable = true)
    private static void injectedGetPlaceSide(BlockPos blockPos, CallbackInfoReturnable<Direction> cir) {

        ArrayList<Direction> placeableDirections = new ArrayList<>();
        for (Direction side : Direction.values()) {
            BlockPos neighbor = blockPos.relative(side);
            BlockState state = mc.level.getBlockState(neighbor);

            // Check if neighbour isn't empty
            if (state.isAir() || isClickable(state.getBlock())) continue;

            // Check if neighbour is a fluid
            if (!state.getFluidState().isEmpty()) continue;
            placeableDirections.add(side);
        }

        //Get the direction the player is looking at
        Vec3 lookVec = Vec3.atCenterOf(blockPos).subtract(mc.player.getEyePosition());

        // 26.2 移除了 net.minecraft.util.Tuple，这里直接用打分循环，不再依赖任何原版元组类。
        // 分数越大表示玩家越朝着该方向看，只在有可放置邻居的方向里挑最高的那个。
        Direction best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (Direction side : Direction.values()) {
            if (!placeableDirections.contains(side)) continue;

            double score = switch (side) {
                case WEST -> -lookVec.x();
                case EAST -> lookVec.x();
                case DOWN -> -lookVec.y();
                case UP -> lookVec.y();
                case NORTH -> -lookVec.z();
                case SOUTH -> lookVec.z();
            };

            if (score > bestScore) {
                bestScore = score;
                best = side;
            }
        }

        cir.setReturnValue(best);
    }
}
