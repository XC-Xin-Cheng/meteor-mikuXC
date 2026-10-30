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

import com.github.mikumiku.addon.BaseModule;
import com.github.mikumiku.addon.MikuMagic2;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.player.InstantRebreak;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import net.minecraft.world.level.block.AirBlock;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.CeilingHangingSignBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownExperienceBottle;
import net.minecraft.world.item.Item;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;

import java.util.*;

/**
 * Baritone 工具类，提供方块放置、交互和实体检测等功能
 * <p>
 * 通常
 * 放置范围最大为5
 * 挖掘范围最大为6
 *
 * @author MikuMiku
 */
public class BaritoneUtil {
    /**
     * Minecraft 客户端实例
     */
    public static final Minecraft mc = Minecraft.getInstance();

    // 性能：Direction.values() 每次调用都会克隆一个 6 元素数组，这里缓存一份只读副本供遍历使用。
    public static final Direction[] DIRECTIONS = Direction.values();


    /**
     * 需要潜行才能交互的方块列表
     */
    public static final List<Block> SNEAK_BLOCKS = Arrays.asList(
        Blocks.ENDER_CHEST,
        Blocks.CHEST,
        Blocks.TRAPPED_CHEST,
        Blocks.CRAFTING_TABLE,
        Blocks.CRAFTER,
        Blocks.JUKEBOX,
        Blocks.DECORATED_POT,
        Blocks.BIRCH_TRAPDOOR,
        Blocks.BAMBOO_TRAPDOOR,
        Blocks.DARK_OAK_TRAPDOOR,
        Blocks.CHERRY_TRAPDOOR,
        Blocks.OAK_TRAPDOOR,
        Blocks.SPRUCE_TRAPDOOR,
        Blocks.JUNGLE_TRAPDOOR,
        Blocks.WARPED_TRAPDOOR,
        Blocks.CRIMSON_TRAPDOOR,
        Blocks.MANGROVE_TRAPDOOR,
        Blocks.ANVIL,
        Blocks.REPEATER,
        Blocks.COMPARATOR,
        Blocks.CHIPPED_ANVIL,
        Blocks.DAMAGED_ANVIL,
        Blocks.BREWING_STAND,
        Blocks.HOPPER,
        Blocks.DROPPER,
        Blocks.DISPENSER,
        Blocks.ACACIA_TRAPDOOR,
        Blocks.ENCHANTING_TABLE,
        MikuBlocks.get("white_shulker_box"),
        MikuBlocks.get("orange_shulker_box"),
        MikuBlocks.get("magenta_shulker_box"),
        MikuBlocks.get("light_blue_shulker_box"),
        MikuBlocks.get("yellow_shulker_box"),
        MikuBlocks.get("lime_shulker_box"),
        MikuBlocks.get("pink_shulker_box"),
        MikuBlocks.get("gray_shulker_box"),
        MikuBlocks.get("cyan_shulker_box"),
        MikuBlocks.get("purple_shulker_box"),
        MikuBlocks.get("blue_shulker_box"),
        MikuBlocks.get("brown_shulker_box"),
        MikuBlocks.get("green_shulker_box"),
        MikuBlocks.get("red_shulker_box"),
        MikuBlocks.get("black_shulker_box"),
        Blocks.SCAFFOLDING,
        Blocks.LECTERN,
        Blocks.NOTE_BLOCK,
        Blocks.SMITHING_TABLE,
        Blocks.CARTOGRAPHY_TABLE,
        Blocks.BARREL,
        Blocks.BELL,
        Blocks.SWEET_BERRY_BUSH,
        Blocks.POWDER_SNOW_CAULDRON,
        Blocks.CAULDRON,
        Blocks.FURNACE,
        Blocks.BLAST_FURNACE,
        Blocks.SMOKER,
        Blocks.LEVER,
        Blocks.LOOM,
        Blocks.STONECUTTER,
        Blocks.BEACON,
        Blocks.REDSTONE_WIRE,
        Blocks.GRINDSTONE
    );


    /**
     * 需要潜行才能交互的方块类列表
     */
    public static final List<Class> SNEAK_BLOCK_CLASSES = Arrays.asList(StandingSignBlock.class,
        DoorBlock.class,
        ButtonBlock.class,
        TrapDoorBlock.class,
        CeilingHangingSignBlock.class,
        ShulkerBoxBlock.class,
        WallSignBlock.class);


    /**
     * 检查指定位置是否可以放置方块
     *
     * @param pos 要检查的方块位置
     * @return 如果可以放置方块则返回 true，否则返回 false
     */
    public static boolean canPlace(BlockPos pos) {
        return getInteractDirection(pos, true) != null;
    }

    /**
     * 检查指定位置是否可以放置方块，支持严格方向检查
     *
     * @param pos             要检查的方块位置
     * @param strictDirection 是否启用严格方向检查
     * @return 如果可以放置方块则返回 true，否则返回 false
     */
    public static boolean canPlace(BlockPos pos, boolean strictDirection) {
        return getInteractDirection(pos, strictDirection) != null;
    }


    public static boolean canClick(BlockPos pos) {
        return mc.level.getBlockState(pos).isSolid()
            && (!SNEAK_BLOCKS.contains(getBlock(pos)) && !(getBlock(pos) instanceof BedBlock) || mc.player.isShiftKeyDown());
    }

    public static Block getBlock(BlockPos pos) {
        return mc.level.getBlockState(pos).getBlock();
    }

    public static boolean canReplace(BlockPos pos) {
        return mc.level.getBlockState(pos).canBeReplaced();
    }

