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
import meteordevelopment.meteorclient.events.entity.player.PlayerMoveEvent;
import meteordevelopment.meteorclient.mixininterface.IVec3;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.utils.misc.input.Input;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.world.phys.Vec3;

public class Hover extends BaseModule {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> hoverSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("悬停速度")
        .description("悬停时的移动速度")
        .defaultValue(0.1)
        .min(0.01)
        .sliderRange(0.01, 1)
        .build()
    );

    private final Setting<Boolean> disableOnGround = sgGeneral.add(new BoolSetting.Builder()
        .name("地面禁用")
        .description("在玩家接触地面时禁用悬停")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> autoElytra = sgGeneral.add(new BoolSetting.Builder()
        .name("自动鞘翅")
        .description("自动激活鞘翅飞行以保持悬停状态")
        .defaultValue(true)
        .build()
    );

    // 是否启用移动检测
    private final Setting<Boolean> pauseOnMovement = sgGeneral.add(new BoolSetting.Builder()
        .name("移动时暂停")
        .description("当玩家移动时暂停悬停")
        .defaultValue(true)
        .build());

    public Hover() {
        super("悬停", "允许玩家在空中保持位置不变");
    }

    @EventHandler
    private void onPlayerMove(PlayerMoveEvent event) {
        if (mc.player == null || mc.level == null) return;

        // 检查是否应该禁用悬停
        if (disableOnGround.get() && mc.player.onGround()) {
            return;
        }

        // 自动激活鞘翅飞行
        if (autoElytra.get() && !Via.isFallFlying(mc) && !mc.player.onGround()) {
            mc.player.connection.send(
                new ServerboundPlayerCommandPacket(mc.player, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING)
            );
        }

        // 如果正在鞘翅飞行，控制移动以实现悬停
        if (Via.isFallFlying(mc)) {
            // 获取当前移动输入
            float forward = Via.movementForward(mc.player.input);
            float sideways = Via.movementSideways(mc.player.input);
            float yaw = mc.player.getYRot();

            // 计算方向向量
            double cos = Math.cos(Math.toRadians(yaw + 90));
            double sin = Math.sin(Math.toRadians(yaw + 90));

            // 计算移动向量
            double moveX = (forward * cos + sideways * sin) * hoverSpeed.get();
            double moveZ = (forward * sin - sideways * cos) * hoverSpeed.get();

            // 垂直移动控制
            double moveY = 0;
            if (mc.options.keyJump.isDown()) {
                moveY = hoverSpeed.get();
            } else if (mc.options.keyShift.isDown()) {
                moveY = -hoverSpeed.get();
            }

            // 应用移动
            Via.setMovement(((IVec3) event.movement), moveX, moveY, moveZ);

            // 减少下落速度以实现更好的悬停效果
            Vec3 velocity = mc.player.getDeltaMovement();
            mc.player.setDeltaMovement(velocity.x, velocity.y * 0.9, velocity.z);
        }


        // 检查是否需要暂停（玩家移动检测）
        boolean shouldPause = false;
        if (pauseOnMovement.get()) {
            shouldPause = isPlayerMoving();
        }

        if (!shouldPause) {
            performSpin();
        }
    }


    /**
     * 检测玩家是否在移动
     *
     * @return true 如果玩家正在移动
     */
    public boolean isPlayerMoving() {
        // 检查按键输入
        boolean keyPressed =
            Input.isPressed(mc.options.keyUp) ||
                Input.isPressed(mc.options.keyDown) ||
                Input.isPressed(mc.options.keyLeft) ||
                Input.isPressed(mc.options.keyRight) ||
                Input.isPressed(mc.options.keyJump) ||
                Input.isPressed(mc.options.keyShift);

        return keyPressed;
    }

    /**
     * 执行旋转操作
     */
    private void performSpin() {

        float currentYaw = mc.player.getYRot();

        // 同时发送到服务器确保其他玩家也能看到
        mc.player.connection.send(
            //LookAndOnGround
            Via.get(currentYaw,
                mc.player.getXRot(),
                mc.player.onGround())

        );

    }
}
