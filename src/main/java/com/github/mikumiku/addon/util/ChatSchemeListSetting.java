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

import com.github.mikumiku.addon.gui.ChatHistoryScreen;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.utils.CharFilter;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.input.WDropdown;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.gui.widgets.pressable.WCheckbox;
import meteordevelopment.meteorclient.gui.widgets.pressable.WMinus;
import meteordevelopment.meteorclient.gui.widgets.pressable.WPlus;
import meteordevelopment.meteorclient.settings.IVisible;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntSupplier;

/**
 * 「Miku 聊天」的聊天方案列表设置。
 *
 * <p>配置界面里直接展开成一张表：一行一套方案，能勾启用、改名字、填间隔、
 * 写多条消息（用 {@code |} 分隔）、切顺序/随机、开关 {@code %s} 替换，
 * 也能加/删方案。和 {@link ClickProfileListSetting} 保持同一套内嵌表格风格。</p>
 *
 * <p>「间隔」列后面紧跟一列单位下拉框（毫秒/ms、秒/s、分/min），<b>每套方案各选各的</b>：
 * 毫秒最长 10 分钟、秒最长 5 小时、分最长 10 小时。换档只改这一套方案显示与输入的口径，
 * 方案里存的始终是毫秒，所以随便切也不会把原来的间隔写坏。</p>
 *
 * <p>表下方的「重置」会把当前整套方案追加进 {@code Miku-Chat-old.txt} 再恢复默认方案；
 * 「加载旧方案集」会打开一个新的界面，列出文件里保存过的每一份旧方案集，可以逐份查看
 * 里面的方案，也可以挑一份恢复成当前方案。</p>
 */
public class ChatSchemeListSetting extends Setting<List<ChatScheme>> {

    /** 只允许数字的输入过滤器（间隔这类整数框用）。 */
    private static final CharFilter DIGITS = (text, c) -> c >= '0' && c <= '9';

    /** 「Miku-Chat-old.txt 最多留几份旧方案集」的取值来源，由模块的设置项接进来。 */
    private IntSupplier historyLimit;

    public ChatSchemeListSetting(String name, String description, List<ChatScheme> defaultValue,
                                 Consumer<List<ChatScheme>> onChanged,
                                 Consumer<Setting<List<ChatScheme>>> onModuleActivated,
                                 IVisible visible) {
        super(name, description, defaultValue, onChanged, onModuleActivated, visible);
    }

    /** 当前允许保留的旧方案集份数，限制在 1 ~ {@link MikuChatStore#HARD_MAX_SNAPSHOTS}。 */
    public int historyLimit() {
        int value = MikuChatStore.DEFAULT_MAX_SNAPSHOTS;
        if (historyLimit != null) {
            try {
                value = historyLimit.getAsInt();
            } catch (Throwable ignored) {
                // 设置项还没准备好就按默认值来
            }
        }
        return Math.max(1, Math.min(MikuChatStore.HARD_MAX_SNAPSHOTS, value));
    }

    @Override
    public List<ChatScheme> get() {
        if (value == null) value = new ArrayList<>();
        return value;
    }

    /**
     * 字符串形式（命令/配置文本里用）：方案之间用 {@code ;} 分隔，
     * 单套格式为 {@code 名称:间隔ms:顺序(seq/random):消息1|消息2}。
     */
    @Override
    protected List<ChatScheme> parseImpl(String str) {
        List<ChatScheme> list = new ArrayList<>();
        for (String entry : str.split(";")) {
            String part = entry.trim();
            if (part.isEmpty()) continue;

            String[] fields = part.split(":", 4);
            ChatScheme scheme = new ChatScheme(fields[0].trim());

            if (fields.length > 1) {
                try {
                    scheme.intervalMs = ChatScheme.clampIntervalMs(Integer.parseInt(fields[1].trim()));
                } catch (NumberFormatException ignored) {
                }
            }
            if (fields.length > 2) scheme.random = fields[2].trim().equalsIgnoreCase("random");
            if (fields.length > 3) scheme.setMessagesFromText(fields[3]);

            list.add(scheme);
        }
        return list;
    }

    @Override
    protected boolean isValueValid(List<ChatScheme> value) {
        return true;
    }

    @Override
    protected void resetImpl() {
        value = new ArrayList<>();
        if (defaultValue != null) {
            for (ChatScheme scheme : defaultValue) value.add(scheme.copy());
        }
    }

