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
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.Holder;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;

public class AutoSpray extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTargets = settings.createGroup("喷什么");
    private final SettingGroup sgConditions = settings.createGroup("什么情况喷");
    private final SettingGroup sgBinds = settings.createGroup("快捷键");

    // 常规设置 (General)
    private final Setting<Double> delay = sgGeneral.add(new DoubleSetting.Builder()
        .name("延迟 (s)")
        .description("两次投掷药水之间的延迟（秒）。")
        .defaultValue(0.5)
        .min(0.0).max(10.0)
        .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("旋转视角")
        .description("投掷药水时是否向下看。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> pitch = sgGeneral.add(new DoubleSetting.Builder()
        .name("俯仰角")
        .description("投掷时的视角俯仰角度。")
        .defaultValue(90.0)
        .min(70.0).max(90.0)
        .visible(rotate::get)
        .build()
    );

    private final Setting<Boolean> raytrace = sgGeneral.add(new BoolSetting.Builder()
        .name("轨迹预测")
        .description("计算抛物线与运动轨迹，确保药水能砸中自己。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> predictTicks = sgGeneral.add(new IntSetting.Builder()
        .name("预测刻")
        .description("预测自己未来的位置刻数。")
        .defaultValue(2)
        .min(0).max(10)
        .visible(raytrace::get)
        .build()
    );

    private final Setting<Double> effectRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("有效范围")
        .description("预测落点与自身的最大允许距离。")
        .defaultValue(3.0)
        .min(0.0).max(6.0)
        .visible(raytrace::get)
        .build()
    );

    // 目标效果 (Targets)
    private final Setting<Boolean> resistance = sgTargets.add(new BoolSetting.Builder().name("自动神龟").defaultValue(false).build());
    private final Setting<Boolean> strength = sgTargets.add(new BoolSetting.Builder().name("自动力量").defaultValue(false).build());
    private final Setting<Boolean> speed = sgTargets.add(new BoolSetting.Builder().name("自动速度").defaultValue(false).build());
    private final Setting<Boolean> slowFalling = sgTargets.add(new BoolSetting.Builder().name("自动缓降").defaultValue(false).build());

    // 条件检测 (Conditions)
    private final Setting<Boolean> healthCheck = sgConditions.add(new BoolSetting.Builder()
        .name("生命值检测 (抗性)")
        .description("仅当生命值低于设定值时才投掷抗性药水。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> health = sgConditions.add(new DoubleSetting.Builder()
        .name("生命值阈值")
        .description("触发抗性药水的最低血量（含黄心）。")
        .defaultValue(17.0)
        .min(1.0).max(36.0)
        .visible(healthCheck::get)
        .build()
    );

    private final Setting<Boolean> onlyPlayerNearby = sgConditions.add(new BoolSetting.Builder()
        .name("附近有敌人才喷")
        .description("仅当附近有敌对玩家时才投掷药水。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> playerRange = sgConditions.add(new DoubleSetting.Builder()
        .name("敌人检测范围")
        .description("检测敌对玩家的范围（格）。")
        .defaultValue(16.0)
        .min(4.0).max(64.0)
        .visible(onlyPlayerNearby::get)
        .build()
    );

    private final Setting<Boolean> ignoreFriends = sgConditions.add(new BoolSetting.Builder()
        .name("忽略好友")
        .description("将好友列表中的玩家视为非敌人。")
        .defaultValue(true)
        .visible(onlyPlayerNearby::get)
        .build()
    );

    private final Setting<Boolean> earlyThrow = sgConditions.add(new BoolSetting.Builder()
        .name("提前续杯")
        .description("在药水效果结束前提前投掷刷新时间。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> earlyThrowTime = sgConditions.add(new DoubleSetting.Builder()
        .name("提前时间 (s)")
        .description("剩余多少秒时提前扔出药水。")
        .defaultValue(0.4)
        .min(0.0).max(5.0)
        .visible(earlyThrow::get)
        .build()
    );

    private final Setting<Boolean> usingPause = sgConditions.add(new BoolSetting.Builder()
        .name("吃东西时别喷")
        .description("当你正在吃东西或拉弓时暂停投掷。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> onlyGround = sgConditions.add(new BoolSetting.Builder()
        .name("空中别喷")
        .description("仅当你站在地面上时才自动投掷。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> inventorySearch = sgConditions.add(new BoolSetting.Builder()
        .name("检索背包")
        .description("当快捷栏没有对应药水时，从背包中检索并替换。")
        .defaultValue(true)
        .build()
    );

    // 快捷键 (Keybinds) - 使用 Meteor 的 KeybindSetting
    private final Setting<Keybind> strengthKey = sgBinds.add(new KeybindSetting.Builder().name("力量快捷键").defaultValue(Keybind.none()).build());
    private final Setting<Keybind> resistanceKey = sgBinds.add(new KeybindSetting.Builder().name("神龟快捷键").defaultValue(Keybind.none()).build());
    private final Setting<Keybind> speedKey = sgBinds.add(new KeybindSetting.Builder().name("速度快捷键").defaultValue(Keybind.none()).build());

    private long lastThrowTime = 0;
    private boolean turtlePress;
    private boolean speedPress;
    private boolean strengthPress;

    public AutoSpray() {
        super(BaseModule.CATEGORY_MIKU_COMBAT, "自动喷药", "高级自动药水投掷。");
    }

    @Override
    public void onDeactivate() {
        turtlePress = false;
        speedPress = false;
        strengthPress = false;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.level == null) return;

        handleKeybinds();

        if (System.currentTimeMillis() - lastThrowTime < delay.get() * 1000.0) return;

        if (usingPause.get() && mc.player.isUsingItem()) return;

        if (onlyGround.get() && !mc.player.onGround()) {
            return; // 简化版地面检测，若需更严谨可加入方块碰撞检测
        }

        // 检查附近是否有敌人
        if (onlyPlayerNearby.get() && !isEnemyNearby()) {
            return;
        }

        checkAndThrow(MobEffects.RESISTANCE, resistance.get(), true);
        checkAndThrow(MobEffects.SPEED, speed.get(), false);
        checkAndThrow(MobEffects.STRENGTH, strength.get(), false);
        checkAndThrow(MobEffects.SLOW_FALLING, slowFalling.get(), false);
    }

    private void handleKeybinds() {
        if (resistanceKey.get().isPressed()) {
            if (!turtlePress && canThrowPotion(MobEffects.RESISTANCE)) {
                executeThrow(MobEffects.RESISTANCE);
                turtlePress = true;
            }
        } else turtlePress = false;

        if (strengthKey.get().isPressed()) {
            if (!strengthPress && canThrowPotion(MobEffects.STRENGTH)) {
                executeThrow(MobEffects.STRENGTH);
                strengthPress = true;
            }
        } else strengthPress = false;

        if (speedKey.get().isPressed()) {
            if (!speedPress && canThrowPotion(MobEffects.SPEED)) {
                executeThrow(MobEffects.SPEED);
                speedPress = true;
            }
        } else speedPress = false;
    }

    private void checkAndThrow(Holder<MobEffect> effect, boolean isEnabled, boolean isResistance) {
        if (!isEnabled || System.currentTimeMillis() - lastThrowTime < delay.get() * 1000.0) return;

        MobEffectInstance currentEffect = mc.player.getEffect(effect);
        boolean hasEffect = currentEffect != null;
        boolean shouldThrow = false;

        // 特殊处理抗性（涉及血量检测）
        if (isResistance) {
            boolean healthLow = !healthCheck.get() || (mc.player.getHealth() + mc.player.getAbsorptionAmount()) <= health.get();
            if (earlyThrow.get() && hasEffect && currentEffect.getDuration() <= earlyThrowTime.get() * 20.0) {
                shouldThrow = true;
            }
            if (!hasEffect || (healthLow && currentEffect.getAmplifier() < 1)) {
                shouldThrow = true;
            }
        } else {
            // 普通效果处理
            if (earlyThrow.get() && hasEffect && currentEffect.getDuration() <= earlyThrowTime.get() * 20.0) {
                shouldThrow = true;
            }
            if (!hasEffect) {
                shouldThrow = true;
            }
        }

        if (shouldThrow && canThrowPotion(effect)) {
            executeThrow(effect);
        }
    }

    private boolean canThrowPotion(Holder<MobEffect> effect) {
        if (raytrace.get()) {
            Vec3 hitPos = calcTrajectory(mc.player.getYRot(), pitch.get().floatValue());
            if (hitPos == null) return false;

            Vec3 playerFuturePos = getPredictedPos(mc.player, predictTicks.get());
            if (playerFuturePos.distanceToSqr(hitPos) > effectRange.get() * effectRange.get()) {
                return false;
            }
        }

        FindItemResult item = getPotionItem(effect);
        return item.found();
    }

    private void executeThrow(Holder<MobEffect> effect) {
        FindItemResult item = getPotionItem(effect);
        if (!item.found()) return;

        Runnable throwAction = () -> {
            // Meteor 的 InvUtils 自动处理背包物品切换/替换，并且支持投掷后切回原物品 (swapBack)
            InvUtils.swap(item.slot(), true);
            mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
            InvUtils.swapBack();
            lastThrowTime = System.currentTimeMillis();
        };

        if (rotate.get()) {
            Rotations.rotate(mc.player.getYRot(), pitch.get().floatValue(), throwAction);
        } else {
            throwAction.run();
        }
    }

    // --- 工具方法区 ---

    /**
     * 检测附近是否有敌对玩家
     *
     * @return true 表示附近有敌人
     */
    private boolean isEnemyNearby() {
        for (Player player : mc.level.players()) {
            // 跳过自己
            if (player.getUUID().equals(mc.player.getUUID())) {
                continue;
            }

            // 忽略好友
            if (ignoreFriends.get() && Friends.get().isFriend(player)) {
                continue;
            }

            // 检查距离
            double distance = mc.player.distanceTo(player);
            if (distance <= playerRange.get()) {
                return true;
            }
        }
        return false;
    }

    private FindItemResult getPotionItem(Holder<MobEffect> targetEffect) {
        return inventorySearch.get()
            ? InvUtils.find(stack -> isTargetPotion(stack, targetEffect))
            : InvUtils.findInHotbar(stack -> isTargetPotion(stack, targetEffect));
    }

    private boolean isTargetPotion(ItemStack stack, Holder<MobEffect> targetEffect) {
        if (stack.getItem() != Items.SPLASH_POTION) return false;

        // 适配 1.20.5+ 的 Data Component
        PotionContents contents = stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
        for (MobEffectInstance effectInstance : contents.getAllEffects()) {
            if (effectInstance.getEffect() == targetEffect) {
                return true;
            }
        }
        return false;
    }

    private Vec3 getPredictedPos(Entity entity, int ticks) {
        if (ticks == 0) return Via.getEntityPos(entity);

        Vec3 motion = entity.getDeltaMovement();
        return Via.getEntityPos(entity).add(motion.scale(ticks));
    }

    // 完全还原抛物线轨迹模拟 (基于原版重力/摩擦力计算提取)
    private Vec3 calcTrajectory(float yaw, float pitch) {
//        float tickDelta = mc.getRenderTickCounter().getTickDelta(true);
//        double x = Mth.lerp(tickDelta, mc.player.prevX, mc.player.getX());
//        double y = Mth.lerp(tickDelta, mc.player.prevY, mc.player.getY()) + mc.player.getEyeHeight(mc.player.getPose()) - 0.1;
//        double z = Mth.lerp(tickDelta, mc.player.prevZ, mc.player.getZ());
//
//        x -= Mth.cos(yaw / 180.0F * (float) Math.PI) * 0.16F;
//        z -= Mth.sin(yaw / 180.0F * (float) Math.PI) * 0.16F;
//
//        double motionX = -Mth.sin(yaw / 180.0F * (float) Math.PI) * Mth.cos(pitch / 180.0F * (float) Math.PI) * 0.4F;
//        double motionY = -Mth.sin((pitch - 20.0F) / 180.0F * (float) Math.PI) * 0.4F;
//        double motionZ = Mth.cos(yaw / 180.0F * (float) Math.PI) * Mth.cos(pitch / 180.0F * (float) Math.PI) * 0.4F;
//
//        float distance = Mth.sqrt((float) (motionX * motionX + motionY * motionY + motionZ * motionZ));
//        motionX /= distance;
//        motionY /= distance;
//        motionZ /= distance;
//
//        motionX *= 0.5;
//        motionY *= 0.5;
//        motionZ *= 0.5;
//
//        if (!mc.player.isOnGround()) {
//            motionY += mc.player.getVelocity().y;
//        }
//
//        for (int i = 0; i < 300; i++) {
//            Vec3 lastPos = new Vec3(x, y, z);
//            x += motionX;
//            y += motionY;
//            z += motionZ;
//
//            // 如果砸到水中，阻力变大
//            if (mc.world.getBlockState(net.minecraft.core.BlockPos.ofFloored(x, y, z)).isOf(net.minecraft.world.level.block.Blocks.WATER)) {
//                motionX *= 0.8;
//                motionY *= 0.8;
//                motionZ *= 0.8;
//            } else {
//                motionX *= 0.99; // 空气阻力
//                motionY *= 0.99;
//                motionZ *= 0.99;
//            }
//
//            motionY -= 0.03F; // 重力加速度
//
//            Vec3 pos = new Vec3(x, y, z);
//            BlockHitResult bhr = mc.world.raycast(new ClipContext(lastPos, pos, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player));
//
//            if (bhr != null && bhr.getType() == HitResult.Type.BLOCK) {
//                return bhr.getPos();
//            }
//        }
        return null;
    }
}
