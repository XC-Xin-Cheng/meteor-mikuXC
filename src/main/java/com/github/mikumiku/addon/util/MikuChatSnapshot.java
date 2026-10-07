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

import java.util.ArrayList;
import java.util.List;

/**
 * 「Miku 聊天」保存在 {@code Miku-Chat-old.txt} 里的一份<b>旧方案集</b>。
 *
 * <p>每次点「重置」都会把当时的整套方案追加成一个方案集，因此一个旧方案集 = 一次保存：
 * 一个序号 / 时间 + 当时所有方案。文件里用一行 {@code # =====} 分隔行把相邻两份隔开，
 * 分隔行由 {@link MikuChatStore} 负责读写成，这里只装解析结果。</p>
 */
public final class MikuChatSnapshot {

    /** 方案集序号（从 1 开始）；解析不出时由读取顺序决定，永远 &gt; 0。 */
    public final int index;
    /** 保存时的可读时间文本，例如 {@code 2026-02-14 12:34:56}；未知时为“时间未知”。 */
    public final String timeText;
    /** 这一份里保存的所有方案，顺序与当时一致。 */
    public final List<ChatScheme> schemes;

    public MikuChatSnapshot(int index, String timeText, List<ChatScheme> schemes) {
        this.index = Math.max(1, index);
        this.timeText = (timeText == null || timeText.isBlank()) ? "时间未知" : timeText;
        this.schemes = schemes == null ? new ArrayList<>() : schemes;
    }

    /** 列表里显示的一行标题，例如 {@code 第 3 次保存 · 2026-02-14 12:34:56}。 */
    public String title() {
        return "第 " + index + " 次保存 · " + timeText;
    }

    /** 这一份里有几套方案。 */
    public int size() {
        return schemes.size();
    }

    /** 这一份里有多少套是勾了启用的。 */
    public int enabledCount() {
        int count = 0;
        for (ChatScheme scheme : schemes) {
            if (scheme.enabled) count++;
        }
        return count;
    }

    /** {@code 35 套（12 套启用）} 这样的摘要，用在列表的“方案数”一列。 */
    public String summary() {
        return schemes.size() + " 套（" + enabledCount() + " 套启用）";
    }

    @Override
    public String toString() {
        return title() + " " + summary();
    }
}
