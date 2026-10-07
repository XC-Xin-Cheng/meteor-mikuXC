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
import com.github.mikumiku.addon.util.MikuCompat;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.screens.settings.ColorSettingScreen;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WSection;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.gui.widgets.pressable.WCheckbox;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 矿石选择界面：按浅层（Y≥0 石头）、深层（Y&lt;0 深板岩）、下界分成三段，
 * 每段标题右边有一个全选勾选框（一次勾上这一层全部矿石）。
 * 段内每行是“矿石名 + 颜色块 + 边框连接 + 显示勾选框”：
 * 点颜色块打开取色器，连接勾选框决定边框是否与相邻同类矿石连成一体，
 * 显示勾选框决定这种矿石画不画、挖不挖。
 *
 * <p>和「Miku 聊天」一样，选择会实时保存到游戏根目录 {@code meteor-miku} 文件夹里的
 * {@code Select-Ore.txt}（格式：层 矿石 连接 显示，例如 {@code 深层 绿宝石 on on}）；
 * 底部的按钮可以手动保存、从文件加载、重置（重置前会把当前选择存进
 * {@code Select-Ore-old.txt}）以及查看旧选择方案集。</p>
 */
public class OreSelectScreen extends WindowScreen {
    private final OreListSetting setting;
    private final WTextBox filter;
    private final WVerticalList list;
    private String filterText = "";

    public OreSelectScreen(GuiTheme theme, OreListSetting setting) {
        super(theme, "选择矿石");
        this.setting = setting;

        this.filter = add(theme.textBox("", "搜索")).minWidth(400).expandX().widget();
        this.filter.action = () -> {
            this.filterText = filter.get().trim().toLowerCase(Locale.ROOT);
            rebuild();
        };

        add(theme.label("颜色：点色块选择；连接：相邻同类矿石边框连成一体；显示：勾选才显示/挖掘。"));

        this.list = add(theme.verticalList()).expandX().widget();

        add(theme.label("选择会实时保存到游戏根目录 meteor-miku 文件夹的 " + OreSelectStore.FILE_NAME
            + "（一行一种：层 矿石 连接 显示，例如「深层 绿宝石 on on」）。"));

        WHorizontalList buttons = add(theme.horizontalList()).expandX().widget();

        WButton saveLocal = buttons.add(theme.button("保存到本地")).widget();
        saveLocal.tooltip = "把当前选择写入 " + OreSelectStore.FILE_NAME
            + "；备份文件 " + OreSelectStore.OLD_FILE_NAME + " 还不存在时会建出第一份（已存在则原样保留）";
        saveLocal.action = () -> {
            if (OreSelectStore.save(OreSelectStore.mainFile(), setting)) {
                OreSelectStore.ensureOldFile(setting);
                ChatUtils.info("矿石选择已保存到 " + OreSelectStore.FILE_NAME);
            } else {
                ChatUtils.warning("保存失败，请检查 meteor-miku 文件夹是否可写");
            }
        };

        WButton loadLocal = buttons.add(theme.button("从本地文件加载")).widget();
        loadLocal.tooltip = "读回 " + OreSelectStore.FILE_NAME + " 里的选择并替换当前选择；颜色不变";
        loadLocal.action = () -> {
            if (OreSelectStore.load(OreSelectStore.mainFile(), setting)) {
                rebuild();
                ChatUtils.info("已从 " + OreSelectStore.FILE_NAME + " 载入矿石选择");
            } else {
                ChatUtils.warning(OreSelectStore.FILE_NAME + " 里没有能识别的矿石行，当前选择保持不变");
            }
        };

        WButton reset = buttons.add(theme.button("重置")).widget();
        reset.tooltip = "恢复成默认选择；会先把当前选择追加进 " + OreSelectStore.OLD_FILE_NAME;
        reset.action = () -> {
            boolean backedUp = OreSelectStore.appendSnapshot(setting, OreSelectStore.DEFAULT_MAX_SNAPSHOTS);
            setting.reset();
            OreSelectStore.save(OreSelectStore.mainFile(), setting);
            rebuild();

            if (backedUp) {
                ChatUtils.info("已把重置前的选择存进 " + OreSelectStore.OLD_FILE_NAME + "，并恢复默认选择");
            } else {
                ChatUtils.warning("恢复默认选择了，但写 " + OreSelectStore.OLD_FILE_NAME + " 失败，重置前的选择没能存下来");
            }
        };

        WHorizontalList buttons2 = add(theme.horizontalList()).expandX().widget();

        WButton openConfig = buttons2.add(theme.button("打开配置文件")).widget();
        openConfig.tooltip = "用系统默认程序打开 " + OreSelectStore.FILE_NAME
            + "，改完再点「从本地文件加载」读回来；文件不存在会先按当前选择生成一份";
        openConfig.action = () -> {
            if (OreSelectStore.openConfigFile(setting)) {
                ChatUtils.info("已打开配置文件：" + OreSelectStore.mainFile().getAbsolutePath());
            } else {
                ChatUtils.warning("打不开配置文件，请手动前往：" + OreSelectStore.mainFile().getAbsolutePath());
            }
        };

        WButton loadOld = buttons2.add(theme.button("加载旧选择方案集")).widget();
        loadOld.tooltip = "打开新界面查看 " + OreSelectStore.OLD_FILE_NAME
            + " 里保存过的每一份旧选择，可以挑一份恢复成当前选择";
        loadOld.action = () -> {
            List<OreSelectSnapshot> history = OreSelectStore.loadHistory();
            if (history.isEmpty()) {
                ChatUtils.warning("还没有旧选择方案集：点「重置」时才会把当时的选择存一份");
                return;
            }
            MikuCompat.setScreen(new OreHistoryScreen(theme, setting, this::rebuild));
        };
    }

