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
package com.github.mikumiku.addon.util;

import com.github.mikumiku.addon.modules.VillagerRoller;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.meteor.KeyEvent;
import meteordevelopment.meteorclient.mixininterface.IRaycastContext;
import meteordevelopment.meteorclient.mixininterface.IVec3d;
import meteordevelopment.meteorclient.utils.player.Rotations;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;

public class Via {
    // 1.21.10：EnchantedBookItem 已移除，附魔书改为 DataComponents.STORED_ENCHANTMENTS 构造
    public static ItemStack getEnchantedBookWith(Optional<Holder.Reference<Enchantment>> en) {
        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        ItemEnchantments.Mutable mut = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        mut.set(en.get(), en.get().value().getMaxLevel());
        book.set(DataComponents.STORED_ENCHANTMENTS, mut.toImmutable());
        return book;
    }

    public static Registry<Enchantment> getEnchantmentRegistry() {
        RegistryAccess registryManager = Minecraft.getInstance().level.registryAccess();
        // 1.21.10：registryOrThrow 更名为 lookupOrThrow
        return registryManager.lookupOrThrow(Registries.ENCHANTMENT);
    }


    // 26.1：ServerboundMovePlayerPacket 的嵌套类改名为 Rot/PosRot/Pos/StatusOnly
    // 1.21.10：Pos/PosRot/StatusOnly 构造器追加 horizontalCollision 参数
    public static ServerboundMovePlayerPacket get(float currentYaw, float pitch, boolean onGround) {
        return new ServerboundMovePlayerPacket.Rot(currentYaw, pitch, onGround, false);
    }

    public static ServerboundMovePlayerPacket getFull(double x, double y, double z, float yaw, float pitch, boolean onGround) {
        return new ServerboundMovePlayerPacket.PosRot(x, y, z, yaw, pitch, onGround, false);
    }

    public static ServerboundMovePlayerPacket getPositionAndOnGround(double x, double y, double z, boolean onGround) {
        return new ServerboundMovePlayerPacket.Pos(x, y, z, onGround, false);
    }

    public static ServerboundMovePlayerPacket getOnGroundOnly(boolean onGround) {
        return new ServerboundMovePlayerPacket.StatusOnly(onGround, false);
    }


    public static boolean isFallFlying(Minecraft mc) {
        return mc.player.isFallFlying();
    }

    // 1.21.10：Input 变为 record（net.minecraft.world.entity.player.Input），挂在 ClientInput.keyPresses 上
    public static boolean isJumping(Minecraft mc) {
        return mc.player.input.keyPresses.jump();
    }

    public static boolean isSneaking(Minecraft mc) {
        return mc.player.input.keyPresses.shift();
    }

    public static int getTopY(Minecraft mc) {
        return mc.level.getHeight(Heightmap.Types.WORLD_SURFACE, (int) mc.player.getX(), (int) mc.player.getZ());
    }

    public static Direction getOppositeDirectionTo(BlockPos blockPos) {
        // 1.21.10：Rotations.getYaw 只接受 Entity/Vec3，BlockPos 先取中心点
        Direction dir = Direction.fromYRot(Rotations.getYaw(Vec3.atCenterOf(blockPos))).getOpposite();

        return dir;
    }

    public static double getToughness(LivingEntity entity) {
        double value = entity.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
        return value;
    }

    public static void setRaycast(IRaycastContext raycastContext, Vec3 source, Vec3 vec3d, ClipContext.Block shapeType, ClipContext.Fluid fluidHandling, LocalPlayer player) {
        // 1.21.10：meteor 注入接口方法更名 meteor$set，且末参放宽为 Entity
        raycastContext.meteor$set(source, vec3d, shapeType, fluidHandling, player);
    }

    public static void setMovement(IRaycastContext raycastContext, Vec3 source, Vec3 vec3d, ClipContext.Block shapeType, ClipContext.Fluid fluidHandling, LocalPlayer player) {
        raycastContext.meteor$set(source, vec3d, shapeType, fluidHandling, player);
    }

    public static void setMovement(IVec3d movement, double x, double y, double z) {
        movement.meteor$set(x, y, z);
    }

    public static void drawTexture(GuiGraphics drawContext, ResourceLocation texture2, int i, int i1, int i2, int i3) {
    }

