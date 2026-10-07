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


import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.meteorclient.utils.world.Dimension;
import net.minecraft.world.level.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import com.mojang.math.Axis;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.opengl.GL11;

import java.util.Set;

// 上游用 lombok @UtilityClass（成员隐式 static），此处展开为显式 static 方法
public final class MikuUtil {
    private MikuUtil() {
    }

    // 性能：这些集合原本在方法体内用 Set.of(...) 现建，而 isArmor/isSwordItem/isPickaxeItem
    // 会在背包 36~45 个槽位的谓词循环里被逐个调用，等于每次扫描都要重建几十次集合。
    // 提升为 static final 常量后只构建一次。
    private static final Set<Item> PICKAXES = Set.of(
        Items.WOODEN_PICKAXE,
        Items.STONE_PICKAXE,
        Items.IRON_PICKAXE,
        Items.GOLDEN_PICKAXE,
        Items.DIAMOND_PICKAXE,
        Items.NETHERITE_PICKAXE
    );

    private static final Set<Item> SWORDS = Set.of(
        Items.WOODEN_SWORD,
        Items.STONE_SWORD,
        Items.IRON_SWORD,
        Items.GOLDEN_SWORD,
        Items.DIAMOND_SWORD,
        Items.NETHERITE_SWORD
    );

    private static final Set<Item> ARMOR = Set.of(
        // 皮革
        Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS,
        // 链甲
        Items.CHAINMAIL_HELMET, Items.CHAINMAIL_CHESTPLATE, Items.CHAINMAIL_LEGGINGS, Items.CHAINMAIL_BOOTS,
        // 铁
        Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS,
        // 金
        Items.GOLDEN_HELMET, Items.GOLDEN_CHESTPLATE, Items.GOLDEN_LEGGINGS, Items.GOLDEN_BOOTS,
        // 钻石
        Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS,
        // 下界合金
        Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS,
        // 乌龟壳（也算头盔）
        Items.TURTLE_HELMET
    );

    public static void printInv() {
        String inv = """
            ╔═══╦═══════════╗
            ║ 5 ║    ███    ║   ╔═══╦═══╗
            ╠═══╣    ███    ║   ║ 1 ║ 2 ║   ╔═══╗
            ║ 6 ║  █████   ║   ╠═══╬═══╣   ║ 0 ║
            ╠═══╣  █████   ║   ║ 3 ║ 4 ║   ╚═══╝
            ║ 7 ║  █████   ║   ╚═══╩═══╝
            ╠═══╣    ███    ╠═══╗
            ║ 8 ║    ███    ║45 ║
            ╚═══╩══════════  ═╩═══╝
            ╔═══╦═══╦═══╦═══╦═══╦═══╦═══╦═══╦═══╗
            ║ 9 ║10 ║11 ║12 ║13 ║14 ║15 ║16 ║17 ║
            ╠═══╬═══╬═══╬═══╬═══╬═══╬═══╬═══╬═══╣
            ║18 ║19 ║20 ║21 ║22 ║23 ║24 ║25 ║26 ║
            ╠═══╬═══╬═══╬═══╬═══╬═══╬═══╬═══╬═══╣
            ║27 ║28 ║29 ║30 ║31 ║32 ║33 ║34 ║35 ║
            ╚═══╩═══╩═══╩═══╩═══╩═══╩═══╩═══╩═══╝
            ╔═══╦═══╦═══╦═══╦═══╦═══╦═══╦═══╦═══╗
            ║36 ║37 ║38 ║39 ║40 ║41 ║42 ║43 ║44 ║
            ╚═══╩═══╩═══╩═══╩═══╩═══╩═══╩═══╩═══╝

            """;
        String inv2 = """
            ╔═══╦═══════════╗
            ║ 5 ║    ███    ║    ╔═══╦═══╗
            ╠═══╣    ███    ║    ║ 1 ║ 2 ║   ╔═══╗
            ║ 6 ║  ██████  ║   ╠═══╬═══╣   ║ 0 ║
            ╠═══╣  ██████  ║   ║ 3 ║ 4 ║   ╚═══╝
            ║ 7 ║  ██████  ║   ╚═══╩═══╝
            ╠═══╣    ███    ╠═══╗
            ║ 8 ║    ███    ║45 ║ ← 45 = 合成结果(Result slot)
            ╚═══╩═════════════╩═══╝
            ╔═══╦═══╦═══╦═══╦═══╦═══╦═══╦═══╦═══╗
            ║ 9 ║10 ║11 ║12 ║13 ║14 ║15 ║16 ║17 ║  ← 背包第一行
            ╠═══╬═══╬═══╬═══╬═══╬═══╬═══╬═══╬═══╣
            ║18 ║19 ║20 ║21 ║22 ║23 ║24 ║25 ║26 ║  ← 背包第二行
            ╠═══╬═══╬═══╬═══╬═══╬═══╬═══╬═══╬═══╣
            ║27 ║28 ║29 ║30 ║31 ║32 ║33 ║34 ║35 ║  ← 背包第三行
            ╚═══╩═══╩═══╩═══╩═══╩═══╩═══╩═══╩═══╝
            ╔═══╦═══╦═══╦═══╦═══╦═══╦═══╦═══╦═══╗
            ║36 ║37 ║38 ║39 ║40 ║41 ║42 ║43 ║44 ║  ← 快捷栏 (0~8)
            ╚═══╩═══╩═══╩═══╩═══╩═══╩═══╩═══╩═══╝
            """;
        String inv3 = """
            ╔══════════════════════════════════════════════════════╗
            ║                 玩家物品栏 PlayerInventory            ║
            ╠══════════════════════════════════════════════════════╣
            ║                     ↑ 背包部分 ↑                     ║
            ║╔═══╦═══╦═══╦═══╦═══╦═══╦═══╦═══╦═══╗
            ║║ 9 ║10 ║11 ║12 ║13 ║14 ║15 ║16 ║17 ║
            ║╠═══╬═══╬═══╬═══╬═══╬═══╬═══╬═══╬═══╣
            ║║18 ║19 ║20 ║21 ║22 ║23 ║24 ║25 ║26 ║
            ║╠═══╬═══╬═══╬═══╬═══╬═══╬═══╬═══╬═══╣
            ║║27 ║28 ║29 ║30 ║31 ║32 ║33 ║34 ║35 ║
            ║╚═══╩═══╩═══╩═══╩═══╩═══╩═══╩═══╩═══╝
            ║                     ↓ 快捷栏 ↓                     ║
            ║╔═══╦═══╦═══╦═══╦═══╦═══╦═══╦═══╦═══╗
            ║║ 0 ║ 1 ║ 2 ║ 3 ║ 4 ║ 5 ║ 6 ║ 7 ║ 8 ║
            ║╚═══╩═══╩═══╩═══╩═══╩═══╩═══╩═══╩═══╝
            ║   ↑副手 40↑                                        ║
            ╚══════════════════════════════════════════════════════╝
            ╔═════╦═════╦═════╦═════╗
            ║ 36  ║ 37  ║ 38  ║ 39  ║ 40
            ╚═════╩═════╩═════╩═════╝
            头盔 胸甲 护腿 靴子
            ╚═══╩═══╩═══╩═══╩═══╩═══╩═══╩═══╩═══╝
            """;
        Minecraft mc = Minecraft.getInstance();
        //3
        for (int i = 0; i <= 45; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            String name = stack.getDisplayName().getString();
            ChatUtils.success(i + ": " + name);
        }


    }

