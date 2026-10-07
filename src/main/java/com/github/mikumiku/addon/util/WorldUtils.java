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

import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class WorldUtils {
    public static List<BlockPos> getSphere(BlockPos centerPos, int radius, int height) {
        // 性能：i/j/k 三元组唯一，(i,j,k) 映射出的 BlockPos 必然不重复，
        // 原先的 blocks.contains(pos) 恒为 false 却让整体退化成 O(n^2)（每 tick 被 FarmHelper 等多次调用）。
        ArrayList<BlockPos> blocks = new ArrayList<>();
        int cx = centerPos.getX(), cy = centerPos.getY(), cz = centerPos.getZ();

        for (int i = cx - radius; i < cx + radius; i++) {
            for (int j = cy - height; j < cy + height; j++) {
                for (int k = cz - radius; k < cz + radius; k++) {
                    BlockPos pos = new BlockPos(i, j, k);
                    if (distanceBetween(centerPos, pos) <= radius) blocks.add(pos);
                }
            }
        }

        return blocks;
    }


    public static List<BlockPos> getSphere(double range) {
        Vec3 pos = Minecraft.getInstance().player.getEyePosition();

        List<BlockPos> list = new ArrayList<>();
        // 性能：用 HashSet 去重替代 list.contains 的线性扫描（AutoCrystal 每 tick 调用，候选数百个）。
        // 保留 distanceTo 的原始比较方式，保证筛选结果与顺序逐项不变。
        Set<BlockPos> seen = new HashSet<>();

        for (double x = pos.x() - range; x < pos.x() + range; x++) {
            for (double z = pos.z() - range; z < pos.z() + range; z++) {
                for (double y = pos.y() - range; y < pos.y() + range; y++) {
                    BlockPos blockPos = new BlockPos(Mth.floor(x), Mth.floor(y), Mth.floor(z));

                    if (!(Vec3.atCenterOf(blockPos).distanceTo(pos) > range) && seen.add(blockPos)) {
                        list.add(blockPos);
                    }
                }
            }
        }

        return list;
    }
    public static double distanceBetween(BlockPos pos1, BlockPos pos2) {
        double d = pos1.getX() - pos2.getX();
        double e = pos1.getY() - pos2.getY();
        double f = pos1.getZ() - pos2.getZ();
        return Mth.sqrt((float) (d * d + e * e + f * f));
    }

    public static boolean interact(BlockPos pos, FindItemResult findItemResult, boolean rotate) {
        if (!findItemResult.found()) return false;
        Runnable action = () -> {
            boolean wasSneaking = mc.player.isShiftKeyDown();
            mc.player.setShiftKeyDown(false);
            InvUtils.swap(findItemResult.slot(), true);
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.upFromBottomCenterOf(pos, 0), Direction.UP, pos, false));
            mc.player.swing(InteractionHand.MAIN_HAND);
            InvUtils.swapBack();
            mc.player.setShiftKeyDown(wasSneaking);
        };
        if (rotate) Rotations.rotate(Rotations.getYaw(pos), Rotations.getPitch(pos), -100, action);
        else action.run();
        return true;
    }
}
