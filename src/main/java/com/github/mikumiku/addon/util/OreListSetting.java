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

import meteordevelopment.meteorclient.settings.IVisible;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * 种子矿透的矿石选择：界面上是一行 “矿石 [Select] (N selected)”，
 * 点开的 {@link com.github.mikumiku.addon.gui.OreSelectScreen} 按浅层/深层/下界分段勾选，
 * 每个矿石还能单独设置边框是否连接、用什么颜色。
 * 一个值就是一组“矿石 + 层”，例如深层钻石 = diamond_ore@deep。
 */
public class OreListSetting extends Setting<Set<Ore.OreKey>> {

    /**
     * 每个“矿石 + 层”的边框样式，跟着选择一起保存。
     *
     * <p>这里刻意**不用字段初始化器**（{@code = new HashMap<>()}）：Meteor 的
     * {@code Setting} 基类构造函数内部会调用 {@code reset()}，虚分发到本类重写的
     * {@link #resetImpl()}，再走到 {@link #resetStyles()}。Java 规定子类字段初始化器
     * 在 {@code super()} 返回之后才执行，所以那一刻本字段仍是 {@code null}，
     * {@code styles.clear()} 会抛 NPE 直接把模组打崩。
     * 因此改为在 {@link #resetStyles()} 里按需创建，谁先到都不怕。
     */
    private Map<Ore.OreKey, Ore.OreStyle> styles;

    public OreListSetting(String name, String description, Set<Ore.OreKey> defaultValue,
                          Consumer<Set<Ore.OreKey>> onChanged,
                          Consumer<Setting<Set<Ore.OreKey>>> onModuleActivated,
                          IVisible visible) {
        super(name, description, defaultValue, onChanged, onModuleActivated, visible);
        // super() 期间已经回调过一次 resetStyles()，这里只是保证最终状态一致（幂等）。
        resetStyles();
    }

    /**
     * 所有矿石样式回到默认：不连接、颜色用内置色。
     * 允许在字段初始化器执行之前被调用（基类构造期间），此时自行创建 Map。
     */
    private void resetStyles() {
        if (styles == null) {
            styles = new HashMap<>();
        } else {
            styles.clear();
        }
        for (Ore.OreType type : Ore.OreType.values()) {
            for (Ore.OreLayer layer : type.layers()) {
                styles.put(Ore.OreKey.of(type, layer), new Ore.OreStyle(type.color));
            }
        }
    }

    /** styles 可能尚未初始化（基类构造期间被访问），统一从这里取。 */
    private Map<Ore.OreKey, Ore.OreStyle> styles() {
        if (styles == null) resetStyles();
        return styles;
    }

    /** 取某个“矿石 + 层”的样式；缺失时用该矿石默认色补一个。 */
    public Ore.OreStyle style(Ore.OreKey key) {
        Map<Ore.OreKey, Ore.OreStyle> map = styles();
        Ore.OreStyle style = map.get(key);
        if (style == null) {
            style = new Ore.OreStyle(key.type.color);
            map.put(key, style);
        }
        return style;
    }

    @Override
    public Set<Ore.OreKey> get() {
        if (value == null) value = new LinkedHashSet<>();
        return value;
    }

    @Override
    protected Set<Ore.OreKey> parseImpl(String str) {
        Set<Ore.OreKey> keys = new LinkedHashSet<>();
        for (String part : str.split(",")) {
            String key = part.trim().toLowerCase(Locale.ROOT);
            if (key.isEmpty()) continue;

            if (key.contains("@")) {
                Ore.OreKey oreKey = Ore.OreKey.byId(key);
                if (oreKey != null) keys.add(oreKey);
            } else {
                // 只写了矿石名：浅层/深层/下界里能出现的层全部选上
                Ore.OreType type = Ore.OreType.byId(key);
                if (type != null) {
                    for (Ore.OreLayer layer : type.layers()) keys.add(Ore.OreKey.of(type, layer));
                }
            }
        }
        return keys;
    }

    @Override
    protected boolean isValueValid(Set<Ore.OreKey> value) {
        return true;
    }

    @Override
    protected void resetImpl() {
        value = new LinkedHashSet<>(defaultValue);
        resetStyles();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (Ore.OreKey key : get()) {
            if (sb.length() > 0) sb.append(',');
            sb.append(key.id());
        }
        return sb.toString();
    }

    @Override
    protected CompoundTag save(CompoundTag tag) {
        ListTag valueTag = new ListTag();
        for (Ore.OreKey key : get()) {
            valueTag.add(StringTag.valueOf(key.id()));
        }
        tag.put("value", valueTag);

        // 只保存被改过的样式（连接开着，或颜色不再是内置色），配置里不会塞一堆默认值。
        CompoundTag stylesTag = new CompoundTag();
        for (Map.Entry<Ore.OreKey, Ore.OreStyle> entry : styles().entrySet()) {
            Ore.OreKey key = entry.getKey();
            Ore.OreStyle style = entry.getValue();
            if (!style.connected && style.color.equals(key.type.color)) continue;

            CompoundTag styleTag = new CompoundTag();
            styleTag.putBoolean("connected", style.connected);
            styleTag.putInt("color", style.color.getPacked());
            stylesTag.put(key.id(), styleTag);
        }
        tag.put("styles", stylesTag);
        return tag;
    }

    @Override
    protected Set<Ore.OreKey> load(CompoundTag tag) {
        Set<Ore.OreKey> keys = new LinkedHashSet<>();
        ListTag valueTag = tag.getList("value").orElse(new ListTag());
        for (Tag tagI : valueTag) {
            Ore.OreKey key = Ore.OreKey.byId(tagI.asString().orElse(""));
            if (key != null) keys.add(key);
        }

        // 先把样式恢复到默认，再套用配置里存过的覆盖值。
        resetStyles();
        CompoundTag stylesTag = Via.getNbtCompound(tag, "styles");
        for (String id : stylesTag.keySet()) {
            Ore.OreKey key = Ore.OreKey.byId(id);
            if (key == null) continue;

            CompoundTag styleTag = Via.getNbtCompound(stylesTag, id);
            Ore.OreStyle style = style(key);
            style.connected = styleTag.getBooleanOr("connected", false);
            style.color.set(new Color(styleTag.getIntOr("color", key.type.color.getPacked())));
        }

        // Setting.fromTag 不会替我们写回 value，这里必须自己 set，否则选择读不回来。
        set(keys);
        return keys;
    }

    public static class Builder extends SettingBuilder<Builder, Set<Ore.OreKey>, OreListSetting> {
        public Builder() {
            super(new LinkedHashSet<>(0));
        }

        public Builder defaultValue(Ore.OreKey... defaults) {
            Set<Ore.OreKey> set = new LinkedHashSet<>();
            if (defaults != null) {
                for (Ore.OreKey key : defaults) set.add(key);
            }
            return defaultValue(set);
        }

        @Override
        public OreListSetting build() {
            return new OreListSetting(name, description, defaultValue, onChanged, onModuleActivated, visible);
        }
    }
}
