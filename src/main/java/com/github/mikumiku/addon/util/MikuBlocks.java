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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 跨版本方块解析（与 {@link MikuEntities}、{@link MikuItems} 同一套路）。
 *
 * <p>26.2 删掉了 {@code Blocks} 里 16 种颜色的潜影盒常量（{@code WHITE_SHULKER_BOX} …
 * {@code BLACK_SHULKER_BOX}，改成了 {@code DYED_SHULKER_BOX} 的 {@code ColorCollection}）。
 * 直接读这些静态字段在 26.2 会抛 {@code NoSuchFieldError}，类初始化阶段就会崩。</p>
 *
 * <p>这里按注册名解析：先查 {@code BuiltInRegistries.BLOCK}（{@code white_shulker_box}
 * 这类 id 在 26.2 依然存在，只是 Java 常量没了），再退回反射读同名字段。两步都包在
 * {@code catch (Throwable)} 里，字段被删也只是解析失败。</p>
 */
public final class MikuBlocks {
    private MikuBlocks() {}

    private static final Map<String, Block> CACHE = new ConcurrentHashMap<>();

    /**
     * 按注册名解析方块。
     *
     * @param registryPath 注册名路径，如 {@code white_shulker_box}
     * @return 对应方块；当前版本不存在时返回 {@code null}
     */
    public static Block get(String registryPath) {
        if (registryPath == null || registryPath.isEmpty()) return null;
        Block cached = CACHE.get(registryPath);
        if (cached != null) return cached;
        Block resolved = resolve(registryPath);
        if (resolved != null) CACHE.put(registryPath, resolved);
        return resolved;
    }

    /** 批量解析，自动跳过当前版本不存在的方块。 */
    public static List<Block> getList(String... registryPaths) {
        List<Block> result = new ArrayList<>(registryPaths.length);
        for (String path : registryPaths) {
            Block block = get(path);
            if (block != null) result.add(block);
        }
        return result;
    }

    /** 批量解析成数组，自动跳过当前版本不存在的方块。用于设置项的默认值。 */
    public static Block[] getAll(String... registryPaths) {
        List<Block> list = getList(registryPaths);
        return list.toArray(new Block[0]);
    }

    /** 把存在的方块加进集合。 */
    public static void add(Collection<Block> target, String... registryPaths) {
        for (String path : registryPaths) {
            Block block = get(path);
            if (block != null) target.add(block);
        }
    }

    private static Block resolve(String registryPath) {
        // 1) 注册表：数据层标识，跨版本稳定
        try {
            Object value = BuiltInRegistries.BLOCK
                .getOptional(Identifier.withDefaultNamespace(registryPath))
                .orElse(null);
            if (value instanceof Block block) return block;
        } catch (Throwable ignored) {
            // 注册表接口不可用，继续下一步
        }

        // 2) 26.1 及更早的静态字段
        try {
            Field field = Blocks.class.getField(registryPath.toUpperCase(Locale.ROOT));
            Object value = field.get(null);
            if (value instanceof Block block) return block;
        } catch (Throwable ignored) {
            // 字段在新版本被移除或不可访问
        }

        return null;
    }
}
