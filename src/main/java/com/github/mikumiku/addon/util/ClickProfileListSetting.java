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

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.utils.CharFilter;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.gui.widgets.pressable.WCheckbox;
import meteordevelopment.meteorclient.gui.widgets.pressable.WMinus;
import meteordevelopment.meteorclient.gui.widgets.pressable.WPlus;
import meteordevelopment.meteorclient.settings.IVisible;
import meteordevelopment.meteorclient.settings.Setting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 「Miku 连点器」的连点方案列表设置。
 *
 * <p>配置界面里直接展开成一张表：一行一套方案，能勾启用、改名字、切按键、
 * 填速度区间与按住时长、开关“仅瞄准”，也能加/删方案。相比另开一个窗口，
 * 这种内嵌表格和本工程的 {@link StringMapSetting}、{@link ItemListMapSetting}
 * 保持一致，且不需要注册新的 widget factory 之外的任何东西。</p>
 */
public class ClickProfileListSetting extends Setting<List<ClickProfile>> {

    /** 只允许数字的输入过滤器（速度、按住时长这类整数框用）。 */
    private static final CharFilter DIGITS = (text, c) -> c >= '0' && c <= '9';

    public ClickProfileListSetting(String name, String description, List<ClickProfile> defaultValue,
                                   Consumer<List<ClickProfile>> onChanged,
                                   Consumer<Setting<List<ClickProfile>>> onModuleActivated,
                                   IVisible visible) {
        super(name, description, defaultValue, onChanged, onModuleActivated, visible);
    }

    @Override
    public List<ClickProfile> get() {
        if (value == null) value = new ArrayList<>();
        return value;
    }

    /**
     * 字符串形式（命令/配置文本里用）：方案之间用 {@code ;} 分隔，
     * 单套格式为 {@code 名称:按键:最低-最高:按住ms:仅瞄准:方式:间隔tick:类人化}，
     * 后几段可省略（默认 Miku 方式、类人化开）。
     */
    @Override
    protected List<ClickProfile> parseImpl(String str) {
        List<ClickProfile> list = new ArrayList<>();
        for (String entry : str.split(";")) {
            String part = entry.trim();
            if (part.isEmpty()) continue;

            String[] fields = part.split(":");
            ClickProfile profile = new ClickProfile();
            profile.name = fields[0].trim();

            if (fields.length > 1) profile.button = ClickProfile.Button.byName(fields[1].trim());
            if (fields.length > 2) {
                String[] cps = fields[2].trim().split("-");
                try {
                    profile.minCps = clamp(Integer.parseInt(cps[0].trim()), 1, 50);
                    profile.maxCps = clamp(Integer.parseInt(cps.length > 1 ? cps[1].trim() : cps[0].trim()), 1, 50);
                } catch (NumberFormatException ignored) {
                }
            }
            if (fields.length > 3) {
                try {
                    profile.holdMs = clamp(Integer.parseInt(fields[3].trim()), 0, 5000);
                } catch (NumberFormatException ignored) {
                }
            }
            if (fields.length > 4) profile.requireTarget = Boolean.parseBoolean(fields[4].trim());
            if (fields.length > 5) profile.method = ClickProfile.Method.byName(fields[5].trim());
            if (fields.length > 6) {
                try {
                    profile.intervalTicks = clamp(Integer.parseInt(fields[6].trim()), 1, 40);
                } catch (NumberFormatException ignored) {
                }
            }
            if (fields.length > 7) profile.humanized = Boolean.parseBoolean(fields[7].trim());

            list.add(profile);
        }
        return list;
    }

    @Override
    protected boolean isValueValid(List<ClickProfile> value) {
        return true;
    }

    @Override
    protected void resetImpl() {
        value = new ArrayList<>();
        if (defaultValue != null) {
            for (ClickProfile profile : defaultValue) value.add(profile.copy());
        }
    }

    @Override
    protected CompoundTag save(CompoundTag tag) {
        ListTag listTag = new ListTag();
        for (ClickProfile profile : get()) listTag.add(profile.toTag());
        tag.put("profiles", listTag);
        return tag;
    }

