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
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import meteordevelopment.meteorclient.events.entity.player.AttackEntityEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public class MaceCombo extends BaseModule {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgPower = settings.createGroup("威力增强");

    public enum WeaponType {
        SWORD("剑"),
        AXE("斧头"),
        HAND("空手"),
        ANY("任意武器");

        private final String name;

        WeaponType(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    // 通用设置
    private final Setting<WeaponType> weaponType = sgGeneral.add(new EnumSetting.Builder<WeaponType>()
        .name("武器类型")
        .description("触发切换的武器类型")
        .defaultValue(WeaponType.SWORD)
        .build()
    );

    private final Setting<Boolean> autoSwitch = sgGeneral.add(new BoolSetting.Builder()
        .name("自动切换")
        .description("使用指定武器攻击时自动切换到锤子")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> breachOnly = sgGeneral.add(new BoolSetting.Builder()
        .name("仅破甲锤")
        .description("只切换到有破甲附魔的锤子")
        .defaultValue(true)
        .visible(autoSwitch::get)
        .build()
    );

    private final Setting<Integer> switchDelay = sgGeneral.add(new IntSetting.Builder()
        .name("切回延迟")
        .description("攻击后多少tick切回剑")
        .defaultValue(1)
        .min(0)
        .max(5)
        .sliderMax(5)
        .visible(autoSwitch::get)
        .build()
    );

    // 威力增强设置
    private final Setting<Boolean> macePower = sgPower.add(new BoolSetting.Builder()
        .name("威力增强")
        .description("使用锤子时增强伤害")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> elytraOnly = sgPower.add(new BoolSetting.Builder()
        .name("仅鞘翅时")
        .description("只在鞘翅飞行时增强威力")
        .defaultValue(true)
        .visible(macePower::get)
        .build()
    );


    private final Setting<Boolean> maxPower = sgPower.add(new BoolSetting.Builder()
        .name("最大威力")
        .description("自动寻找最大可用高度")
        .defaultValue(false)
        .visible(macePower::get)
        .build()
    );

    private final Setting<Integer> fallHeight = sgPower.add(new IntSetting.Builder()
        .name("下落高度")
        .description("模拟的下落高度")
        .defaultValue(22)
        .min(1)
        .max(50)
        .sliderMax(50)
        .visible(() -> macePower.get() && !maxPower.get())
        .build()
    );


    private final Setting<Boolean> checkTarget = sgPower.add(new BoolSetting.Builder()
        .name("检查目标")
        .description("不对创造模式、无敌、格挡的玩家使用")
        .defaultValue(true)
        .visible(macePower::get)
        .build()
    );

    private boolean wasGliding = false;
    private int originalSlot = -1;
    private int switchBackTicks = 0;

    public MaceCombo() {
        super(BaseModule.CATEGORY_MIKU_COMBAT, "切锤增伤", "武器锤连击. 使用指定武器攻击时自动切锤增伤");
    }

    @Override
    public void onDeactivate() {
        if (originalSlot != -1 && mc.player != null) {
            BagUtil.swap(originalSlot, false);
            originalSlot = -1;
        }
        switchBackTicks = 0;
        wasGliding = false;
    }

    @EventHandler(priority = EventPriority.HIGH)
    private void onAttack(AttackEntityEvent event) {
        if (mc.player == null || mc.level == null) return;

        Entity target = event.entity;
        if (target == null || !target.isAlive() || !(target instanceof LivingEntity)) return;

        wasGliding = Via.isFallFlying(mc);

        // 自动切换逻辑
        if (autoSwitch.get() && isWeaponTypeMatched()) {
            FindItemResult mace = findMace();
            if (mace.found()) {
                originalSlot = Via.getSelectedSlot();
                BagUtil.swap(mace.slot(), false);
                switchBackTicks = switchDelay.get();
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    private void onAttackAfter(AttackEntityEvent event) {
        if (mc.player == null || mc.level == null) return;

        Entity target = event.entity;
        if (!(target instanceof LivingEntity living)) return;

        // 威力增强逻辑
        if (macePower.get() && mc.player.getMainHandItem().getItem() instanceof MaceItem) {
            if (!elytraOnly.get() || Via.isFallFlying(mc)) {
                if (!shouldSkipTarget(living)) {
                    applyMacePower();
                }
            }
        }

        // 恢复鞘翅飞行状态
        if (wasGliding && !Via.isFallFlying(mc)) {
            mc.player.connection.send(
                new ServerboundPlayerCommandPacket(mc.player, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING)
            );
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null) return;

        // 处理切回剑
        if (switchBackTicks > 0) {
            switchBackTicks--;
            if (switchBackTicks == 0 && originalSlot != -1) {
                BagUtil.swap(originalSlot, false);
                originalSlot = -1;
            }
        }

        // 持续保持鞘翅状态
        if (wasGliding && !Via.isFallFlying(mc)) {
            mc.player.connection.send(
                new ServerboundPlayerCommandPacket(mc.player, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING)
            );
            wasGliding = false;
        }
    }

    private boolean isWeaponTypeMatched() {
        Item item = mc.player.getMainHandItem().getItem();
        switch (weaponType.get()) {
            case SWORD:
                return MikuUtil.isArmor(item);
            case AXE:
                return item instanceof AxeItem;
            case HAND:
                return mc.player.getMainHandItem().isEmpty();
            case ANY:
                return true; // 任意武器都触发切换
            default:
                return false;
        }
    }

    private FindItemResult findMace() {
        if (breachOnly.get()) {
            return BagUtil.findInHotbar(stack -> isMaceWithBreach(stack));
        } else {
            return BagUtil.findInHotbar(stack -> stack.getItem() instanceof MaceItem);
        }
    }

    private boolean isMaceWithBreach(ItemStack stack) {
        if (!(stack.getItem() instanceof MaceItem)) return false;

        ItemEnchantments enchants = stack.getOrDefault(
            DataComponents.ENCHANTMENTS,
            ItemEnchantments.EMPTY
        );

        for (Object2IntMap.Entry<Holder<Enchantment>> entry : enchants.entrySet()) {
            Holder<?> enchant = entry.getKey();
            if (enchant.unwrapKey().isPresent()) {
                Identifier id = enchant.unwrapKey().get().identifier();
                if (id.getPath().equals("breach")) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean shouldSkipTarget(LivingEntity target) {
        if (!checkTarget.get()) return false;

        if (target instanceof Player player) {
            return player.isCreative() || player.isBlocking();
        }
        return target.isInvulnerable();
    }

    private void applyMacePower() {
        try {
            Vec3 originalPos = Via.getEntityPos(mc.player);
            int height = getOptimalHeight();

            if (height <= 0) return;

            BlockPos checkPos1 = mc.player.blockPosition().offset(0, height, 0);
            BlockPos checkPos2 = checkPos1.above();

            if (!isSafeBlock(checkPos1) || !isSafeBlock(checkPos2)) return;


            applyPower(originalPos, height);
        } catch (Exception ignored) {
        }
    }


    private void applyPower(Vec3 originalPos, int height) {
        int packets = Math.min((int) Math.ceil(height / 10.0), 20);

        for (int i = 0; i < Math.max(4, packets - 1); i++) {
            mc.player.connection.send(
                Via.getOnGroundOnly(false)
            );
        }

        double targetY = mc.player.getY() + Math.min(height, fallHeight.get());
        mc.player.connection.send(Via.getPositionAndOnGround(
                mc.player.getX(), targetY, mc.player.getZ(),
                false
            )
        );

        mc.player.connection.send(
            Via.getPositionAndOnGround(
                originalPos.x, originalPos.y, originalPos.z,
                false
            )
        );
    }

    private int getOptimalHeight() {
        if (!maxPower.get()) {
            return fallHeight.get();
        }

        BlockPos playerPos = mc.player.blockPosition();
        int maxSearch = playerPos.getY() + 170;

        for (int y = maxSearch; y > playerPos.getY(); y--) {
            BlockPos check1 = new BlockPos(playerPos.getX(), y, playerPos.getZ());
            BlockPos check2 = check1.above();
            if (isSafeBlock(check1) && isSafeBlock(check2)) {
                return y - playerPos.getY();
            }
        }
        return 0;
    }

    private boolean isSafeBlock(BlockPos pos) {
        return mc.level.getBlockState(pos).canBeReplaced() &&
            mc.level.getFluidState(pos).isEmpty() &&
            !mc.level.getBlockState(pos).is(Blocks.POWDER_SNOW);
    }
}
