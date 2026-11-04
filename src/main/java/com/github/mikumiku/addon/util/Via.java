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
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.player.Input;
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
        // 1.21.6：EnchantedBookItem 已删除，改用数据组件直接写入 STORED_ENCHANTMENTS
        Holder.Reference<Enchantment> holder = en.get();
        ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        mutable.set(holder, holder.value().getMaxLevel());
        ItemStack stack = new ItemStack(Items.ENCHANTED_BOOK);
        stack.set(DataComponents.STORED_ENCHANTMENTS, mutable.toImmutable());
        return stack;
    }

    public static Registry<Enchantment> getEnchantmentRegistry() {
        RegistryAccess registryManager = Minecraft.getInstance().level.registryAccess();
        // 1.21.2+：RegistryAccess.registryOrThrow 改名 lookupOrThrow
        return registryManager.lookupOrThrow(Registries.ENCHANTMENT);
    }


    // 26.1：ServerboundMovePlayerPacket 的嵌套类改名为 Rot/PosRot/Pos/StatusOnly
    // 1.21.2+：构造器多一个 horizontalCollision 参数，与 Meteor 自身 Rotations 的做法一致
    public static ServerboundMovePlayerPacket get(float currentYaw, float pitch, boolean onGround) {
        return new ServerboundMovePlayerPacket.Rot(currentYaw, pitch, onGround, Minecraft.getInstance().player.horizontalCollision);
    }

    public static ServerboundMovePlayerPacket getFull(double x, double y, double z, float yaw, float pitch, boolean onGround) {
        return new ServerboundMovePlayerPacket.PosRot(x, y, z, yaw, pitch, onGround, Minecraft.getInstance().player.horizontalCollision);
    }

    public static ServerboundMovePlayerPacket getPositionAndOnGround(double x, double y, double z, boolean onGround) {
        return new ServerboundMovePlayerPacket.Pos(x, y, z, onGround, Minecraft.getInstance().player.horizontalCollision);
    }

    public static ServerboundMovePlayerPacket getOnGroundOnly(boolean onGround) {
        return new ServerboundMovePlayerPacket.StatusOnly(onGround, Minecraft.getInstance().player.horizontalCollision);
    }


    public static boolean isFallFlying(Minecraft mc) {
        return mc.player.isFallFlying();
    }

    public static boolean isJumping(Minecraft mc) {
        // 1.21.6：ClientInput 的 jumping 字段改为 Input record 的 jump() 访问器
        return mc.player.input.keyPresses.jump();
    }

    public static boolean isSneaking(Minecraft mc) {
        return mc.player.input.keyPresses.shift();
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

    // 1.21.6 meteor：注入接口方法统一加 meteor$ 前缀，末参放宽为 Entity
    public static void setRaycast(IRaycastContext raycastContext, Vec3 source, Vec3 vec3d, ClipContext.Block shapeType, ClipContext.Fluid fluidHandling, LocalPlayer player) {
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
        // 1.21.2+：ClientboundExplodePacket 改为 record，击退合并成一个 Optional<Vec3>
        return packet.playerKnockback().orElse(Vec3.ZERO);
    }

    public static int getSelectedSlot() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player.getInventory().getSelectedSlot();
    }

    public static void setSelectedSlot(int selectedSlot) {
        Minecraft mc = Minecraft.getInstance();
        mc.player.getInventory().setSelectedSlot(selectedSlot);
    }

    public static CompoundTag getNbtCompound(CompoundTag tag, String key) {
        // 1.21.5+ NBT：getCompound 返回 Optional，改用 getCompoundOrEmpty
        return tag.getCompoundOrEmpty(key);
    }

    public static ListTag getNbtList(CompoundTag tag, String key) {
        // 1.21.5+ NBT：getList 不再带类型参数，改用 getListOrEmpty
        return tag.getListOrEmpty(key);
    }
    public static boolean isNoneProfession(VillagerData data) {
        // 1.21.6：VillagerData 是 record，profession() 返回 Holder；NONE 是 ResourceKey
        return data.profession().is(VillagerProfession.NONE);
    }
    public static void sendPressShift() {
        // 1.21.6：ServerboundPlayerInputPacket 改收 Input record（forward,backward,left,right,jump,shift,sprint）
        MeteorClient.mc.player.connection.send(new ServerboundPlayerInputPacket(new Input(false, false, false, false, false, true, false)));
    }

    public static void sendReleaseShift() {
        MeteorClient.mc.player.connection.send(new ServerboundPlayerInputPacket(Input.EMPTY));
    }


    public static int getSyncId(ClientboundContainerSetContentPacket inventoryS2CPacket) {
        // 1.21.6：改为 record 访问器
        return inventoryS2CPacket.containerId();
    }

    public static List<ItemStack> getInvContent(ClientboundContainerSetContentPacket inventoryS2CPacket) {
        return inventoryS2CPacket.items();
    }

    public static VillagerProfession getVillagerProfession(Villager villager) {
        return villager.getVillagerData().profession().value();
    }

    // 1.21.6：mc.player.input 是 ClientInput，方向冲量从 keyPresses（Input record）推导，
    // 与原版 KeyboardInput.calculateImpulse 的语义一致（等价旧 forwardImpulse/leftImpulse）
    public static float movementForward(ClientInput input) {
        Input kp = input.keyPresses;
        return kp.forward() == kp.backward() ? 0.0F : (kp.forward() ? 1.0F : -1.0F);
    }


    public static float movementSideways(ClientInput input) {
        Input kp = input.keyPresses;
        return kp.left() == kp.right() ? 0.0F : (kp.left() ? 1.0F : -1.0F);
    }

    public static void tagRollingEnchantment(CompoundTag tag, VillagerRoller.RollingEnchantment rolling) {
        // 1.21.5+ NBT：getString/getInt/getBoolean 返回 Optional，改用带默认值的 getXxxOr
        rolling.enchantment = ResourceLocation.tryParse(tag.getStringOr("enchantment", ""));
        rolling.minLevel = tag.getIntOr("minLevel", 1);
        rolling.maxCost = tag.getIntOr("maxCost", 64);
        rolling.enabled = tag.getBooleanOr("enabled", true);
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
