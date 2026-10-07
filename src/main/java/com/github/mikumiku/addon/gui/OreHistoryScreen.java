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

import com.github.mikumiku.addon.util.Ore;
import com.github.mikumiku.addon.util.OreListSetting;
import com.github.mikumiku.addon.util.OreSelectSnapshot;
import com.github.mikumiku.addon.util.OreSelectStore;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.utils.player.ChatUtils;

import java.util.List;
import java.util.Set;

/**
 * 「加载旧选择方案集」界面：把 {@code Select-Ore-old.txt} 里保存过的每一份旧矿石选择
 * 列成一张表，最新的排在最上面。每行可以「恢复」——用这一份旧选择替换当前选择，
 * 并写回 {@code Select-Ore.txt}。
 *
 * <p>恢复后用构造时传进来的回调把矿石选择界面重建一遍，回到界面看到的就是恢复好的内容。</p>
 */
public class OreHistoryScreen extends WindowScreen {

    private final OreListSetting setting;
    private final Runnable onRestored;

    public OreHistoryScreen(GuiTheme theme, OreListSetting setting, Runnable onRestored) {
        super(theme, "加载旧选择方案集");
        this.setting = setting;
        this.onRestored = onRestored;
    }

    @Override
    public void initWidgets() {
        add(theme.label("每次点「重置」都会把当时的矿石选择存一份进来，最新的排在最上面。"));
        add(theme.label("点「恢复」用这一份旧选择替换掉当前选择（颜色不变）。"));

        WTable table = add(theme.table()).expandX().widget();
        fill(table);

        WButton refresh = add(theme.button("刷新")).widget();
        refresh.tooltip = "重新读一遍 " + OreSelectStore.OLD_FILE_NAME;
        refresh.action = () -> {
            table.clear();
            fill(table);
        };
    }

    /** 把历史方案集倒着填进表里（文件里最新的在最后，界面上最新的在最上面）。 */
    private void fill(WTable table) {
        List<OreSelectSnapshot> history = OreSelectStore.loadHistory();

        table.add(theme.label("旧选择方案集")).expandCellX().widget();
        table.add(theme.label("内容")).widget();
        table.add(theme.label("恢复")).widget();
        table.row();

        if (history.isEmpty()) {
            table.add(theme.label("还没有旧选择方案集。")).expandCellX().widget();
            table.row();
            return;
        }

        for (int i = history.size() - 1; i >= 0; i--) {
            OreSelectSnapshot snapshot = history.get(i);

            table.add(theme.label(snapshot.title())).expandCellX().widget();
            table.add(theme.label(snapshot.summary())).widget();

            WButton restore = table.add(theme.button("恢复")).widget();
            restore.tooltip = "用这一份旧选择替换当前选择，并写回 " + OreSelectStore.FILE_NAME;
            restore.action = () -> {
                apply(snapshot);
                onClose();
            };

            table.row();
        }
    }

    /** 把一份旧选择换回当前选择，写回主文件，并让选择界面重建。 */
    private void apply(OreSelectSnapshot snapshot) {
        for (Ore.OreKey key : OreSelectStore.allKeys()) {
            setting.style(key).connected = snapshot.connected.contains(key);
        }

        Set<Ore.OreKey> current = setting.get();
        current.clear();
        current.addAll(snapshot.selected);
        setting.onChanged();

        if (OreSelectStore.save(OreSelectStore.mainFile(), setting)) {
            ChatUtils.info("已恢复旧选择方案集「" + snapshot.title() + "」，共 "
                + snapshot.selected.size() + " 种显示");
        } else {
            ChatUtils.warning("选择已恢复，但写回 " + OreSelectStore.FILE_NAME + " 失败");
        }

        if (onRestored != null) onRestored.run();
    }
}
