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
package com.github.mikumiku.addon.mixinface;

import com.github.mikumiku.addon.modules.ElytraFlyPlusPlus;
import com.github.mikumiku.addon.modules.FakeCoordinates;
import com.github.mikumiku.addon.modules.SeedMine;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

public class MagicMix {

    // 静态变量用于存储偏移后的坐标
    public static double x = 0;
    public static double z = 0;

    public static List<BlockPos> oreGoals = new ArrayList<>();

    // 性能：模块实例在 addon 初始化后终生不变，缓存引用即可。
    // 这三个方法位于 getPose/isSprinting/push/isFallFlying 等每帧每实体都会命中的热路径上，
    // 原先每次都走 Modules.get().get(Class) 的哈希查找；缓存后降为一次字段读取。
    // 尚未注册时保持 null 并每次重试，不会把“暂时查不到”固化成永久 false。
    private static volatile ElytraFlyPlusPlus cachedElytraFly;
    private static volatile FakeCoordinates cachedFakeCoordinates;
    private static volatile SeedMine cachedSeedMine;

    public static boolean eflyenabled() {
        ElytraFlyPlusPlus efly = cachedElytraFly;
        if (efly == null) {
            Modules modules = Modules.get();
            if (modules == null) return false;
            efly = modules.get(ElytraFlyPlusPlus.class);
            if (efly == null) return false;
            cachedElytraFly = efly;
        }
        return efly.enabled();
    }

    public static boolean coordinatesisActive() {
        FakeCoordinates coordinates = cachedFakeCoordinates;
        if (coordinates == null) {
            Modules modules = Modules.get();
            if (modules == null) return false;
            coordinates = modules.get(FakeCoordinates.class);
            if (coordinates == null) return false;
            cachedFakeCoordinates = coordinates;
        }
        return coordinates.isActive();
    }

    public static boolean oreSimBaritone() {
        SeedMine oreSim = cachedSeedMine;
        if (oreSim == null) {
            Modules modules = Modules.get();
            if (modules == null) return false;
            oreSim = modules.get(SeedMine.class);
            if (oreSim == null) return false;
            cachedSeedMine = oreSim;
        }
        return oreSim.baritone();
    }
}
