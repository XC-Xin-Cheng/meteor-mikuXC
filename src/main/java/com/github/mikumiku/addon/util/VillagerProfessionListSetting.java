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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.npc.villager.VillagerProfession;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class VillagerProfessionListSetting extends Setting<List<VillagerProfession>> {
    public final Predicate<VillagerProfession> filter;
    private final boolean bypassFilterWhenSavingAndLoading;

    public VillagerProfessionListSetting(String name, String description, List<VillagerProfession> defaultValue,
                                         Consumer<List<VillagerProfession>> onChanged,
                                         Consumer<Setting<List<VillagerProfession>>> onModuleActivated,
                                         IVisible visible,
                                         Predicate<VillagerProfession> filter,
                                         boolean bypassFilterWhenSavingAndLoading) {
        super(name, description, defaultValue, onChanged, onModuleActivated, visible);

        this.filter = filter;
        this.bypassFilterWhenSavingAndLoading = bypassFilterWhenSavingAndLoading;
    }

    @Override
    protected List<VillagerProfession> parseImpl(String str) {
        String[] values = str.split(",");
        List<VillagerProfession> professions = new ArrayList<>(values.length);

        try {
            for (String value : values) {
                VillagerProfession profession = parseId(BuiltInRegistries.VILLAGER_PROFESSION, value);
                if (profession != null && (filter == null || filter.test(profession))) {
                    professions.add(profession);
                }
            }
        } catch (Exception ignored) {
        }

        return professions;
    }

    @Override
    public void resetImpl() {
        value = new ArrayList<>(defaultValue);
    }

    @Override
    protected boolean isValueValid(List<VillagerProfession> value) {
        return true;
    }

    @Override
    public Iterable<Identifier> getIdentifierSuggestions() {
        return BuiltInRegistries.VILLAGER_PROFESSION.keySet();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag valueTag = new ListTag();
        for (VillagerProfession profession : get()) {
            if (bypassFilterWhenSavingAndLoading || (filter == null || filter.test(profession))) {
                valueTag.add(StringTag.valueOf(BuiltInRegistries.VILLAGER_PROFESSION.getKey(profession).toString()));
            }
        }
        tag.put("value", valueTag);

        return tag;
    }

    @Override
    public List<VillagerProfession> load(CompoundTag tag) {
        get().clear();

        ListTag valueTag = tag.getList("value").get();
        for (Tag tagI : valueTag) {
            VillagerProfession profession = BuiltInRegistries.VILLAGER_PROFESSION.getOptional(Identifier.parse(tagI.asString().get())).orElse(null);

            if (bypassFilterWhenSavingAndLoading || (filter == null || filter.test(profession))) {
                get().add(profession);
            }
        }

        return get();
    }

    public static class Builder extends SettingBuilder<Builder, List<VillagerProfession>, VillagerProfessionListSetting> {
        private Predicate<VillagerProfession> filter;
        private boolean bypassFilterWhenSavingAndLoading;

        public Builder() {
            super(new ArrayList<>(0));
        }

        public Builder defaultValue(VillagerProfession... defaults) {
            return defaultValue(defaults != null ? Arrays.asList(defaults) : new ArrayList<>());
        }

        public Builder filter(Predicate<VillagerProfession> filter) {
            this.filter = filter;
            return this;
        }

        public Builder bypassFilterWhenSavingAndLoading() {
            this.bypassFilterWhenSavingAndLoading = true;
            return this;
        }

        @Override
        public VillagerProfessionListSetting build() {
            return new VillagerProfessionListSetting(name, description, defaultValue, onChanged, onModuleActivated, visible, filter, bypassFilterWhenSavingAndLoading);
        }
    }
}
