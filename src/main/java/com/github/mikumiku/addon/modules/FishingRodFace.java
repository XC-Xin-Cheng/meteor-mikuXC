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
import com.github.mikumiku.addon.util.BagUtil;
import com.github.mikumiku.addon.util.ChatUtils;
import com.github.mikumiku.addon.util.Via;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.InteractionHand;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class FishingRodFace extends BaseModule {

    // 设置组
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTarget = settings.createGroup("目标设置");

    // 基本设置
    private final Setting<Integer> detectionRange = sgGeneral.add(new IntSetting.Builder()
        .name("检测范围")
        .description("检测敌人的范围（方块）")
        .defaultValue(6)
        .min(1)
        .max(256)
        .sliderMin(1)
        .sliderMax(128)
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("丢钓延迟")
        .description("丢钓鱼竿的延迟（tick）")
        .defaultValue(1)
        .min(0)
        .max(20)
        .sliderMin(0)
        .sliderMax(20)
        .build()
    );

    private final Setting<Boolean> autoRecast = sgGeneral.add(new BoolSetting.Builder()
        .name("自动重新丢钓")
        .description("钓到人后自动重新丢钓")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> recastDelay = sgGeneral.add(new IntSetting.Builder()
        .name("重新丢钓延迟")
        .description("钓到人后重新丢钓的延迟（tick）")
        .defaultValue(10)
        .min(0)
        .max(40)
        .sliderMin(0)
        .sliderMax(40)
        .build()
    );

    // 目标设置
    private final Setting<Boolean> targetPlayers = sgTarget.add(new BoolSetting.Builder()
        .name("糊弄玩家")
        .description("是否针对玩家")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> targetTeam = sgTarget.add(new BoolSetting.Builder()
        .name("糊弄队友")
        .description("是否针对队友")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> headOffsetY = sgTarget.add(new DoubleSetting.Builder()
        .name("头部高度偏移")
        .description("针对头部的高度偏移")
        .defaultValue(1.6)
        .min(0)
        .max(3)
        .sliderMin(0)
        .sliderMax(3)
        .build()
    );

    // 状态变量
    private int tickTimer = 0;
    private int recastTimer = 0;
    private boolean isFishingRodThrown = false;
    private Player lastTargetedPlayer = null;

    public FishingRodFace() {
        super(CATEGORY_MIKU_COMBAT, "鱼竿糊脸", "使用鱼竿丢敌人脸上");
    }

    @Override
    public void onActivate() {
        super.onActivate();
        tickTimer = 0;
        recastTimer = 0;
        isFishingRodThrown = false;
        lastTargetedPlayer = null;

        ChatUtils.sendMsg("鱼竿糊脸模块已启动");
        ChatUtils.sendMsg("正在搜索范围内的目标...");
    }

    @Override
    public void onDeactivate() {
        tickTimer = 0;
        recastTimer = 0;
        isFishingRodThrown = false;
        lastTargetedPlayer = null;

        ChatUtils.sendMsg("鱼竿糊脸模块已停止");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.level == null) return;

        // 检查是否有鱼竿
        if (!hasFishingRodInInventory()) {
            warning("背包中没有鱼竿！");
            toggle();
            return;
        }

        // 处理重新丢钓延迟
        if (recastTimer > 0) {
            recastTimer--;
            return;
        }

        // 处理丢钓延迟
        if (tickTimer > 0) {
            tickTimer--;
            return;
        }

        // 检查鱼钩是否已经抛出
        FishingHook fishHook = mc.player.fishing;
        if (fishHook != null) {
            isFishingRodThrown = true;
            // 如果鱼钩已经抛出，等待收回
            return;
        } else {
            isFishingRodThrown = false;
        }

        // 寻找目标
        Player target = findNearestTarget();

        if (target != null) {
            // 选择或切换目标
            if (lastTargetedPlayer == null || !lastTargetedPlayer.equals(target)) {
                lastTargetedPlayer = target;
                info("选中目标: " + target.getName().getString());
            }

            // 计算敌人头部位置
            Vec3 targetHeadPos = getPlayerHeadPosition(target);

            // 丢钓鱼竿
            castFishingRod(targetHeadPos);

            // 设置下次丢钓延迟
            tickTimer = delay.get();

            // 如果启用了自动重新丢钓，设置重新丢钓延迟
            if (autoRecast.get()) {
                recastTimer = recastDelay.get();
            }
        } else {
            // 没有找到目标
            if (lastTargetedPlayer != null) {
                lastTargetedPlayer = null;
            }
        }
    }

    /**
     * 寻找范围内最近的目标
     */
    private Player findNearestTarget() {
        Player nearestTarget = null;
        double nearestDistance = Double.MAX_VALUE;

        List<AbstractClientPlayer> players = mc.level.players();

        for (Player player : players) {
            // 不能以自己为目标
            if (player.equals(mc.player)) {
                continue;
            }

            // 检查是否是队友
            if (!isValidTarget(player)) {
                continue;
            }

            // 计算距离
            double distance = mc.player.distanceTo(player);

            // 检查范围
            if (distance <= detectionRange.get()) {
                if (distance < nearestDistance) {
                    nearestDistance = distance;
                    nearestTarget = player;
                }
            }
        }

        return nearestTarget;
    }

    /**
     * 检查玩家是否是有效的目标
     */
    private boolean isValidTarget(Player player) {
        // 创意模式玩家不是有效目标
        if (player.isCreative()) {
            return false;
        }

        // 死亡的玩家不是有效目标
        if (player.isDeadOrDying()) {
            return false;
        }

        // 检查是否是队友
        if (isFriendly(player)) {
            return targetTeam.get();
        }

        return targetPlayers.get();
    }

    /**
     * 判断玩家是否为友好玩家
     */
    private boolean isFriendly(Player player) {

        boolean friend = Friends.get().isFriend(player);

        return friend;
    }

    /**
     * 获取玩家头部位置
     */
    private Vec3 getPlayerHeadPosition(Player player) {
        Vec3 playerPos = Via.getEntityPos(player);
        // 头部位置 = 玩家位置 + 高度偏移
        return playerPos.add(0, headOffsetY.get(), 0);
    }

    /**
     * 丢钓鱼竿
     */
    private void castFishingRod(Vec3 targetPos) {
        // 装备鱼竿
        equipFishingRod();

        // 计算看向目标所需的旋转角度
        Vec3 playerEyePos = mc.player.getEyePosition();
        Vec3 direction = targetPos.subtract(playerEyePos).normalize();

        // 计算偏航角和俯仰角
        float[] rotation = calculateRotation(playerEyePos, targetPos);

        // 旋转玩家视角
        mc.player.setYRot(rotation[0]);
        mc.player.setXRot(rotation[1]);

        // 使用鱼竿（右键）
        mc.gameMode.startDestroyBlock(mc.player.blockPosition(), Direction.UP);
        mc.player.swing(InteractionHand.MAIN_HAND);

        info("丢钓鱼竿向目标: " + String.format("(%.1f, %.1f, %.1f)",
            targetPos.x, targetPos.y, targetPos.z));
    }

    /**
     * 计算看向目标位置的旋转角度
     */
    private float[] calculateRotation(Vec3 from, Vec3 to) {
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;

        double distance = Math.sqrt(dx * dx + dz * dz);

        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90;
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, distance));

        return new float[]{yaw, pitch};
    }

    /**
     * 装备鱼竿
     */
    private void equipFishingRod() {
        int slot = BagUtil.findItemInventorySlot(itemStack ->
            itemStack.getItem() instanceof FishingRodItem
        );

        BagUtil.doSwap(slot);
    }

    /**
     * 检查背包中是否有鱼竿
     */
    private boolean hasFishingRodInInventory() {
        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
            var stack = mc.player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.getItem() instanceof FishingRodItem) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取当前丢钓状态
     */
    public String getStatus() {
        if (lastTargetedPlayer != null) {
            double distance = mc.player.distanceTo(lastTargetedPlayer);
            return "目标: " + lastTargetedPlayer.getName().getString() +
                " | 距离: " + String.format("%.1f", distance);
        }
        return "搜索中...";
    }
}
