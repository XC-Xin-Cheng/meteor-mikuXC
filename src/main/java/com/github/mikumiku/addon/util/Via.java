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
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
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
    public static ItemStack getEnchantedBookWith(Optional<Holder.Reference<Enchantment>> en) {
        return EnchantedBookItem.createForEnchantment(new EnchantmentInstance(en.get(), en.get().value().getMaxLevel()));
    }

    public static Registry<Enchantment> getEnchantmentRegistry() {
        RegistryAccess registryManager = Minecraft.getInstance().level.registryAccess();
        return registryManager.registryOrThrow(Registries.ENCHANTMENT);
    }


    // 26.1：ServerboundMovePlayerPacket 的嵌套类改名为 Rot/PosRot/Pos/StatusOnly
    public static ServerboundMovePlayerPacket get(float currentYaw, float pitch, boolean onGround) {
        return new ServerboundMovePlayerPacket.Rot(currentYaw, pitch, onGround);
    }

    public static ServerboundMovePlayerPacket getFull(double x, double y, double z, float yaw, float pitch, boolean onGround) {
        return new ServerboundMovePlayerPacket.PosRot(x, y, z, yaw, pitch, onGround);
    }

    public static ServerboundMovePlayerPacket getPositionAndOnGround(double x, double y, double z, boolean onGround) {
        return new ServerboundMovePlayerPacket.Pos(x, y, z, onGround);
    }

    public static ServerboundMovePlayerPacket getOnGroundOnly(boolean onGround) {
        return new ServerboundMovePlayerPacket.StatusOnly(onGround);
    }


    public static boolean isFallFlying(Minecraft mc) {
        return mc.player.isFallFlying();
    }

    public static boolean isJumping(Minecraft mc) {
        return mc.player.input.jumping;
    }

    public static boolean isSneaking(Minecraft mc) {
        return mc.player.input.shiftKeyDown;

    }

    public static int getTopY(Minecraft mc) {
        return mc.level.getHeight(Heightmap.Types.WORLD_SURFACE, (int) mc.player.getX(), (int) mc.player.getZ());
    }

    public static Direction getOppositeDirectionTo(BlockPos blockPos) {
        Direction dir = Direction.fromYRot(Rotations.getYaw(blockPos)).getOpposite();

        return dir;
    }

    public static double getToughness(LivingEntity entity) {
        double value = entity.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
        return value;
    }

    public static void setRaycast(IRaycastContext raycastContext, Vec3 source, Vec3 vec3d, ClipContext.Block shapeType, ClipContext.Fluid fluidHandling, LocalPlayer player) {
        raycastContext.set(source, vec3d, shapeType, fluidHandling, player);
    }

    public static void setMovement(IRaycastContext raycastContext, Vec3 source, Vec3 vec3d, ClipContext.Block shapeType, ClipContext.Fluid fluidHandling, LocalPlayer player) {
        raycastContext.set(source, vec3d, shapeType, fluidHandling, player);
    }

    public static void setMovement(IVec3d movement, double x, double y, double z) {
        movement.set(x, y, z);
    }

    public static void drawTexture(GuiGraphics drawContext, ResourceLocation texture2, int i, int i1, int i2, int i3) {
    }

    public static Vec3 playerKnockback(ClientboundExplodePacket packet) {
        return new Vec3(packet.getKnockbackX(), packet.getKnockbackY(), packet.getKnockbackZ());
    }

    public static int getSelectedSlot() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player.getInventory().selected;
    }

    public static void setSelectedSlot(int selectedSlot) {
        Minecraft mc = Minecraft.getInstance();
        mc.player.getInventory().selected = selectedSlot;
    }

    public static CompoundTag getNbtCompound(CompoundTag tag, String key) {
        return tag.getCompound(key);
    }

    public static ListTag getNbtList(CompoundTag tag, String key) {
        return tag.getList(key, net.minecraft.nbt.Tag.TAG_LIST);
    }
    public static boolean isNoneProfession(VillagerData data) {
        return data.getProfession() == VillagerProfession.NONE;
    }
    public static void sendPressShift() {
        MeteorClient.mc.player.connection.send(new ServerboundPlayerInputPacket(0, 0, false, true));
    }

    public static void sendReleaseShift() {
        MeteorClient.mc.player.connection.send(new ServerboundPlayerInputPacket(0, 0, false, false));
    }


    public static int getSyncId(ClientboundContainerSetContentPacket inventoryS2CPacket) {
        return inventoryS2CPacket.getContainerId();
    }

    public static List<ItemStack> getInvContent(ClientboundContainerSetContentPacket inventoryS2CPacket) {
        return inventoryS2CPacket.getItems();
    }

    public static VillagerProfession getVillagerProfession(Villager villager) {
        return villager.getVillagerData().getProfession();
    }

    public static float movementForward(Input input) {
        return input.forwardImpulse;
    }


    public static float movementSideways(Input input) {
        return input.leftImpulse;
    }

    public static void tagRollingEnchantment(CompoundTag tag, VillagerRoller.RollingEnchantment rolling) {
        rolling.enchantment = ResourceLocation.tryParse(tag.contains("enchantment") ? tag.getString("enchantment") : "");
        rolling.minLevel = tag.contains("minLevel") ? tag.getInt("minLevel") : 1;
        rolling.maxCost = tag.contains("maxCost") ? tag.getInt("maxCost") : 64;
        rolling.enabled = tag.contains("enabled") ? tag.getBoolean("enabled") : true;
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
        return event.key;
    }
}
