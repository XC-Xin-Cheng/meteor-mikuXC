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
import com.github.mikumiku.addon.util.*;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.movement.Blink;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.Items;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.Direction;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class SurroundPlus extends BaseModule {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    public Setting<Boolean> onlyObsidian = this.sgGeneral
        .add(
            new BoolSetting.Builder()
                .name("仅使用黑曜石")
                .defaultValue(false)
                .build()
        );
    public Setting<Integer> delay = this.sgGeneral
        .add(
            new IntSetting.Builder()
                .name("延迟")
                .sliderRange(0, 20)
                .defaultValue(0)
                .build()
        );
    public Setting<Integer> placeNums = this.sgGeneral
        .add(
            new IntSetting.Builder()
                .name("放置数量")
                .sliderRange(1, 8)
                .defaultValue(4)
                .build()
        );
    public Setting<Boolean> attack = this.sgGeneral
        .add(new BoolSetting.Builder().name("攻击水晶").defaultValue(true).build());
    public Setting<Integer> crystalAge = this.sgGeneral
        .add(
            new IntSetting.Builder()
                .name("水晶年龄")
                .sliderRange(0, 10)
                .defaultValue(4)
                .build()
        );
    public Setting<Boolean> bottom = this.sgGeneral
        .add(new BoolSetting.Builder().name("底部").defaultValue(true).build());
    private final SettingGroup toggleGeneral = this.settings.createGroup("切换设置");
    private final Setting<Boolean> yChange = this.toggleGeneral
        .add(
            new BoolSetting.Builder().name("Y轴变化").defaultValue(false).build()
        );
    private final Setting<Boolean> chorus = this.toggleGeneral
        .add(new BoolSetting.Builder().name("紫颂果").defaultValue(false).build());

    private final Setting<Boolean> pearl = this.toggleGeneral
        .add(new BoolSetting.Builder().name("末影珍珠").defaultValue(false).build());

    private final Setting<Boolean> flying = this.toggleGeneral
        .add(new BoolSetting.Builder().name("飞行").defaultValue(false).build());

    private final Setting<Boolean> death = this.toggleGeneral
        .add(new BoolSetting.Builder().name("死亡").defaultValue(false).build());


    private final SettingGroup renderGeneral = this.settings.createGroup("渲染设置");

    /**
     * 本模块自己的「类人化」设置组：旋转注入手抖。模式默认「跟随全局」。
     */
    private final HumanizedSettings humanized = HumanizedSettings.builder(this, "SurroundPlus").view().rotateNoise().build();
    private final Setting<Boolean> render = this.renderGeneral
        .add(
            new BoolSetting.Builder()
                .name("渲染")
                .description("是否渲染")
                .defaultValue(true)
                .build()
        );
    private final Setting<ShapeMode> shapeMode = renderGeneral.add(new EnumSetting.Builder<ShapeMode>()
        .name("形状模式")
        .description("渲染的形状")
        .defaultValue(ShapeMode.Both)
        .visible(render::get)
        .build()
    );
    private final Setting<SettingColor> readySideColor = this.renderGeneral
        .add(
            new ColorSetting.Builder()
                .name("准备侧面颜色")
                .description("准备的侧面颜色")
                .defaultValue(new SettingColor(238, 76, 229, 50))
                .build()
        );
    private final Setting<SettingColor> readyLineColor = this.renderGeneral
        .add(
            new ColorSetting.Builder()
                .name("准备线条颜色")
                .description("准备的线条颜色")
                .defaultValue(new SettingColor(255, 255, 255, 128))
                .build()
        );
    private int tickDelay = 0;
    private int lastY = -1000;
    private boolean isEatingChorus = false;
    private Thread breakThread = null;
    private final ConcurrentHashMap<BlockPos, Long> renderPos = new ConcurrentHashMap<>();

    public SurroundPlus() {
        super(BaseModule.CATEGORY_MIKU_COMBAT, "围脚+", "使用方块快速包裹自己的玉足，减少水晶爆炸造成的伤害。");

    }

    @Override
    public void onActivate() {
        this.tickDelay = 20;
        this.lastY = -1000;
        this.isEatingChorus = false;
        this.breakThread = null;
        this.renderPos.clear();
    }

    @Override
    public void onDeactivate() {
        this.tickDelay = 20;
        this.lastY = -1000;
        this.isEatingChorus = false;
        if (this.breakThread != null) {
            this.breakThread.interrupt();
        }
    }

    @EventHandler
    public void onTick(TickEvent.Pre event) {
        if (Via.isFallFlying(mc) && this.flying.get()) {
            this.info("玩家正在飞行，关闭模块");
            this.toggle();
        } else if (!Modules.get().isActive(Blink.class)) {
            if (this.yChange.get()) {
                if (this.lastY == -1000) {
                    this.lastY = mc.player.blockPosition().getY();
                }

                if (mc.player.blockPosition().getY() != this.lastY) {
                    this.info("玩家Y轴坐标发生变化，关闭模块");
                    this.toggle();
                    return;
                }
            }

            if (this.chorus.get()) {
                this.isEatingChorus = mc.player.getActiveItem().getItem() == Items.CHORUS_FRUIT;
            }

            if (this.death.get() && mc.player.isDeadOrDying()) {
                this.info("玩家已死亡，关闭模块");
                this.toggle();
            } else {
                int slot = BagUtil.findItemInventorySlot(Items.OBSIDIAN);
                boolean found = slot != -1;

                if (!found) {
                    if (this.onlyObsidian.get()) {
                        return;
                    }
                    slot = BagUtil.findItemInventorySlot(Items.ENDER_CHEST);
                    found = slot != -1;
                    if (!found) {
                        return;
                    }
                }

                if (this.tickDelay <= this.delay.get()) {
                    this.tickDelay++;
                } else {
                    this.tickDelay = 0;
                    List<BlockPos> surroundPos = this.getSurroundPos();
                    if (this.bottom.get()) {
                        surroundPos.add(0, mc.player.blockPosition().below());
                    }

                    List<BlockPos> airPos = new ArrayList<>();
                    surroundPos = surroundPos.stream()
                        .filter(
                            p -> {
                                if (BlockUtils.canPlace(p, !this.attack.get())
                                    && !mc.level.getBlockState(p).isCollisionShapeFullBlock(mc.level, p)
                                    && BaritoneUtil.canPlace(p, true)) {
                                    return true;
                                } else {
                                    if (mc.level.getBlockState(p).getBlock().asItem() == Items.AIR) {
                                        airPos.add(p);
                                    }

                                    return false;
                                }
                            }
                        )
                        .collect(Collectors.toList());

                    for (BlockPos airblockPos : airPos) {
                        BlockPos blockPos = airblockPos.below();
                        if (BaritoneUtil.canPlace(blockPos, true) && !surroundPos.contains(blockPos)) {
                            surroundPos.add(0, blockPos);
                        }
                    }

                    int placeSize = Math.min(surroundPos.size(), this.placeNums.get());

                    for (int i = 0; i < placeSize; i++) {
                        BagUtil.doSwap(slot);
                        if (this.attack.get()) {
                            for (Entity entity : mc.level.entitiesForRendering()) {
                                if (entity instanceof EndCrystal crystalEntity
                                    && PlayerUtils.distanceTo(entity) < 3
                                    && entity.getBoundingBox().intersects(new AABB(surroundPos.get(i)))) {
                                    BlockPos entityBlockPos = crystalEntity.blockPosition();
                                    RotationManager.getInstance().register(new Rotation((float) Rotations.getYaw(entityBlockPos), (float) Rotations.getPitch(entityBlockPos)), humanized.profile());
                                    mc.player.connection.send(new ServerboundInteractPacket(crystalEntity.getId(), InteractionHand.MAIN_HAND, crystalEntity.position(), false));
                                    mc.player.swing(InteractionHand.MAIN_HAND);

                                }
                            }
                        }

                        BaritoneUtil.placeBlock(surroundPos.get(i), true, true, true);
                        if (!this.renderPos.containsKey(surroundPos.get(i))) {
                            this.renderPos.put(surroundPos.get(i), System.currentTimeMillis());
                        }
                        BagUtil.doSwap(slot);

                    }
                }
            }
        }
    }

//    public List<BlockPos> getSurroundPos() {
//        List<BlockPos> result = new ArrayList<>();
//        List<BlockPos> BurrowBlockPos = new ArrayList<>();
//        BlockPos playerPos = mc.player.getBlockPos();
//        BurrowBlockPos.add(playerPos);
//
//        for (WalkDirection direction : WalkDirection.values()) {
//            if (direction != WalkDirection.UP && direction != WalkDirection.DOWN) {
//                BlockPos blockPos = playerPos.relative(direction);
//                if (mc.player.getBoundingBox().intersects(new AABB(blockPos)) && !BurrowBlockPos.contains(blockPos)) {
//                    BurrowBlockPos.add(blockPos);
//
//                    for (WalkDirection direction1 : WalkDirection.values()) {
//                        if (direction1 != WalkDirection.UP
//                            && direction1 != WalkDirection.DOWN
//                            && mc.player.getBoundingBox().intersects(new AABB(blockPos.offset(direction1)))
//                            && !BurrowBlockPos.contains(blockPos.offset(direction1))) {
//                            BurrowBlockPos.add(blockPos.offset(direction1));
//                        }
//                    }
//                }
//            }
//        }
//
//        for (int i = 0; i < BurrowBlockPos.size(); i++) {
//            for (WalkDirection directionx : WalkDirection.values()) {
//                if (directionx != WalkDirection.UP && directionx != WalkDirection.DOWN) {
//                    BlockPos blockPos = BurrowBlockPos.get(i).relative(directionx);
//                    if (!mc.player.getBoundingBox().intersects(new AABB(blockPos)) && !result.contains(blockPos)) {
//                        result.add(blockPos);
//                    }
//                }
//            }
//        }
//
//        return result;
//    }

    public List<BlockPos> getSurroundPos() {
        Set<BlockPos> burrowSet = new LinkedHashSet<>();
        Set<BlockPos> result = new LinkedHashSet<>();

        BlockPos playerPos = mc.player.blockPosition();
        AABB playerBox = mc.player.getBoundingBox();

        // Step 1: Add player position
        burrowSet.add(playerPos);

        // Step 2: Add adjacent horizontal blocks that intersect player bounding box
        for (Direction dir : Direction.values()) {
            if (dir == Direction.UP || dir == Direction.DOWN) continue;

            BlockPos adj = playerPos.relative(dir);
            if (playerBox.intersects(new AABB(adj))) {
                burrowSet.add(adj);

                // Step 3: Add second-layer adjacent blocks (also horizontal)
                for (Direction dir2 : Direction.values()) {
                    if (dir2 == Direction.UP || dir2 == Direction.DOWN) continue;

                    BlockPos adj2 = adj.relative(dir2);
                    if (playerBox.intersects(new AABB(adj2))) {
                        burrowSet.add(adj2);
                    }
                }
            }
        }

        // Step 4: For every block in burrowSet, add its horizontal neighbors that DO NOT intersect player box
        for (BlockPos pos : burrowSet) {
            for (Direction dir : Direction.values()) {
                if (dir == Direction.UP || dir == Direction.DOWN) continue;

                BlockPos neighbor = pos.relative(dir);
                if (!playerBox.intersects(new AABB(neighbor))) {
                    result.add(neighbor);
                }
            }
        }

        return new ArrayList<>(result);
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!this.render.get() || this.renderPos.isEmpty()) {
            return;
        }

        long currentTime = System.currentTimeMillis();
        Iterator<Map.Entry<BlockPos, Long>> iterator = this.renderPos.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<BlockPos, Long> entry = iterator.next();
            BlockPos pos = entry.getKey();
            long diff = currentTime - entry.getValue();

            if (diff <= 300) {
                double scale = 0.5 * (diff / 300.0);
                double x1 = pos.getX() + scale;
                double y1 = pos.getY() + scale;
                double z1 = pos.getZ() + scale;
                double x2 = pos.getX() + 1 - scale;
                double y2 = pos.getY() + 1 - scale;
                double z2 = pos.getZ() + 1 - scale;

                event.renderer.box(x1, y1, z1, x2, y2, z2,
                    this.readySideColor.get(),
                    this.readyLineColor.get(),
                    this.shapeMode.get(),
                    0);
            } else {
                iterator.remove();
            }
        }
    }

    @EventHandler
    public void onPacketReceive(PacketEvent.Receive receive) {
        if (receive.packet instanceof ClientboundPlayerPositionPacket && this.chorus.get() && this.isEatingChorus) {
            this.info("检测到服务器传送，关闭模块");
            this.toggle();
        }
    }

    @EventHandler
    public void onPacketSend(PacketEvent.Send event) {
        if (event.packet instanceof ServerboundUseItemPacket packet && this.pearl.get() && mc.player.getMainHandItem().getItem() == Items.ENDER_PEARL) {
            this.info("使用末影珍珠，关闭模块");
            this.toggle();
        }
    }

}
