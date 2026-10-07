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
import com.github.mikumiku.addon.mixin.AccessorFireworkRocketEntity;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.network.protocol.common.ServerboundPongPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;

import java.text.DecimalFormat;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 延长烟花助推时间的 Meteor 模块
 * 原理：拦截烟花销毁与 Pong 包，让客户端保持烟花存在
 * super("烟花延长", "延长烟花持续时间");
 */
public class ExtendedFirework extends BaseModule {
    private final List<ServerboundPongPacket> packetList = new CopyOnWriteArrayList<>();

    private boolean extendFirework;
    private long extendFireworkTimer;
    private FireworkRocketEntity firework;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> maxDuration = sgGeneral.add(new IntSetting.Builder()
        .name("最大持续时间")
        .description("烟花推进的最大持续时间(秒)")
        .defaultValue(44)
        .min(1)
        .max(120)
        .sliderMax(120)
        .build()
    );

    private final Setting<Boolean> autoDisableOnGround = sgGeneral.add(new BoolSetting.Builder()
        .name("着陆自动停止")
        .description("着陆时自动停止延长烟花推进")
        .defaultValue(true)
        .build()
    );


    public ExtendedFirework() {
        super(BaseModule.CATEGORY_MIKU_PRO, "烟花延长", "延长烟花持续时间， 在高反作弊服可能无用。");
    }

    @Override
    public String getInfoString() {
        if (!extendFirework) {
            return null;
        }
        float elapsed = (System.currentTimeMillis() - extendFireworkTimer) / 1000.0f;
        return new DecimalFormat("0.0").format(elapsed) + "s";
    }

    @Override
    public void onDeactivate() {
        cleanupFirework();
        extendFirework = false;
        extendFireworkTimer = 0;
        sendPongPackets();
    }

    @EventHandler
    private void onGameJoin(GameJoinedEvent event) {
        cleanupFirework();
        extendFirework = false;
        extendFireworkTimer = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!extendFirework) {
            // Look for player's firework rocket
            for (Entity entity : mc.level.entitiesForRendering()) {
                if (entity instanceof FireworkRocketEntity rocket) {
                    AccessorFireworkRocketEntity accessor = (AccessorFireworkRocketEntity) rocket;
                    if (accessor.hookWasShotByEntity() && accessor.hookGetShooter() == mc.player) {
                        firework = rocket;
                        break;
                    }
                }
            }
            extendFireworkTimer = System.currentTimeMillis();
            return;
        }

        // Check if should stop extending
        long elapsed = System.currentTimeMillis() - extendFireworkTimer;
        boolean shouldStop = elapsed >= maxDuration.get() * 1000L;

        if (autoDisableOnGround.get() && mc.player.onGround()) {
            shouldStop = true;
        }

        if (shouldStop) {
            extendFirework = false;
            cleanupFirework();
            sendPongPackets();
        }
    }

    @EventHandler
    private void onSendPacket(PacketEvent.Send event) {
        if (event.packet instanceof ServerboundPongPacket packet && extendFirework) {
            event.cancel();
            packetList.add(packet);
        }
    }

    @EventHandler
    private void onReceivePacket(PacketEvent.Receive event) {
        // Cancel firework destroy packet
        if (event.packet instanceof ClientboundRemoveEntitiesPacket packet && firework != null) {
            for (int id : packet.getEntityIds()) {
                if (id == firework.getId()) {
                    event.cancel();
                    extendFirework = true;
                    extendFireworkTimer = System.currentTimeMillis();
                    return;
                }
            }
        }

        // Handle teleport/position update
        if (event.packet instanceof ClientboundPlayerPositionPacket && extendFirework) {
            extendFirework = false;
            cleanupFirework();
            sendPongPackets();
        }
    }

    private void cleanupFirework() {
        if (firework != null) {
            // Simply discard the firework, let the server handle removal
            // The entity will be removed naturally when we stop canceling destroy packets
            firework.discard();
            firework = null;
        }
    }

    private void sendPongPackets() {
        for (ServerboundPongPacket packet : packetList) {
            mc.getConnection().send(packet);
        }
        packetList.clear();
    }

    public boolean isExtendingFirework() {
        return extendFirework;
    }
}
