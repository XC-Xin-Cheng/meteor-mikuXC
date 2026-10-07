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

import java.util.concurrent.atomic.AtomicLong;

public class DebounceLimiter {
    private final AtomicLong lastRun = new AtomicLong(0);
    private final long intervalNanos; // 防抖间隔，单位纳秒

    public DebounceLimiter(long intervalMillis) {
        this.intervalNanos = intervalMillis * 1_000_000;
    }

    /**
     * 尝试执行，如果1秒内已经执行过则跳过
     */
    public void run(Runnable action) {
        long now = System.nanoTime();
        long last = lastRun.get();

        // 如果距离上次执行不足 interval，则跳过
        if (now - last < intervalNanos) {
            return;
        }

        // CAS 确保并发下只有一个线程能通过
        if (lastRun.compareAndSet(last, now)) {
            try {
                action.run();
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
    }

    public void tt(Runnable action, long intervalMillis) {
        DebounceLimiter limiter = new DebounceLimiter(1000); // 1秒防抖

        // 假设多个线程都在调用：
        limiter.run(() -> {
            System.out.println("执行一次: " + System.currentTimeMillis());
        });
    }
}
