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
import com.github.mikumiku.addon.util.BaritoneUtil;
import com.github.mikumiku.addon.util.PositionCache;
import com.github.mikumiku.addon.util.Via;
import com.google.common.collect.Streams;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.render.RenderUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class Scaffold extends BaseModule {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("渲染");

    private final Setting<List<Block>> blocks = sgGeneral.add(new BlockListSetting.Builder()
        .name("方块列表")
        .description("选择的方块.")
        .build()
    );

    private final Setting<ListMode> blocksFilter = sgGeneral.add(new EnumSetting.Builder<ListMode>()
        .name("方块过滤")
        .description("如何使用方块列表设置")
        .defaultValue(ListMode.Blacklist)
        .build()
    );

    private final Setting<Boolean> fastTower = sgGeneral.add(new BoolSetting.Builder()
        .name("快速搭塔")
        .description("是否启用更快的向上搭塔.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> towerSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("搭塔速度")
        .description("搭塔时的速度.")
        .defaultValue(0.5)
        .min(0)
        .sliderMax(1)
        .visible(fastTower::get)
        .build()
    );

    private final Setting<Boolean> whileMoving = sgGeneral.add(new BoolSetting.Builder()
        .name("移动时搭塔")
        .description("允许在移动时搭塔.")
        .defaultValue(true)
        .visible(fastTower::get)
        .build()
    );

    private final Setting<Boolean> airPlace = sgGeneral.add(new BoolSetting.Builder()
        .name("空气放置")
        .description("允许在空气中放置方块. 这也允许你修改脚手架半径.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> aheadDistance = sgGeneral.add(new DoubleSetting.Builder()
        .name("提前距离")
        .description("提前放置方块的距离.")
        .defaultValue(0)
        .min(0)
        .sliderMax(1)
        .visible(() -> !airPlace.get())
        .build()
    );

    private final Setting<Double> placeRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("放置范围")
        .description("在空中时可以放置方块的距离.")
        .defaultValue(5)
        .min(0)
        .sliderMax(8)
        .visible(() -> !airPlace.get())
        .build()
    );

    private final Setting<Double> radius = sgGeneral.add(new DoubleSetting.Builder()
        .name("半径")
        .description("空放半径.")
        .defaultValue(0)
        .min(0)
        .max(6)
        .visible(airPlace::get)
        .build()
    );

    private final Setting<Integer> blocksPerTick = sgGeneral.add(new IntSetting.Builder()
        .name("每刻方块数")
        .description("每个tick放置的方块数量.")
        .defaultValue(2)
        .min(1)
        .visible(airPlace::get)
        .build()
    );

    // 渲染

    private final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder()
        .name("渲染")
        .description("是否渲染已放置的方块.")
        .defaultValue(true)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("形状模式")
        .description("形状的渲染方式.")
        .defaultValue(ShapeMode.Both)
        .visible(render::get)
        .build()
    );

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("侧面颜色")
        .description("目标方块渲染的侧面颜色.")
        .defaultValue(new SettingColor(197, 137, 232, 10))
        .visible(render::get)
        .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("线条颜色")
        .description("目标方块渲染的线条颜色.")
        .defaultValue(new SettingColor(197, 137, 232))
        .visible(render::get)
        .build()
    );

    private final BlockPos.MutableBlockPos bp = new BlockPos.MutableBlockPos();

    /**
     * 位置缓存管理器，防止短时间重复放置
     */
    private final PositionCache positionCache = new PositionCache(1000L); // 1秒过期

    public Scaffold() {
        super(CATEGORY_MIKU_BUILD, "自动搭路", "scaffold. 自动在你脚下放置方块搭路.");
    }

    @Override
    public void onActivate() {
        super.onActivate();
        positionCache.startCleanupThread();
    }

    @Override
    public void onDeactivate() {
        positionCache.shutdown(); // 关闭缓存管理器
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        try {


            Vec3 vec = Via.getEntityPos(mc.player).add(mc.player.getDeltaMovement()).add(0, -0.75, 0);
            if (airPlace.get()) {
                // 空气放置模式：优先放置玩家脚下
                bp.set(mc.player.blockPosition().getX(), mc.player.blockPosition().getY() - 1, mc.player.blockPosition().getZ());
            } else {
                Vec3 pos = Via.getEntityPos(mc.player);
                if (aheadDistance.get() != 0 && !towering() && !mc.level.getBlockState(mc.player.blockPosition().below()).getCollisionShape(mc.level, mc.player.blockPosition()).isEmpty()) {
                    Vec3 dir = Vec3.directionFromRotation(0, mc.player.getYRot()).multiply(aheadDistance.get(), 0, aheadDistance.get());
                    if (mc.options.keyUp.isDown()) pos = pos.add(dir.x, 0, dir.z);
                    if (mc.options.keyDown.isDown()) pos = pos.add(-dir.x, 0, -dir.z);
                    if (mc.options.keyLeft.isDown()) pos = pos.add(dir.z, 0, -dir.x);
                    if (mc.options.keyRight.isDown()) pos = pos.add(-dir.z, 0, dir.x);
                }
                bp.set(pos.x, vec.y, pos.z);

                // 直接设置目标位置为玩家脚下
//            bp.set(pos.x, mc.player.getBlockPos().getY() - 1, pos.z);
            }

            // 潜行时向下移动，但限制最小高度  && mc.player.getY() > mc.world.getBottomY()
            if (mc.options.keyShift.isDown() && !mc.options.keyJump.isDown() && mc.player.getY() + vec.y > -1) {
                bp.setY(bp.getY() - 1);
            }

            // 确保不会放置在玩家当前位置或以上
            if (bp.getY() >= mc.player.blockPosition().getY()) {
                bp.setY(mc.player.blockPosition().getY() - 1);
            }

            BlockPos targetBlock = bp.immutable();

            if (!airPlace.get() && (!BaritoneUtil.canPlace(bp))) {

                // 优先尝试玩家正下方的位置
                BlockPos playerUnderPos = mc.player.blockPosition();
                if (BaritoneUtil.canPlace(playerUnderPos)) {
                    bp.set(playerUnderPos);
                } else if (BaritoneUtil.canPlace(playerUnderPos.below())) {
                    bp.set(playerUnderPos);
                } else {
                    // 如果正下方无法放置，则搜索附近的最佳位置
                    // 性能：原来把所有候选塞进 List 再排序，这里只记录最近的一个：
                    // 距离相等时保留先遍历到的位置，与稳定排序后取首元素的结果一致。
                    Vec3 eyePos = mc.player.getEyePosition();
                    double searchRange = placeRange.get();
                    double playerX = mc.player.getX();
                    double playerY = mc.player.getY();
                    double playerZ = mc.player.getZ();
                    double yLowBound = Math.max(mc.level.getMinY(), playerY - searchRange);
                    double yHighBound = Math.min(Via.getTopY(mc), playerY + searchRange);
                    int targetX = targetBlock.getX();
                    int targetY = targetBlock.getY();
                    int targetZ = targetBlock.getZ();

                    double bestDistSq = Double.MAX_VALUE;
                    long bestPos = Long.MIN_VALUE; // 哨兵：没有候选时保持最小值
                    // 限制搜索范围，优先搜索与玩家相同高度或稍低的位置
                    for (int x = (int) (playerX - searchRange); x < playerX + searchRange; x++) {
                        for (int z = (int) (playerZ - searchRange); z < playerZ + searchRange; z++) {
                            for (int y = (int) playerY; y > yLowBound && y < yHighBound; y--) {
                                bp.set(x, y, z);
                                if (!BaritoneUtil.canPlace(bp)) continue;
                                if (eyePos.distanceToSqr(Vec3.atCenterOf(bp.relative(BlockUtils.getClosestPlaceSide(bp)))) > 36)
                                    continue;
                                double dx = x - targetX, dy = y - targetY, dz = z - targetZ;
                                double distSq = dx * dx + dy * dy + dz * dz;
                                if (distSq < bestDistSq) {
                                    bestDistSq = distSq;
                                    bestPos = bp.asLong();
                                }
                            }
                        }
                    }

                    if (bestPos == Long.MIN_VALUE) return;

                    bp.set(BlockPos.of(bestPos));
                }
            }

            if (airPlace.get()) {
                // 优先尝试放置玩家正下方的位置
                BlockPos playerUnderPos = mc.player.blockPosition().below();
                if (BaritoneUtil.canPlace(playerUnderPos)) {
                    place(playerUnderPos);
                } else {
                    // 如果正下方无法放置，则在半径范围内搜索
                    List<BlockPos> blocks = new ArrayList<>();

                    // 添加玩家正下方位置到列表开头
                    blocks.add(playerUnderPos);

                    // 搜索半径内的其他位置
                    for (int x = (int) (bp.getX() - radius.get()); x <= bp.getX() + radius.get(); x++) {
                        for (int z = (int) (bp.getZ() - radius.get()); z <= bp.getZ() + radius.get(); z++) {
                            BlockPos blockPos = BlockPos.containing(x, bp.getY(), z);
                            // 避免重复添加玩家正下方位置
                            if (!blockPos.equals(playerUnderPos) &&
                                (Via.getEntityPos(mc.player).distanceTo(Vec3.upFromBottomCenterOf(blockPos, 0)) <= radius.get() ||
                                    (x == bp.getX() && z == bp.getZ()))) {
                                blocks.add(blockPos);
                            }
                        }
                    }

                    if (!blocks.isEmpty()) {
                        blocks.sort(Comparator.comparingDouble(PlayerUtils::squaredDistanceTo));
                        int counter = 0;
                        for (BlockPos block : blocks) {
                            if (place(block)) {
                                counter++;
                            }

                            if (counter >= blocksPerTick.get()) {
                                break;
                            }
                        }
                    }
                }
            } else {
                place(bp);
            }

            int slot = BagUtil.findItemInventorySlot(itemStack -> validItem(itemStack, bp));

            if (fastTower.get() && mc.options.keyJump.isDown() && !mc.options.keyShift.isDown() && slot != -1) {
                Vec3 velocity = mc.player.getDeltaMovement();
                AABB playerBox = mc.player.getBoundingBox();
                if (Streams.stream(mc.level.getBlockCollisions(mc.player, playerBox.move(0, 1, 0))).toList().isEmpty()) {
                    // 如果玩家上方没有方块：将玩家向上移动，以便他可以放置另一个方块
                    if (whileMoving.get() || !PlayerUtils.isMoving()) {
                        velocity = new Vec3(velocity.x, towerSpeed.get(), velocity.z);
                    }
                    mc.player.setDeltaMovement(velocity);
                } else {
                    // 如果玩家上方有一个方块：将玩家向下移动，使他位于放置的方块的顶部
                    mc.player.setDeltaMovement(velocity.x, Math.ceil(mc.player.getY()) - mc.player.getY(), velocity.z);
                    mc.player.setOnGround(true);
                }
            }

        } catch (Exception e) {
            info("Scaffolding error:" + e);
        }
    }

    public boolean scaffolding() {
        return isActive();
    }

    public boolean towering() {
        int slot = BagUtil.findItemInventorySlot(itemStack -> validItem(itemStack, bp));
        return scaffolding() && fastTower.get() && mc.options.keyJump.isDown() && !mc.options.keyShift.isDown() &&
            (whileMoving.get() || !PlayerUtils.isMoving()) && slot != -1;
    }

    private boolean validItem(ItemStack itemStack, BlockPos pos) {
        if (!(itemStack.getItem() instanceof BlockItem)) return false;

        Block block = ((BlockItem) itemStack.getItem()).getBlock();

        if (blocksFilter.get() == ListMode.Blacklist && blocks.get().contains(block)) return false;
        else if (blocksFilter.get() == ListMode.Whitelist && !blocks.get().contains(block)) return false;

        if (!Block.isShapeFullBlock(block.defaultBlockState().getCollisionShape(mc.level, pos))) return false;
        return !(block instanceof FallingBlock) || !FallingBlock.isFree(mc.level.getBlockState(pos));
    }

    private boolean place(BlockPos bp) {
        // 检查位置是否在缓存中（防止短时间重复放置）
        if (positionCache.isInCache(bp)) {
            return false;
        }

        int slot = BagUtil.findItemInventorySlot(itemStack -> validItem(itemStack, bp));
        if (slot == -1) {
            return false;
        }

        // 切换到正确的物品栏
        BagUtil.doSwap(slot);

        // 放置方块
        boolean placed = false;

        if (airPlace.get()) {
            placed = BaritoneUtil.airPlaceBlock(bp);
        } else {
            placed = BaritoneUtil.placeBlock(bp);
        }

        BagUtil.doSwap(slot);

        // 使用 BaritoneUtil 放置方块
        if (placed) {
            // 将位置添加到缓存，记录放置尝试
            positionCache.addToCache(bp);
            // Render block if was placed
            if (render.get())
                RenderUtils.renderTickingBlock(bp.immutable(), sideColor.get(), lineColor.get(), shapeMode.get(), 0, 8, true, false);
            return true;
        } else {
            // 放置失败也要缓存，避免短时间重复尝试
            positionCache.addToCache(bp);
        }
        return false;
    }

}