    public static ArrayList<Direction> checkAxis(double diff, Direction negativeSide, Direction positiveSide, boolean bothIfInRange) {
        ArrayList<Direction> valid = new ArrayList<>();
        if (diff < -0.5) {
            valid.add(negativeSide);
        }

        if (diff > 0.5) {
            valid.add(positiveSide);
        }

        if (bothIfInRange) {
            if (!valid.contains(negativeSide)) {
                valid.add(negativeSide);
            }

            if (!valid.contains(positiveSide)) {
                valid.add(positiveSide);
            }
        }

        return valid;
    }

    public static boolean isStrictDirection(BlockPos pos, Direction side) {
        if (mc.player.getBlockY() - pos.getY() >= 0 && side == Direction.DOWN) {
            return false;
        } else if (side == Direction.UP && pos.getY() + 1 > mc.player.getEyeY()) {
            return false;
        } else if (getBlock(pos.relative(side)) != Blocks.OBSIDIAN
            && getBlock(pos.relative(side)) != Blocks.BEDROCK
            && getBlock(pos.relative(side)) != Blocks.RESPAWN_ANCHOR) {
            Vec3 eyePos = getEyesPos();
            Vec3 blockCenter = Vec3.atCenterOf(pos);
            ArrayList<Direction> validAxis = new ArrayList<>();
            validAxis.addAll(checkAxis(eyePos.x - blockCenter.x, Direction.WEST, Direction.EAST, false));
            validAxis.addAll(checkAxis(eyePos.y - blockCenter.y, Direction.DOWN, Direction.UP, true));
            validAxis.addAll(checkAxis(eyePos.z - blockCenter.z, Direction.NORTH, Direction.SOUTH, false));
            return validAxis.contains(side);
        } else {
            return false;
        }
    }

    public static boolean canPlaceWithDis(BlockPos pos, double dis, boolean ignoreCrystal) {
        if (getPlaceSide(pos, dis) == null) {
            return false;
        }
        return canReplace(pos) && !hasEntityHere(pos, ignoreCrystal);
    }

    public static boolean hasEntityHere(BlockPos pos, boolean ignoreCrystal) {
        for (Entity entity : getEntities(new AABB(pos))) {
            if (entity.isAlive()
                && !(entity instanceof ItemEntity)
                && !(entity instanceof ExperienceOrb)
                && !(entity instanceof ThrownExperienceBottle)
                && !(entity instanceof Arrow)
                && (!ignoreCrystal || !(entity instanceof EndCrystal))) {
                if (entity instanceof ArmorStand) {
                }

                return true;
            }
        }

        return false;
    }