    @Override
    public void initWidgets() {
        filter.setFocused(true);
        rebuild();
    }

    /** 勾选/改色后统一走这里：先通知设置变了，再把当前选择实时写回 Select-Ore.txt。 */
    private void changed() {
        setting.onChanged();
        OreSelectStore.save(OreSelectStore.mainFile(), setting);
    }

    private void rebuild() {
        list.clear();

        for (Ore.OreLayer layer : Ore.OreLayer.values()) {
            List<Ore.OreType> types = new ArrayList<>();
            for (Ore.OreType type : Ore.OreType.inLayer(layer)) {
                if (matches(type)) types.add(type);
            }
            if (types.isEmpty()) continue;

            Set<Ore.OreKey> selected = setting.get();
            List<Ore.OreKey> keys = new ArrayList<>(types.size());
            for (Ore.OreType type : types) keys.add(Ore.OreKey.of(type, layer));

            // 段标题右边的全选勾选框
            WCheckbox master = theme.checkbox(selected.containsAll(keys));
            master.tooltip = "一次勾上/取消这一层的全部矿石";
            master.action = () -> {
                Set<Ore.OreKey> current = setting.get();
                if (master.checked) current.addAll(keys);
                else current.removeAll(keys);
                changed();
                rebuild();
            };

            WSection section = list.add(theme.section(layer.displayName, true, master)).expandX().widget();
            WTable table = section.add(theme.table()).expandX().widget();

            // 表头，对应下面每一行的三个控件
            table.add(theme.label("")).expandCellX().widget();
            table.add(theme.label("颜色")).widget();
            table.add(theme.label("连接")).widget();
            table.add(theme.label("显示")).widget();
            table.row();

            for (int i = 0; i < types.size(); i++) {
                Ore.OreType type = types.get(i);
                Ore.OreKey key = keys.get(i);
                Ore.OreStyle style = setting.style(key);

                table.add(theme.label(type.displayName)).expandCellX().widget();

                WOreColorSwatch swatch = table.add(new WOreColorSwatch(style.color)).widget();
                swatch.action = () -> openColorPicker(key);

                WCheckbox connected = table.add(theme.checkbox(style.connected)).widget();
                connected.tooltip = "边框是否与相邻的同类矿石连成一体";
                connected.action = () -> {
                    style.connected = connected.checked;
                    changed();
                };

                WCheckbox box = table.add(theme.checkbox(selected.contains(key))).widget();
                box.tooltip = "勾选后才显示/挖掘这种矿石";
                box.action = () -> {
                    Set<Ore.OreKey> current = setting.get();
                    if (box.checked) current.add(key);
                    else current.remove(key);
                    changed();
                    rebuild();
                };

                table.row();
            }
        }
    }

    /** 打开 Meteor 自带的取色器，改完直接写回这个矿石的样式。 */
    private void openColorPicker(Ore.OreKey key) {
        Ore.OreStyle style = setting.style(key);
        ColorSetting picker = new ColorSetting(
            key.type.displayName + "·" + key.layer.displayName + " 颜色",
            "选择这种矿石边框的颜色",
            new SettingColor(style.color.r, style.color.g, style.color.b, style.color.a),
            color -> {
                style.color.set(color);
                changed();
            },
            null,
            null
        );
        MikuCompat.setScreen(new ColorSettingScreen(theme, picker));
    }

    private boolean matches(Ore.OreType type) {
        if (filterText.isEmpty()) return true;
        return type.displayName.toLowerCase(Locale.ROOT).contains(filterText)
            || type.mineName.toLowerCase(Locale.ROOT).contains(filterText)
            || type.name().toLowerCase(Locale.ROOT).contains(filterText);
    }
}
