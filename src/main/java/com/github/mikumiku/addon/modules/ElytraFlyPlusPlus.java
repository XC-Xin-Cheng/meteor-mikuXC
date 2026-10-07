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
import meteordevelopment.meteorclient.events.world.PlaySoundEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * 甲飞++ - 使用胸甲飞行，不消耗鞘翅耐久
 * 实现 LAZY 模式: 拦截服务器的停止滑翔信号，保持客户端滑翔状态
 */
public class ElytraFlyPlusPlus extends BaseModule {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> autoRocket = sgGeneral.add(new BoolSetting.Builder()
        .name("自动烟花")
        .description("飞行时自动使用烟花火箭加速。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> rocketDelay = sgGeneral.add(new IntSetting.Builder()
        .name("烟花间隔")
        .description("两次烟花发射之间的最小间隔（tick数）。")
        .defaultValue(40)
        .min(10)
        .sliderRange(10, 100)
        .build()
    );

    private final Setting<Boolean> cancelSounds = sgGeneral.add(new BoolSetting.Builder()
        .name("静音")
        .description("取消装备声音。")
        .defaultValue(true)
        .build()
    );

    // 胸甲物品列表（不含鞘翅）
    private static final List<Item> CHESTPLATES = List.of(
        Items.NETHERITE_CHESTPLATE,
        Items.DIAMOND_CHESTPLATE,
        Items.GOLDEN_CHESTPLATE,
        Items.IRON_CHESTPLATE,
        Items.CHAINMAIL_CHESTPLATE,
        Items.LEATHER_CHESTPLATE
    );

    // 需要取消的声音
    private static final List<Identifier> CANCEL_SOUNDS = List.of(
        Identifier.parse("minecraft:item.armor.equip_generic"),
        Identifier.parse("minecraft:item.armor.equip_netherite"),
        Identifier.parse("minecraft:item.armor.equip_elytra"),
        Identifier.parse("minecraft:item.armor.equip_diamond"),
        Identifier.parse("minecraft:item.armor.equip_gold"),
        Identifier.parse("minecraft:item.armor.equip_iron"),
        Identifier.parse("minecraft:item.armor.equip_chain"),
        Identifier.parse("minecraft:item.armor.equip_leather"),
        Identifier.parse("minecraft:item.elytra.flying")
    );

    // 状态变量
    private boolean wasSprinting = false;
    private int globalTickCounter = 0;
    private int lastRocketTick = 0;
    private int flyStartTick = 0;

    public ElytraFlyPlusPlus() {
        super(
            CATEGORY_MIKU_PRO,
            "甲飞++",
            "使用胸甲飞行，不消耗鞘翅耐久。需在快捷栏放置胸甲。"
        );
    }

    @Override
    public void onActivate() {
        super.onActivate();
        if (mc.player == null || mc.player.getAbilities().mayfly) return;

        wasSprinting = mc.player.isSprinting();
        globalTickCounter = 0;
        lastRocketTick = 0;
        flyStartTick = 0;

        // 确保胸甲在快捷栏
        ensureChestplateInHotbar();

        // 如果在地面，先跳起来
        if (mc.player.onGround()) {
            mc.player.jumpFromGround();
        }
    }

    @Override
    public void onDeactivate() {
        super.onDeactivate();
        if (mc.player == null) return;

        // 恢复冲刺状态
        mc.player.setSprinting(wasSprinting);
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.player.getAbilities().mayfly) return;
        if (mc.player.onGround()) return;

        globalTickCounter++;

        // 核心 LAZY 模式逻辑
        doLazyArmorFly();

        // 保持冲刺状态
        if (Via.isFallFlying(mc)) {
            mc.player.setSprinting(true);
        }

        // 自动烟花
        if (autoRocket.get()) {
            Modules.get().get(OnekeyFireWork.class).toggle();
        }
    }