    public static Vec3 playerKnockback(ClientboundExplodePacket packet) {
        // 1.21.10：ClientboundExplodePacket 变为 record，击退字段是 Optional<Vec3>
        return packet.playerKnockback().orElse(Vec3.ZERO);
    }

    public static int getSelectedSlot() {
        Minecraft mc = Minecraft.getInstance();
        // 1.21.10：Inventory.selected 字段改为访问器
        return mc.player.getInventory().getSelectedSlot();
    }

    public static void setSelectedSlot(int selectedSlot) {
        Minecraft mc = Minecraft.getInstance();
        mc.player.getInventory().setSelectedSlot(selectedSlot);
    }

    public static CompoundTag getNbtCompound(CompoundTag tag, String key) {
        // 1.21.5+：getCompound 返回 Optional，取 OrEmpty 版本
        return tag.getCompoundOrEmpty(key);
    }

    public static ListTag getNbtList(CompoundTag tag, String key) {
        return tag.getListOrEmpty(key);
    }
    public static boolean isNoneProfession(VillagerData data) {
        // 1.21.10：VillagerProfession.NONE 是 ResourceKey，用 Holder.is(ResourceKey) 比较
        return data.profession().is(VillagerProfession.NONE);
    }
    public static void sendPressShift() {
        // 1.21.10：ServerboundPlayerInputPacket 构造器改为携带 Input record（f/b/l/r/jump/shift/sprint）
        MeteorClient.mc.player.connection.send(new ServerboundPlayerInputPacket(new net.minecraft.world.entity.player.Input(false, false, false, false, false, true, false)));
    }

    public static void sendReleaseShift() {
        MeteorClient.mc.player.connection.send(new ServerboundPlayerInputPacket(new net.minecraft.world.entity.player.Input(false, false, false, false, false, false, false)));
    }


    public static int getSyncId(ClientboundContainerSetContentPacket inventoryS2CPacket) {
        return inventoryS2CPacket.containerId();
    }

    public static List<ItemStack> getInvContent(ClientboundContainerSetContentPacket inventoryS2CPacket) {
        return inventoryS2CPacket.items();
    }

    public static VillagerProfession getVillagerProfession(Villager villager) {
        // 1.21.10：profession() 返回 Holder，取 value
        return villager.getVillagerData().profession().value();
    }

    // 1.21.9+：ClientInput.getMoveVector() 是归一化后的向量（KeyboardInput.tick 里 Vec2.normalized()），
    // 斜向时幅值不是 1；老版 forwardImpulse/leftImpulse 是原始冲量（等价 KeyboardInput.calculateImpulse），
    // 为保持语义直接从 keyPresses 布尔方向推导冲量。
    public static float movementForward(ClientInput input) {
        return calculateImpulse(input.keyPresses.forward(), input.keyPresses.backward());
    }


    public static float movementSideways(ClientInput input) {
        return calculateImpulse(input.keyPresses.left(), input.keyPresses.right());
    }

    // 与 vanilla KeyboardInput.calculateImpulse 一致：两键同按/都松为 0，正键为 1，负键为 -1
    private static float calculateImpulse(boolean positive, boolean negative) {
        if (positive == negative) {
            return 0.0F;
        }
        return positive ? 1.0F : -1.0F;
    }

    public static void tagRollingEnchantment(CompoundTag tag, VillagerRoller.RollingEnchantment rolling) {
        // 1.21.5+：getString/getInt/getBoolean 返回 Optional，改用 xxxOr 默认值版本
        rolling.enchantment = ResourceLocation.tryParse(tag.contains("enchantment") ? tag.getStringOr("enchantment", "") : "");
        rolling.minLevel = tag.contains("minLevel") ? tag.getIntOr("minLevel", 1) : 1;
        rolling.maxCost = tag.contains("maxCost") ? tag.getIntOr("maxCost", 64) : 64;
        rolling.enabled = tag.contains("enabled") ? tag.getBooleanOr("enabled", true) : true;
    }
    public  static Vec3 getEntityPos(Entity entity) {
        return entity.position();
    }

    public  static Level getEntityWorld(Entity entity) {
        return entity.level();
    }

    public  static String getGameProfileName(Player entity) {
        return entity.getScoreboardName();
    }

    public static int getKeyEventKey(KeyEvent event) {
        // 1.21.10：meteor KeyEvent 的 key 字段改为 key() 访问器
        return event.key();
    }
}
