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

import com.github.mikumiku.addon.util.ChatScheme;
import com.github.mikumiku.addon.util.MikuChatSnapshot;
import com.github.mikumiku.addon.util.MikuChatStore;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;

/**
 * 一份旧方案集的详情界面：只读地列出那一份里保存的所有方案（启用状态、方案名、
 * 间隔、消息、挑选方式），底部有一个「恢复这一份」按钮，点了才会真的替换当前方案。
 *
 * <p>从 {@link ChatHistoryScreen} 打开，关闭后自动回到那个列表界面。恢复动作走
 * {@link #setOnRestore(Runnable)} 注册的回调，由列表界面负责写回主文件并刷新设置界面。</p>
 */
public class ChatSnapshotScreen extends WindowScreen {

    /** 消息列最多显示多少个字符，太长的截断，免得窗口被撑得特别宽。 */
    private static final int MAX_MESSAGE_PREVIEW = 60;

    private final MikuChatSnapshot snapshot;
    private Runnable onRestore;

    public ChatSnapshotScreen(GuiTheme theme, MikuChatSnapshot snapshot) {
        super(theme, "旧方案集详情");
        this.snapshot = snapshot;
    }

    /** 注册「恢复这一份」要执行的动作，由打开这个界面的列表界面传入。 */
    public void setOnRestore(Runnable onRestore) {
        this.onRestore = onRestore;
    }

    @Override
    public void initWidgets() {
        add(theme.label(snapshot.title() + "：共 " + snapshot.schemes.size() + " 套方案"));
        add(theme.label("这里只是查看，点下面的按钮才会用它替换当前方案。"));

        WTable table = add(theme.table()).expandX().widget();
        table.add(theme.label("启用")).widget();
        table.add(theme.label("方案名")).widget();
        table.add(theme.label("间隔")).widget();
        table.add(theme.label("消息")).expandCellX().widget();
        table.add(theme.label("挑选")).widget();
        table.row();

        if (snapshot.schemes.isEmpty()) {
            table.add(theme.label("这一份记录里没有能识别的方案。")).expandCellX().widget();
            table.row();
        }

        for (ChatScheme scheme : snapshot.schemes) {
            table.add(theme.label(scheme.enabled ? "启用" : "停用")).widget();
            table.add(theme.label(scheme.name)).widget();
            table.add(theme.label(scheme.intervalText())).widget();
            table.add(theme.label(shorten(scheme.messagesAsText()))).expandCellX().widget();
            table.add(theme.label(pickText(scheme))).widget();
            table.row();
        }

        WButton restore = add(theme.button("恢复这一份")).widget();
        restore.tooltip = "用这一份旧方案集替换当前方案，并写回 " + MikuChatStore.FILE_NAME;
        restore.action = () -> {
            if (onRestore != null) onRestore.run();
            onClose();
        };
    }

    /** 一句话说明这套方案怎么挑消息。 */
    private static String pickText(ChatScheme scheme) {
        if (!scheme.random) return "顺序";
        return scheme.avoidRepeat ? "随机·不重复" : "随机";
    }

    /** 消息太长就截断，只留前 {@value #MAX_MESSAGE_PREVIEW} 个字符。 */
    private static String shorten(String text) {
        if (text == null) return "";
        String trimmed = text.trim();
        if (trimmed.length() <= MAX_MESSAGE_PREVIEW) return trimmed;
        return trimmed.substring(0, MAX_MESSAGE_PREVIEW) + "…";
    }
}