    @EventHandler
    private void onPlaySound(PlaySoundEvent event) {
        if (!cancelSounds.get()) return;

        for (Identifier id : CANCEL_SOUNDS) {
            if (id.equals(event.sound.getIdentifier())) {
                event.cancel();
                return;
            }
        }
    }

    /**
     * LAZY 模式核心逻辑
     * 1. 如果没有在飞行，装备胸甲并发送开始滑翔包
     * 2. 如果已经在飞行，定期发送开始滑翔包保持状态
     */
    private void doLazyArmorFly() {
        // 检查是否可以继续滑翔
        if (!canContinueGliding()) return;

        // 查找快捷栏中的胸甲
        int chestplateSlot = findChestplateInHotbar();
        if (chestplateSlot == -1) return;

        if (!Via.isFallFlying(mc)) {
            // 没有在飞行 - 装备胸甲并开始飞行
            equipChestplate(chestplateSlot);
            sendStartFallFlying();
            flyStartTick = globalTickCounter;
        } else {
            // 已经在飞行 - 定期发送开始滑翔包保持状态
            // 每 40 tick 发送一次（参考 ElytraExtra 的实现）
            if (globalTickCounter - flyStartTick >= 40) {
                sendStartFallFlying();
                flyStartTick = globalTickCounter;
            }
        }
    }

    /**
     * 检查是否可以继续滑翔
     */
    private boolean canContinueGliding() {
        if (mc.player == null) return false;

        // 基本条件检查
        if (mc.player.onGround() || mc.player.getAbilities().flying || mc.player.isPassenger()) {
            return false;
        }

        // 液体检查
        if (mc.player.isInWater() || mc.player.isInLava()) {
            return false;
        }

        // 效果检查
        if (mc.player.hasEffect(MobEffects.LEVITATION)) {
            return false;
        }

        return true;
    }

    /**
     * 确保胸甲在快捷栏中
     */
    private void ensureChestplateInHotbar() {
        // 检查快捷栏是否已有胸甲
        if (findChestplateInHotbar() != -1) return;

        // 在背包中查找胸甲
        int invSlot = findChestplateInInventory();
        if (invSlot == -1) {
            error("未找到可用胸甲！");
            return;
        }

        // 查找快捷栏空位
        int hotbarSlot = findEmptyHotbarSlot();
        if (hotbarSlot == -1) {
            hotbarSlot = 0;
        }

        // 交换到快捷栏
        BagUtil.inventorySwap(invSlot, hotbarSlot + 36);
    }

    /**
     * 装备胸甲到胸甲槽
     */
    private void equipChestplate(int hotbarSlot) {
        mc.gameMode.handleContainerInput(
            mc.player.containerMenu.containerId,
            hotbarSlot + 36,
            6,
            ContainerInput.SWAP,
            mc.player
        );
    }

    /**
     * 在快捷栏查找胸甲
     */
    private int findChestplateInHotbar() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (isChestplate(stack)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 在背包中查找胸甲（不含快捷栏）
     */
    private int findChestplateInInventory() {
        for (int i = 9; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (isChestplate(stack)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 查找快捷栏空位
     */
    private int findEmptyHotbarSlot() {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getItem(i).isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 判断是否为可用的胸甲
     */
    private boolean isChestplate(ItemStack stack) {
        if (stack.isEmpty()) return false;
        for (Item item : CHESTPLATES) {
            if (stack.is(item)) return true;
        }
        return false;
    }

    /**
     * 检查是否正在装甲飞行（供外部调用）
     * 注意：不能调用 isFallFlying()，会导致与 LivingEntityMixin 死循环
     */
    public boolean enabled() {
        return this.isActive() && mc.player != null && !mc.player.onGround();
    }

    /**
     * 发送开始滑翔数据包
     */
    private void sendStartFallFlying() {
        if (mc.getConnection() != null) {
            mc.getConnection().send(
                new ServerboundPlayerCommandPacket(mc.player, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING)
            );
        }
    }

}
