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
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 跨版本注册表常量解析（方块实体类型）。
 *
 * <p>与 {@link MikuEntities} 同理：26.2 起注册表常量持有类的部分静态字段不再保证存在，
 * 直接写 {@code BlockEntityType.CHEST} 在 26.1 能编译通过，却可能在 26.2 运行时抛
 * {@code NoSuchFieldError}。这里优先按注册名查询 {@code BuiltInRegistries.BLOCK_ENTITY_TYPE}
 * （注册名是数据层标识，跨版本稳定），失败再退回反射读取同名字段。当前版本确实不存在的
 * 返回 {@code null}，由调用方跳过。</p>
 */
public final class MikuRegistries {
    private MikuRegistries() {}

    private static final Map<String, Optional<BlockEntityType<?>>> BLOCK_ENTITY_CACHE = new ConcurrentHashMap<>();

    /** 按注册名解析方块实体类型；当前版本不存在时返回 {@code null}。 */
    public static BlockEntityType<?> blockEntity(String registryPath) {
        if (registryPath == null || registryPath.isEmpty()) return null;
        return BLOCK_ENTITY_CACHE.computeIfAbsent(registryPath, MikuRegistries::resolveBlockEntity).orElse(null);
    }

    /** 批量解析方块实体类型，自动跳过当前版本不存在的。 */
    public static BlockEntityType<?>[] blockEntities(String... registryPaths) {
        BlockEntityType<?>[] resolved = new BlockEntityType<?>[registryPaths.length];
        int size = 0;
        for (String path : registryPaths) {
            BlockEntityType<?> type = blockEntity(path);
            if (type != null) resolved[size++] = type;
        }
        return size == registryPaths.length ? resolved : Arrays.copyOf(resolved, size);
    }

    private static Optional<BlockEntityType<?>> resolveBlockEntity(String registryPath) {
        // 1) 注册表：数据层标识，跨版本稳定
        try {
            Object value = BuiltInRegistries.BLOCK_ENTITY_TYPE
                .getOptional(Identifier.withDefaultNamespace(registryPath))
                .orElse(null);
            if (value instanceof BlockEntityType<?> type) return Optional.of(type);
        } catch (Throwable ignored) {
            // 注册表接口不可用，继续下一步
        }

        // 2) 26.1 及更早的静态字段
        try {
            Field field = BlockEntityType.class.getField(registryPath.toUpperCase(Locale.ROOT));
            Object value = field.get(null);
            if (value instanceof BlockEntityType<?> type) return Optional.of(type);
        } catch (Throwable ignored) {
            // 字段在新版本被移除或不可访问
        }

        return Optional.empty();
    }
}