    public static boolean pathTo(BlockPos pos, double distance) {

        Minecraft mc = Minecraft.getInstance();
        // 玩家是否存在与世界中
        if (mc.player == null || mc.level == null) {
            return false;
        }
        BlockPos playerBlockPos = mc.player.blockPosition();
        BlockPos.MutableBlockPos posMutable = new BlockPos.MutableBlockPos();
        posMutable.set(pos);
        posMutable.setY(playerBlockPos.getY());
        double currentDistance = Vec3.atLowerCornerOf(playerBlockPos).distanceToSqr(Vec3.atLowerCornerOf(pos));
        double requiredDistanceSq = distance * distance;

        // 如果距离足够近，则取消寻路并返回 true
        if (currentDistance <= requiredDistanceSq) {
            MikuBaritone.cancelEverything();
            return true;
        }

        if (!MikuBaritone.isPathing()) {
            disableBlockActions();
            // 如果 Baritone 当前没有在朝该目标寻路，则设置新的路径
            MikuBaritone.setGoalAndPath(pos, distance);
        }

        return false;
    }

    public static void disableBlockActions() {
        // 禁止破坏方块，也禁止放置方块；Baritone 不存在时安全空操作。
        MikuBaritone.setAllowBreakAndPlace(false);
    }

    public static void cancelBaritone() {
        MikuBaritone.cancelEverything();
    }

    public static int countItem(Item item) {
        Minecraft mc = Minecraft.getInstance();
        return mc.player.getInventory().countItem(item);
    }

    public static String getItemName(ItemStack item) {

        return BuiltInRegistries.ITEM.getKey(item.getItem()).getPath();
    }

    public static String getItemName(Item item) {

        return BuiltInRegistries.ITEM.getKey(item).getPath();
    }

