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
//package com.github.mikumiku.addon.modules.pitch4;
//
//import com.stash.hunt.Addon;
//import meteordevelopment.meteorclient.events.world.TickEvent;
//import meteordevelopment.meteorclient.settings.*;
//import meteordevelopment.meteorclient.systems.modules.Module;
//import meteordevelopment.meteorclient.systems.modules.Modules;
//import meteordevelopment.meteorclient.systems.modules.movement.elytrafly.ElytraFly;
//import meteordevelopment.meteorclient.utils.player.InvUtils;
//import meteordevelopment.orbit.EventHandler;
//import net.minecraft.world.InteractionHand;
//import meteordevelopment.meteorclient.systems.modules.movement.elytrafly.ElytraFlightModes;
//
//import static com.stash.hunt.Utils.firework;
//
//
//public class Pitch40Util extends Module {
//
//    private final SettingGroup sgGeneral = settings.getDefaultGroup();
//
//    public final Setting<Double> boundGap = sgGeneral.add(new DoubleSetting.Builder()
//        .name("bound-gap")
//        .description("The gap between the upper and lower bounds. Used when reconnecting, or when at max height if Auto Adjust Bounds is enabled.")
//        .defaultValue(60)
//        .sliderRange(50, 100)
//        .build()
//    );
//
//    public Pitch40Util() {
//        super(Addon.CATEGORY, "Pitch40Util", "Makes sure pitch 40 stays on when reconnecting to 2b2t, and sets your bounds as you reach highest point each climb.");
//    }
//
//    Module elytraFly = Modules.get().get(ElytraFly.class);
//
//    int fireworkCooldown = 0;
//
//    boolean goingUp = true;
//
//    int elytraSwapSlot = -1;
//
//    private void resetBounds() {
//        Setting<Double> upperBounds = (Setting<Double>) elytraFly.settings.get("pitch40-upper-bounds");
//        upperBounds.set(mc.player.getY() - 5);
//        Setting<Double> lowerBounds = (Setting<Double>) elytraFly.settings.get("pitch40-lower-bounds");
//        lowerBounds.set(mc.player.getY() - 5 - boundGap.get());
//    }
//
//    @EventHandler
//    private void onTick(TickEvent.Pre event) {
//        if (elytraFly.isActive()) {
//
//            if (fireworkCooldown > 0) {
//                fireworkCooldown--;
//            }
//
//            if (elytraSwapSlot != -1) {
//                InvUtils.swap(elytraSwapSlot, true);
//                mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
//                InvUtils.swapBack();
//                elytraSwapSlot = -1;
//            }
//
//            // this means the player fell below the lower bound, so we reset the bounds. this will only really happen if not using fireworks
//            if (mc.player.getY() <= (double) elytraFly.settings.get("pitch40-lower-bounds").get() - 10) {
//                resetBounds();
//                return;
//            }
//
//            // -40 pitch is facing upwards
//            if (mc.player.getPitch() == -40) {
////                info("Y less than upper bounds: " + (mc.player.getY() < (double)elytraFlyModule.settings.get("pitch40-upper-bounds").get()));
//                goingUp = true;
//            }
//            // waits until your at the highest point, when y velocity is 0, then sets min and max bounds based on your position
//            else if (goingUp && mc.player.getVelocity().y <= 0) {
//                goingUp = false;
//                resetBounds();
//            }
//        } else {
//            // waits for you to not be in queue, then turns elytrafly back on
//            if (!mc.player.getAbilities().allowFlying) {
//                elytraFly.toggle();
//                // always reset when rejoining
//                resetBounds();
//            }
//        }
//
//    }
//
//
//}
