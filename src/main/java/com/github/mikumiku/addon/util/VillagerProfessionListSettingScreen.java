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
        // 26.1：profession.name() 是 Component.translatable("entity.<ns>.villager.<path>")，
        // 拼接/getString 会得到本地化文本（或 Component 的调试串），拼不出正确的翻译键，
        // 必须用注册表键的 path 还原 "entity.minecraft.villager.<path>"（与 1.21.10 语义一致）。
        String key = "entity.minecraft.villager." + BuiltInRegistries.VILLAGER_PROFESSION.getKey(profession).getPath();
        String translated = I18n.get(key);
        return theme.label(translated);
    }

    @Override
    protected String[] getValueNames(VillagerProfession profession) {
        // 展示名沿用注册表路径（与 1.21.10 一致），避免 Component.toString() 的调试串混入搜索匹配。
        return new String[]{BuiltInRegistries.VILLAGER_PROFESSION.getKey(profession).getPath()};
    }

}
