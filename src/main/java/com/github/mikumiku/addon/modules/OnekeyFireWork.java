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
import com.github.mikumiku.addon.util.MikuUtil;
import com.github.mikumiku.addon.util.Via;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.InteractionHand;

public class OnekeyFireWork extends BaseModule {
    private int delay;
    private int slotBefore;
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final Setting<Integer> closeDelay = sgGeneral.add(
        new IntSetting.Builder()
            .name("关闭延迟")
            .description("使用烟花后关闭界面的延迟（游戏刻）")
            .defaultValue(4)
            .sliderRange(1, 40)
            .build()
    );

    public OnekeyFireWork() {
        super(BaseModule.CATEGORY_MIKU_COMBAT,
            "一键烟花",
            "快捷键一键放烟花"
        );

        this.delay = 0;
        this.slotBefore = mc.player == null ? 0 : Via.getSelectedSlot();

    }

    @Override
    public void onActivate() {
        this.fire();
//        BagUtil.quickUse(Items.FIREWORK_ROCKET);

        this.delay = this.closeDelay.get();
        this.slotBefore = mc.player == null ? 0 : Via.getSelectedSlot();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (this.delay == 0) {
            InvUtils.swap(this.slotBefore, false);
            this.toggle();
        } else {
            this.delay--;
        }
    }


    public void fire() {

        try {
            if (!mc.player.onGround()) {
                if (mc.player.getInventory().getItem(38).getItem().equals(Items.ELYTRA)) {
                    Item item = mc.player.getMainHandItem().getItem();
                    if (!MikuUtil.isArmor(item)) {
                        if (item == Items.FIREWORK_ROCKET) {
                            firework();
                        } else {
                            int fireworkSlot;
                            if ((fireworkSlot = InvUtils.findInHotbar(Items.FIREWORK_ROCKET).slot()) != -1) {
                                int old = Via.getSelectedSlot();
                                BagUtil.switchToSlot(fireworkSlot);
                                firework();
                                BagUtil.switchToSlot(old);

                                mc.getConnection().send(new ServerboundContainerClosePacket(mc.player.containerMenu.containerId));
                            } else if ((fireworkSlot = InvUtils.find(Items.FIREWORK_ROCKET).slot()) != -1) {
                                BagUtil.inventorySwap(fireworkSlot, Via.getSelectedSlot());
                                firework();
                                BagUtil.inventorySwap(fireworkSlot, Via.getSelectedSlot());
                                BagUtil.sync();
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {

        }
    }

    private void firework() {
        BaseModule.sendSequencedPacket(id -> new ServerboundUseItemPacket(InteractionHand.MAIN_HAND, id, mc.player.getYRot(), mc.player.getXRot()));
    }
}
