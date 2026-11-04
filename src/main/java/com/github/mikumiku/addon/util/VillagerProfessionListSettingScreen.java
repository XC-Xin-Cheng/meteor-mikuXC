package com.github.mikumiku.addon.util;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.screens.settings.RegistryListSettingScreen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.world.entity.npc.VillagerProfession;

import java.util.function.Predicate;

// 1.21.4 meteor：RegistryListSettingScreen 仍存在（DynamicRegistryListSettingScreen /
// CollectionListSettingScreen 是 1.21.5+ 才引入的），直接继承它即可。
// VillagerProfession.NONE 在 1.21.4 还是实例常量（ResourceKey 化是 1.21.5+）。
public class VillagerProfessionListSettingScreen extends RegistryListSettingScreen<VillagerProfession> {
    public VillagerProfessionListSettingScreen(GuiTheme theme, VillagerProfessionListSetting setting) {
        super(theme, "选择", setting, setting.get(), net.minecraft.core.registries.BuiltInRegistries.VILLAGER_PROFESSION);
    }

    @Override
    protected boolean includeValue(VillagerProfession value) {
        Predicate<VillagerProfession> filter = ((VillagerProfessionListSetting) setting).filter;
        if (filter != null && !filter.test(value)) return false;

        return value != VillagerProfession.NONE;
    }

    @Override
    protected WWidget getValueWidget(VillagerProfession profession) {
        String key = "entity.minecraft.villager." + profession.name();
        String translated = I18n.get(key);
        return theme.label(translated);
    }

    @Override
    protected String getValueName(VillagerProfession profession) {
        return profession.name();
    }
}
