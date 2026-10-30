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

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.BlockDestructionProgress;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.Set;

/**
 * 跨版本读取“某个方块正在被挖掘”的进度。
 *
 * <p>26.1 这份数据在 {@code LevelRenderer.destroyingBlocks}；26.2 把它搬到了
 * {@code ClientLevel}（私有字段 {@code destroyingBlocks} + 公开方法
 * {@code destructionProgress()}）。用 @Accessor 硬绑字段名会在另一个版本上直接
 * 抛 InvalidAccessorException 让游戏起不来，所以这里只用反射按“类型”查找，
 * 不写死字段名：先试 {@code LevelRenderer}，找不到再退到 {@code ClientLevel}。
 * 两个版本都找不到（未来又搬家了）时返回 false，只是这一项判定失效，绝不崩游戏。
 */
public final class DestructionProgressHelper {
    private static boolean triedLevelRenderer;

    /** LevelRenderer 里那个 Int2ObjectMap&lt;BlockDestructionProgress&gt; 字段；26.2 起为 null。 */
    private static Field levelRendererBreakingField;

    private static boolean triedClientLevel;

    /** ClientLevel 里返回“被挖掘方块”集合的方法；26.1 里为 null。 */
    private static Method clientLevelDestructionMethod;

    private DestructionProgressHelper() {
    }

    /** 给定的方块坐标现在是否正被挖掘。 */
    public static boolean isBeingMined(BlockPos pos) {
        if (pos == null) {
            return false;
        }

        Int2ObjectMap<?> breaking = levelRendererBreakingBlocks();
        if (breaking != null) {
            for (Object value : breaking.values()) {
                if (value instanceof BlockDestructionProgress progress && pos.equals(progress.getPos())) {
                    return true;
                }
            }

            return false;
        }

        Long2ObjectMap<?> destruction = clientLevelDestructionProgress();
        if (destruction != null) {
            for (Object value : destruction.values()) {
                if (!(value instanceof Set<?> progresses) || progresses.isEmpty()) {
                    continue;
                }

                for (Object entry : progresses) {
                    if (entry instanceof BlockDestructionProgress progress && pos.equals(progress.getPos())) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /** 26.1 路径：LevelRenderer.destroyingBlocks。 */
    private static Int2ObjectMap<?> levelRendererBreakingBlocks() {
        Minecraft mc = Minecraft.getInstance();
        Object levelRenderer = mc == null ? null : mc.levelRenderer;
        if (levelRenderer == null) {
            return null;
        }

        if (!triedLevelRenderer) {
            triedLevelRenderer = true;
            levelRendererBreakingField = findField(levelRenderer.getClass(), Int2ObjectMap.class, BlockDestructionProgress.class);
        }

        if (levelRendererBreakingField == null) {
            return null;
        }

        try {
            return (Int2ObjectMap<?>) levelRendererBreakingField.get(levelRenderer);
        } catch (ReflectiveOperationException | RuntimeException e) {
            levelRendererBreakingField = null;
            return null;
        }
    }

    /** 26.2 路径：ClientLevel 里返回被挖掘方块集合的方法。 */
    private static Long2ObjectMap<?> clientLevelDestructionProgress() {
        Minecraft mc = Minecraft.getInstance();
        Object level = mc == null ? null : mc.level;
        if (level == null) {
            return null;
        }

        if (!triedClientLevel) {
            triedClientLevel = true;
            clientLevelDestructionMethod = findDestructionMethod(level.getClass());
        }

        if (clientLevelDestructionMethod == null) {
            return null;
        }

        try {
            return (Long2ObjectMap<?>) clientLevelDestructionMethod.invoke(level);
        } catch (ReflectiveOperationException | RuntimeException e) {
            clientLevelDestructionMethod = null;
            return null;
        }
    }

    /** 按泛型实参匹配字段，避免把名字写死。 */
    private static Field findField(Class<?> owner, Class<?> rawType, Class<?> valueType) {
        for (Class<?> current = owner; current != null; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (!rawType.isAssignableFrom(field.getType())) {
                    continue;
                }

                if (!hasTypeArgument(field.getGenericType(), valueType)) {
                    continue;
                }

                field.setAccessible(true);
                return field;
            }
        }

        return null;
    }

    /** 按返回类型 + 泛型实参匹配公开方法。 */
    private static Method findDestructionMethod(Class<?> owner) {
        for (Class<?> current = owner; current != null; current = current.getSuperclass()) {
            for (Method method : current.getMethods()) {
                if (method.isSynthetic() || method.getParameterCount() != 0) {
                    continue;
                }

                if (!Map.class.isAssignableFrom(method.getReturnType())) {
                    continue;
                }

                if (!hasTypeArgument(method.getGenericReturnType(), BlockDestructionProgress.class)) {
                    continue;
                }

                return method;
            }
        }

        return null;
    }

    private static boolean hasTypeArgument(Type type, Class<?> wanted) {
        if (!(type instanceof java.lang.reflect.ParameterizedType parameterized)) {
            return false;
        }

        for (Type argument : parameterized.getActualTypeArguments()) {
            if (argument == wanted) {
                return true;
            }

            if (argument instanceof java.lang.reflect.ParameterizedType nested
                && hasTypeArgument(nested, wanted)) {
                return true;
            }
        }

        return false;
    }

    /** 供外部调试：当前走的是哪条路径。 */
    public static String activePath() {
        if (levelRendererBreakingField != null) {
            return "LevelRenderer.destroyingBlocks";
        }

        if (clientLevelDestructionMethod != null) {
            return "ClientLevel." + clientLevelDestructionMethod.getName() + "()";
        }

        return "unavailable";
    }
}
