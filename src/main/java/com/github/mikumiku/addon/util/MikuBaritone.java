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

import net.minecraft.core.BlockPos;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Baritone 兼容桥。
 *
 * <p>本模组很多模块依赖 Baritone，但 Baritone 并不一定随游戏一起安装，
 * 或者当前游戏版本（例如 26.2）还没有对应的 Baritone。早期实现里
 * {@link MikuUtil} 直接 import 了 {@code baritone.api.pathing.goals.Goal} 等类，
 * 于是「加载 MikuUtil」这一步本身就会抛出
 * {@code NoClassDefFoundError: baritone/api/pathing/goals/Goal}，
 * 哪怕调用的只是 {@code isArmor()} 这种完全不需要 Baritone 的方法，
 * 结果就是开启「自动鞘翅切换」时整个游戏崩溃。</p>
 *
 * <p>这里把对 Baritone 的调用全部收拢到反射里：
 * <ul>
 *   <li>本类在类加载阶段不引用任何 Baritone 类型，因此没有装 Baritone 也能安全加载；</li>
 *   <li>Baritone 不存在时所有方法都安全地变成空操作，不会抛异常；</li>
 *   <li>Baritone 存在时行为与原来的直接调用完全一致。</li>
 * </ul>
 * </p>
 */
public final class MikuBaritone {
    private MikuBaritone() {
    }

    private static final String CLASS_BARITONE_API = "baritone.api.BaritoneAPI";
    private static final String CLASS_PROVIDER = "baritone.api.IBaritoneProvider";
    private static final String CLASS_BARITONE = "baritone.api.IBaritone";
    private static final String CLASS_PATHING_BEHAVIOR = "baritone.api.behavior.IPathingBehavior";
    private static final String CLASS_CUSTOM_GOAL_PROCESS = "baritone.api.process.ICustomGoalProcess";
    private static final String CLASS_GOAL = "baritone.api.pathing.goals.Goal";
    private static final String CLASS_GOAL_NEAR = "baritone.api.pathing.goals.GoalNear";
    private static final String CLASS_GOAL_BLOCK = "baritone.api.pathing.goals.GoalBlock";
    private static final String CLASS_SETTINGS = "baritone.api.Settings";
    private static final String CLASS_SETTING = "baritone.api.Settings$Setting";

    private static boolean initDone;
    private static boolean available;

    private static Method getProvider;
    private static Method getSettings;
    private static Method getPrimaryBaritone;
    private static Method getPathingBehavior;
    private static Method getCustomGoalProcess;
    private static Method cancelEverything;
    private static Method isPathing;
    private static Method setGoalAndPath;
    private static Constructor<?> goalNearConstructor;
    private static Constructor<?> goalBlockConstructor;
    private static Field allowBreakField;
    private static Field allowPlaceField;
    private static Field settingValueField;

    private static synchronized void init() {
        if (initDone) {
            return;
        }
        initDone = true;

        try {
            ClassLoader loader = MikuBaritone.class.getClassLoader();
            Class<?> api = Class.forName(CLASS_BARITONE_API, false, loader);
            Class<?> provider = Class.forName(CLASS_PROVIDER, false, loader);
            Class<?> baritone = Class.forName(CLASS_BARITONE, false, loader);
            Class<?> pathing = Class.forName(CLASS_PATHING_BEHAVIOR, false, loader);
            Class<?> customGoal = Class.forName(CLASS_CUSTOM_GOAL_PROCESS, false, loader);
            Class<?> goal = Class.forName(CLASS_GOAL, false, loader);
            Class<?> goalNear = Class.forName(CLASS_GOAL_NEAR, false, loader);
            Class<?> goalBlock = Class.forName(CLASS_GOAL_BLOCK, false, loader);
            Class<?> settings = Class.forName(CLASS_SETTINGS, false, loader);
            Class<?> setting = Class.forName(CLASS_SETTING, false, loader);

            getProvider = api.getMethod("getProvider");
            getSettings = api.getMethod("getSettings");
            getPrimaryBaritone = provider.getMethod("getPrimaryBaritone");
            getPathingBehavior = baritone.getMethod("getPathingBehavior");
            getCustomGoalProcess = baritone.getMethod("getCustomGoalProcess");
            cancelEverything = pathing.getMethod("cancelEverything");
            isPathing = pathing.getMethod("isPathing");
            setGoalAndPath = customGoal.getMethod("setGoalAndPath", goal);
            goalNearConstructor = goalNear.getConstructor(BlockPos.class, int.class);
            goalBlockConstructor = goalBlock.getConstructor(BlockPos.class);
            allowBreakField = settings.getField("allowBreak");
            allowPlaceField = settings.getField("allowPlace");
            settingValueField = setting.getField("value");

            available = true;
        } catch (Throwable ignored) {
            // Baritone 不存在或版本不匹配：保持不可用，所有调用退化为空操作。
            available = false;
        }
    }

    /** 当前环境下 Baritone 是否可用。 */
    public static boolean isAvailable() {
        init();
        return available;
    }

    private static Object primaryBaritone() throws Exception {
        Object provider = getProvider.invoke(null);
        return getPrimaryBaritone.invoke(provider);
    }

    /** 取消 Baritone 当前的一切操作；不可用时无操作。 */
    public static void cancelEverything() {
        if (!isAvailable()) {
            return;
        }
        try {
            Object baritone = primaryBaritone();
            cancelEverything.invoke(getPathingBehavior.invoke(baritone));
        } catch (Throwable ignored) {
        }
    }

    /** Baritone 是否正在寻路；不可用时返回 false。 */
    public static boolean isPathing() {
        if (!isAvailable()) {
            return false;
        }
        try {
            Object baritone = primaryBaritone();
            return Boolean.TRUE.equals(isPathing.invoke(getPathingBehavior.invoke(baritone)));
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * 让 Baritone 走到指定坐标附近。distance 为可接受的靠近距离，
     * 小于等于 0 时使用精确坐标 {@code GoalBlock}，否则使用 {@code GoalNear}。
     */
    public static void setGoalAndPath(BlockPos pos, double distance) {
        if (!isAvailable()) {
            return;
        }
        try {
            Object baritone = primaryBaritone();
            Object goal = distance <= 0
                ? goalBlockConstructor.newInstance(pos)
                : goalNearConstructor.newInstance(pos, (int) distance - 1);
            setGoalAndPath.invoke(getCustomGoalProcess.invoke(baritone), goal);
        } catch (Throwable ignored) {
        }
    }

    /** 设置 Baritone 是否允许破坏 / 放置方块；不可用时无操作。 */
    public static void setAllowBreakAndPlace(boolean value) {
        if (!isAvailable()) {
            return;
        }
        try {
            Object settings = getSettings.invoke(null);
            settingValueField.set(allowBreakField.get(settings), value);
            settingValueField.set(allowPlaceField.get(settings), value);
        } catch (Throwable ignored) {
        }
    }
}