    @Override
    protected List<ClickProfile> load(CompoundTag tag) {
        List<ClickProfile> list = new ArrayList<>();
        ListTag listTag = tag.getList("profiles").orElse(new ListTag());
        for (Tag entry : listTag) {
            entry.asCompound().ifPresent(compound -> list.add(ClickProfile.fromTag(compound)));
        }

        // Setting.fromTag 不会替我们写回 value，这里必须自己 set。
        set(list);
        return list;
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    /**
     * 把方案列表画成一张可编辑的表。每次增删方案都会重建整张表。
     */
    public static void fillTable(GuiTheme theme, WTable table, ClickProfileListSetting setting) {
        table.clear();

        List<ClickProfile> profiles = setting.get();

        // 表头，列数必须和下面每行一一对应
        table.add(theme.label("启用")).widget();
        table.add(theme.label("方案名")).widget();
        table.add(theme.label("点击方式")).widget();
        table.add(theme.label("按键")).widget();
        table.add(theme.label("最低CPS")).widget();
        table.add(theme.label("最高CPS")).widget();
        table.add(theme.label("按住ms")).widget();
        table.add(theme.label("仅瞄准")).widget();
        table.add(theme.label("类人化")).widget();
        table.add(theme.label("")).widget();
        table.row();

        for (ClickProfile profile : profiles) {
            WCheckbox enabled = table.add(theme.checkbox(profile.enabled)).widget();
            enabled.tooltip = "勾上后这套方案才会连点；多套方案可以同时启用";
            enabled.action = () -> {
                profile.enabled = enabled.checked;
                setting.onChanged();
            };

            // 方案名框收窄，给它后面的「点击方式」腾地方
            WTextBox nameBox = table.add(theme.textBox(profile.name)).minWidth(60).widget();
            nameBox.tooltip = "方案名，只用于区分不同方案";
            nameBox.actionOnUnfocused = () -> {
                String text = nameBox.get().trim();
                if (!text.isEmpty()) {
                    profile.name = text;
                    setting.onChanged();
                } else {
                    nameBox.set(profile.name);
                }
            };

            // 点击方式紧跟在方案名后面：Miku 或 Meteor
            WButton method = table.add(theme.button(profile.method.displayName)).widget();
            method.tooltip = "点一下在 Miku（按键队列 + CPS 速度）和 Meteor（直接点击 + tick 间隔）之间切换";
            method.action = () -> {
                profile.method = profile.method.next();
                setting.onChanged();
                // 两种方式要填的字段不一样，切完重建整张表
                fillTable(theme, table, setting);
            };

            WButton button = table.add(theme.button(profile.button.displayName)).widget();
            button.tooltip = "点一下在左键/右键之间切换";
            button.action = () -> {
                profile.button = profile.button.next();
                button.set(profile.button.displayName);
                setting.onChanged();
            };

            if (profile.method == ClickProfile.Method.VANILLA) {
                // Meteor 方式只需要一个「间隔多少 tick」的输入
                WTextBox intervalBox = table.add(theme.textBox(Integer.toString(profile.clampedIntervalTicks()), DIGITS)).minWidth(40).widget();
                intervalBox.tooltip = "Meteor 方式：每隔多少 tick 点击一次（1 tick = 50 ms），1~40";
                intervalBox.actionOnUnfocused = () -> {
                    profile.intervalTicks = clamp(parseInt(intervalBox.get(), profile.intervalTicks), 1, 40);
                    intervalBox.set(Integer.toString(profile.intervalTicks));
                    setting.onChanged();
                };

                // 最高CPS 这一列在 Meteor 方式下用不到，留空占位保持列对齐
                table.add(theme.label("")).widget();
            } else {
                WTextBox minBox = table.add(theme.textBox(Integer.toString(profile.lowerCps()), DIGITS)).minWidth(40).widget();
                minBox.tooltip = "速度区间下限（每秒点击次数），1~50";
                minBox.actionOnUnfocused = () -> {
                    profile.minCps = clamp(parseInt(minBox.get(), profile.minCps), 1, 50);
                    minBox.set(Integer.toString(profile.minCps));
                    setting.onChanged();
                };

                WTextBox maxBox = table.add(theme.textBox(Integer.toString(profile.upperCps()), DIGITS)).minWidth(40).widget();
                maxBox.tooltip = "速度区间上限（每秒点击次数），1~50；越小越稳";
                maxBox.actionOnUnfocused = () -> {
                    profile.maxCps = clamp(parseInt(maxBox.get(), profile.maxCps), 1, 50);
                    maxBox.set(Integer.toString(profile.maxCps));
                    setting.onChanged();
                };
            }

            WTextBox holdBox = table.add(theme.textBox(Integer.toString(profile.holdMs), DIGITS)).minWidth(45).widget();
            holdBox.tooltip = "每次点击按住多少毫秒再松开，0 表示只补一次瞬时点击";
            holdBox.actionOnUnfocused = () -> {
                profile.holdMs = clamp(parseInt(holdBox.get(), profile.holdMs), 0, 5000);
                holdBox.set(Integer.toString(profile.holdMs));
                setting.onChanged();
            };

            WCheckbox target = table.add(theme.checkbox(profile.requireTarget)).widget();
            target.tooltip = "只在准星指着方块或实体时才连点，避免对着空气挥砍";
            target.action = () -> {
                profile.requireTarget = target.checked;
                setting.onChanged();
            };

            WCheckbox humanized = table.add(theme.checkbox(profile.humanized)).widget();
            humanized.tooltip = "勾上后这套方案走「类人化」节奏：间隔钟形抖动 + 可变延迟 + 错开松开；" +
                "具体参数看本模块「类人化」设置组（关闭/跟随全局/自定义）。取消勾选则保持等距机械节奏";
            humanized.action = () -> {
                profile.humanized = humanized.checked;
                setting.onChanged();
            };

            WMinus delete = table.add(theme.minus()).widget();
            delete.tooltip = "删除这套方案";
            delete.action = () -> {
                profiles.remove(profile);
                setting.onChanged();
                fillTable(theme, table, setting);
            };

            table.row();
        }

        if (!profiles.isEmpty()) {
            table.add(theme.horizontalSeparator()).expandX();
            table.row();
        }

        WButton reset = table.add(theme.button(GuiRenderer.RESET)).widget();
        reset.tooltip = "恢复成默认方案";
        reset.action = () -> {
            setting.reset();
            fillTable(theme, table, setting);
        };

        WPlus add = table.add(theme.plus()).widget();
        add.tooltip = "添加一套新方案";
        add.action = () -> {
            profiles.add(new ClickProfile("方案 " + (profiles.size() + 1)));
            setting.onChanged();
            fillTable(theme, table, setting);
        };

        table.row();
    }

    private static int parseInt(String text, int fallback) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public static class Builder extends SettingBuilder<Builder, List<ClickProfile>, ClickProfileListSetting> {
        public Builder() {
            super(new ArrayList<>(0));
        }

        public Builder defaultValue(ClickProfile... defaults) {
            List<ClickProfile> list = new ArrayList<>();
            if (defaults != null) {
                for (ClickProfile profile : defaults) list.add(profile.copy());
            }
            return defaultValue(list);
        }

        @Override
        public ClickProfileListSetting build() {
            return new ClickProfileListSetting(name, description, defaultValue, onChanged, onModuleActivated, visible);
        }
    }
}
