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

public class Timer {
    private long time = -1;

    public Timer() {
        this.reset();
    }

    public Timer reset() {
        this.time = System.nanoTime();
        return this;
    }

    public boolean tick(int tick) {
        return this.passedMs(tick * 50);
    }

    public boolean passedTicks(int tick) {
        return this.passedMs(tick * 50);
    }

    public boolean passedS(double s) {
        return this.passedMs(s * 1000);
    }

    public boolean passedMs(long ms) {
        return this.passedNS(this.convertToNS(ms));
    }

    public boolean passedMs(double ms) {
        return this.passedMs((long) ms);
    }

    public boolean passed(long ms) {
        return this.passedMs(ms);
    }

    public boolean passed(double ms) {
        return this.passedMs((long) ms);
    }

    public void setMs(long ms) {
        this.time = System.nanoTime() - this.convertToNS(ms);
    }

    public boolean passedNS(long ns) {
        return System.nanoTime() - this.time >= ns;
    }

    public long getPassedTimeMs() {
        return this.getMs(System.nanoTime() - this.time);
    }

    public long getMs(long time) {
        return time / 1000000;
    }

    public long convertToNS(long time) {
        return time * 1000000;
    }
}
