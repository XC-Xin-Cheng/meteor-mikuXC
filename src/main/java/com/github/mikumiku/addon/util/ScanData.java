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

import java.util.List;

/**
 * Read-only-from-the-HUD snapshot of the last pre-mining chunk scan. {@code MassExtractor} runs the
 * scan once when it's activated (on the client/main thread) and writes the ranked results here; the
 * element just reads them. All fields are {@code volatile} since the writer
 * (module tick) and reader (HUD render) are different threads.
 */
public final class ScanData {
    /**
     * One ranked row: a display name (e.g. "Deepslate", "Iron Ore") and how many were found.
     */
    public record Count(String name, int count) {
    }

    public static volatile List<Count> topBlocks = List.of(); // up to 3 most abundant non-ore blocks
    public static volatile List<Count> topOres = List.of(); // up to 5 most abundant ores
    public static volatile String summary = "";               // e.g. "9 chunks · y-59..-50"
    public static volatile boolean valid = false;             // a scan has run this/last activation

    private ScanData() {
    }

    /**
     * Forget the last scan (called when the module re-activates, before the new scan runs).
     */
    public static void clear() {
        topBlocks = List.of();
        topOres = List.of();
        summary = "";
        valid = false;
    }

    /**
     * Counts run into the 100k+ for deepslate; cap the displayed number to 6 digits as requested.
     */
    public static String fmt(int n) {
        return n > 999999 ? "999999+" : Integer.toString(n);
    }
}
