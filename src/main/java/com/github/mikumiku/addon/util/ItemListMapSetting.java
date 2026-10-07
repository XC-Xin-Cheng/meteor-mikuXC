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

import com.github.mikumiku.addon.util.Via;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.screens.settings.ItemListSettingScreen;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.gui.widgets.pressable.WMinus;
import meteordevelopment.meteorclient.gui.widgets.pressable.WPlus;
import meteordevelopment.meteorclient.settings.IVisible;
import meteordevelopment.meteorclient.settings.ItemListSetting;
import meteordevelopment.meteorclient.settings.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Item;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class ItemListMapSetting extends Setting<Map<String, List<Item>>> {
    public final Predicate<Item> filter;
    private final boolean bypassFilterWhenSavingAndLoading;

    public ItemListMapSetting(String name, String description, Map<String, List<Item>> defaultValue,
                              Consumer<Map<String, List<Item>>> onChanged,
                              Consumer<Setting<Map<String, List<Item>>>> onModuleActivated,
                              IVisible visible, Predicate<Item> filter, boolean bypassFilterWhenSavingAndLoading) {
        super(name, description, defaultValue, onChanged, onModuleActivated, visible);
        this.filter = filter;
        this.bypassFilterWhenSavingAndLoading = bypassFilterWhenSavingAndLoading;
    }

    @Override
    protected Map<String, List<Item>> parseImpl(String str) {
        // 格式: key1:item1,item2;key2:item3,item4
        String[] entries = str.split(";");
        Map<String, List<Item>> map = new LinkedHashMap<>();

        try {
            for (String entry : entries) {
                String[] parts = entry.split(":", 2);
                if (parts.length != 2) continue;

                String key = parts[0].trim();
                String[] itemNames = parts[1].split(",");
                List<Item> items = new ArrayList<>();

                for (String itemName : itemNames) {
                    Item item = (Item) parseId(BuiltInRegistries.ITEM, itemName.trim());
                    if (item != null && (filter == null || filter.test(item))) {
                        items.add(item);
                    }
                }

                if (!items.isEmpty()) {
                    map.put(key, items);
                }
            }
        } catch (Exception ignored) {
        }

        return map;
    }

    @Override
    protected boolean isValueValid(Map<String, List<Item>> value) {
        return true;
    }

    @Override
    protected void resetImpl() {
        value = new LinkedHashMap<>();
        for (Map.Entry<String, List<Item>> entry : defaultValue.entrySet()) {
            value.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
    }

    @Override
    public Iterable<Identifier> getIdentifierSuggestions() {
        return BuiltInRegistries.ITEM.keySet();
    }

    @Override
    protected CompoundTag save(CompoundTag tag) {
        CompoundTag mapTag = new CompoundTag();

        for (Map.Entry<String, List<Item>> entry : get().entrySet()) {
            ListTag itemList = new ListTag();
            for (Item item : entry.getValue()) {
                if (bypassFilterWhenSavingAndLoading || filter == null || filter.test(item)) {
                    itemList.add(StringTag.valueOf(BuiltInRegistries.ITEM.getKey(item).toString()));
                }
            }
            if (!itemList.isEmpty()) {
                mapTag.put(entry.getKey(), itemList);
            }
        }

        tag.put("value", mapTag);
        return tag;
    }


    @Override
    protected Map<String, List<Item>> load(CompoundTag tag) {
        get().clear();

        CompoundTag mapTag = Via.getNbtCompound(tag, "value");
        for (String key : mapTag.keySet()) {
            List<Item> items = new ArrayList<>();
            ListTag itemList = mapTag.getList(key).orElse(new ListTag());

            for (Tag tagI : itemList) {
                Item item = BuiltInRegistries.ITEM.getOptional(Identifier.parse(tagI.asString().orElse(""))).orElse(null);
                if (bypassFilterWhenSavingAndLoading || filter == null || filter.test(item)) {
                    items.add(item);
                }
            }

            if (!items.isEmpty()) {
                get().put(key, items);
            }
        }

        return get();
    }

    public static void fillTable(GuiTheme theme, WTable table, ItemListMapSetting setting) {
        table.clear();

        Map<String, List<Item>> map = setting.get();

        for (String key : map.keySet()) {
            AtomicReference<String> keyRef = new AtomicReference<>(key);

            // Key textbox
            WTextBox keyBox = table.add(theme.textBox(keyRef.get())).minWidth(100).expandX().widget();
            keyBox.actionOnUnfocused = () -> {
                String newKey = keyBox.get();
                if (map.containsKey(newKey) && !newKey.equals(keyRef.get())) {
                    keyBox.set(keyRef.get());
                    return;
                }
                List<Item> items = map.remove(keyRef.get());
                keyRef.set(newKey);
                map.put(newKey, items);
            };

            // Items display (simplified - you may want to add item selector GUI)
            List<Item> items = map.get(keyRef.get());
            // "Select" button
            WButton selectBtn = table.add(theme.button("Select")).minWidth(100).expandX().widget();

            // Item count label
            String labelText = "(" + items.size() + " items)";
            table.add(theme.label(labelText)).expandX().widget();
            selectBtn.action = () -> {
                List<Item> itemsList = map.get(keyRef.get());

                ItemListSetting tempSetting = new ItemListSetting(
                    "items",
                    "Items for key '" + keyRef.get() + "'",
                    itemsList,
                    newValue -> {
                        map.put(keyRef.get(), newValue);
                        fillTable(theme, table, setting);
                    },
                    null,
                    null,
                    setting.filter,
                    true
                );

                ItemListSettingScreen screen = new ItemListSettingScreen(theme, tempSetting);
                screen.onClosed(() -> map.put(keyRef.get(), tempSetting.get()));

                MikuCompat.setScreen(screen);
            };


            // Delete entry button
            WMinus delete = table.add(theme.minus()).widget();
            delete.action = () -> {
                map.remove(keyRef.get());
                fillTable(theme, table, setting);
            };

            table.row();
        }

        if (!map.isEmpty()) {
            table.add(theme.horizontalSeparator()).expandX();
            table.row();
        }

        // Reset button
        WButton reset = table.add(theme.button(GuiRenderer.RESET)).widget();
        reset.action = () -> {
            setting.reset();
            fillTable(theme, table, setting);
        };

        // Add new entry button
        WPlus add = table.add(theme.plus()).widget();
        add.action = () -> {
            map.put("", new ArrayList<>());
            fillTable(theme, table, setting);
        };

        table.row();
    }

    public static class Builder extends SettingBuilder<Builder, Map<String, List<Item>>, ItemListMapSetting> {
        private Predicate<Item> filter;
        private boolean bypassFilterWhenSavingAndLoading;

        public Builder() {
            super(new LinkedHashMap<>(0));
        }

        public Builder defaultValue(Map<String, List<Item>> map) {
            this.defaultValue = new LinkedHashMap<>();
            for (Map.Entry<String, List<Item>> entry : map.entrySet()) {
                this.defaultValue.put(entry.getKey(), new ArrayList<>(entry.getValue()));
            }
            return this;
        }

        public Builder filter(Predicate<Item> filter) {
            this.filter = filter;
            return this;
        }

        public Builder bypassFilterWhenSavingAndLoading() {
            this.bypassFilterWhenSavingAndLoading = true;
            return this;
        }

        @Override
        public ItemListMapSetting build() {
            return new ItemListMapSetting(name, description, defaultValue, onChanged, onModuleActivated, visible, filter, bypassFilterWhenSavingAndLoading);
        }
    }
}
