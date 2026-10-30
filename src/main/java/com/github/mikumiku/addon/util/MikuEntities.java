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

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;

import java.lang.reflect.Field;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 跨版本实体类型解析。
 *
 * <p>26.2 起 {@code EntityType} 的部分静态常量不再保证存在（已确认 END_CRYSTAL、
 * EXPERIENCE_ORB 被移除），直接写 {@code EntityType.END_CRYSTAL} 在 26.1 能编译通过，
 * 却会在 26.2 运行时的类初始化阶段抛 {@code NoSuchFieldError}，导致游戏初始化直接崩溃。</p>
 *
 * <p>这里统一改成按注册名解析：优先查 {@code BuiltInRegistries.ENTITY_TYPE}（注册名是
 * 数据层标识，跨版本稳定），再退回反射读取同名静态字段（26.1 及更早），最后调用原版
 * {@code EntityType.byString}。三步全部包在 {@code catch (Throwable)} 里，字段/方法被移除时
 * 只是解析失败而不是崩溃；当前版本确实没有的实体返回 {@code null}，由调用方跳过。</p>
 *
 * <p>解析结果按注册名缓存，且注册表里的实体都是单例，因此 {@code ==} 比较依然成立。</p>
 */
public final class MikuEntities {
    private MikuEntities() {}

    private static final Map<String, Optional<EntityType<?>>> CACHE = new ConcurrentHashMap<>();

    /**
     * 按注册名解析实体类型。
     *
     * @param registryPath 注册名路径，如 {@code experience_orb}、{@code end_crystal}
     * @return 对应实体类型；当前版本不存在时返回 {@code null}
     */
    public static EntityType<?> get(String registryPath) {
        if (registryPath == null || registryPath.isEmpty()) return null;
        return CACHE.computeIfAbsent(registryPath, MikuEntities::resolve).orElse(null);
    }

    /**
     * 批量解析，自动跳过当前版本不存在的实体。
     *
     * <p>用于设置项的默认值：若某个实体在新版本被移除，默认值里就不会带上它，
     * 而不是塞进一个 {@code null} 让设置界面或序列化炸掉。</p>
     */
    public static EntityType<?>[] getAll(String... registryPaths) {
        EntityType<?>[] resolved = new EntityType<?>[registryPaths.length];
        int size = 0;
        for (String path : registryPaths) {
            EntityType<?> type = get(path);
            if (type != null) resolved[size++] = type;
        }
        return size == registryPaths.length ? resolved : java.util.Arrays.copyOf(resolved, size);
    }

    /** 把存在的实体加进集合。 */
    public static void add(Set<EntityType<?>> target, String... registryPaths) {
        for (String path : registryPaths) {
            EntityType<?> type = get(path);
            if (type != null) target.add(type);
        }
    }

    /** 把存在的实体从集合里移除。 */
    public static void remove(Set<EntityType<?>> target, String... registryPaths) {
        for (String path : registryPaths) {
            EntityType<?> type = get(path);
            if (type != null) target.remove(type);
        }
    }

    /** 判断集合里是否含有该注册名对应的实体。 */
    public static boolean contains(Set<EntityType<?>> target, String registryPath) {
        EntityType<?> type = get(registryPath);
        return type != null && target.contains(type);
    }

    /** 末地水晶的实体类型；当前版本确实不存在时返回 null。 */
    public static EntityType<?> endCrystal() {
        return get("end_crystal");
    }

    private static Optional<EntityType<?>> resolve(String registryPath) {
        // 1) 注册表：数据层标识，跨版本稳定
        try {
            Object value = BuiltInRegistries.ENTITY_TYPE
                .getOptional(Identifier.withDefaultNamespace(registryPath))
                .orElse(null);
            if (value instanceof EntityType<?> type) return Optional.of(type);
        } catch (Throwable ignored) {
            // 注册表接口不可用，继续下一步
        }

        // 2) 26.1 及更早的静态字段
        try {
            Field field = EntityType.class.getField(registryPath.toUpperCase(Locale.ROOT));
            Object value = field.get(null);
            if (value instanceof EntityType<?> type) return Optional.of(type);
        } catch (Throwable ignored) {
            // 字段在新版本被移除或不可访问
        }

        // 3) 原版按名解析
        try {
            Optional<EntityType<?>> byName = EntityType.byString(registryPath);
            if (byName != null && byName.isPresent()) return byName;
        } catch (Throwable ignored) {
            // 方法在新版本被移除
        }

        return Optional.empty();
    }
}
