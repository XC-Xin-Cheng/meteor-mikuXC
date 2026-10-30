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
import meteordevelopment.meteorclient.events.meteor.KeyInputEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.movement.Blink;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.meteorclient.utils.misc.input.KeyAction;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.Items;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

public class SurroundPlus2 extends BaseModule {
    private final SettingGroup sgGeneral = this.settings.createGroup("通用设置");
    private final SettingGroup sgBreak = this.settings.createGroup("破坏设置");
    private final SettingGroup renderGeneral = this.settings.getDefaultGroup();

    /**
     * 本模块自己的「类人化」设置组：旋转注入手抖。模式默认「跟随全局」。
     */
    private final HumanizedSettings humanized = HumanizedSettings.builder(this, "SurroundPlus2").view().rotateNoise().build();
    private final Setting<Boolean> closeByElytra = this.sgGeneral
        .add(
            new BoolSetting.Builder()
                .name("鞘翅时关闭")
                .description("当玩家装备鞘翅时关闭围脚模式")
                .defaultValue(true)
                .build()
        );
    private final Setting<Boolean> closeByChorus = this.sgGeneral
        .add(
            new BoolSetting.Builder()
                .name("紫颂果时关闭")
                .description("当玩家食用紫颂果时关闭围脚模式")
                .defaultValue(true)
                .build()
        );
    private final Setting<Boolean> surroundSetting = this.sgGeneral
        .add(
            new BoolSetting.Builder()
                .name("启用围脚")
                .description("启用方块围脚模式")
                .defaultValue(true)
                .build()
        );
    private final Setting<Keybind> surroundBind = this.sgGeneral
        .add(
            new KeybindSetting.Builder()
                .name("围脚快捷键")
                .description("切换围脚模式的快捷键")
                .defaultValue(Keybind.none())
                .build()
        );

    private final Setting<SurroundType> surroundType = sgGeneral.add(new EnumSetting.Builder<SurroundType>()
        .name("围脚厚度")
        .defaultValue(SurroundType.恐惧)
        .build()
    );
    private final Setting<Integer> delaySetting = this.sgGeneral
        .add(
            new IntSetting.Builder()
                .name("放置延迟")
                .description("方块放置的延迟（毫秒）")
                .defaultValue(50)
                .sliderRange(0, 500)
                .build()
        );
    private final Setting<Integer> bptimes = this.sgGeneral
        .add(
            new IntSetting.Builder()
                .name("每次放置数量")
                .description("每次放置的方块数量")
                .sliderRange(1, 8)
                .defaultValue(1)
                .build()
        );
    private final Setting<Boolean> bottomFirst = this.sgGeneral
        .add(
            new BoolSetting.Builder()
                .name("优先底部")
                .description("优先放置脚下方块")
                .defaultValue(false)
                .build()
        );
    private final Setting<Boolean> Top = this.sgGeneral
        .add(
            new BoolSetting.Builder()
                .name("顶部围脚")
                .description("在头顶添加围脚方块")
                .defaultValue(false)
                .build()
        );
    private final Setting<Boolean> TopOnly = this.sgGeneral
        .add(
            new BoolSetting.Builder()
                .name("仅顶部")
                .description("只放置顶部围脚方块")
                .defaultValue(false)
                .visible(this.Top::get)
                .build()
        );
    private final Setting<Boolean> breakCrystalSetting = this.sgBreak
        .add(
            new BoolSetting.Builder()
                .name("破坏水晶")
                .description("自动破坏阻碍围脚的末影水晶")
                .defaultValue(false)
                .build()
        );
    private final Setting<Keybind> breakCrystalBind = this.sgBreak
        .add(
            new KeybindSetting.Builder()
                .name("破坏水晶快捷键")
                .description("切换自动破坏水晶的快捷键")
                .defaultValue(Keybind.none())
                .build()
        );
    private final Setting<Integer> breakSetting = this.sgBreak
        .add(
            new IntSetting.Builder()
                .name("破坏延迟")
                .description("破坏末影水晶的延迟（毫秒）")
                .defaultValue(50)
                .sliderRange(0, 1000)
                .build()
        );
    private final Setting<Integer> waitTicks = this.sgBreak
        .add(
            new IntSetting.Builder()
                .name("等待刻数")
                .description("等待末影水晶的刻数")
                .defaultValue(5)
                .sliderRange(0, 5)
                .build()
        );