    @Override
    protected CompoundTag save(CompoundTag tag) {
        ListTag listTag = new ListTag();
        for (ChatScheme scheme : get()) listTag.add(scheme.toTag());
        tag.put("schemes", listTag);
        return tag;
    }

    @Override
    protected List<ChatScheme> load(CompoundTag tag) {
        List<ChatScheme> list = new ArrayList<>();
        // r72 及以前的单位是全局一份存在 "intervalUnit" 里的；现在改成每套方案各存各的，
        // 读旧配置时拿它给还没带单位的方案兜底，之后不再写回这个全局字段。
        ChatIntervalUnit legacyUnit =
            ChatIntervalUnit.byName(tag.getStringOr("intervalUnit", ChatIntervalUnit.MILLIS.name()));
        ListTag listTag = tag.getList("schemes").orElse(new ListTag());
        for (Tag entry : listTag) {
            entry.asCompound().ifPresent(compound -> list.add(ChatScheme.fromTag(compound, legacyUnit)));
        }

        // Setting.fromTag 不会替我们写回 value，这里必须自己 set。
        set(list);
        return list;
    }

    private static int parseInt(String text, int fallback) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /**
     * 把聊天方案列表画成一张可编辑的表。每次增删方案、切换间隔单位都会重建整张表。
     */
    public static void fillTable(GuiTheme theme, WTable table, ChatSchemeListSetting setting) {
        table.clear();

        List<ChatScheme> schemes = setting.get();

        // 表头，列数必须和下面每行一一对应
        table.add(theme.label("启用")).widget();
        table.add(theme.label("方案名")).widget();
        table.add(theme.label("间隔")).widget();
        // 单位紧跟「间隔」一列：下拉框在每一行的间隔数值后面，每套方案各选各的
        table.add(theme.label("单位")).widget();

        // 消息列表头也要 expandCellX：数据行里消息输入框是 expandX 的，会额外吃掉表格剩余宽度，
        // 于是「随机 / 不重复 / 删除」三格会比表头整体往右偏一截（截图里红色箭头指的就是这里）。
        // 表头这一格跟着一起撑开，表头和下面的勾选框就落在同一条竖线上了。
        table.add(theme.label("消息（多条用 | 分隔，%s=可选附近玩家名）")).expandCellX().widget();
        table.add(theme.label("随机")).widget();
        table.add(theme.label("不重复")).widget();
        table.add(theme.label("")).widget();
        table.row();

        for (ChatScheme scheme : schemes) {
            WCheckbox enabled = table.add(theme.checkbox(scheme.enabled)).widget();
            enabled.tooltip = "勾上后这套方案才会发消息；多套方案可以同时启用";
            enabled.action = () -> {
                scheme.enabled = enabled.checked;
                setting.onChanged();
            };

            WTextBox nameBox = table.add(theme.textBox(scheme.name)).minWidth(70).widget();
            nameBox.tooltip = "方案名，只用于区分不同方案";
            nameBox.actionOnUnfocused = () -> {
                String text = nameBox.get().trim();
                if (!text.isEmpty()) {
                    scheme.name = text;
                    setting.onChanged();
                } else {
                    nameBox.set(scheme.name);
                }
            };

            // 间隔填的是「本套方案当前单位」下的数值，实际保存始终是毫秒
            ChatIntervalUnit unit = scheme.intervalUnit();
            WTextBox intervalBox = table.add(theme.textBox(Integer.toString(unit.toUnits(scheme.clampedIntervalMs())), DIGITS))
                .minWidth(50).widget();
            intervalBox.tooltip = "两条消息之间至少间隔多久，单位：" + unit.displayName()
                + "；本档范围 " + unit.rangeText() + "，方案里按毫秒保存";
            intervalBox.actionOnUnfocused = () -> {
                scheme.intervalMs = unit.toMs(parseInt(intervalBox.get(), unit.toUnits(scheme.intervalMs)));
                intervalBox.set(Integer.toString(unit.toUnits(scheme.intervalMs)));
                setting.onChanged();
            };

            // 单位下拉框就放在这一行间隔数值的右边（截图里蓝圈标的位置），每套方案各选各的
            WDropdown<ChatIntervalUnit> unitDropdown =
                table.add(theme.dropdown(ChatIntervalUnit.values(), unit)).widget();
            unitDropdown.tooltip = "点开切换这套方案的间隔单位：毫秒/ms、秒/s、分/min。"
                + "毫秒最长 10 分钟、秒最长 5 小时、分最长 10 小时；方案里始终按毫秒保存";
            unitDropdown.action = () -> {
                scheme.intervalUnit = unitDropdown.get();
                setting.onChanged();
                fillTable(theme, table, setting);
            };

            WTextBox messagesBox = table.add(theme.textBox(scheme.messagesAsText())).minWidth(200).expandX().widget();
            messagesBox.tooltip = "这套方案要发的消息；多条之间用 | 分隔。%s：开启「范围内搜索玩家」时替换成附近玩家名、找不到玩家就跳过这一轮；关闭时直接去掉 %s 后发送";
            messagesBox.actionOnUnfocused = () -> {
                scheme.setMessagesFromText(messagesBox.get());
                setting.onChanged();
            };

            WCheckbox random = table.add(theme.checkbox(scheme.random)).widget();
            random.tooltip = "勾上=每次随机挑一条；不勾=按顺序轮播";
            random.action = () -> {
                scheme.random = random.checked;
                setting.onChanged();
            };

            WCheckbox noRepeat = table.add(theme.checkbox(scheme.avoidRepeat)).widget();
            noRepeat.tooltip = "随机发送时避免和上一条重复（「自动社交」的挑选方式）；只在勾了随机时有效";
            noRepeat.action = () -> {
                scheme.avoidRepeat = noRepeat.checked;
                setting.onChanged();
            };

            WMinus delete = table.add(theme.minus()).widget();
            delete.tooltip = "删除这套方案";
            delete.action = () -> {
                schemes.remove(scheme);
                setting.onChanged();
                fillTable(theme, table, setting);
            };

            table.row();
        }

        if (!schemes.isEmpty()) {
            table.add(theme.horizontalSeparator()).expandX();
            table.row();
        }

        WButton reset = table.add(theme.button(GuiRenderer.RESET)).widget();
        reset.tooltip = "恢复成默认方案；会先把当前整套方案追加进游戏根目录 meteor-miku 文件夹的 "
            + MikuChatStore.OLD_FILE_NAME + "（最多留 " + setting.historyLimit() + " 份，超出丢最旧的）";
        reset.action = () -> {
            // 先把当前方案原样追加进 Miku-Chat-old.txt（不清空历史），再恢复默认方案
            List<ChatScheme> current = new ArrayList<>(setting.get());
            boolean backedUp = MikuChatStore.appendSnapshot(current, setting.historyLimit());
            setting.reset();
            MikuChatStore.save(MikuChatStore.mainFile(), setting.get());
            fillTable(theme, table, setting);

            if (backedUp) {
                ChatUtils.info("已把重置前的 " + current.size() + " 套方案存进 " + MikuChatStore.OLD_FILE_NAME
                    + "，并恢复默认方案");
            } else {
                ChatUtils.warning("恢复默认方案了，但写 " + MikuChatStore.OLD_FILE_NAME
                    + " 失败，重置前的方案没能存下来");
            }
        };

        WPlus add = table.add(theme.plus()).widget();
        add.tooltip = "添加一套新方案";
        add.action = () -> {
            schemes.add(new ChatScheme("方案 " + (schemes.size() + 1)));
            setting.onChanged();
            fillTable(theme, table, setting);
        };

        WButton openConfig = table.add(theme.button("打开配置文件")).widget();
        openConfig.tooltip = "用系统默认程序打开游戏根目录 meteor-miku 文件夹的 " + MikuChatStore.FILE_NAME
            + "，改完再点「从本地文件加载」读回来；文件不存在会先按当前方案生成一份";
        openConfig.action = () -> {
            if (MikuChatStore.openConfigFile(setting.get())) {
                ChatUtils.info("已打开配置文件：" + MikuChatStore.mainFile().getAbsolutePath());
            } else if (MikuChatStore.openFolder()) {
                ChatUtils.warning("打不开配置文件，已改为打开 meteor-miku 文件夹：" + MikuChatStore.storeDir().getAbsolutePath());
            } else {
                ChatUtils.warning("打不开配置文件，请手动前往：" + MikuChatStore.mainFile().getAbsolutePath());
            }
        };

        WButton saveLocal = table.add(theme.button("保存到本地")).widget();
        saveLocal.tooltip = "把当前所有方案写入游戏根目录 meteor-miku 文件夹的 " + MikuChatStore.FILE_NAME
            + "；备份文件 " + MikuChatStore.OLD_FILE_NAME + " 还不存在时会建出第一份旧方案集（已存在则原样保留，不会覆盖你的历史）";
        saveLocal.action = () -> {
            if (MikuChatStore.save(MikuChatStore.mainFile(), setting.get())) {
                // 两个方案文件都保证存在，用户去 meteor-miku 文件夹能直接看到
                MikuChatStore.ensureOldFile(setting.get());
                ChatUtils.info("Miku 聊天方案已保存到 " + MikuChatStore.FILE_NAME);
            } else {
                ChatUtils.warning("保存失败，请检查 meteor-miku 文件夹是否可写");
            }
        };

        WButton loadLocal = table.add(theme.button("从本地文件加载")).widget();
        loadLocal.tooltip = "从本地挑一个 " + MikuChatStore.FILE_NAME
            + " 格式的文本文件（一行一套方案），读进来替换当前方案；取消或选不了文件时会改读游戏根目录 meteor-miku 文件夹里的同名文件";
        loadLocal.action = () -> {
            ChatUtils.info("正在打开文件选择框，选一个方案文本文件…");
            MikuChatStore.chooseFileAsync(
                file -> Minecraft.getInstance().execute(() -> {
                    if (file != null) loadFromFile(file, theme, table, setting);
                }),
                () -> Minecraft.getInstance().execute(() -> {
                    ChatUtils.warning("打不开系统文件选择框，改读游戏根目录 meteor-miku 文件夹的 " + MikuChatStore.FILE_NAME);
                    loadFromFile(MikuChatStore.mainFile(), theme, table, setting);
                })
            );
        };

        WButton loadOld = table.add(theme.button("加载旧方案集")).widget();
        loadOld.tooltip = "打开新界面查看 " + MikuChatStore.OLD_FILE_NAME
            + " 里保存过的每一份旧方案集：可以逐份查看里面的方案，也可以挑一份恢复成当前方案";
        loadOld.action = () -> {
            List<MikuChatSnapshot> history = MikuChatStore.loadHistory();
            if (history.isEmpty()) {
                ChatUtils.warning("还没有旧方案集：点「重置」时才会把当时的整套方案存一份");
                return;
            }
            MikuCompat.setScreen(new ChatHistoryScreen(theme, setting, () -> fillTable(theme, table, setting)));
        };

        table.row();
    }

