package com.github.mikumiku.addon.util;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.screens.settings.CollectionListSettingScreen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.npc.VillagerProfession;

import java.util.function.Predicate;

// 1.21.6 meteor：RegistryListSettingScreen 改名为 DynamicRegistryListSettingScreen 且泛型变成
// ResourceKey<T>（增删都走 ResourceKey，还要再转回职业对象，改动面太大）。这里直接继承它共同的
// 基类 CollectionListSettingScreen<VillagerProfession>（1.21.6 在 settings 包下，1.21.8 才挪进
// settings.base），继续用职业对象本身当条目，功能等价。
public class VillagerProfessionListSettingScreen extends CollectionListSettingScreen<VillagerProfession> {
    public VillagerProfessionListSettingScreen(GuiTheme theme, VillagerProfessionListSetting setting) {
        // 1.21.6 的 Registry 仍实现 Iterable（经 IdMap），候选列表可直接传注册表本体
        super(theme, "选择", setting, setting.get(), BuiltInRegistries.VILLAGER_PROFESSION);
    }

    @Override
    protected boolean includeValue(VillagerProfession value) {
        Predicate<VillagerProfession> filter = ((VillagerProfessionListSetting) setting).filter;
        if (filter != null && !filter.test(value)) return false;

        // 1.21.5+：VillagerProfession.NONE 变成 ResourceKey，用注册表 key 判断是否为空职业
        return !BuiltInRegistries.VILLAGER_PROFESSION.getResourceKey(value)
            .map(key -> key == VillagerProfession.NONE)
            .orElse(false);
    }

    @Override
    protected WWidget getValueWidget(VillagerProfession profession) {
        // 修复：Registry.getKey 返回 ResourceLocation，直接拼接得到 "minecraft:farmer" 的 toString 形式，
        // i18n 键变成非法的 "entity.minecraft.villager.minecraft:farmer"（I18n 回显键名）；改用 .getPath()。
        String key = "entity.minecraft.villager." + BuiltInRegistries.VILLAGER_PROFESSION.getKey(profession).getPath();
        String translated = I18n.get(key);
        return theme.label(translated);
    }

    @Override
    protected String getValueName(VillagerProfession profession) {
        return BuiltInRegistries.VILLAGER_PROFESSION.getKey(profession).getPath();
    }
}