    private final Setting<SurroundType> breakTypeSetting = sgBreak.add(new EnumSetting.Builder<SurroundType>()
        .name("破坏类型")
        .description("选择破坏水晶的类型")
        .defaultValue(SurroundType.正常)
        .build()
    );
    private final Setting<Boolean> render = this.renderGeneral
        .add(
            new BoolSetting.Builder()
                .name("渲染")
                .description("是否渲染围脚方块")
                .defaultValue(false)
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
                .description("准备放置方块的侧面颜色")
                .defaultValue(new SettingColor(0, 204, 0, 10))
                .build()
        );
    private final Setting<SettingColor> readyLineColor = this.renderGeneral
        .add(
            new ColorSetting.Builder()
                .name("准备线条颜色")
                .description("准备放置方块的线条颜色")
                .defaultValue(new SettingColor(0, 204, 0, 255))
                .build()
        );
    private final List<BlockPos> placePositions = new ArrayList<>();
    private List<BlockPos> breakPositions = new ArrayList<>();
    private final Timer placeDelay = new Timer();
    private final Timer breakDelay = new Timer();
    public boolean sendMsg = false;
    private boolean isEatingChorus = false;

    public SurroundPlus2() {
        super(BaseModule.CATEGORY_MIKU_COMBAT, "围脚++", "使用方块快速包裹自己的玉足，减少水晶爆炸造成的伤害。");

    }

    @Override
    public void onActivate() {

        if (!this.placePositions.isEmpty()) {
            this.placePositions.clear();
        }

        this.placeDelay.reset();
        this.breakDelay.reset();
        this.isEatingChorus = false;
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (this.render.get() && this.placePositions.size() > 0) {
            for (int i = 0; i < this.placePositions.size(); i++) {
                double x1 = this.placePositions.get(i).getX();
                double y1 = this.placePositions.get(i).getY();
                double z1 = this.placePositions.get(i).getZ();
                double x2 = this.placePositions.get(i).getX() + 1;
                double y2 = this.placePositions.get(i).getY() + 1;
                double z2 = this.placePositions.get(i).getZ() + 1;
                event.renderer.box(x1, y1, z1, x2, y2, z2, this.readySideColor.get(), this.readyLineColor.get(), this.shapeMode.get(), 0);
            }
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!Modules.get().isActive(Blink.class)) {
            if (this.closeByChorus.get()) {
                this.isEatingChorus = mc.player.getActiveItem().getItem() == Items.CHORUS_FRUIT;
            }

            if (!this.sendMsg) {
                this.info(Component.literal("模块已启动"));
                this.sendMsg = true;
            }

            if (this.surroundSetting.get() && mc.player.getInventory().getItem(36 + 2).getItem() == Items.ELYTRA
                && this.closeByElytra.get()) {
                this.surroundSetting.set(false);

                this.info(Component.literal("检测到鞘翅，自动关闭围脚模式"));
            }

            int slot = BagUtil.findItemInventorySlot(Items.OBSIDIAN);
            if (slot == -1) {
                this.placePositions.clear();
            } else {
                this.findPlacePos(Blocks.OBSIDIAN);
                if (this.placePositions.contains(mc.player.blockPosition().below()) && this.bottomFirst.get()) {
                    BagUtil.doSwap(slot);
                    BaritoneUtil.placeBlock(mc.player.blockPosition().below(), true, true, true);
                    BagUtil.doSwap(slot);
                }

                if (this.placeDelay.passed(this.delaySetting.get().intValue()) && this.placePositions.size() > 0) {
                    int nums = Math.min(this.bptimes.get(), this.placePositions.size());

                    for (int i = 0; i < nums; i++) {
                        BagUtil.doSwap(slot);

                        BaritoneUtil.placeBlock(this.placePositions.get(i), true, true, true);
                        BagUtil.doSwap(slot);

                        mc.getConnection().send(new ServerboundContainerClosePacket(mc.player.containerMenu.containerId));
                    }

                    this.placeDelay.reset();
                }

                if (this.breakPositions.size() > 0 && this.placeDelay.passed(this.delaySetting.get().intValue()) && slot != -1) {
                    this.breakPositions = this.breakPositions
                        .stream()
                        .filter(
                            p -> mc.level.getBlockState(p).getBlock().asItem() == Items.AIR
                                && BaritoneUtil.canPlace(p, true)
                                && PlayerUtils.distanceTo(p) <= 4
                        )
                        .collect(Collectors.toList());
                    int nums = Math.min(this.bptimes.get(), this.breakPositions.size());

                    for (int i = 0; i < nums; i++) {
                        BlockPos pos = this.breakPositions.get(i);

                        // Skip if block is not replaceable or cannot place obsidian
                        if (!mc.level.getBlockState(pos).canBeReplaced()
                            || !mc.level.isUnobstructed(Blocks.OBSIDIAN.defaultBlockState(), pos, CollisionContext.empty())) {
                            continue;
                        }

                        // Check adjacent blocks for specific types (any one NOT matching triggers placement)
                        boolean hasInvalidSupport =
                            !MikuUtil.isBlockAt(pos.below(), Blocks.OBSIDIAN) &&
                                !MikuUtil.isBlockAt(pos.east(), Blocks.CRYING_OBSIDIAN) &&
                                !MikuUtil.isBlockAt(pos.west(), Blocks.BEDROCK) &&
                                !MikuUtil.isBlockAt(pos.south(), Blocks.ENDER_CHEST) &&
                                !MikuUtil.isBlockAt(pos.north(), Blocks.ANVIL);

                        if (hasInvalidSupport) {
                            BagUtil.doSwap(slot);
                            BaritoneUtil.placeBlock(pos, true, true, true);
                            BagUtil.doSwap(slot); // restore original slot
                            mc.getConnection().send(
                                new ServerboundContainerClosePacket(mc.player.containerMenu.containerId)
                            );
                        }
                    }
                }
            }
        }
    }