    /**
     * 从指定文本文件读回方案：读到至少 1 行就替换当前方案并重建表格，
     * 读不到就保持现状（避免把用户已经填好的方案清空）。
     *
     * @param file 要读的文件，可能是 {@code null}（用户取消选择）
     */
    private static void loadFromFile(File file, GuiTheme theme, WTable table, ChatSchemeListSetting setting) {
        if (file == null) return;

        if (!file.isFile()) {
            ChatUtils.warning("没找到文件：" + file.getAbsolutePath());
            return;
        }

        List<ChatScheme> loaded = MikuChatStore.load(file);
        if (loaded.isEmpty()) {
            ChatUtils.warning(file.getName() + " 里没有能识别的方案，当前方案保持不变");
            return;
        }

        List<ChatScheme> list = setting.get();
        list.clear();
        long now = System.currentTimeMillis();
        for (ChatScheme scheme : loaded) {
            scheme.resetRuntime(now);
            list.add(scheme);
        }
        setting.onChanged();

        fillTable(theme, table, setting);
        ChatUtils.info("已从 " + file.getName() + " 载入 " + loaded.size() + " 套聊天方案");
    }

    public static class Builder extends SettingBuilder<Builder, List<ChatScheme>, ChatSchemeListSetting> {
        private IntSupplier historyLimit;

        public Builder() {
            super(new ArrayList<>(0));
        }

        public Builder defaultValue(ChatScheme... defaults) {
            List<ChatScheme> list = new ArrayList<>();
            if (defaults != null) {
                for (ChatScheme scheme : defaults) list.add(scheme.copy());
            }
            return defaultValue(list);
        }

        /** 从模块的「旧方案集上限」设置项取值，决定 {@code Miku-Chat-old.txt} 里最多留几份。 */
        public Builder historyLimit(IntSupplier supplier) {
            this.historyLimit = supplier;
            return this;
        }

        @Override
        public ChatSchemeListSetting build() {
            ChatSchemeListSetting setting =
                new ChatSchemeListSetting(name, description, defaultValue, onChanged, onModuleActivated, visible);
            setting.historyLimit = historyLimit;
            return setting;
        }
    }
}
