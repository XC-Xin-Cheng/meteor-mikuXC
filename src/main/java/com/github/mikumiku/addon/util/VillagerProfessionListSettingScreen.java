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

import com.github.mikumiku.addon.util.VillagerProfessionListSetting;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.screens.settings.base.CollectionListSettingScreen;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.npc.villager.VillagerProfession;

import java.util.function.Predicate;

public class VillagerProfessionListSettingScreen extends CollectionListSettingScreen<VillagerProfession> {
    public VillagerProfessionListSettingScreen(GuiTheme theme, VillagerProfessionListSetting setting) {
        super(theme, "选择", setting, setting.get(), BuiltInRegistries.VILLAGER_PROFESSION);
    }

    @Override
    protected boolean includeValue(VillagerProfession value) {
        Predicate<VillagerProfession> filter = ((VillagerProfessionListSetting) setting).filter;
        if (filter != null && !filter.test(value)) return false;


        VillagerProfession profession = BuiltInRegistries.VILLAGER_PROFESSION.getValue(VillagerProfession.NONE);
        return value != profession;
    }

    @Override
    protected WWidget getValueWidget(VillagerProfession profession) {

        String key = "entity.minecraft.villager." + profession.name();
        String translated = I18n.get(key);
        return theme.label(translated);
    }

    @Override
    protected String[] getValueNames(VillagerProfession profession) {

        return new String[]{profession.name().getString(), profession.name().toString()};
    }

}
