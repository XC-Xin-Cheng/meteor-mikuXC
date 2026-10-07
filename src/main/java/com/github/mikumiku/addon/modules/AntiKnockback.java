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
import com.github.mikumiku.addon.util.Via;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.world.phys.Vec3;

public class AntiKnockback extends BaseModule {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgAdvanced = settings.createGroup("Advanced");

    // 基础设置
    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("模式")
        .description("选择反击退的工作模式")
        .defaultValue(Mode.Grim)
        .build()
    );

    private final Setting<Integer> horizontal = sgGeneral.add(new IntSetting.Builder()
        .name("水平强度")
        .description("水平方向击退减少百分比")
        .defaultValue(0)
        .min(0)
        .max(100)
        .sliderMax(100)
        .visible(() -> mode.get() == Mode.Custom)
        .build()
    );

    private final Setting<Integer> vertical = sgGeneral.add(new IntSetting.Builder()
        .name("垂直强度")
        .description("垂直方向击退减少百分比")
        .defaultValue(0)
        .min(0)
        .max(100)
        .sliderMax(100)
        .visible(() -> mode.get() == Mode.Custom)
        .build()
    );

    // 高级设置
    private final Setting<Boolean> explosions = sgAdvanced.add(new BoolSetting.Builder()
        .name("减少爆炸击退")
        .description("减少爆炸击退")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> moveStop = sgGeneral.add(new BoolSetting.Builder()
        .name("防卡脚")
        .description("防卡脚， 移动时不反击退")
        .defaultValue(false)
        .build()
    );


    private final Setting<Boolean> onlyGround = sgAdvanced.add(new BoolSetting.Builder()
        .name("只在地面时生效")
        .description("只在地面时生效")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> delay = sgAdvanced.add(new IntSetting.Builder()
        .name("延迟")
        .description("应用击退的延迟(tick)")
        .defaultValue(0)
        .min(0)
        .max(10)
        .sliderMax(10)
        .visible(() -> mode.get() == Mode.Grim)
        .build()
    );

    private final Setting<Boolean> moveCorrection = sgAdvanced.add(new BoolSetting.Builder()
        .name("移动修正")
        .description("移动修正,更好地绕过Grim")
        .defaultValue(true)
        .visible(() -> mode.get() == Mode.Grim)
        .build()
    );

    private double storedX, storedY, storedZ;
    private int tickDelay = 0;
    private boolean hasVelocity = false;

    public AntiKnockback() {
        super(BaseModule.CATEGORY_MIKU_COMBAT, "反击退++", "减少或取消击退效果");
    }

    @EventHandler
    private void onReceivePacket(PacketEvent.Receive event) {
        if (mc.player == null) return;
        if (moveStop.get()) {
            if (mc.options.keyDown.isDown()
                || mc.options.keyUp.isDown()
                || mc.options.keyLeft.isDown()
                || mc.options.keyRight.isDown()) {
                return;
            }
        }
        // 只在地面检查
        if (onlyGround.get() && !mc.player.onGround()) return;

        // 处理实体速度包
        if (event.packet instanceof ClientboundSetEntityMotionPacket packet) {
            if (packet.id() != mc.player.getId()) return;

            double h = horizontal.get() / 100.0;
            double v = vertical.get() / 100.0;

            switch (mode.get()) {
                case Cancel -> event.cancel();

                case Grim -> {
                    // Grim模式: 存储速度,延迟应用
                    event.cancel();
                    //TODO Via pa  packet.getVelocity().x
//                    storedX = packet.getVelocityX() / 8000.0;
//                    storedY = packet.getVelocityY() / 8000.0;
//                    storedZ = packet.getVelocityZ() / 8000.0;

                    tickDelay = delay.get();
                    hasVelocity = true;
                }

                case Custom -> {
                    // 自定义百分比减少
                    if (h == 0 && v == 0) {
                        event.cancel();
                    }
                }
            }
        }

        // 处理爆炸包
        if (explosions.get() && event.packet instanceof ClientboundExplodePacket) {
            if (mode.get() == Mode.Cancel) {
                event.cancel();
            } else if (mode.get() == Mode.Grim) {
                ClientboundExplodePacket packet = (ClientboundExplodePacket) event.packet;
                event.cancel();
                Vec3 vec3d = Via.playerKnockback(packet);

                storedX = vec3d.x;
                storedY = vec3d.y;
                storedZ = vec3d.z;
                tickDelay = delay.get();
                hasVelocity = true;
            }
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mode.get() != Mode.Grim) return;
        if (moveStop.get()) {
            if (mc.options.keyDown.isDown()
                || mc.options.keyUp.isDown()
                || mc.options.keyLeft.isDown()
                || mc.options.keyRight.isDown()) {
                return;
            }
        }
        // Grim模式的延迟速度应用
        if (hasVelocity) {
            if (tickDelay > 0) {
                tickDelay--;
            } else {
                // 应用存储的速度
                double velX = storedX;
                double velY = storedY;
                double velZ = storedZ;

                // 移动修正,使其看起来更自然
                if (moveCorrection.get()) {
                    velX *= 0.6;
                    velZ *= 0.6;
                    velY *= 0.98;
                }

                mc.player.setDeltaMovement(
                    mc.player.getDeltaMovement().x + velX,
                    mc.player.getDeltaMovement().y + velY,
                    mc.player.getDeltaMovement().z + velZ
                );

                hasVelocity = false;
                tickDelay = 0;
            }
        }
    }

    @Override
    public void onDeactivate() {
        hasVelocity = false;
        tickDelay = 0;
        storedX = storedY = storedZ = 0;
    }

    public enum Mode {
        Cancel("暴力"),      // 完全取消
        Grim("Grim"),         // Grim绕过模式
        Custom("自定义");     // 自定义百分比

        private final String title;

        Mode(String title) {
            this.title = title;
        }

        @Override
        public String toString() {
            return title;
        }
    }
}
