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
package com.github.mikumiku.addon.util.timer;

public class StandardTimer implements Timer {
    private long time = System.currentTimeMillis();
    private final long tickTimeMs;

    StandardTimer() {
        this.tickTimeMs = 1L;
    }

    StandardTimer(final long tickTimeMs) {
        this.tickTimeMs = tickTimeMs;
    }

    public void reset() {
        this.time = System.currentTimeMillis();
    }

    public void skip() {
        this.time = 0L;
    }

    public boolean tick(final long delay) {
        return tick(delay, true);
    }

    public boolean tick(final long delay, final boolean resetIfTick) {
        if (System.currentTimeMillis() - this.time > delay * tickTimeMs) {
            if (resetIfTick) this.time = System.currentTimeMillis();
            return true;
        } else {
            return false;
        }
    }
}
