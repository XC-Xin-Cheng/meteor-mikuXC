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
import meteordevelopment.meteorclient.events.world.PlaySoundEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.Items;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.InteractionHand;
import net.minecraft.resources.Identifier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class ChestplateFly extends BaseModule {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final Setting<Mode> mode = this.sgGeneral.add(
        new EnumSetting.Builder<Mode>()
            .name("模式")
            .description("决定模块在飞行时如何处理装备：\n" +
                " - 胸甲模式：每次使用鞘翅飞行后会立即换回胸甲，用于在飞行与战斗间快速切换。\n" +
                " - 鞘翅模式：保持鞘翅不换回胸甲，适合长时间飞行或远距离探索。")
            .defaultValue(Mode.胸甲模式)
            .build()
    );
    public final Setting<Integer> fireworkDelay = this.sgGeneral.add(new IntSetting.Builder()
        .name("烟花延迟")
        .description("控制自动使用烟花火箭的时间间隔（单位：秒）。\n" +
            "数值越低，烟花使用越频繁，飞行速度越平稳；数值越高，则使用间隔越长，节省烟花。\n" +
            "默认值 5 表示每隔约 5 秒使用一次烟花推进飞行。")
        .defaultValue(5)
        .sliderRange(0, 10)
        .build());
    private int fireworkTicksLeft = 0;
    private boolean needsFirework = false;
    private Vec3 currentVelocity = Vec3.ZERO;
    private InventorySlotSwap slotSwap = null;

    public ChestplateFly() {
        super("甲飞", "再一次。");
    }

    @Override
    public void onActivate() {
        super.onActivate();
        this.needsFirework = this.getIsUsingFirework();
        this.currentVelocity = this.mc.player.getDeltaMovement();
        this.mc.player.jumpFromGround();
        this.mc.player.setOnGround(false);
    }

    @Override
    public void onDeactivate() {
        this.equipChestplate(this.slotSwap);
        Via.sendReleaseShift();
        this.mc.player.setShiftKeyDown(false);
        this.fireworkTicksLeft = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        boolean isUsingFirework = this.getIsUsingFirework();
        if (isUsingFirework || InvUtils.find(Items.FIREWORK_ROCKET).found()) {
            AABB boundingBox = this.mc.player.getBoundingBox();
            double playerFeetY = boundingBox.minY;
            AABB groundBox = new AABB(boundingBox.minX, playerFeetY - 0.1, boundingBox.minZ, boundingBox.maxX, playerFeetY, boundingBox.maxZ);

            for (BlockPos pos : BlockPos.betweenClosed(
                (int) Math.floor(groundBox.minX),
                (int) Math.floor(groundBox.minY),
                (int) Math.floor(groundBox.minZ),
                (int) Math.floor(groundBox.maxX),
                (int) Math.floor(groundBox.maxY),
                (int) Math.floor(groundBox.maxZ)
            )) {
                BlockState blockState = this.mc.level.getBlockState(pos);
                if (blockState.isRedstoneConductor(this.mc.level, pos)) {
                    double blockTopY = pos.getY() + 1.0;
                    double distanceToBlock = playerFeetY - blockTopY;
                    if (distanceToBlock >= 0.0 && distanceToBlock < 0.1 && this.currentVelocity.y < 0.0) {
                        this.currentVelocity = new Vec3(this.currentVelocity.x, 0.1, this.currentVelocity.z);
                    }
                }
            }

            this.slotSwap = this.equipElytra();
            this.mc.player
                .connection
                .send(new ServerboundPlayerCommandPacket(this.mc.player, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
            if (this.fireworkTicksLeft <= 0) {
                this.needsFirework = true;
            }

            if (this.needsFirework && this.currentVelocity.length() > 1.0E-7) {
                this.useFirework();
                this.needsFirework = false;
            }

            if (this.fireworkTicksLeft >= 0) {
                this.fireworkTicksLeft--;
            } else {
                this.fireworkTicksLeft = this.fireworkDelay.get();
            }

            if (this.mode.get() == Mode.胸甲模式) {
                this.equipChestplate(this.slotSwap);
                this.slotSwap = null;
            }
        }
    }

    private boolean getIsUsingFirework() {
        boolean usingFirework = false;

        for (Entity entity : this.mc.level.entitiesForRendering()) {
            if (entity instanceof FireworkRocketEntity firework && firework.getOwner() != null && firework.getOwner().equals(this.mc.player)) {
                usingFirework = true;
            }
        }

        return usingFirework;
    }

    public void equipChestplate(InventorySlotSwap slotSwap) {
        if (!this.mc.player.getItemBySlot(EquipmentSlot.CHEST).getItem().equals(Items.DIAMOND_CHESTPLATE)
            && !this.mc.player.getItemBySlot(EquipmentSlot.CHEST).getItem().equals(Items.NETHERITE_CHESTPLATE)) {
            FindItemResult result = InvUtils.findInHotbar(Items.NETHERITE_CHESTPLATE);
            if (!result.found()) {
                result = InvUtils.findInHotbar(Items.DIAMOND_CHESTPLATE);
            }

            if (result.found()) {
                this.mc.gameMode.handleContainerInput(this.mc.player.inventoryMenu.containerId, 6, result.slot(), ContainerInput.SWAP, this.mc.player);
                if (slotSwap != null) {
                    this.mc
                        .gameMode
                        .handleContainerInput(this.mc.player.inventoryMenu.containerId, slotSwap.inventorySlot, result.slot(), ContainerInput.SWAP, this.mc.player);
                }
            } else {
                result = InvUtils.find(Items.NETHERITE_CHESTPLATE);
                if (!result.found()) {
                    result = InvUtils.find(Items.DIAMOND_CHESTPLATE);
                }

                if (result.found()) {
                    InvUtils.move().from(result.slot()).toArmor(2);
                }
            }
        }
    }

    public InventorySlotSwap equipElytra() {
        if (this.mc.player.getItemBySlot(EquipmentSlot.CHEST).getItem().equals(Items.ELYTRA)) {
            return null;
        } else {
            FindItemResult result = InvUtils.findInHotbar(Items.ELYTRA);
            if (result.found()) {
                this.mc.gameMode.handleContainerInput(this.mc.player.inventoryMenu.containerId, 6, result.slot(), ContainerInput.SWAP, this.mc.player);
                return null;
            } else {
                result = InvUtils.find(Items.ELYTRA);
                if (!result.found()) {
                    return null;
                } else {
                    FindItemResult hotbarSlot = InvUtils.findInHotbar(x -> x.getItem() != Items.TOTEM_OF_UNDYING);
                    this.mc
                        .gameMode
                        .handleContainerInput(
                            this.mc.player.inventoryMenu.containerId,
                            result.slot(),
                            hotbarSlot.found() ? hotbarSlot.slot() : 0,
                            ContainerInput.SWAP,
                            this.mc.player
                        );
                    this.mc
                        .gameMode
                        .handleContainerInput(
                            this.mc.player.inventoryMenu.containerId, 6, hotbarSlot.found() ? hotbarSlot.slot() : 0, ContainerInput.SWAP, this.mc.player
                        );
                    InventorySlotSwap slotSwap = new InventorySlotSwap();
                    slotSwap.hotbarSlot = hotbarSlot.found() ? hotbarSlot.slot() : 0;
                    slotSwap.inventorySlot = result.slot();
                    return slotSwap;
                }
            }
        }
    }

    private void useFirework() {
        this.fireworkTicksLeft = (int) (this.fireworkDelay.get().intValue() * 20.0);
        int hotbarSilentSwapSlot = -1;
        int inventorySilentSwapSlot = -1;
        FindItemResult itemResult = InvUtils.findInHotbar(Items.FIREWORK_ROCKET);
        if (!itemResult.found()) {
            FindItemResult invResult = InvUtils.find(Items.FIREWORK_ROCKET);
            if (!invResult.found()) {
                return;
            }

            FindItemResult hotbarSlotToSwapToResult = InvUtils.findInHotbar(x -> x.getItem() != Items.TOTEM_OF_UNDYING);
            inventorySilentSwapSlot = invResult.slot();
            hotbarSilentSwapSlot = hotbarSlotToSwapToResult.found() ? hotbarSlotToSwapToResult.slot() : 0;
            this.mc
                .gameMode
                .handleContainerInput(this.mc.player.inventoryMenu.containerId, inventorySilentSwapSlot, hotbarSilentSwapSlot, ContainerInput.SWAP, this.mc.player);
            itemResult = InvUtils.findInHotbar(Items.FIREWORK_ROCKET);
        }

        if (itemResult.found()) {
            if (itemResult.isOffhand()) {
                this.mc.gameMode.useItem(this.mc.player, InteractionHand.OFF_HAND);
                this.mc.player.swing(InteractionHand.OFF_HAND);
            } else {
                InvUtils.swap(itemResult.slot(), true);
                this.mc.gameMode.useItem(this.mc.player, InteractionHand.MAIN_HAND);
                this.mc.player.swing(InteractionHand.MAIN_HAND);
                InvUtils.swapBack();
            }

            if (inventorySilentSwapSlot != -1 && hotbarSilentSwapSlot != -1) {
                this.mc.gameMode
                    .handleContainerInput(
                        this.mc.player.inventoryMenu.containerId, inventorySilentSwapSlot, hotbarSilentSwapSlot, ContainerInput.SWAP, this.mc.player
                    );
            }
        }
    }

    @EventHandler
    public void onPlaySound(PlaySoundEvent event) {
        List<Identifier> armorEquipSounds = List.of(
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
        for (Identifier identifier : armorEquipSounds) {
            if (identifier.equals(event.sound.getIdentifier())) {
                event.cancel();
                break;
            }
        }
    }
    private class InventorySlotSwap {
        public int hotbarSlot;
        public int inventorySlot;
    }

    public static enum Mode {
        胸甲模式,
        鞘翅模式;
    }
}
