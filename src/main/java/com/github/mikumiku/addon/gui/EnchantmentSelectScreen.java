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
package com.github.mikumiku.addon.gui;

import com.github.mikumiku.addon.modules.VillagerRoller;
import com.github.mikumiku.addon.util.Via;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.utils.misc.Names;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.core.Registry;
import net.minecraft.core.Holder;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class EnchantmentSelectScreen extends WindowScreen {
    private final GuiTheme theme;
    private final EnchantmentSelectCallback callback;
    private String filterText = "";
    private final boolean onlyTradeable;

    public EnchantmentSelectScreen(GuiTheme theme, boolean onlyTradeable, EnchantmentSelectCallback callback) {
        super(theme, "选择附魔");
        this.theme = theme;
        this.callback = callback;
        this.onlyTradeable = onlyTradeable;
    }

    public interface EnchantmentSelectCallback {
        void selection(VillagerRoller.RollingEnchantment e);
    }

    @Override
    public void initWidgets() {
        WTable table = theme.table();
        table.minWidth = 400;

        WTextBox filter = add(theme.textBox(filterText, "搜索")).minWidth(400).expandX().widget();
        filter.setFocused(true);
        filter.setCursorMax();
        filter.action = () -> {
            filterText = filter.get().trim();
            table.clear();
            fillTable(table);
        };

        WHorizontalList customList = add(theme.horizontalList()).expandX().widget();
        WTextBox cc = customList.add(theme.textBox("", "自定义")).expandX().expandWidgetX().widget();
        WButton ca = customList.add(theme.button("选择")).widget();
        ca.action = () -> {
            String idtext = cc.get();
            if (idtext.isEmpty()) return;
            Identifier id = Identifier.tryParse(cc.get());
            if (id == null) return;
            callback.selection(new VillagerRoller.RollingEnchantment(id, 0, 0, true));
            onClose();
        };

        add(table);
        fillTable(table);
    }

    private void fillTable(WTable table) {
        if (mc.level == null) {
            return;
        }
        Registry<Enchantment> reg = Via.getEnchantmentRegistry();
        List<Holder<Enchantment>> available = new ArrayList<>();
        if (this.onlyTradeable) {
            var l = reg.getTagOrEmpty(EnchantmentTags.TRADEABLE);
            l.forEach(available::add);
        } else {
            for (var a : reg.asHolderIdMap()) {
                available.add(a);
            }
        }
        for (Holder<Enchantment> e : available.stream()
            .sorted((o1, o2) -> Names.get(o1)
                .compareToIgnoreCase(Names.get(o2))).toList()) {
            if (!filterText.isEmpty() && !Names.get(e).toLowerCase().startsWith(filterText.toLowerCase())) {
                continue;
            }
            table.add(theme.label(Names.get(e))).expandCellX();
            WButton a = table.add(theme.button("选择")).widget();
            a.action = () -> {
                callback.selection(new VillagerRoller.RollingEnchantment(reg.getKey(e.value()), e.value().getMaxLevel(),
                    VillagerRoller.getMinimumPrice(e), true));
                onClose();
            };
            table.row();
        }
    }

}
