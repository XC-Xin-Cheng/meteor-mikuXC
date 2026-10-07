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
package com.github.mikumiku.addon.modules;

import com.github.mikumiku.addon.BaseModule;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.pathing.NopPathManager;
import meteordevelopment.meteorclient.pathing.PathManagers;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.utils.misc.input.Input;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;

public class AutoWalk extends BaseModule {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    protected Minecraft mc;

    // 慢速行走的内部变量
    private int slowWalkTickCounter = 0;
    private static final int SLOW_WALK_CYCLE = 20; // 固定周期为20 tick (1秒)

    private final Setting<WalkMode> mode = sgGeneral.add(new EnumSetting.Builder<WalkMode>()
        .name("模式")
        .description("行走模式。")
        .defaultValue(WalkMode.Smart)
        .onChanged(walkMode1 -> {
            if (isActive()) {
                if (walkMode1 == WalkMode.Simple) {
                    PathManagers.get().stop();
                } else {
                    createGoal();
                }

                unpress(true);
            }
        })
        .build()
    );

    private final Setting<WalkDirection> direction = sgGeneral.add(new EnumSetting.Builder<WalkDirection>()
        .name("简单模式方向")
        .description("简单模式下的行走方向。")
        .defaultValue(WalkDirection.Forwards)
        .onChanged(walkDirection1 -> {
            if (isActive()) unpress(false);
        })
        .visible(() -> mode.get() == WalkMode.Simple)
        .build()
    );

    private final Setting<Boolean> sprint = sgGeneral.add(new BoolSetting.Builder()
        .name("疾跑")
        .description("行走时疾跑。")
        .defaultValue(false)
        .visible(() -> mode.get() == WalkMode.Simple)
        .build()
    );

    private final Setting<Boolean> slowWalk = sgGeneral.add(new BoolSetting.Builder()
        .name("慢速行走")
        .description("启用慢速行走模式。")
        .defaultValue(false)
        .visible(() -> mode.get() == WalkMode.Simple)
        .build()
    );

    private final Setting<Integer> slowWalkPressDuration = sgGeneral.add(new IntSetting.Builder()
        .name("按键持续时间")
        .description("慢速行走时每20刻周期内按键持续的刻数。数值越小越慢。")
        .defaultValue(1)
        .min(1)
        .max(19)
        .sliderMax(20)
        .visible(() -> mode.get() == WalkMode.Simple && slowWalk.get())
        .build()
    );

    public AutoWalk() {
        super("自动走路", "自动走路. 增加了一个慢速模式. 方便");
        mc = Minecraft.getInstance();
    }

    @Override
    public void onActivate() {
        slowWalkTickCounter = 0;
        if (mode.get() == WalkMode.Smart) createGoal();
    }

    @Override
    public void onDeactivate() {
        if (mode.get() == WalkMode.Simple) unpress(true);
        else PathManagers.get().stop();
    }

    @EventHandler(priority = EventPriority.HIGH)
    private void onTick(TickEvent.Pre event) {
        if (mode.get() == WalkMode.Simple) {
            // 处理慢速行走时机
            if (slowWalk.get()) {
                slowWalkTickCounter++;

                // 在周期内的前 slowWalkPressDuration 刻按住按键
                if (slowWalkTickCounter <= slowWalkPressDuration.get()) {
                    handleMovement();
                } else {
                    // 剩余的刻数释放按键
                    unpress(false);
                }

                // 周期结束，重置计数器
                if (slowWalkTickCounter >= SLOW_WALK_CYCLE) {
                    slowWalkTickCounter = 0;
                }
            } else {
                // 正常速度移动
                handleMovement();
            }
        } else {
            if (PathManagers.get() instanceof NopPathManager) {
                info("智能模式需要 Baritone");
                toggle();
            }
        }
    }

    private void handleMovement() {
        // 处理移动方向
        switch (direction.get()) {
            case Forwards -> setPressed(mc.options.keyUp, true);
            case Backwards -> setPressed(mc.options.keyDown, true);
            case Left -> setPressed(mc.options.keyLeft, true);
            case Right -> setPressed(mc.options.keyRight, true);
        }

        // 处理疾跑（只有在用户明确选择疾跑时才启用）
        if (sprint.get()) {
            setPressed(mc.options.keySprint, sprint.get());
        }
    }

    private void unpress(boolean sprintChange) {
        setPressed(mc.options.keyUp, false);
        setPressed(mc.options.keyDown, false);
        setPressed(mc.options.keyLeft, false);
        setPressed(mc.options.keyRight, false);
        if (sprintChange) {
            setPressed(mc.options.keySprint, false);
        }
    }

    private void setPressed(KeyMapping key, boolean pressed) {
        key.setDown(pressed);
        Input.setKeyState(key, pressed);
    }

    private void createGoal() {
        PathManagers.get().moveInDirection(mc.player.getYRot());
    }


}