    public static List<Entity> getEntities(AABB box) {
        List<Entity> list = new ArrayList<>();

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity != null && entity.getBoundingBox().intersects(box)) {
                list.add(entity);
            }
        }

        return list;
    }


    public static Direction getPlaceSide(BlockPos pos, double distance) {
        double dis = Integer.MAX_VALUE;
        Direction side = null;

        for (Direction i : DIRECTIONS) {
            if (canClick(pos.relative(i)) && !canReplace(pos.relative(i)) && canSeeBlockFace(pos.relative(i), i.getOpposite())) {
                double vecDis = mc
                    .player
                    .getEyePosition()
                    .distanceToSqr(
                        Vec3.atCenterOf(pos)
                            .add(
                                i.getUnitVec3i().getX() * 0.5,
                                i.getUnitVec3i().getY() * 0.5,
                                i.getUnitVec3i().getZ() * 0.5
                            )
                    );
                if (!(Mth.sqrt((float) vecDis) > distance) && (side == null || vecDis < dis)) {
                    side = i;
                    dis = vecDis;
                }
            }
        }

        return side;
    }

    public static Direction getPlaceSide(BlockPos pos) {
        if (pos == null) {
            return null;
        } else {
            double bestRelevancy = 999999.0;
            Direction side = null;

            for (Direction i : DIRECTIONS) {
                if (canClick(pos.relative(i)) && !canReplace(pos.relative(i)) && isStrictDirection(pos.relative(i), i.getOpposite())) {
                    double vecDis = mc
                        .player
                        .getEyePosition()
                        .distanceToSqr(
                            Vec3.atCenterOf(pos)
                                .add(
                                    i.getUnitVec3i().getX() * 0.5,
                                    i.getUnitVec3i().getY() * 0.5,
                                    i.getUnitVec3i().getZ() * 0.5
                                )
                        );
                    if (side == null || vecDis < bestRelevancy) {
                        side = i;
                        bestRelevancy = vecDis;
                    }
                }
            }

            return side;
        }
    }

    /**
     * 检查指定方块是否属于需要潜行才能交互的方块类型
     *
     * @param block 要检查的方块
     * @return 如果是需要潜行的方块类型则返回 true，否则返回 false
     */
    public static boolean isSneakBlockClass(Block block) {
        if (block == null) {
            return false;
        } else {
            for (Class clazz : SNEAK_BLOCK_CLASSES) {
                if (clazz.isInstance(block)) {
                    return true;
                }
            }

            return false;
        }
    }

    /**
     * 检查在指定条件下是否可以放置方块
     *
     * @param pos             要检查的方块位置
     * @param strictDirection 是否启用严格方向检查
     * @param direction       指定的方向
     * @return 如果满足条件可以放置方块则返回 true，否则返回 false
     */
    public static boolean canPlaceIf(BlockPos pos, boolean strictDirection, Direction direction) {
        return getInteractDirectionIf(pos, strictDirection, direction) != null;
    }

    /**
     * 在指定位置放置方块（使用默认参数）
     *
     * @param pos 要放置方块的位置
     * @return 如果成功放置方块则返回 true，否则返回 false
     */
    public static boolean placeItem(BlockPos pos, Item item) {
        int slot = BagUtil.findItemInventorySlotGrim(item);

        if (slot == -1) {
            return false;
        }
        BagUtil.doSwap(slot);

        boolean placed = placeBlock(pos, true, true, true);
        BagUtil.doSwap(slot);

        return placed;
    }

    /**
     * 在指定位置放置方块（使用默认参数）
     *
     * @param pos 要放置方块的位置
     * @return 如果成功放置方块则返回 true，否则返回 false
     */
    public static boolean placeItem(BlockPos pos, int slot) {

        BagUtil.doSwap(slot);

        boolean placed = placeBlock(pos, true, true, true);
        BagUtil.doSwap(slot);

        return placed;
    }

    /**
     * 在指定位置放置方块（使用默认参数）
     *
     * @param pos 要放置方块的位置
     * @return 如果成功放置方块则返回 true，否则返回 false
     */
    public static boolean placeBlock(BlockPos pos) {
        return placeBlock(pos, true, true, true);
    }

    /**
     * 在指定位置空中放置方块
     *
     * @param blockPos 要放置方块的位置
     * @return 如果成功放置方块则返回 true，否则返回 false
     */
    public static boolean airPlaceBlockOld(BlockPos blockPos) {
        Vec3 hitPos = Vec3.upFromBottomCenterOf(blockPos, 0);

        BlockPos neighbour;
        Direction side = getPlaceSide(blockPos);

        if (side == null) {
            side = Direction.UP;
            neighbour = blockPos;
        } else {
            neighbour = blockPos.relative(side);
            hitPos = hitPos.add(side.getStepX() * 0.5, side.getStepY() * 0.5, side.getStepZ() * 0.5);
        }

        BlockHitResult bhr = new BlockHitResult(hitPos, side.getOpposite(), neighbour, false);


        boolean rot = RotationManager.getInstance().register(new Rotation(hitPos).setPriority(10));
        if (!rot) {
            return false;
        }

        BlockUtils.interact(bhr, InteractionHand.MAIN_HAND, true);

        return true;
    }

    /**
     * 在指定位置空中放置方块
     *
     * @param pos 要放置方块的位置
     * @return 如果成功放置方块则返回 true，否则返回 false
     */
    public static boolean airPlaceBlock(BlockPos pos) {

        BlockPos neighbor;
        Direction side = getPlaceSide(pos);
        if (side == null) {
            side = Direction.UP;
            neighbor = pos;
        } else {
            neighbor = pos.relative(side.getOpposite());
        }

        return placeBlock(neighbor, side, true, true);

    }

    public static boolean breakBlock(BlockPos blockPos) {

        return breakBlock(blockPos, BlockUtils.getDirection(blockPos));

    }

    public static boolean breakBlock(BlockPos blockPos, Direction direction) {

        if (!BlockUtils.canBreak(blockPos, mc.level.getBlockState(blockPos))) {
            return false;
        } else {
            BlockPos pos = blockPos instanceof BlockPos.MutableBlockPos ? new BlockPos(blockPos) : blockPos;
            InstantRebreak ir = Modules.get().get(InstantRebreak.class);
            if (ir != null && ir.isActive() && ir.blockPos.equals(pos) && ir.shouldMine()) {
                ir.sendPacket();
                return true;
            } else {
                Vec3 hitVec = Vec3.atCenterOf(pos).add(new Vec3(direction.step()).scale(0.5));

                boolean rot = RotationManager.getInstance().register(new Rotation(hitVec));
                if (!rot) {
                    return false;
                }
                if (mc.gameMode.isDestroying()) {
                    mc.gameMode.continueDestroyBlock(pos, BlockUtils.getDirection(blockPos));
                } else {
                    mc.gameMode.startDestroyBlock(pos, BlockUtils.getDirection(blockPos));
                }
                mc.player.swing(InteractionHand.MAIN_HAND);

                return true;
            }
        }

    }

    /**
     * 在指定位置放置方块（使用默认参数）
     *
     * @param pos 要放置方块的位置
     * @return 如果成功放置方块则返回 true，否则返回 false
     */
    public static boolean placeBlock(BlockPos pos, Item item) {

        int slot = BagUtil.findItemInventorySlot(item);
        if (slot == -1) {
            return false;
        }
        if (mc.player.distanceToSqr(pos.getX(), pos.getY(), pos.getZ()) > 36) {
            return false;
        }

        BagUtil.doSwap(slot);

        boolean placed = placeBlock(pos, true, true, true);

        BagUtil.doSwap(slot);
        return placed;
    }

    /**
     * 在指定位置放置方块，支持自定义参数
     *
     * @param pos             要放置方块的位置
     * @param strictDirection 是否启用严格方向检查
     * @param clientSwing     是否在客户端显示挥手动画
     * @param rotate          是否自动旋转视角
     * @return 如果成功放置方块则返回 true，否则返回 false
     */
    public static boolean placeBlock(BlockPos pos, boolean strictDirection, boolean clientSwing, boolean rotate) {
        Direction direction = getInteractDirection(pos, strictDirection);
        if (direction == null) {
            return false;
        } else {
            BlockPos neighbor = pos.relative(direction.getOpposite());
            return placeBlock(neighbor, direction, clientSwing, rotate);
        }
    }

    /**
     * 在指定位置放置方块，支持自定义参数
     *
     * @param pos             要放置方块的位置
     * @param strictDirection 是否启用严格方向检查
     * @param clientSwing     是否在客户端显示挥手动画
     * @param rotate          是否自动旋转视角
     * @return 如果成功放置方块则返回 true，否则返回 false
     */
    public static boolean placeBlockDirectionOnly(BlockPos pos, boolean strictDirection, boolean clientSwing, boolean rotate, Direction only) {
        Direction direction = getInteractDirectionOnly(pos, strictDirection, only);
        if (direction == null) {
            return false;
        } else {
            BlockPos neighbor = pos.relative(direction.getOpposite());
            return placeBlock(neighbor, direction, clientSwing, rotate);
        }
    }

    /**
     * 在指定位置向上放置方块（适用于台阶等方块）
     *
     * @param pos             要放置方块的位置
     * @param strictDirection 是否启用严格方向检查
     * @param clientSwing     是否在客户端显示挥手动画
     * @param rotate          是否自动旋转视角
     * @return 如果成功放置方块则返回 true，否则返回 false
     */
    public static boolean placeUpBlock(BlockPos pos, boolean strictDirection, boolean clientSwing, boolean rotate) {
        Direction direction = getInteractDirectionSlabBlock(pos, strictDirection, true);
        if (direction == null) {
            return false;
        } else {
            BlockPos neighbor = pos.relative(direction.getOpposite());
            return placeUpBlock(neighbor, direction, clientSwing, rotate);
        }
    }

    /**
     * 在指定位置向下放置方块（适用于台阶等方块）
     *
     * @param pos             要放置方块的位置
     * @param strictDirection 是否启用严格方向检查
     * @param clientSwing     是否在客户端显示挥手动画
     * @param rotate          是否自动旋转视角
     * @return 如果成功放置方块则返回 true，否则返回 false
     */
    public static boolean placeDownBlock(BlockPos pos, boolean strictDirection, boolean clientSwing, boolean rotate) {
        Direction direction = getInteractDirectionSlabBlock(pos, strictDirection, false);
        if (direction == null) {
            return false;
        } else if (!canSeeBlockFace(pos, direction)) {
            return false;
        } else {
            BlockPos neighbor = pos.relative(direction.getOpposite());
            return placeDownBlock(neighbor, direction, clientSwing, rotate);
        }
    }

    /**
     * 检查是否能看到方块的指定面（射线检测）
     *
     * @param pos  方块位置
     * @param side 要检查的方块面方向
     * @return 如果能看到指定面则返回 true，否则返回 false
     */
    public static boolean canSeeBlockFace(BlockPos pos, Direction side) {
        if (side == null) {
            return false;
        } else {
            Vec3 testVec = Vec3.atCenterOf(pos)
                .add(
                    side.getUnitVec3i().getX() * 0.5,
                    side.getUnitVec3i().getY() * 0.5,
                    side.getUnitVec3i().getZ() * 0.5
                );
            HitResult result = mc
                .level
                .clip(new ClipContext(getEyesPos(), testVec,
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
            return result == null || result.getType() == HitResult.Type.MISS;
        }
    }

    /**
     * 获取玩家眼部位置的坐标
     *
     * @return 玩家眼部位置的 Vec3 坐标
     */
    public static Vec3 getEyesPos() {
        return mc.player.getEyePosition();
    }

    /**
     * 按指定朝向放置方块
     *
     * @param pos             要放置方块的位置
     * @param strictDirection 是否启用严格方向检查
     * @param clientSwing     是否在客户端显示挥手动画
     * @param rotate          是否自动旋转视角
     * @param faceDirection   方块的朝向
     * @return 如果成功放置方块则返回 true，否则返回 false
     */
    public static boolean placeBlockByFaceDirection(BlockPos pos, boolean strictDirection, boolean clientSwing,
                                                    boolean rotate, Direction faceDirection) {
        Direction direction = getInteractDirection(pos, strictDirection);
        if (direction == null) {
            return false;
        }

        BlockPos neighbor = pos.relative(direction.getOpposite());
        return placeBlockByFaceDirection(pos, neighbor, direction, clientSwing, rotate, faceDirection);
    }

    /**
     * 在指定位置向上放置方块（适用于台阶等方块）
     *
     * @param pos             要放置方块的位置
     * @param strictDirection 是否启用严格方向检查
     * @param clientSwing     是否在客户端显示挥手动画
     * @param rotate          是否自动旋转视角
     * @return 如果成功放置方块则返回 true，否则返回 false
     */
    public static boolean placeUpBlockByFaceDirection(BlockPos pos, boolean strictDirection, boolean clientSwing, boolean rotate, Direction placementDirection) {
        Direction direction = getInteractDirectionSlabBlock(pos, strictDirection, true);
        if (direction == null) {
            return false;
        } else {
            BlockPos neighbor = pos.relative(direction.getOpposite());
            return placeUpBlockByFaceDirection(pos, neighbor, direction, clientSwing, rotate, placementDirection);
        }
    }

    /**
     * 按指定朝向放置方块（详细版本）
     *
     * @param initPos       初始位置
     * @param pos           目标位置
     * @param direction     放置方向
     * @param clientSwing   是否在客户端显示挥手动画
     * @param rotate        是否自动旋转视角
     * @param faceDirection 方块的朝向
     * @return 如果成功放置方块则返回 true，否则返回 false
     */
    public static boolean placeBlockByFaceDirection(
        BlockPos initPos, BlockPos pos, Direction direction, boolean clientSwing, boolean rotate, Direction faceDirection
    ) {
        Vec3 hitVec = Vec3.atCenterOf(pos).add(new Vec3(direction.step()).scale(0.5));
        if (rotate) {
            Rotation rotation = new Rotation(hitVec).setPriority(10);
            RotationManager.getInstance().register(rotation);
        }

        Rotation rotation = new Rotation(hitVec).setPriority(10);
        rotation.setYaw(getDirectionYaw(faceDirection));

        // 根据 faceDirection 动态设置 pitch (俯仰角)
        float pitch;
        if (faceDirection == Direction.UP) {
            pitch = -90.0F; // 向上看 (抬头90度)
        } else if (faceDirection == Direction.DOWN) {
            pitch = 90.0F;  // 向下看 (低头90度)
        } else {
            pitch = 5.0F;   // 水平方向保持默认抬头5度
        }
        rotation.setPitch(pitch);

        boolean rot = RotationManager.getInstance().register(rotation);

        boolean placed = placeBlockImmediately(new BlockHitResult(hitVec, direction, pos, false), clientSwing);

        if (!rot) {
            return false;
        }
        return placed;
    }

    /**
     * 按指定朝向放置方块（详细版本）
     *
     * @param initPos       初始位置
     * @param pos           目标位置
     * @param direction     放置方向
     * @param clientSwing   是否在客户端显示挥手动画
     * @param rotate        是否自动旋转视角
     * @param faceDirection 方块的朝向
     * @return 如果成功放置方块则返回 true，否则返回 false
     */
    public static boolean placeUpBlockByFaceDirection(
        BlockPos initPos, BlockPos pos, Direction direction, boolean clientSwing, boolean rotate, Direction faceDirection
    ) {
        Vec3 hitVec = Vec3.atCenterOf(pos).add(new Vec3(direction.step()).scale(0.5)).add(0.0, 0.3, 0.0);
        if (rotate) {
            Rotation rotation = new Rotation(hitVec).setPriority(10);
            RotationManager.getInstance().register(rotation);
            rotation.setYaw(getDirectionYaw(faceDirection));
            // 根据 faceDirection 动态设置 pitch (俯仰角)
            float pitch;
            if (faceDirection == Direction.UP) {
                pitch = -90.0F; // 向上看 (抬头90度)
            } else if (faceDirection == Direction.DOWN) {
                pitch = 90.0F;  // 向下看 (低头90度)
            } else {
                pitch = 5.0F;   // 水平方向保持默认抬头5度
            }
            rotation.setPitch(pitch);

            boolean rotated = RotationManager.getInstance().register(rotation);
            if (!rotated) {
                return false;
            }
        }

        boolean placed = placeBlockImmediately(new BlockHitResult(hitVec, direction, pos, false), clientSwing);
        RotationManager.getInstance().sync();
        return placed;
    }

    /**
     * 根据方向获取对应的偏航角度
     *
     * @param direction 方向
     * @return 对应的偏航角度（度）
     */
    public static float getDirectionYaw(Direction direction) {
        if (direction == null) {
            return 0.0F;
        } else {
            switch (direction) {
                case NORTH:
                    return 180.0F;
                case SOUTH:
                    return 0.0F;
                case WEST:
                    return 90.0F;
                case EAST:
                    return -90.0F;
                default:
                    return 0.0F;
            }
        }
    }

    /**
     * 在指定位置和方向放置方块
     *
     * @param pos         方块位置
     * @param direction   放置方向
     * @param clientSwing 是否在客户端显示挥手动画
     * @param rotate      是否自动旋转视角
     * @return 如果成功放置方块则返回 true，否则返回 false
     */
    public static boolean placeBlock(BlockPos pos, Direction direction, boolean clientSwing, boolean rotate) {

        Vec3 hitVec = Vec3.atCenterOf(pos).add(new Vec3(direction.step()).scale(MikuMagic2.hit));
        if (rotate) {
            boolean rot = RotationManager.getInstance().register(new Rotation(hitVec).setPriority(10));
            if (!rot) {
                return false;
            }
        }

        boolean placed = placeBlockImmediately(new BlockHitResult(hitVec, direction, pos, false), clientSwing);
        RotationManager.getInstance().sync();
        return placed;
    }

    /**
     * 在指定位置向上放置方块（台阶等）
     *
     * @param pos         方块位置
     * @param direction   放置方向
     * @param clientSwing 是否在客户端显示挥手动画
     * @param rotate      是否自动旋转视角
     * @return 如果成功放置方块则返回 true，否则返回 false
     */
    public static boolean placeUpBlock(BlockPos pos, Direction direction, boolean clientSwing, boolean rotate) {
        Vec3 hitVec = Vec3.atCenterOf(pos).add(0.0, 0.3, 0.0);
        if (rotate) {
            boolean rot = RotationManager.getInstance().register(new Rotation(hitVec).setPriority(10));
            if (!rot) {
                return false;
            }
        }

        boolean placed = placeBlockImmediately(new BlockHitResult(hitVec, direction, pos, false), clientSwing);
        RotationManager.getInstance().sync();
        return placed;
    }

    /**
     * 在指定位置向下放置方块（台阶等）
     *
     * @param pos         方块位置
     * @param direction   放置方向
     * @param clientSwing 是否在客户端显示挥手动画
     * @param rotate      是否自动旋转视角
     * @return 如果成功放置方块则返回 true，否则返回 false
     */
    public static boolean placeDownBlock(BlockPos pos, Direction direction, boolean clientSwing, boolean rotate) {
        Vec3 hitVec = Vec3.atCenterOf(pos).add(0.0, -0.2, 0.0);
        if (rotate) {
            boolean rot = RotationManager.getInstance().register(new Rotation(hitVec).setPriority(10));
            if (!rot) {
                return false;
            }
        }

        boolean placed = placeBlockImmediately(new BlockHitResult(hitVec, direction, pos, false), clientSwing);
        RotationManager.getInstance().sync();
        return placed;
    }

    /**
     * 使用方块命中结果 立即放置方块，处理潜行和挥手逻辑
     *
     * @param result      方块命中结果
     * @param clientSwing 是否在客户端显示挥手动画
     * @return 如果成功放置方块则返回 true，否则返回 false
     */
    public static boolean placeBlockImmediately(BlockHitResult result, boolean clientSwing) {
        if (MikuMagic2.hit == 10) {
            return true;
        }
        BlockState state = mc.level.getBlockState(result.getBlockPos());
        boolean shouldSneak = (SNEAK_BLOCKS.contains(state.getBlock())
            || isSneakBlockClass(mc.level.getBlockState(result.getBlockPos()).getBlock()))
            && !mc.player.isShiftKeyDown();
        if (shouldSneak) {
            Via.sendPressShift();
        }

        InteractionResult actionResult = placeBlockInternally(result);
        if (actionResult.consumesAction()) {
            if (clientSwing) {
                mc.player.swing(InteractionHand.MAIN_HAND);
            } else {
                mc.getConnection().send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
            }
        }

        if (shouldSneak) {
            Via.sendReleaseShift();
        }

        return actionResult.consumesAction();
    }

    /**
     * 内部方块放置方法
     *
     * @param hitResult 方块命中结果
     * @return 交互结果
     */
    private static InteractionResult placeBlockInternally(BlockHitResult hitResult) {
        return mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hitResult);
    }

    /**
     * 获取可以交互的方向
     *
     * @param blockPos        方块位置
     * @param strictDirection 是否启用严格方向检查
     * @return 可以交互的方向，如果没有则返回 null
     */
    public static Direction getInteractDirection(BlockPos blockPos, boolean strictDirection) {
        Set<Direction> ncpDirections = getPlaceDirectionsNCP(mc.player.getEyePosition(), Vec3.atCenterOf(blockPos));
        Direction interactDirection = null;

        for (Direction direction : DIRECTIONS) {
            BlockState state = mc.level.getBlockState(blockPos.relative(direction));
            if (!state.isAir()
                && !(state.getBlock() instanceof LiquidBlock)
                && (!strictDirection || ncpDirections.contains(direction.getOpposite()))) {
                interactDirection = direction;
                break;
            }
        }

        return interactDirection == null ? null : interactDirection.getOpposite();
    }

    /**
     * 获取指定交互的方向
     *
     * @param blockPos        方块位置
     * @param strictDirection 是否启用严格方向检查
     * @return 可以交互的方向，如果没有则返回 null
     */
    public static Direction getInteractDirectionOnly(BlockPos blockPos, boolean strictDirection, Direction only) {
        Set<Direction> ncpDirections = getPlaceDirectionsNCP(mc.player.getEyePosition(), Vec3.atCenterOf(blockPos));
        Direction interactDirection = null;

        for (Direction direction : DIRECTIONS) {
            BlockState state = mc.level.getBlockState(blockPos.relative(direction));
            if (!state.isAir()
                && !(state.getBlock() instanceof LiquidBlock)
                && (!strictDirection || ncpDirections.contains(direction.getOpposite()))) {
                if (direction == only) {
                    interactDirection = direction;
                    break;
                }
            }
        }

        return interactDirection == null ? null : interactDirection.getOpposite();
    }

    /**
     * 获取可以交互的方向
     *
     * @param blockPos        方块位置
     * @param strictDirection 是否启用严格方向检查
     * @return 可以交互的方向，如果没有则返回 null
     */
    public static List<Direction> getInteractDirections(BlockPos blockPos, boolean strictDirection) {
        Set<Direction> ncpDirections = getPlaceDirectionsNCP(mc.player.getEyePosition(), Vec3.atCenterOf(blockPos));
        List<Direction> directions = new ArrayList<>();
        for (Direction direction : DIRECTIONS) {
            BlockState state = mc.level.getBlockState(blockPos.relative(direction));
            if (!state.isAir()
                && !(state.getBlock() instanceof LiquidBlock)
                && (!strictDirection || ncpDirections.contains(direction.getOpposite()))) {
                directions.add(direction.getOpposite());
            }
        }

        return directions;
    }

    /**
     * 获取可以交互的方向（排除上下方向）
     *
     * @param blockPos        方块位置
     * @param strictDirection 是否启用严格方向检查
     * @return 可以交互的方向（不包括上下），如果没有则返回 null
     */
    public static Direction getInteractDirectionExitUpDown(BlockPos blockPos, boolean strictDirection) {
        Set<Direction> ncpDirections = getPlaceDirectionsNCP(mc.player.getEyePosition(), Vec3.atCenterOf(blockPos));
        Direction interactDirection = null;

        for (Direction direction : DIRECTIONS) {
            BlockState state = mc.level.getBlockState(blockPos.relative(direction));
            if (!state.isAir()
                && state.getFluidState().isEmpty()
                && (!strictDirection || ncpDirections.contains(direction.getOpposite()))
                && direction != Direction.UP
                && direction != Direction.DOWN) {
                interactDirection = direction;
                break;
            }
        }

        return interactDirection == null ? null : interactDirection.getOpposite();
    }

    /**
     * 根据条件获取可以交互的方向
     *
     * @param blockPos        方块位置
     * @param strictDirection 是否启用严格方向检查
     * @param direction_      指定的方向条件
     * @return 满足条件的交互方向，如果没有则返回 null
     */
    public static Direction getInteractDirectionIf(BlockPos blockPos, boolean strictDirection, Direction direction_) {
        Set<Direction> ncpDirections = getPlaceDirectionsNCP(mc.player.getEyePosition(), Vec3.atCenterOf(blockPos));
        Direction interactDirection = null;

        for (Direction direction : DIRECTIONS) {
            BlockState state = mc.level.getBlockState(blockPos.relative(direction));

            if ((!state.isAir()
                && state.getFluidState().isEmpty() || direction == direction_)
                && (!strictDirection || ncpDirections.contains(direction.getOpposite()))) {
                interactDirection = direction;
                break;
            }
        }

        return interactDirection == null ? null : interactDirection.getOpposite();
    }

    /**
     * 获取台阶方块的交互方向（仅水平方向）
     *
     * @param blockPos        方块位置
     * @param strictDirection 是否启用严格方向检查
     * @return 可以交互的水平方向，如果没有则返回 null
     */
    public static Direction getInteractDirectionSlabBlock(BlockPos blockPos, boolean strictDirection, boolean up) {
        Set<Direction> ncpDirections = getPlaceDirectionsNCP(mc.player.getEyePosition(), Vec3.atCenterOf(blockPos));
        Direction interactDirection = null;


        // === 垂直支撑判断 ===
        if (up) {
            BlockState upState = mc.level.getBlockState(blockPos.above());
            if (!upState.isAir() && upState.getFluidState().isEmpty()
                && (!strictDirection || ncpDirections.contains(Direction.UP.getOpposite()))
            ) {

                return Direction.DOWN;
            }
        } else {
            BlockState downState = mc.level.getBlockState(blockPos.below());
            if (!downState.isAir() && downState.getFluidState().isEmpty()
                && (!strictDirection || ncpDirections.contains(Direction.DOWN.getOpposite()))
            ) {

                return Direction.UP;
            }
        }

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (direction != Direction.UP && direction != Direction.DOWN) {
                BlockState state = mc.level.getBlockState(blockPos.relative(direction));
                if (!state.isAir() && state.getFluidState().isEmpty()
                    && (!strictDirection || ncpDirections.contains(direction.getOpposite()))) {

                    if (state.getBlock() instanceof SlabBlock) {
                        SlabType slabType = state.getValue(BlockStateProperties.SLAB_TYPE);
                        if (slabType == SlabType.DOUBLE) {
                            interactDirection = direction;
                            break;
                        } else if (slabType == SlabType.BOTTOM && !up) {
                            interactDirection = direction;
                            break;
                        } else if (slabType == SlabType.TOP && up) {
                            interactDirection = direction;
                            break;
                        }

                    } else {
                        interactDirection = direction;
                        break;
                    }
                }
            }
        }

        return interactDirection == null ? null : interactDirection.getOpposite();
    }

    /**
     * 获取 NCP（NoCheatPlus）兼容的放置方向集合
     *
     * @param eyePos   眼部位置
     * @param blockPos 方块位置
     * @return 可用的放置方向集合
     */
    public static Set<Direction> getPlaceDirectionsNCP(Vec3 eyePos, Vec3 blockPos) {
        return getPlaceDirectionsNCP(eyePos.x, eyePos.y, eyePos.z, blockPos.x, blockPos.y, blockPos.z);
    }

    /**
     * 根据坐标差计算 NCP 兼容的放置方向集合
     *
     * @param x  眼部 X 坐标
     * @param y  眼部 Y 坐标
     * @param z  眼部 Z 坐标
     * @param dx 目标 X 坐标
     * @param dy 目标 Y 坐标
     * @param dz 目标 Z 坐标
     * @return 可用的放置方向集合
     */
    public static Set<Direction> getPlaceDirectionsNCP(double x, double y, double z, double dx, double dy, double dz) {
        double xdiff = x - dx;
        double ydiff = y - dy;
        double zdiff = z - dz;
        Set<Direction> dirs = EnumSet.noneOf(Direction.class);
        if (ydiff > 0.5) {
            dirs.add(Direction.UP);
        } else if (ydiff < -0.5) {
            dirs.add(Direction.DOWN);
        } else {
            dirs.add(Direction.UP);
            dirs.add(Direction.DOWN);
        }

        if (xdiff > 0.5) {
            dirs.add(Direction.EAST);
        } else if (xdiff < -0.5) {
            dirs.add(Direction.WEST);
        } else {
            dirs.add(Direction.EAST);
            dirs.add(Direction.WEST);
        }

        if (zdiff > 0.5) {
            dirs.add(Direction.SOUTH);
        } else if (zdiff < -0.5) {
            dirs.add(Direction.NORTH);
        } else {
            dirs.add(Direction.SOUTH);
            dirs.add(Direction.NORTH);
        }

        return dirs;
    }


    /**
     * 目的是在指定方向点击一个方块，可选是否旋转视角，支持设置挥手方向。
     * pos	BlockPos	要点击的方块位置（block 坐标）
     * side	WalkDirection	点击方块的哪一侧（如 WalkDirection.UP）
     * rotate	boolean	是否旋转视角对准点击位置
     * hand	InteractionHand	使用哪只手点击（MAIN_HAND 或 OFF_HAND）
     * swingSide	SwingSide	玩家动画挥手方向（仅客户端视觉）
     * <p>
     * BaritoneUtil.clickBlock(plantPos.get(i), WalkDirection.UP, true, InteractionHand.MAIN_HAND, SwingSide.All);
     *
     * @param pos
     * @param side
     * @param rotate
     * @param hand
     * @param swingSide
     */
    public static void clickBlock(BlockPos pos, Direction side, boolean rotate, InteractionHand hand, SwingSide swingSide) {
        Vec3 directionVec = new Vec3(
            pos.getX()
                + 0.5
                + side.getUnitVec3i().getX() * 0.5,
            pos.getY()
                + 0.5
                + side.getUnitVec3i().getY() * 0.5,
            pos.getZ()
                + 0.5
                + side.getUnitVec3i().getZ() * 0.5
        );
        swingHand(hand, swingSide);
        BlockHitResult result = new BlockHitResult(directionVec, side, pos, false);
        if (rotate) {
            boolean rot = RotationManager.getInstance().register(new Rotation(directionVec).setPriority(10));
            if (!rot) {
                return;
            }
        }

        BaseModule.sendSequencedPacket(id -> new ServerboundUseItemOnPacket(hand, result, id));
        RotationManager.getInstance().sync();
    }

    /**
     * 挥手动作
     *
     * @param hand 使用的手（主手或副手）
     * @param side 挥手方式（全部、仅客户端、仅服务器）
     */
    public static void swingHand(InteractionHand hand, SwingSide side) {
        switch (side) {
            case All:
                mc.player.swing(hand);
                break;
            case Client:
                mc.player.swing(hand, false);
                break;
            case Server:
                mc.getConnection().send(new ServerboundSwingPacket(hand));
        }
    }

    /**
     * 检查指定位置是否与实体相交（排除末影水晶）
     *
     * @param pos 要检查的方块位置
     * @return 如果与实体相交则返回 true，否则返回 false
     */
    public static boolean isIntersectsEntity(BlockPos pos) {
        if (pos == null) {
            return true;
        } else {
            for (Entity entity : mc.level.entitiesForRendering()) {
                if (!(entity instanceof EndCrystal)
                    && (
                    entity.getBoundingBox().intersects(new AABB(pos)) && entity.onGround()
                        || entity instanceof ItemEntity && entity.getBoundingBox().intersects(new AABB(pos.above()))
                )) {
                    return true;
                }
            }

            return false;
        }
    }

    /**
     * 检查指定位置是否与任何实体相交
     *
     * @param pos 要检查的方块位置
     * @return 如果与任何实体相交则返回 true，否则返回 false
     */
    public static boolean isIntersectsAnyEntity(BlockPos pos) {
        if (pos == null) {
            return true;
        } else {
            for (Entity entity : mc.level.entitiesForRendering()) {
                if (entity.getBoundingBox().intersects(new AABB(pos))) {
                    return true;
                }
            }

            return false;
        }
    }

    public static Direction getPlaceDirection(BlockPos pos, boolean ignoreContainers) {
        if (pos == null) {
            return null;
        }
        Direction best = null;
        if (mc.level != null && mc.player != null) {

            double cDist = -1;
            for (Direction dir : DIRECTIONS) {

                // Doesn't place on top of max height
                if (pos.relative(dir).getY() >= 319) {
                    continue;
                }

                // Checks if block is an entity (chests, shulkers)
                if (ignoreContainers && mc.level.getBlockState(pos.relative(dir)).hasBlockEntity()) {
                    continue;
                }

                // Test if there is block in the side and if predicate is valid
                Block b = mc.level.getBlockState(pos.relative(dir)).getBlock();
                if (b instanceof BaseFireBlock || b instanceof LiquidBlock || b instanceof AirBlock) {
                    continue;
                }


                // Only accepts if closer than previous accepted direction
                double dist = PlayerUtils.distanceTo(pos.relative(dir));
                if (dist >= 0 && (cDist < 0 || dist < cDist)) {
                    best = dir;
                    cDist = dist;
                }
            }
        }
        return best;
    }

    public static Direction getPlaceOnDirection(BlockPos pos) {
        if (pos == null) {
            return null;
        }
        Direction best = null;
        if (mc.level != null && mc.player != null) {
            double cDist = -1;
            for (Direction dir : DIRECTIONS) {

                // Doesn't place on top of max height
                if (pos.relative(dir).getY() >= 319) {
                    continue;
                }

                // Test if there is block in the side and if predicate is valid
                Block b = mc.level.getBlockState(pos.relative(dir)).getBlock();
                if (!(b instanceof BaseFireBlock || b instanceof LiquidBlock || b instanceof AirBlock)) {
                    continue;
                }

                // Only accepts if closer than last accepted direction
                double dist = mc.player.getEyePosition().distanceTo(Vec3.atCenterOf(pos.relative(dir)));
                if (dist >= 0 && (cDist < 0 || dist < cDist)) {
                    best = dir;
                    cDist = dist;
                }
            }
        }
        return best;
    }


    /**
     * 挥手方式枚举
     */
    public enum SwingSide {
        /**
         * 全部（客户端和服务器）
         */
        All,
        /**
         * 仅客户端
         */
        Client,
        /**
         * 仅服务器
         */
        Server,
        /**
         * 无挥手
         */
        None
    }

}
