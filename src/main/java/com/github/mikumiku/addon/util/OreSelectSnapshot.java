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

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 「种子矿透」矿石选择保存在 {@code Select-Ore-old.txt} 里的一份<b>旧选择方案集</b>。
 *
 * <p>每次点矿石选择界面里的「重置」都会把当时的选择追加成一个方案集，因此一份旧方案集 =
 * 一次保存：一个序号 / 时间 + 当时哪些矿石显示、哪些开了边框连接。颜色属于 Meteor 自己的
 * 配置，不进这个文件，所以恢复旧方案集时只覆盖「显示」和「连接」两项。</p>
 */
public final class OreSelectSnapshot {

    /** 方案集序号（从 1 开始）；解析不出时由读取顺序决定，永远 &gt; 0。 */
    public final int index;
    /** 保存时的可读时间文本，例如 {@code 2026-02-14 12:34:56}；未知时为“时间未知”。 */
    public final String timeText;
    /** 这一份里勾了「显示」的“矿石 + 层”。 */
    public final Set<Ore.OreKey> selected;
    /** 这一份里勾了「连接」的“矿石 + 层”。 */
    public final Set<Ore.OreKey> connected;

    public OreSelectSnapshot(int index, String timeText, Set<Ore.OreKey> selected, Set<Ore.OreKey> connected) {
        this.index = Math.max(1, index);
        this.timeText = (timeText == null || timeText.isBlank()) ? "时间未知" : timeText;
        this.selected = selected == null ? new LinkedHashSet<>() : selected;
        this.connected = connected == null ? new LinkedHashSet<>() : connected;
    }

    /** 列表里显示的一行标题，例如 {@code 第 3 次保存 · 2026-02-14 12:34:56}。 */
    public String title() {
        return "第 " + index + " 次保存 · " + timeText;
    }

    /** {@code 12 种显示（5 种连接）} 这样的摘要，用在列表的“内容”一列。 */
    public String summary() {
        return selected.size() + " 种显示（" + connected.size() + " 种连接）";
    }

    @Override
    public String toString() {
        return title() + " " + summary();
    }
}
