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

import com.github.mikumiku.addon.gui.OreSelectScreen;
import com.github.mikumiku.addon.util.ChatSchemeListSetting;
import com.github.mikumiku.addon.util.ClickProfileListSetting;
import com.github.mikumiku.addon.util.ItemListMapSetting;
import com.github.mikumiku.addon.util.OreListSetting;
import com.github.mikumiku.addon.util.StringMapSetting;
import com.github.mikumiku.addon.util.VillagerProfessionListSetting;
import com.github.mikumiku.addon.util.VillagerProfessionListSettingScreen;
import com.github.mikumiku.addon.util.MikuCompat;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.themes.meteor.widgets.WMeteorLabel;
import meteordevelopment.meteorclient.gui.utils.SettingsWidgetFactory;
import meteordevelopment.meteorclient.gui.widgets.containers.WContainer;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.settings.Setting;

import java.util.Collection;
import java.util.Map;

public class MySettings {
    private final Map<Class<?>, SettingsWidgetFactory.Factory> factories;

    private final GuiTheme theme;

    public MySettings(Map<Class<?>, SettingsWidgetFactory.Factory> factories, GuiTheme theme) {
        this.factories = factories;
        this.theme = theme;
    }

    public void addSettings() {
        factories.put(StringMapSetting.class, (table, setting) -> stringMapW(table, (StringMapSetting) setting));
        factories.put(ItemListMapSetting.class, (table, setting) -> itemListMapW(table, (ItemListMapSetting) setting));
        factories.put(VillagerProfessionListSetting.class, (table, setting) -> proListW(table, (VillagerProfessionListSetting) setting));
        factories.put(OreListSetting.class, (table, setting) -> oreListW(table, (OreListSetting) setting));
        factories.put(ClickProfileListSetting.class, (table, setting) -> clickProfileW(table, (ClickProfileListSetting) setting));
        factories.put(ChatSchemeListSetting.class, (table, setting) -> chatSchemeW(table, (ChatSchemeListSetting) setting));
    }

    public void stringMapW(WTable table, StringMapSetting setting) {
        WTable wtable = table.add(theme.table()).expandX().widget();
        StringMapSetting.fillTable(theme, wtable, setting);
    }

    public void itemListMapW(WTable table, ItemListMapSetting setting) {
        WTable wtable = table.add(theme.table()).expandX().widget();
        ItemListMapSetting.fillTable(theme, wtable, setting);
    }

    public void proListW(WTable table, VillagerProfessionListSetting setting) {
        selectW(table, setting, () -> MikuCompat.setScreen(new VillagerProfessionListSettingScreen(theme, setting)));
    }

    public void oreListW(WTable table, OreListSetting setting) {
        selectW(table, setting, "Select", () -> MikuCompat.setScreen(new OreSelectScreen(theme, setting)));
    }

    public void clickProfileW(WTable table, ClickProfileListSetting setting) {
        WTable wtable = table.add(theme.table()).expandX().widget();
        ClickProfileListSetting.fillTable(theme, wtable, setting);
    }

    public void chatSchemeW(WTable table, ChatSchemeListSetting setting) {
        WTable wtable = table.add(theme.table()).expandX().widget();
        ChatSchemeListSetting.fillTable(theme, wtable, setting);
    }

    public void selectW(WContainer c, Setting<?> setting, Runnable action) {
        selectW(c, setting, "选择", action);
    }

    public void selectW(WContainer c, Setting<?> setting, String buttonText, Runnable action) {
        boolean addCount = WSelectedCountLabel.getSize(setting) != -1;
        WContainer c2 = c;
        if (addCount) {
            c2 = c.add(this.theme.horizontalList()).expandCellX().widget();
            ((WHorizontalList) c2).spacing *= 2.0F;
        }

        WButton button = c2.add(this.theme.button(buttonText)).expandCellX().widget();
        button.action = action;
        if (addCount) {
            c2.add((new WSelectedCountLabel(setting)).color(this.theme.textSecondaryColor()));
        }

        reset(c, setting, null);
    }

    private void reset(WContainer c, Setting<?> setting, Runnable action) {
        WButton reset = c.add(this.theme.button(GuiRenderer.RESET)).widget();
        reset.action = () -> {
            setting.reset();
            if (action != null) {
                action.run();
            }

        };
    }


    public static class WSelectedCountLabel extends WMeteorLabel {
        private final Setting<?> setting;
        private int lastSize = -1;

        public WSelectedCountLabel(Setting<?> setting) {
            super("", false);
            this.setting = setting;
        }

        protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
            int size = getSize(this.setting);
            if (size != this.lastSize) {
                this.set("(" + size + " selected)");
                this.lastSize = size;
            }

            super.onRender(renderer, mouseX, mouseY, delta);
        }

        public static int getSize(Setting<?> setting) {
            Object var2 = setting.get();
            if (var2 instanceof Collection<?> collection) {
                return collection.size();
            } else {
                var2 = setting.get();
                if (var2 instanceof Map<?, ?> map) {
                    return map.size();
                } else {
                    return -1;
                }
            }
        }
    }
}