    @EventHandler
    public void onPacketReceive(PacketEvent.Receive receive) {
        if (receive.packet instanceof ClientboundPlayerPositionPacket && this.closeByChorus.get() && this.isEatingChorus && this.surroundSetting.get()) {
            this.surroundSetting.set(false);
            this.info("检测到服务器传送，关闭围脚模式");
        }
    }

    private boolean isSurroundBlock(BlockPos pos) {
        return this.getSurroundPosList(this.breakTypeSetting.get()).contains(pos);
    }

    private List<BlockPos> getSurroundPosList(SurroundType type) {
        BlockPos playerPos = mc.player.blockPosition();
        List<BlockPos> result = new ArrayList<>();
        if (type == SurroundType.正常) {
            result.add(playerPos.offset(1, 0, 0));
            result.add(playerPos.offset(-1, 0, 0));
            result.add(playerPos.offset(0, 0, -1));
            result.add(playerPos.offset(0, 0, 1));
        }

        if (type == SurroundType.害怕) {
            result.add(playerPos.offset(1, 0, 0));
            result.add(playerPos.offset(1, 0, 1));
            result.add(playerPos.offset(-1, 0, 0));
            result.add(playerPos.offset(-1, 0, 1));
            result.add(playerPos.offset(0, 0, 1));
            result.add(playerPos.offset(1, 0, -1));
            result.add(playerPos.offset(0, 0, -1));
            result.add(playerPos.offset(-1, 0, -1));
            result.add(playerPos.offset(2, 0, 0));
            result.add(playerPos.offset(-2, 0, 0));
            result.add(playerPos.offset(0, 0, 2));
            result.add(playerPos.offset(0, 0, -2));
        }

        if (type == SurroundType.恐惧) {
            result.add(playerPos.offset(1, 0, 0));
            result.add(playerPos.offset(1, 0, 1));
            result.add(playerPos.offset(-1, 0, 0));
            result.add(playerPos.offset(-1, 0, 1));
            result.add(playerPos.offset(0, 0, 1));
            result.add(playerPos.offset(1, 0, -1));
            result.add(playerPos.offset(0, 0, -1));
            result.add(playerPos.offset(-1, 0, -1));
            result.add(playerPos.offset(2, 0, 0));
            result.add(playerPos.offset(-2, 0, 0));
            result.add(playerPos.offset(0, 0, 2));
            result.add(playerPos.offset(0, 0, -2));
            result.add(playerPos.offset(1, 1, 0));
            result.add(playerPos.offset(-1, 1, 0));
            result.add(playerPos.offset(0, 1, 1));
            result.add(playerPos.offset(0, 1, -1));
            result.add(playerPos.offset(2, 1, 0));
            result.add(playerPos.offset(-2, 1, 0));
            result.add(playerPos.offset(0, 1, 2));
            result.add(playerPos.offset(0, 1, -2));
            result.add(playerPos.offset(-1, 1, 1));
            result.add(playerPos.offset(1, 1, -1));
            result.add(playerPos.offset(1, 1, 1));
            result.add(playerPos.offset(-1, 1, -1));
        }

        return result;
    }

