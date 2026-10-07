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
import com.github.mikumiku.addon.util.MikuCompat;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.world.BlockIterator;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.resources.Identifier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;

import java.util.concurrent.atomic.AtomicInteger;

public class AutoTouchFire extends BaseModule {
    private final SettingGroup sgGeneral = settings.createGroup("扑灭周围的火");
    private final SettingGroup sgBucket = settings.createGroup("扑灭身上的火");

    private final Setting<Boolean> extinguish = sgGeneral.add(new BoolSetting.Builder()
        .name("灭火")
        .description("自动扑灭你周围的火。")
        .defaultValue(true)
        .build()
    );
    private final Setting<Integer> horizontalRadius = sgGeneral.add(new IntSetting.Builder()
        .name("水平半径")
        .description("搜索火的水平半径。")
        .defaultValue(4)
        .min(0)
        .sliderMax(6)
        .build()
    );

    private final Setting<Integer> verticalRadius = sgGeneral.add(new IntSetting.Builder()
        .name("垂直半径")
        .description("搜索火的垂直半径。")
        .defaultValue(4)
        .min(0)
        .sliderMax(6)
        .build()
    );
    private final Setting<Integer> maxBlockPerTick = sgGeneral.add(new IntSetting.Builder()
        .name("数量")
        .description("每刻最多扑灭的方块数量。")
        .defaultValue(5)
        .min(1)
        .sliderMax(50)
        .build()
    );
    private final Setting<Boolean> waterBucket = sgBucket.add(new BoolSetting.Builder()
        .name("自动放水")
        .description("当你身上着火时（且没有抗火效果）自动放置水。")
        .defaultValue(false)
        .build()
    );
    private final Setting<Boolean> center = sgBucket.add(new BoolSetting.Builder()
        .name("居中放水")
        .description("放置水时自动将你居中。")
        .defaultValue(false)
        .build()
    );
    private final Setting<Boolean> onGround = sgBucket.add(new BoolSetting.Builder()
        .name("地面才水")
        .description("只有在地面上时才放置水。")
        .defaultValue(false)
        .build()
    );

    private boolean hasPlacedWater = false;
    private BlockPos blockPos = null;
    private boolean doesWaterBucketWork = true;

    private static final Holder<MobEffect> FIRE_RESISTANCE = BuiltInRegistries.MOB_EFFECT.get(Identifier.parse("fire_resistance")).orElse(null);

    public AutoTouchFire() {
        super(CATEGORY_MIKU_BUILD, "自动消防", "自动扑灭你周围的火");
    }

    @Override
    public void onActivate() {
        super.onActivate();

    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
//        if (mc.world.getDimension().respawnAnchorWorks()) {
//            if (doesWaterBucketWork) {
//                warning("水桶在此维度不起作用！");
//                doesWaterBucketWork = false;
//
//            }
//        } else
        if (!doesWaterBucketWork) {
            warning("启用水桶！");
            doesWaterBucketWork = true;
        }
        if (onGround.get() && !mc.player.onGround()) {
            return;
        }

        if (waterBucket.get() && doesWaterBucketWork) {
            if (hasPlacedWater) {
                final int slot = findSlot(Items.BUCKET);
                blockPos = mc.player.blockPosition();
                place(slot);
                hasPlacedWater = false;

            } else if (!mc.player.hasEffect(FIRE_RESISTANCE) && mc.player.isOnFire()) {
                blockPos = mc.player.blockPosition();
                final int slot = findSlot(Items.WATER_BUCKET);
                if (mc.level.getBlockState(blockPos).getBlock() == Blocks.FIRE || mc.level.getBlockState(blockPos).getBlock() == Blocks.SOUL_FIRE) {
                    float yaw = MikuCompat.mainCamera().yRot() % 360;
                    float pitch = MikuCompat.mainCamera().xRot() % 360;
                    if (center.get()) {
                        PlayerUtils.centerPlayer();
                    }
                    Rotations.rotate(yaw, 90);
                    mc.getConnection().send(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, blockPos, Direction.UP));
                    mc.player.swing(InteractionHand.MAIN_HAND);
                    mc.getConnection().send(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, blockPos, Direction.UP));

                    Rotations.rotate(yaw, pitch);
                }
                place(slot);
                hasPlacedWater = true;
            }
        }

        if (extinguish.get()) {
            AtomicInteger blocksPerTick = new AtomicInteger();
            BlockIterator.register(horizontalRadius.get(), verticalRadius.get(), (blockPos, blockState) -> {
                if (blocksPerTick.get() <= maxBlockPerTick.get()) {
                    if (blockState.getBlock() == Blocks.FIRE || mc.level.getBlockState(blockPos).getBlock() == Blocks.SOUL_FIRE) {
                        extinguishFire(blockPos);
                        blocksPerTick.getAndIncrement();
                    }
                }
            });
        }
    }

    private void place(int slot) {
        if (slot != -1) {
            final int preSlot = Via.getSelectedSlot();
            if (center.get()) {
                PlayerUtils.centerPlayer();
            }
            Via.setSelectedSlot(slot);
            float yaw = MikuCompat.mainCamera().yRot() % 360;
            float pitch = MikuCompat.mainCamera().xRot() % 360;

            Rotations.rotate(yaw, 90);
            mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
            Via.setSelectedSlot(preSlot);
            Rotations.rotate(yaw, pitch);

        }
    }

    private void extinguishFire(BlockPos blockPos) {
        mc.getConnection().send(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, blockPos, Direction.UP));
        mc.player.swing(InteractionHand.MAIN_HAND);
        mc.getConnection().send(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, blockPos, Direction.UP));
    }

    private int findSlot(Item item) {
        int slot = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack block = mc.player.getInventory().getItem(i);
            if (block.getItem() == item) {
                slot = i;
                break;
            }
        }

        return slot;
    }

}
