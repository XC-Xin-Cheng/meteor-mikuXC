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
import com.github.mikumiku.addon.util.Humanized;
import com.github.mikumiku.addon.util.HumanizedSettings;
import net.minecraft.util.Mth;
import meteordevelopment.meteorclient.events.entity.player.BlockBreakingCooldownEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.RenderUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockIterator;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.world.level.block.Block;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class Nuker extends BaseModule {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgWhitelist = settings.createGroup("Whitelist");
    private final SettingGroup sgRender = settings.createGroup("Render");

    /**
     * 本模块自己的「类人化」设置组：旋转注入手抖。模式默认「跟随全局」。
     */
    private final HumanizedSettings humanized = HumanizedSettings.builder(this, "Nuker").view().rotateNoise().build();

    // General

    private final Setting<Shape> shape = sgGeneral.add(new EnumSetting.Builder<Shape>()
        .name("shape")
        .description("挖掘的形状")
        .defaultValue(Shape.Sphere)
        .build()
    );

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("挖掘的方式")
        .defaultValue(Mode.Flatten)
        .build()
    );

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("破坏范围")
        .defaultValue(4)
        .min(0)
        .visible(() -> shape.get() != Shape.Cube)
        .build()
    );

    private final Setting<Integer> range_up = sgGeneral.add(new IntSetting.Builder()
        .name("up")
        .description("向上破坏范围")
        .defaultValue(1)
        .min(0)
        .visible(() -> shape.get() == Shape.Cube)
        .build()
    );

    private final Setting<Integer> range_down = sgGeneral.add(new IntSetting.Builder()
        .name("down")
        .description("向下破坏范围")
        .defaultValue(1)
        .min(0)
        .visible(() -> shape.get() == Shape.Cube)
        .build()
    );

    private final Setting<Integer> range_left = sgGeneral.add(new IntSetting.Builder()
        .name("left")
        .description("向左破坏范围")
        .defaultValue(1)
        .min(0)
        .visible(() -> shape.get() == Shape.Cube)
        .build()
    );

    private final Setting<Integer> range_right = sgGeneral.add(new IntSetting.Builder()
        .name("right")
        .description("向右破坏范围")
        .defaultValue(1)
        .min(0)
        .visible(() -> shape.get() == Shape.Cube)
        .build()
    );

    private final Setting<Integer> range_forward = sgGeneral.add(new IntSetting.Builder()
        .name("forward")
        .description("向前破坏范围")
        .defaultValue(1)
        .min(0)
        .visible(() -> shape.get() == Shape.Cube)
        .build()
    );

    private final Setting<Integer> range_back = sgGeneral.add(new IntSetting.Builder()
        .name("back")
        .description("向后破坏范围")
        .defaultValue(1)
        .min(0)
        .visible(() -> shape.get() == Shape.Cube)
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("破坏间隔")
        .defaultValue(0)
        .build()
    );

    private final Setting<Integer> maxBlocksPerTick = sgGeneral.add(new IntSetting.Builder()
        .name("max-blocks-per-tick")
        .description("一次破坏方块的数量")
        .defaultValue(1)
        .min(1)
        .sliderRange(1, 6)
        .build()
    );

    private final Setting<SortMode> sortMode = sgGeneral.add(new EnumSetting.Builder<SortMode>()
        .name("sort-mode")
        .description("方块筛选")
        .defaultValue(SortMode.Closest)
        .build()
    );

    private final Setting<Boolean> swingHand = sgGeneral.add(new BoolSetting.Builder()
        .name("swing-hand")
        .description("摆手")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> packetMine = sgGeneral.add(new BoolSetting.Builder()
        .name("packet-mine")
        .description("使用包挖")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate")
        .description("服务器端转头")
        .defaultValue(true)
        .build()
    );

    // Whitelist and blacklist

    private final Setting<ListMode> listMode = sgWhitelist.add(new EnumSetting.Builder<ListMode>()
        .name("list-mode")
        .description("筛选模式")
        .defaultValue(ListMode.Blacklist)
        .build()
    );

    private final Setting<List<Block>> blacklist = sgWhitelist.add(new BlockListSetting.Builder()
        .name("blacklist")
        .description("不破坏的方块")
        .visible(() -> listMode.get() == ListMode.Blacklist)
        .build()
    );

    private final Setting<List<Block>> whitelist = sgWhitelist.add(new BlockListSetting.Builder()
        .name("whitelist")
        .description("破坏的方块")
        .visible(() -> listMode.get() == ListMode.Whitelist)
        .build()
    );

    // Rendering

    private final Setting<Boolean> enableRenderBounding = sgRender.add(new BoolSetting.Builder()
        .name("bounding-box")
        .description("渲染破坏范围")
        .defaultValue(true)
        .build()
    );

    private final Setting<ShapeMode> shapeModeBox = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("nuke-box-mode")
        .description("渲染模式")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<SettingColor> sideColorBox = sgRender.add(new ColorSetting.Builder()
        .name("side-color")
        .description("边颜色")
        .defaultValue(new SettingColor(16, 106, 144, 100))
        .build()
    );

    private final Setting<SettingColor> lineColorBox = sgRender.add(new ColorSetting.Builder()
        .name("line-color")
        .description("轮廓颜色")
        .defaultValue(new SettingColor(16, 106, 144, 255))
        .build()
    );

    private final Setting<Boolean> enableRenderBreaking = sgRender.add(new BoolSetting.Builder()
        .name("broken-blocks")
        .description("被破坏方块渲染")
        .defaultValue(true)
        .build()
    );

    private final Setting<ShapeMode> shapeModeBreak = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("nuke-block-mode")
        .description("渲染被破坏方块")
        .defaultValue(ShapeMode.Both)
        .visible(enableRenderBreaking::get)
        .build()
    );

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("side-color")
        .description("边颜色")
        .defaultValue(new SettingColor(255, 0, 0, 80))
        .visible(enableRenderBreaking::get)
        .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color")
        .description("轮廓颜色")
        .defaultValue(new SettingColor(255, 0, 0, 255))
        .visible(enableRenderBreaking::get)
        .build()
    );

    private final List<BlockPos> blocks = new ArrayList<>();

    private boolean firstBlock;
    private final BlockPos.MutableBlockPos lastBlockPos = new BlockPos.MutableBlockPos();

    private int timer;
    private int noBlockTimer;

    private final BlockPos.MutableBlockPos pos1 = new BlockPos.MutableBlockPos(); // Rendering for cubes
    private final BlockPos.MutableBlockPos pos2 = new BlockPos.MutableBlockPos();
    int maxh = 0;
    int maxv = 0;

    public Nuker() {
        super(Categories.Player, "nuker", "自动挖周围");
    }

    @Override
    public void onActivate() {
        firstBlock = true;
        timer = 0;
        noBlockTimer = 0;
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (enableRenderBounding.get()) {
            // Render bounding box if cube and should break stuff
            if (shape.get() != Shape.Sphere && mode.get() != Mode.Smash) {
                int minX = Math.min(pos1.getX(), pos2.getX());
                int minY = Math.min(pos1.getY(), pos2.getY());
                int minZ = Math.min(pos1.getZ(), pos2.getZ());
                int maxX = Math.max(pos1.getX(), pos2.getX());
                int maxY = Math.max(pos1.getY(), pos2.getY());
                int maxZ = Math.max(pos1.getZ(), pos2.getZ());
                event.renderer.box(minX, minY, minZ, maxX, maxY, maxZ, sideColorBox.get(), lineColorBox.get(), shapeModeBox.get(), 0);
            }
        }
    }

    @EventHandler
    private void onTickPre(TickEvent.Pre event) {
        // Update timer
        if (timer > 0) {
            timer--;
            return;
        }

        // Calculate some stuff
        double pX = mc.player.getX();
        double pY = mc.player.getY();
        double pZ = mc.player.getZ();

        double rangeSq = Math.pow(range.get(), 2);

        if (shape.get() == Shape.UniformCube) range.set((double) Math.round(range.get()));

        // Some render stuff

        double pX_ = pX;
        double pZ_ = pZ;
        int r = (int) Math.round(range.get());

        if (shape.get() == Shape.UniformCube) {
            pX_ += 1; // weired position stuff
            pos1.set(pX_ - r, pY - r + 1, pZ - r + 1); // down
            pos2.set(pX_ + r - 1, pY + r, pZ + r); // up
        } else {
            int direction = Math.round((mc.player.getRotationVector().y % 360) / 90);
            direction = Math.floorMod(direction, 4);

            // direction == 1
            pos1.set(pX_ - range_forward.get(), Math.ceil(pY) - range_down.get(), pZ_ - range_right.get()); // down
            pos2.set(pX_ + range_back.get() + 1, Math.ceil(pY + range_up.get() + 1), pZ_ + range_left.get() + 1); // up

            // Only change me if you want to mess with 3D rotations:
            // I messed with it
            switch (direction) {
                case 0 -> {
                    pZ_ += 1;
                    pX_ += 1;
                    pos1.set(pX_ - (range_right.get() + 1), Math.ceil(pY) - range_down.get(), pZ_ - (range_back.get() + 1)); // down
                    pos2.set(pX_ + range_left.get(), Math.ceil(pY + range_up.get() + 1), pZ_ + range_forward.get()); // up
                }
                case 2 -> {
                    pX_ += 1;
                    pZ_ += 1;
                    pos1.set(pX_ - (range_left.get() + 1), Math.ceil(pY) - range_down.get(), pZ_ - (range_forward.get() + 1)); // down
                    pos2.set(pX_ + range_right.get(), Math.ceil(pY + range_up.get() + 1), pZ_ + range_back.get()); // up
                }
                case 3 -> {
                    pX_ += 1;
                    pos1.set(pX_ - (range_back.get() + 1), Math.ceil(pY) - range_down.get(), pZ_ - range_left.get()); // down
                    pos2.set(pX_ + range_forward.get(), Math.ceil(pY + range_up.get() + 1), pZ_ + range_right.get() + 1); // up
                }
            }

            // get largest horizontal
            maxh = 1 + Math.max(Math.max(Math.max(range_back.get(), range_right.get()), range_forward.get()), range_left.get());
            maxv = 1 + Math.max(range_up.get(), range_down.get());
        }

        if (mode.get() == Mode.Flatten) {
            pos1.setY((int) Math.floor(pY));
        }
        AABB box = new AABB(Vec3.atCenterOf(pos1), Vec3.atCenterOf(pos2));

        // Find blocks to break
        BlockIterator.register(Math.max((int) Math.ceil(range.get() + 1), maxh), Math.max((int) Math.ceil(range.get()), maxv), (blockPos, blockState) -> {
            // Check for air, unbreakable blocks and distance
            switch (shape.get()) {
                case Sphere -> {
                    if (Utils.squaredDistance(pX, pY, pZ, blockPos.getX() + 0.5, blockPos.getY() + 0.5, blockPos.getZ() + 0.5) > rangeSq)
                        return;
                }
                case UniformCube -> {
                    if (chebyshevDist(mc.player.blockPosition().getX(), mc.player.blockPosition().getY(), mc.player.blockPosition().getZ(), blockPos.getX(), blockPos.getY(), blockPos.getZ()) >= range.get())
                        return;
                }
                case Cube -> {
                    if (!box.contains(Vec3.upFromBottomCenterOf(blockPos, 0))) return;
                }
            }

            if (!BlockUtils.canBreak(blockPos, blockState)) return;

            // Flatten
            if (mode.get() == Mode.Flatten && blockPos.getY() < Math.floor(mc.player.getY())) return;

            // Smash
            if (mode.get() == Mode.Smash && blockState.getDestroySpeed(mc.level, blockPos) != 0) return;

            // Check whitelist or blacklist
            if (listMode.get() == ListMode.Whitelist && !whitelist.get().contains(blockState.getBlock())) return;
            if (listMode.get() == ListMode.Blacklist && blacklist.get().contains(blockState.getBlock())) return;

            // Add block
            blocks.add(blockPos.immutable());
        });

        // Break block if found
        BlockIterator.after(() -> {
            // Sort blocks
            if (sortMode.get() == SortMode.TopDown)
                blocks.sort(Comparator.comparingDouble(value -> -value.getY()));
            else if (sortMode.get() != SortMode.None)
                blocks.sort(Comparator.comparingDouble(value -> Utils.squaredDistance(pX, pY, pZ, value.getX() + 0.5, value.getY() + 0.5, value.getZ() + 0.5) * (sortMode.get() == SortMode.Closest ? 1 : -1)));

            // Check if some block was found
            if (blocks.isEmpty()) {
                // If no block was found for long enough then set firstBlock flag to true to not wait before breaking another again
                if (noBlockTimer++ >= delay.get()) firstBlock = true;
                return;
            } else {
                noBlockTimer = 0;
            }

            // Update timer
            if (!firstBlock && !lastBlockPos.equals(blocks.getFirst())) {
                timer = delay.get();

                firstBlock = false;
                lastBlockPos.set(blocks.getFirst());

                if (timer > 0) return;
            }

            // Break
            int count = 0;

            for (BlockPos block : blocks) {
                if (count >= maxBlocksPerTick.get()) break;

                boolean canInstaMine = BlockUtils.canInstaBreak(block);

                if (rotate.get()) {
                    // 「类人化」打开时，发给服务器的挖掘朝向带一点手抖
                    Humanized.Profile hp = humanized.profile();
                    float yaw = (float) Rotations.getYaw(block);
                    float pitch = (float) Rotations.getPitch(block);
                    if (hp.enabled && hp.viewInput && hp.affectPackets) {
                        yaw += Humanized.jitter(hp, hp.noiseDegrees);
                        pitch = Mth.clamp(pitch + Humanized.jitter(hp, hp.noiseDegrees * 0.7f), -90.0f, 90.0f);
                    }
                    Rotations.rotate(yaw, pitch, () -> breakBlock(block));
                } else breakBlock(block);

                if (enableRenderBreaking.get())
                    RenderUtils.renderTickingBlock(block, sideColor.get(), lineColor.get(), shapeModeBreak.get(), 0, 8, true, false);
                lastBlockPos.set(block);

                count++;
                if (!canInstaMine && !packetMine.get() /* With packet mine attempt to break everything possible at once */)
                    break;
            }

            firstBlock = false;

            // Clear current block positions
            blocks.clear();
        });
    }

    private void breakBlock(BlockPos blockPos) {
        if (packetMine.get()) {
            mc.getConnection().send(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, blockPos, BlockUtils.getDirection(blockPos)));
            mc.player.swing(InteractionHand.MAIN_HAND);
            mc.getConnection().send(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, blockPos, BlockUtils.getDirection(blockPos)));
        } else {
            BlockUtils.breakBlock(blockPos, swingHand.get());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onBlockBreakingCooldown(BlockBreakingCooldownEvent event) {
        event.cooldown = 0;
    }

    public enum Mode {
        All,
        Flatten,
        Smash
    }

    public enum SortMode {
        None,
        Closest,
        Furthest,
        TopDown
    }

    public enum Shape {
        Cube,
        UniformCube,
        Sphere
    }

    public static int chebyshevDist(int x1, int y1, int z1, int x2, int y2, int z2) {
        // Gets the largest X, Y or Z difference, chebyshev distance
        int dX = Math.abs(x2 - x1);
        int dY = Math.abs(y2 - y1);
        int dZ = Math.abs(z2 - z1);
        return Math.max(Math.max(dX, dY), dZ);
    }
}