    @EventHandler
    private void onKeyEvent(KeyInputEvent event) {
        if (event.action != KeyAction.Press) {
            return;
        }

        Keybind breakBind = this.breakCrystalBind.get();
        Keybind surroundBind = this.surroundBind.get();

        if (breakBind != Keybind.none() && Via.getKeyEventKey(event) == breakBind.getValue()) {
            toggleSetting(
                this.breakCrystalSetting,
                "围脚++自动破坏水晶：",
                "已启用",
                "已禁用"
            );
        } else if (surroundBind != Keybind.none() && Via.getKeyEventKey(event) == surroundBind.getValue()) {
            toggleSetting(
                this.surroundSetting,
                "围脚++方块围脚：",
                "已启用",
                "已禁用"
            );
        }
    }

    private void toggleSetting(Setting<Boolean> setting, String prefix, String enableText, String disableText) {
        boolean newValue = !setting.get();
        setting.set(newValue);
        String status = newValue ? enableText : disableText;
        this.info(Component.literal(prefix + status));
    }

    private void findPlacePos(Block block) {
        this.placePositions.clear();
        BlockPos pos = mc.player.blockPosition();
        if (this.surroundSetting.get()) {
            if (this.Top.get()) {
                this.add(pos.offset(0, 2, 0), block);
                this.add(pos.offset(1, 2, 0), block);
                this.add(pos.offset(-1, 2, 0), block);
                this.add(pos.offset(0, 2, 1), block);
                this.add(pos.offset(0, 2, -1), block);
            }

            if (!this.TopOnly.get()) {
                switch (this.surroundType.get()) {
                    case 正常:
                        this.add(pos.offset(1, 0, 0), block);
                        this.add(pos.offset(-1, 0, 0), block);
                        this.add(pos.offset(0, 0, 1), block);
                        this.add(pos.offset(0, 0, -1), block);
                        break;
                    case 害怕:
                        this.add(pos.offset(1, 0, 0), block);
                        this.add(pos.offset(1, 0, 1), block);
                        this.add(pos.offset(-1, 0, 0), block);
                        this.add(pos.offset(-1, 0, 1), block);
                        this.add(pos.offset(0, 0, 1), block);
                        this.add(pos.offset(1, 0, -1), block);
                        this.add(pos.offset(0, 0, -1), block);
                        this.add(pos.offset(-1, 0, -1), block);
                        this.add(pos.offset(2, 0, 0), block);
                        this.add(pos.offset(-2, 0, 0), block);
                        this.add(pos.offset(0, 0, 2), block);
                        this.add(pos.offset(0, 0, -2), block);
                        break;
                    case 恐惧:
                        this.add(pos.offset(1, 0, 0), block);
                        this.add(pos.offset(1, 0, 1), block);
                        this.add(pos.offset(-1, 0, 0), block);
                        this.add(pos.offset(-1, 0, 1), block);
                        this.add(pos.offset(0, 0, 1), block);
                        this.add(pos.offset(1, 0, -1), block);
                        this.add(pos.offset(0, 0, -1), block);
                        this.add(pos.offset(-1, 0, -1), block);
                        this.add(pos.offset(2, 0, 0), block);
                        this.add(pos.offset(-2, 0, 0), block);
                        this.add(pos.offset(0, 0, 2), block);
                        this.add(pos.offset(0, 0, -2), block);
                        this.add(pos.offset(1, 1, 0), block);
                        this.add(pos.offset(-1, 1, 0), block);
                        this.add(pos.offset(0, 1, 1), block);
                        this.add(pos.offset(0, 1, -1), block);
                        this.add(pos.offset(2, 1, 0), block);
                        this.add(pos.offset(-2, 1, 0), block);
                        this.add(pos.offset(0, 1, 2), block);
                        this.add(pos.offset(0, 1, -2), block);
                        this.add(pos.offset(-1, 1, 1), block);
                        this.add(pos.offset(1, 1, -1), block);
                        this.add(pos.offset(1, 1, 1), block);
                        this.add(pos.offset(-1, 1, -1), block);
                }
            }

            this.add(pos.offset(0, -1, 0), block);
        }

        if (this.breakCrystalSetting.get()) {
            if (this.closeByElytra.get() && mc.player.getInventory().getItem(36 + 2).getItem() == Items.ELYTRA) {
                return;
            }

            Iterator iterator = mc.level.entitiesForRendering().iterator();
            FindItemResult itemResult = InvUtils.findInHotbar(Items.OBSIDIAN);
            if (!itemResult.found()) {
                return;
            }

            while (iterator.hasNext()) {
                Entity entity = (Entity) iterator.next();
                if (entity instanceof EndCrystal && PlayerUtils.distanceTo(entity) <= 5) {
                    if (this.breakTypeSetting.get() == SurroundType.正常
                        && (
                        entity.blockPosition().equals(pos.offset(-1, 0, 0))
                            || entity.blockPosition().equals(pos.offset(1, 0, 0))
                            || entity.blockPosition().equals(pos.offset(0, 0, 1))
                            || entity.blockPosition().equals(pos.offset(0, 0, -1))
                    )
                        && this.breakDelay.passed(this.breakSetting.get().intValue())) {
                        BlockPos entityBlockPos = entity.blockPosition();
                        RotationManager.getInstance().register(new Rotation((float) Rotations.getYaw(entityBlockPos), (float) Rotations.getPitch(entityBlockPos)), humanized.profile());

                        mc.gameMode.attack(mc.player, entity);
                        //fixme via
//                        mc.player.resetLastAttackedTicks();
                        mc.player.swing(InteractionHand.MAIN_HAND);
                        RotationManager.getInstance().sync();
                        this.breakDelay.reset();
                        if (!this.breakPositions.contains(entity.blockPosition())) {
                            this.breakPositions.add(entity.blockPosition());
                        }
                    }

                    if (this.breakTypeSetting.get() == SurroundType.害怕
                        && (
                        entity.blockPosition().equals(pos.offset(2, 0, 0))
                            || entity.blockPosition().equals(pos.offset(-2, 0, 0))
                            || entity.blockPosition().equals(pos.offset(0, 0, 2))
                            || entity.blockPosition().equals(pos.offset(0, 0, -2))
                            || entity.blockPosition().equals(pos.offset(1, 0, 1))
                            || entity.blockPosition().equals(pos.offset(-1, 0, 1))
                            || entity.blockPosition().equals(pos.offset(1, 0, -1))
                            || entity.blockPosition().equals(pos.offset(-1, 0, 0))
                            || entity.blockPosition().equals(pos.offset(1, 0, 0))
                            || entity.blockPosition().equals(pos.offset(0, 0, 1))
                            || entity.blockPosition().equals(pos.offset(0, 0, -1))
                    )
                        && this.breakDelay.passed(this.breakSetting.get().intValue())) {
                        BlockPos entityBlockPos = entity.blockPosition();
                        RotationManager.getInstance().register(new Rotation((float) Rotations.getYaw(entityBlockPos), (float) Rotations.getPitch(entityBlockPos)), humanized.profile());
                        mc.gameMode.attack(mc.player, entity);
                        //fixme via
//                        mc.player.resetLastAttackedTicks();
                        mc.player.swing(InteractionHand.MAIN_HAND);
                        RotationManager.getInstance().sync();
                        this.breakDelay.reset();
                        if (!this.breakPositions.contains(entity.blockPosition())) {
                            this.breakPositions.add(entity.blockPosition());
                        }
                    }

                    if (this.breakTypeSetting.get() == SurroundType.恐惧
                        && (
                        entity.blockPosition().equals(pos.offset(2, 0, 0))
                            || entity.blockPosition().equals(pos.offset(-2, 0, 0))
                            || entity.blockPosition().equals(pos.offset(0, 0, 2))
                            || entity.blockPosition().equals(pos.offset(0, 0, -2))
                            || entity.blockPosition().equals(pos.offset(1, 0, 1))
                            || entity.blockPosition().equals(pos.offset(-1, 0, 1))
                            || entity.blockPosition().equals(pos.offset(1, 0, -1))
                            || entity.blockPosition().equals(pos.offset(-1, 0, 0))
                            || entity.blockPosition().equals(pos.offset(1, 0, 0))
                            || entity.blockPosition().equals(pos.offset(0, 0, 1))
                            || entity.blockPosition().equals(pos.offset(0, 0, -1))
                            || entity.blockPosition().equals(pos.offset(-1, 0, -1))
                            || entity.blockPosition().equals(pos.offset(-1, 1, 0))
                            || entity.blockPosition().equals(pos.offset(1, 1, 0))
                            || entity.blockPosition().equals(pos.offset(0, 1, 1))
                            || entity.blockPosition().equals(pos.offset(0, 1, -1))
                            || entity.blockPosition().equals(pos.offset(-2, 1, 0))
                            || entity.blockPosition().equals(pos.offset(2, 1, 0))
                            || entity.blockPosition().equals(pos.offset(0, 1, 2))
                            || entity.blockPosition().equals(pos.offset(0, 1, -2))
                            || entity.blockPosition().equals(pos.offset(1, 1, -1))
                            || entity.blockPosition().equals(pos.offset(1, 1, 1))
                            || entity.blockPosition().equals(pos.offset(-1, 1, -1))
                            || entity.blockPosition().equals(pos.offset(-1, 1, 1))
                            || entity.blockPosition().equals(pos.offset(-1, 2, 0))
                            || entity.blockPosition().equals(pos.offset(1, 2, 0))
                            || entity.blockPosition().equals(pos.offset(0, 2, 1))
                            || entity.blockPosition().equals(pos.offset(0, 2, -1))
                            || entity.blockPosition().equals(pos.offset(0, 3, 0))
                            || entity.blockPosition().equals(pos.offset(0, -1, 1))
                            || entity.blockPosition().equals(pos.offset(0, -1, -1))
                            || entity.blockPosition().equals(pos.offset(1, -1, 0))
                            || entity.blockPosition().equals(pos.offset(-1, -1, 0))
                    )
                        && this.breakDelay.passed(this.breakSetting.get().intValue())) {
                        if (entity.tickCount > this.waitTicks.get()) {
                            BlockPos entityBlockPos = entity.blockPosition();
                            RotationManager.getInstance().register(new Rotation((float) Rotations.getYaw(entityBlockPos), (float) Rotations.getPitch(entityBlockPos)), humanized.profile());
                            mc.gameMode.attack(mc.player, entity);
                            mc.player.swing(InteractionHand.MAIN_HAND);
                            RotationManager.getInstance().sync();
                            this.breakDelay.reset();
                        }

                        if (!this.breakPositions.contains(entity.blockPosition())) {
                            this.breakPositions.add(entity.blockPosition());
                        }
                    }
                }
            }
        }
    }

    private void add(BlockPos blockPos, Block block) {
        if (!this.placePositions.contains(blockPos)
            && mc.level.getBlockState(blockPos).canBeReplaced()
            && mc.level.isUnobstructed(block.defaultBlockState(), blockPos, CollisionContext.empty())
            && BaritoneUtil.canPlace(blockPos, true)) {
            if ((this.surroundType.get() == SurroundType.恐惧
                || this.surroundType.get() == SurroundType.害怕)
                && mc.level
                .getBlockState(blockPos.offset(0, -1, 0))
                .getBlock()
                .getName()
                .getString()
                .equals("air")) {
                return;
            }

            this.placePositions.add(blockPos);
        }
    }

    public enum SurroundType {
        正常,
        害怕,
        恐惧
    }
}
