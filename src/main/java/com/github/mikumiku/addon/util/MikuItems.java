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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 跨版本物品解析（与 {@link MikuEntities} 同一套路，用来根治 {@code NoSuchFieldError}）。
 *
 * <p>26.2 把 {@code Items} 里按颜色分组的一大批静态常量删掉了：{@code WHITE_WOOL}、
 * {@code RED_CONCRETE}、{@code BLUE_SHULKER_BOX}…… 16 种颜色 × 羊毛/混凝土/陶瓦/
 * 染色玻璃/玻璃板/地毯/床/潜影盒全部改成了 {@code DYED_*} 的 {@code ColorCollection}。
 * 直接写 {@code Items.WHITE_SHULKER_BOX} 在 26.1 能编译，到了 26.2 读取该字段时会抛
 * {@code NoSuchFieldError}，模块构造阶段就会把游戏打崩。</p>
 *
 * <p>这里统一改成按注册名解析：先查 {@code BuiltInRegistries.ITEM}（注册名是数据层标识，
 * 跨版本稳定，且 {@code white_wool} 这类 id 在 26.2 依然存在，只是 Java 常量没了），
 * 再退回反射读取同名静态字段（26.1 及更早）。两步都包在 {@code catch (Throwable)} 里，
 * 字段被删时只是解析失败，不再抛异常。</p>
 *
 * <p>解析成功的物品按注册名缓存，注册表里的物品都是单例，因此 {@code ==} 比较依然成立；
 * 解析失败不缓存，以免在注册表还没初始化时把“空”永久记住。</p>
 */
public final class MikuItems {
    private MikuItems() {}

    private static final Map<String, Item> CACHE = new ConcurrentHashMap<>();

    /**
     * 按注册名解析物品。
     *
     * @param registryPath 注册名路径，如 {@code white_wool}、{@code red_shulker_box}
     * @return 对应物品；当前版本不存在时返回 {@code null}
     */
    public static Item get(String registryPath) {
        if (registryPath == null || registryPath.isEmpty()) return null;
        Item cached = CACHE.get(registryPath);
        if (cached != null) return cached;
        Item resolved = resolve(registryPath);
        if (resolved != null) CACHE.put(registryPath, resolved);
        return resolved;
    }

    /** 批量解析，自动跳过当前版本不存在的物品。 */
    public static List<Item> getList(String... registryPaths) {
        List<Item> result = new ArrayList<>(registryPaths.length);
        for (String path : registryPaths) {
            Item item = get(path);
            if (item != null) result.add(item);
        }
        return result;
    }

    /** 批量解析成数组，自动跳过当前版本不存在的物品。用于设置项的默认值。 */
    public static Item[] getAll(String... registryPaths) {
        List<Item> list = getList(registryPaths);
        return list.toArray(new Item[0]);
    }

    /** 把存在的物品加进集合。 */
    public static void add(Collection<Item> target, String... registryPaths) {
        for (String path : registryPaths) {
            Item item = get(path);
            if (item != null) target.add(item);
        }
    }

    private static Item resolve(String registryPath) {
        // 1) 注册表：数据层标识，跨版本稳定
        try {
            Object value = BuiltInRegistries.ITEM
                .getOptional(Identifier.withDefaultNamespace(registryPath))
                .orElse(null);
            if (value instanceof Item item) return item;
        } catch (Throwable ignored) {
            // 注册表接口不可用，继续下一步
        }

        // 2) 26.1 及更早的静态字段
        try {
            Field field = Items.class.getField(registryPath.toUpperCase(Locale.ROOT));
            Object value = field.get(null);
            if (value instanceof Item item) return item;
        } catch (Throwable ignored) {
            // 字段在新版本被移除或不可访问
        }

        return null;
    }
}
