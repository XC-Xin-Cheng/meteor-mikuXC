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
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public class HoleSnap extends BaseModule {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("渲染");

    // 通用设置
    private final Setting<Boolean> anyHole = sgGeneral.add(new BoolSetting.Builder()
        .name("任意坑洞")
        .description("是否捕捉任意类型的坑洞。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("范围")
        .description("搜索坑洞的最大范围。")
        .defaultValue(5.0)
        .min(1.0)
        .max(50.0)
        .sliderMax(50.0)
        .build()
    );

    private final Setting<Integer> timeoutTicks = sgGeneral.add(new IntSetting.Builder()
        .name("超时刻数")
        .description("自动禁用前的最大刻数。")
        .defaultValue(40)
        .min(0)
        .max(100)
        .sliderMax(100)
        .build()
    );

    private final Setting<Double> timer = sgGeneral.add(new DoubleSetting.Builder()
        .name("计时器")
        .description("移动速度倍率。")
        .defaultValue(1.0)
        .min(0.1)
        .max(8.0)
        .sliderMax(8.0)
        .build()
    );

    private final Setting<Boolean> up = sgGeneral.add(new BoolSetting.Builder()
        .name("向上")
        .description("是否允许向上移动到坑洞。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> grim = sgGeneral.add(new BoolSetting.Builder()
        .name("Grim模式")
        .description("使用Grim反作弊兼容模式。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> steps = sgGeneral.add(new DoubleSetting.Builder()
        .name("步进")
        .description("旋转步进速度。")
        .defaultValue(0.8)
        .min(0.0)
        .max(1.0)
        .sliderMax(1.0)
        .visible(grim::get)
        .build()
    );

    private final Setting<Double> priority = sgGeneral.add(new DoubleSetting.Builder()
        .name("优先级")
        .description("旋转优先级。")
        .defaultValue(10.0)
        .min(0.0)
        .max(100.0)
        .sliderMax(100.0)
        .visible(grim::get)
        .build()
    );

    // 渲染设置
    private final Setting<SettingColor> color = sgRender.add(new ColorSetting.Builder()
        .name("颜色")
        .description("渲染圆圈的颜色。")
        .defaultValue(new SettingColor(255, 255, 255, 100))
        .build()
    );

    private final Setting<Double> circleSize = sgRender.add(new DoubleSetting.Builder()
        .name("圆圈大小")
        .description("渲染圆圈的大小。")
        .defaultValue(1.0)
        .min(0.1)
        .max(2.5)
        .sliderMax(2.5)
        .build()
    );

    private final Setting<Boolean> fade = sgRender.add(new BoolSetting.Builder()
        .name("渐变")
        .description("是否使用渐变效果。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> segments = sgRender.add(new IntSetting.Builder()
        .name("分段数")
        .description("圆圈的平滑度。")
        .defaultValue(180)
        .min(0)
        .max(360)
        .sliderMax(360)
        .build()
    );

    // 内部状态
    private boolean resetMove = false;
    private BlockPos holePos;
    private int stuckTicks;
    private int enabledTicks;
    private boolean applyTimer = false;
    private Vec3 targetPos;

    public HoleSnap() {
        super(BaseModule.CATEGORY_MIKU_COMBAT, "进基岩坑", "自动进附近的安全基岩坑。");
    }

    @Override
    public void onActivate() {
        if (mc.player == null) {
            toggle();
            return;
        }

        applyTimer = false;
        resetMove = false;
        holePos = findHole(range.get().floatValue(), true, anyHole.get(), up.get());
        stuckTicks = 0;
        enabledTicks = 0;
    }

    @Override
    public void onDeactivate() {
        holePos = null;
        stuckTicks = 0;
        enabledTicks = 0;

        if (mc.player != null && resetMove && !grim.get()) {
            Vec3 vel = mc.player.getDeltaMovement();
            mc.player.setDeltaMovement(0, vel.y, 0);
        }
    }

    @EventHandler
    private void onReceivePacket(PacketEvent.Receive event) {
        if (event.packet instanceof ClientboundPlayerPositionPacket) {
            toggle();
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.level == null) return;

        holePos = findHole(range.get().floatValue(), true, anyHole.get(), up.get());
        if (holePos == null) {
            toggle();
            return;
        }

        enabledTicks++;
        if (enabledTicks > timeoutTicks.get() - 1) {
            toggle();
            return;
        }

        applyTimer = true;

        if (!grim.get()) {
            // 非Grim模式下的移动逻辑在这里处理
            if (!mc.player.isAlive() || Via.isFallFlying(mc)) {
                toggle();
                return;
            }

            if (stuckTicks > 8) {
                toggle();
                return;
            }

            updateTargetPos();

            Vec3 playerPos = Via.getEntityPos(mc.player);
            float rotation = getRotationTo(playerPos, targetPos)[0];
            float yawRad = rotation / 180.0f * (float) Math.PI;
            double dist = playerPos.distanceTo(targetPos);
            double cappedSpeed = Math.min(0.2873, dist);
            double x = -Math.sin(yawRad) * cappedSpeed;
            double z = Math.cos(yawRad) * cappedSpeed;

            mc.player.setDeltaMovement(x, mc.player.getDeltaMovement().y, z);
            resetMove = true;

            if (Math.abs(x) < 0.1 && Math.abs(z) < 0.1 && playerPos.y <= holePos.getY() + 0.5) {
                toggle();
            }

            if (mc.player.horizontalCollision) {
                stuckTicks++;
            } else {
                stuckTicks = 0;
            }
        } else {
            // Grim模式
            if (!mc.player.isAlive() || Via.isFallFlying(mc)) {
                toggle();
                return;
            }

            if (stuckTicks > 8) {
                toggle();
                return;
            }

            updateTargetPos();

            Vec3 playerPos = Via.getEntityPos(mc.player);
            double dist = playerPos.distanceTo(targetPos);

            if (dist < 0.25 && playerPos.y <= holePos.getY() + 0.8) {
                toggle();
            }

            if (mc.player.horizontalCollision) {
                stuckTicks++;
            } else {
                stuckTicks = 0;
            }
        }
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (targetPos == null || holePos == null || mc.player == null) return;

        Vec3 renderPos = new Vec3(targetPos.x, holePos.getY(), targetPos.z);

        if (fade.get()) {
            double temp = 0.01;
            for (double i = 0; i < circleSize.get(); i += temp) {
                int alpha = (int) Math.min(color.get().a * 2 / (circleSize.get() / temp), 255);
                SettingColor fadeColor = new SettingColor(color.get().r, color.get().g, color.get().b, alpha);
                drawCircle(event, fadeColor, i, renderPos);
            }
        } else {
            drawCircle(event, color.get(), circleSize.get(), renderPos);
        }
    }

    private void drawCircle(Render3DEvent event, SettingColor c, double size, Vec3 pos) {
        int segs = segments.get();
        for (int i = 0; i < segs; i++) {
            double angle1 = Math.toRadians((double) i * 360 / segs);
            double angle2 = Math.toRadians((double) (i + 1) * 360 / segs);

            double x1 = Math.sin(angle1) * size;
            double z1 = Math.cos(angle1) * size;
            double x2 = Math.sin(angle2) * size;
            double z2 = Math.cos(angle2) * size;

            event.renderer.line(
                pos.x + x1, pos.y, pos.z + z1,
                pos.x + x2, pos.y, pos.z + z2,
                c
            );
        }
    }

    // ===== 坑洞检测方法 =====

    private BlockPos findHole(float range, boolean doubleHole, boolean any, boolean up) {
        if (mc.player == null || mc.level == null) return null;

        BlockPos bestPos = null;
        double bestDistance = range + 1;

        for (BlockPos pos : getSphere(range, Via.getEntityPos(mc.player))) {
            if (pos.getX() != mc.player.getBlockX() || pos.getZ() != mc.player.getBlockZ()) {
                if (!up && pos.getY() + 1 > mc.player.getY()) continue;
            }

            if (isHole(pos, true, true, any) || (doubleHole && isDoubleHole(pos))) {
                if (pos.getY() - mc.player.getBlockY() > 1) continue;

                double distance = Mth.sqrt((float) mc.player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
                if (bestPos == null || distance < bestDistance) {
                    bestPos = pos;
                    bestDistance = distance;
                }
            }
        }

        return bestPos;
    }

    private boolean isHole(BlockPos pos, boolean canStand, boolean checkTrap, boolean anyBlock) {
        if (mc.level == null || mc.player == null) return false;

        int blockProgress = 0;
        for (Direction i : Direction.values()) {
            if (i == Direction.UP || i == Direction.DOWN) continue;

            BlockPos offsetPos = pos.relative(i);
            if ((anyBlock && !mc.level.isEmptyBlock(offsetPos)) || isHard(offsetPos)) {
                blockProgress++;
            }
        }

        boolean trapCheck = !checkTrap || (
            mc.level.isEmptyBlock(pos) &&
                mc.level.isEmptyBlock(pos.above()) &&
                mc.level.isEmptyBlock(pos.above(1)) &&
                mc.level.isEmptyBlock(pos.above(2)) &&
                (mc.player.getBlockY() - 1 <= pos.getY() || mc.level.isEmptyBlock(pos.above(3))) &&
                (mc.player.getBlockY() - 2 <= pos.getY() || mc.level.isEmptyBlock(pos.above(4)))
        );

        boolean standCheck = !canStand || mc.level.getBlockState(pos.below()).blocksMotion();

        return trapCheck && blockProgress > 3 && standCheck;
    }

    private boolean isDoubleHole(BlockPos pos) {
        Direction unHardFacing = is3Block(pos);
        if (unHardFacing != null) {
            pos = pos.relative(unHardFacing);
            unHardFacing = is3Block(pos);
            return unHardFacing != null;
        }
        return false;
    }

    private Direction is3Block(BlockPos pos) {
        if (mc.level == null) return null;

        if (!isHard(pos.below())) {
            return null;
        }

        if (!mc.level.isEmptyBlock(pos) || !mc.level.isEmptyBlock(pos.above()) || !mc.level.isEmptyBlock(pos.above(2))) {
            return null;
        }

        int progress = 0;
        Direction unHardFacing = null;

        for (Direction facing : Direction.values()) {
            if (facing == Direction.UP || facing == Direction.DOWN) continue;

            if (isHard(pos.relative(facing))) {
                progress++;
                continue;
            }

            int progress2 = 0;
            for (Direction facing2 : Direction.values()) {
                if (facing2 == Direction.DOWN || facing2 == facing.getOpposite()) {
                    continue;
                }
                if (isHard(pos.relative(facing).relative(facing2))) {
                    progress2++;
                }
            }

            if (progress2 == 4) {
                progress++;
                continue;
            }

            unHardFacing = facing;
        }

        if (progress == 3) {
            return unHardFacing;
        }

        return null;
    }

    private boolean isHard(BlockPos pos) {
        if (mc.level == null) return false;

        Block block = mc.level.getBlockState(pos).getBlock();
        return block == Blocks.OBSIDIAN ||
            block == Blocks.NETHERITE_BLOCK ||
            block == Blocks.ENDER_CHEST ||
            block == Blocks.BEDROCK;
    }

    private List<BlockPos> getSphere(float radius, Vec3 center) {
        List<BlockPos> positions = new ArrayList<>();

        int radiusInt = (int) Math.ceil(radius);
        BlockPos centerPos = BlockPos.containing(center);

        for (int x = -radiusInt; x <= radiusInt; x++) {
            for (int y = -radiusInt; y <= radiusInt; y++) {
                for (int z = -radiusInt; z <= radiusInt; z++) {
                    BlockPos pos = centerPos.offset(x, y, z);
                    double distance = Math.sqrt(x * x + y * y + z * z);
                    if (distance <= radius) {
                        positions.add(pos);
                    }
                }
            }
        }

        return positions;
    }

    private void updateTargetPos() {
        if (holePos == null || mc.player == null) return;

        targetPos = new Vec3(holePos.getX() + 0.5, mc.player.getY(), holePos.getZ() + 0.5);

        if (isDoubleHole(holePos)) {
            Direction facing = is3Block(holePos);
            if (facing != null) {
                targetPos = targetPos.add(
                    facing.getUnitVec3i().getX() * 0.5,
                    facing.getUnitVec3i().getY() * 0.5,
                    facing.getUnitVec3i().getZ() * 0.5
                );
            }
        }
    }

    private float[] getRotationTo(Vec3 posFrom, Vec3 posTo) {
        Vec3 diff = posTo.subtract(posFrom);

        double d = diff.x;
        double d2 = diff.z;
        double xz = Math.hypot(d, d2);

        double yaw = normalizeAngle(Math.toDegrees(Math.atan2(d2, d)) - 90.0);
        double pitch = normalizeAngle(Math.toDegrees(-Math.atan2(diff.y, xz)));

        return new float[]{(float) yaw, (float) pitch};
    }

    private double normalizeAngle(double angleIn) {
        double angle = angleIn;
        angle %= 360.0;

        if (angle >= 180.0) {
            angle -= 360.0;
        }
        if (angle < -180.0) {
            angle += 360.0;
        }

        return angle;
    }
}
