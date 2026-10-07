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
import com.github.mikumiku.addon.util.Via;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class AutoChorusFruit extends BaseModule {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> autoDisable = sgGeneral.add(new BoolSetting.Builder()
        .name("落地后关闭")
        .description("落地后自动关闭模块")
        .defaultValue(true)
        .build()
    );


    private boolean wasInAir = false;
    private boolean eating = false;

    public AutoChorusFruit() {
        super("紫颂果降落", "在空中自动吃紫颂果直到落地");
    }


    @Override
    public void onActivate() {
        super.onActivate();
        int slot = BagUtil.findItemInventorySlot(Items.CHORUS_FRUIT);

        if (slot == -1) {
            this.error("没有紫颂果了");
            toggle();
        }

    }

    @Override
    public void onDeactivate() {
        stopEating();
        wasInAir = false;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null) return;

        boolean isInAir = !mc.player.onGround();

        // 检测是否在空中
        if (isInAir) {
            wasInAir = true;

            if (eating) {
                startEating();
                return;
            }
            // 查找紫颂果
            boolean useItem = findAndUseItem(Items.CHORUS_FRUIT);

            if (!useItem) {
                int slot = BagUtil.findItemInventorySlot(Items.CHORUS_FRUIT);
                if (slot != -1) {
                    BagUtil.doSwap(slot);
                    mc.gameMode.useItem(mc.player, mc.player.getUsedItemHand());
                    startEating();
                } else {
                    stopEating();

                }
            } else {
                startEating();
            }

        } else {
            // 落地了
            if (wasInAir && eating) {
                stopEating();
                wasInAir = false;

                // 如果设置了自动关闭，则关闭模块
                if (autoDisable.get()) {
                    toggle();
                }
            }
        }
    }

    private boolean findAndUseItem(Item item) {
        // 查找并使用物品的实现
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.getItem() == item) {
                Via.setSelectedSlot(i);
                return true;
            }
        }
        return false;
    }

    private void startEating() {
        if (!eating) {
            mc.options.keyUse.setDown(true);
            eating = true;
        }
    }

    private void stopEating() {
        if (eating) {
            mc.options.keyUse.setDown(false);
            eating = false;
        }
    }
}
