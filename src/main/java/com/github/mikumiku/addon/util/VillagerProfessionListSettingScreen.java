package com.github.mikumiku.addon.util;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.screens.settings.base.CollectionListSettingScreen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.npc.VillagerProfession;

import java.util.function.Predicate;

// 1.21.10：meteor 的 RegistryListSettingScreen 被 DynamicRegistryListSettingScreen 取代，
// 且其泛型值类型改为 ResourceKey。这里改用更底层的 CollectionListSettingScreen<VillagerProfession>，
// 保持本插件的设置类继续直接存 VillagerProfession，不牵动存档格式。
public class VillagerProfessionListSettingScreen extends CollectionListSettingScreen<VillagerProfession> {
    public VillagerProfessionListSettingScreen(GuiTheme theme, VillagerProfessionListSetting setting) {
        super(theme, "选择", setting, setting.get(), BuiltInRegistries.VILLAGER_PROFESSION);
    }

    @Override
    protected boolean includeValue(VillagerProfession value) {
        Predicate<VillagerProfession> filter = ((VillagerProfessionListSetting) setting).filter;
        if (filter != null && !filter.test(value)) return false;

        // 1.21.10：VillagerProfession 是 record，NONE 是 ResourceKey，改用注册表键比较。
        // 修复：Registry.getKey 返回 ResourceLocation，与 ResourceKey 类型 equals 恒 false，过滤失效，
        // "无业"（NONE）职业混入候选列表；改为与 NONE 的键位置（ResourceLocation）比较。
        return !BuiltInRegistries.VILLAGER_PROFESSION.getKey(value).equals(VillagerProfession.NONE.location());
    }

    @Override
    protected WWidget getValueWidget(VillagerProfession profession) {
        String key = "entity.minecraft.villager." + BuiltInRegistries.VILLAGER_PROFESSION.getKey(profession).getPath();
        String translated = I18n.get(key);
        return theme.label(translated);
    }

    @Override
    protected String[] getValueNames(VillagerProfession profession) {
        // 1.21.10：getValueName(String) 改为 getValueNames(String[])；name() 返回 Component，取注册表路径展示
        return new String[]{BuiltInRegistries.VILLAGER_PROFESSION.getKey(profession).getPath()};
    }
}