    public static boolean isBlockAt(BlockPos pos, Block expectedBlock) {
        Minecraft mc = Minecraft.getInstance();
        return mc.level.getBlockState(pos).is(expectedBlock);
    }

//    public static void renderSign(String text, double x, double y, double z, float scaling, int color) {
//        Minecraft mc = Minecraft.getInstance();
//        Camera camera = mc.gameRenderer.getCamera();
//        Vec3 camPos = camera.getPos();
//
//        PoseStack matrices = new PoseStack();
//
//        // === 正确顺序 ===
//        matrices.push();
//
//        // 1. 平移到目标点
//        matrices.translate(x - camPos.x, y - camPos.y, z - camPos.z);
//
//        // 2. 旋转以面向玩家
//        matrices.multiply(Axis.POSITIVE_Y.rotationDegrees(-camera.getYaw()));
//        matrices.multiply(Axis.POSITIVE_X.rotationDegrees(camera.getPitch()));
//
//        // 3. 缩放（负号保证文本朝向正确）
//        matrices.scale(-scaling, -scaling, scaling);
//
//        float hwidth = mc.textRenderer.getWidth(text) / 2.0f;
//
//        // === 实际绘制 ===
//        GL11.glDepthFunc(GL11.GL_ALWAYS); // 始终绘制在最前（无遮挡）
//
//
//        MultiBufferSource.Immediate vertexConsumers = mc.getBufferBuilders().getEntityVertexConsumers();
//
////        ((AccessorTextRenderer) mc.textRenderer).hookDrawLayer(
////            text, -hwidth, 0.0f,
////            TextRenderer.tweakTransparency(color), true,
////            matrices.peek().getPositionMatrix(),
////            vertexConsumers, TextRenderer.TextLayerType.SEE_THROUGH,
////            0, 0xF000F0
////        );
////        vertexConsumers.draw();
////
////        ((AccessorTextRenderer) mc.textRenderer).hookDrawLayer(
////            text, -hwidth, 0.0f,
////            TextRenderer.tweakTransparency(color), false,
////            matrices.peek().getPositionMatrix(),
////            vertexConsumers, TextRenderer.TextLayerType.SEE_THROUGH,
////            0, 0xF000F0
////        );
//        vertexConsumers.draw();
//
//        GL11.glDepthFunc(GL11.GL_LEQUAL); // 恢复深度模式
//
//        matrices.pop();
//    }

    /**
     * 判断指定名称的类是否存在（可选是否初始化）
     *
     * @param className  完全限定类名，例如 "java.util.ArrayList"
     * @param initialize 是否在加载时初始化该类（执行 static 块等）
     * @return 如果类存在则返回 true，否则 false
     */
    public static boolean isClassExists(String className, boolean initialize) {
        try {
            Class.forName(className, initialize, Thread.currentThread().getContextClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    // 重载方法：默认不初始化类（更安全，避免副作用）
    public static boolean isClassExists(String className) {
        return isClassExists(className, false);
    }


    public static boolean isPickaxeItem(Item item) {
        return PICKAXES.contains(item);
    }

    public static boolean isSwordItem(Item item) {
        // 是任意剑
        return SWORDS.contains(item);
    }

    public static boolean isArmor(Item item) {
        return ARMOR.contains(item);
    }


    public static BlockPos findSuitablePlacePosition() {
        Minecraft mc = Minecraft.getInstance();

        // Get player's facing direction
        Direction playerFacing = mc.player.getDirection();

        // Priority order: facing direction first, then adjacent sides, then diagonals
        // 1. First try the direction player is facing
        BlockPos playerPos = mc.player.blockPosition();
        BlockPos facingPos = playerPos.relative(playerFacing);
        if (isValidPlacePosition(facingPos)) {
            return facingPos;
        }

        // 2. Try adjacent horizontal directions (not diagonal)
        Direction[] adjacentDirections = {
            playerFacing.getClockWise(),
            playerFacing.getCounterClockWise(),
            playerFacing.getOpposite()
        };

        for (Direction dir : adjacentDirections) {
            BlockPos testPos = playerPos.relative(dir);
            if (isValidPlacePosition(testPos)) {

                return testPos;
            }
        }

        // 3. Try above and below current position
        for (int y = 1; y >= -1; y -= 2) { // +1 then -1
            BlockPos testPos = playerPos.offset(0, y, 0);
            if (isValidPlacePosition(testPos)) {

                return testPos;
            }
        }

        // 4. Finally try diagonal positions if no direct adjacent positions work
        for (int distance = 1; distance <= 3; distance++) {
            for (int x = -distance; x <= distance; x++) {
                for (int z = -distance; z <= distance; z++) {
                    // Skip positions we already checked (direct adjacent)
                    if ((Math.abs(x) == 1 && z == 0) || (x == 0 && Math.abs(z) == 1) || (x == 0 && z == 0)) {
                        continue;
                    }

                    // Only check positions at the current distance boundary
                    if (Math.abs(x) == distance || Math.abs(z) == distance) {
                        for (int y = -1; y <= 1; y++) {
                            BlockPos testPos = playerPos.offset(x, y, z);
                            if (isValidPlacePosition(testPos)) {

                                return testPos;
                            }
                        }
                    }
                }
            }
        }

        return null;
    }


    public static boolean isValidPlacePosition(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        return mc.level.getBlockState(pos).isAir() &&
            BlockUtils.canPlace(pos) &&
            !mc.level.getBlockState(pos.below()).isAir();
    }


    public static boolean isInEnd() {
        return PlayerUtils.getDimension().equals(Dimension.End);
    }

    public static boolean isInNether() {
        return PlayerUtils.getDimension().equals(Dimension.Nether);
    }

    public static boolean isInOverworld() {
        return PlayerUtils.getDimension().equals(Dimension.Overworld);
    }



}
