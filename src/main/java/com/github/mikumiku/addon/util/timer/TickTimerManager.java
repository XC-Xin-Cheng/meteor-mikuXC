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

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.orbit.EventHandler;

public final class TickTimerManager {
    // before any modules
    public static final int TICK_PRIORITY = Integer.MAX_VALUE - 1;
    public static final TickTimerManager INSTANCE = new TickTimerManager();

    // 上游用 lombok @Getter，此处等价展开为显式访问器
    private volatile long tickTime = 0;

    public long getTickTime() {
        return tickTime;
    }

    private TickTimerManager() {
        // 注册到事件总线
        MeteorClient.EVENT_BUS.subscribe(this);

//        ClientTickEvents.END_CLIENT_TICK.register(client -> {
//            this.onClientTick();
//        });
    }

    private void onClientTick() {
        tickTime++;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        // 每 tick 执行
        this.onClientTick();
    }
}
