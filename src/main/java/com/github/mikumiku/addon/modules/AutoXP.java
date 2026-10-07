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
import com.github.mikumiku.addon.util.HumanizedSettings;
import com.github.mikumiku.addon.util.Rotation;
import com.github.mikumiku.addon.util.RotationManager;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ExperienceBottleItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.InteractionHand;

import java.util.concurrent.atomic.AtomicInteger;

public class AutoXP extends BaseModule {

    // 设置组
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    /**
     * 本模块自己的「类人化」设置组：旋转注入手抖。模式默认「跟随全局」。
     */
    private final HumanizedSettings humanized = HumanizedSettings.builder(this, "AutoXP").view().rotateNoise().build();

    // 多任务设置
    private final Setting<Boolean> multiTask = sgGeneral.add(new BoolSetting.Builder()
        .name("使用物品时也丢")
        .description("在使用物品时也允许丢经验瓶")
        .defaultValue(false)
        .build());

    // 延迟设置
    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("延迟")
        .description("丢经验瓶之间的延迟（tick）")
        .defaultValue(1)
        .min(1)
        .sliderMax(20)
        .build());

    // 每次丢瓶数量
    private final Setting<Integer> bottlesPerTick = sgGeneral.add(new IntSetting.Builder()
        .name("每次丢瓶数量")
        .description("每个tick丢出的经验瓶数量")
        .defaultValue(1)
        .min(1)
        .sliderMax(64)
        .build());

    // 耐久度检查
    private final Setting<Boolean> durabilityCheck = sgGeneral.add(new BoolSetting.Builder()
        .name("满耐久禁用")
        .description("检查装备和手持物品的耐久度，如果满耐久则自动禁用")
        .defaultValue(true)
        .build());

    // 仅在满耐久时启用
    private final Setting<Boolean> onlyFullDurability = sgGeneral.add(new BoolSetting.Builder()
        .name("仅满耐久时启用")
        .description("只有在装备满耐久时才启用丢经验瓶")
        .defaultValue(false)
        .build());

    // 内部状态
    private int delayTimer = 0;
    private AtomicInteger xpBottleCount = new AtomicInteger(0);

    public AutoXP() {
        super(BaseModule.CATEGORY_MIKU_COMBAT, "XP自动丢", "自动丢经验瓶修装备");
    }

    @Override
    public void onActivate() {
        super.onActivate();
        delayTimer = 0;
        updateXPBottleCount();
    }

    @Override
    public String getInfoString() {
        return String.valueOf(xpBottleCount.get());
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.level == null) return;

        // 延迟检查
        if (delayTimer > 0) {
            delayTimer--;
            return;
        }

        // 多任务检查
        if (mc.player.isUsingItem() && !multiTask.get()) {
            return;
        }

        // 更新经验瓶数量
        updateXPBottleCount();

        // 检查是否有经验瓶
        if (xpBottleCount.get() <= 0) {
            error("没有经验瓶，禁用模块");
            toggle();
            return;
        }

        // 耐久度检查
        if (durabilityCheck.get()) {
            boolean itemsFullDurability = areItemsFullDurability();

            if (onlyFullDurability.get()) {
                // 仅在满耐久时启用模式
                if (!itemsFullDurability) {
                    return;
                }
            } else {
                // 满耐久时禁用模式
                if (itemsFullDurability) {
                    info("所有装备耐久度已满，禁用模块");
                    toggle();
                    return;
                }
            }
        }

        // 查找经验瓶
        int slot = findXPBottleSlot();
        if (slot == -1) {
            error("背包中没有找到经验瓶，禁用模块");
            toggle();
            return;
        }

        // 切换到经验瓶
        BagUtil.doSwap(slot);

        // 旋转
        RotationManager.getInstance().register(new Rotation(mc.player.getYRot(), 90.0f), humanized.profile());

        // 丢经验瓶
        throwXPBottles();

        BagUtil.doSwap(slot);

        // 重置延迟
        delayTimer = delay.get();
    }

    /**
     * 更新经验瓶数量
     */
    private void updateXPBottleCount() {
        int count = 0;

        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.getItem() instanceof ExperienceBottleItem) {
                count += stack.getCount();
            }
        }
        xpBottleCount.set(count);
    }

    /**
     * 查找经验瓶槽位
     */
    private int findXPBottleSlot() {

        return BagUtil.findItemInventorySlot(stack -> stack.getItem() instanceof ExperienceBottleItem);
    }

    /**
     * 丢经验瓶
     */
    private void throwXPBottles() {
        int bottlesToThrow = Math.min(bottlesPerTick.get(), xpBottleCount.get());

        for (int i = 0; i < bottlesToThrow; i++) {
            // 发送使用物品包
            BaseModule.sendSequencedPacket(id -> new ServerboundUseItemPacket(InteractionHand.MAIN_HAND, id, mc.player.getYRot(), mc.player.getXRot()));
            // 挥手
            mc.player.swing(InteractionHand.MAIN_HAND);
        }
    }

    /**
     * 检查所有物品是否满耐久
     */
    private boolean areItemsFullDurability() {
        // 检查主手和副手
        if (!isItemFullDurability(mc.player.getMainHandItem()) ||
            !isItemFullDurability(mc.player.getOffhandItem())) {
            return false;
        }

        ItemStack head = mc.player.getItemBySlot(EquipmentSlot.HEAD);
        ItemStack chest = mc.player.getItemBySlot(EquipmentSlot.CHEST);
        ItemStack legs = mc.player.getItemBySlot(EquipmentSlot.LEGS);
        ItemStack feet = mc.player.getItemBySlot(EquipmentSlot.FEET);

        ItemStack[] items = {head, chest, legs, feet};

        // 检查盔甲
        for (ItemStack stack : items) {
            if (!isItemFullDurability(stack)) {
                return false;
            }
        }

        return true;
    }

    /**
     * 检查单个物品是否满耐久
     */
    private boolean isItemFullDurability(ItemStack stack) {
        if (stack.isEmpty()) {
            return true;
        }

        // 检查是否有经验修补附魔
        boolean hasMending = stack.isEnchanted() &&
            stack.getEnchantments().toString().contains(Enchantments.MENDING.toString().split(" ")[0]);

        // 如果没有经验修补，认为不需要修复
        if (!hasMending) {
            return true;
        }

        int maxDamage = stack.getMaxDamage();
        int currentDamage = stack.getDamageValue();

        // 检查是否满耐久（损伤为0或物品无法损坏）
        return currentDamage == 0 || maxDamage == 0;
    }
}
