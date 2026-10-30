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
import com.github.mikumiku.addon.util.BaritoneUtil;
import com.github.mikumiku.addon.util.MikuRegistries;
import com.github.mikumiku.addon.util.Via;
import com.github.mikumiku.addon.util.MikuCompat;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.packets.InventoryEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.misc.InventoryTweaks;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.SlotUtils;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.InteractionHand;
import net.minecraft.core.NonNullList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

public class ChestAura extends BaseModule {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("范围")
        .description("交互范围")
        .defaultValue(4)
        .min(0)
        .build()
    );

    private final Setting<List<BlockEntityType<?>>> blocks = sgGeneral.add(new StorageBlockListSetting.Builder()
        .name("方块类型")
        .description("要打开的方块类型")
        // 26.2 移除了部分注册表静态字段，默认值改为按注册名解析，避免初始化时崩溃。
        .defaultValue(MikuRegistries.blockEntities("chest", "shulker_box"))
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("延迟")
        .description("打开箱子之间的延迟")
        .defaultValue(5)
        .sliderMax(40)
        .build()
    );

    private final Setting<Integer> forget = sgGeneral.add(new IntSetting.Builder()
        .name("遗忘时间")
        .description("等待多少tick后遗忘已打开的箱子。0表示永不遗忘")
        .defaultValue(0)
        .min(0)
        .sliderMax(3600)
        .build()
    );

    private final Setting<CloseCondition> closeCondition = sgGeneral.add(new EnumSetting.Builder<CloseCondition>()
        .name("关闭条件")
        .defaultValue(CloseCondition.始终关闭)
        .description("何时关闭箱子界面")
        .build()
    );

    private final Setting<Boolean> dropAll = sgGeneral.add(new BoolSetting.Builder()
        .name("全部丢出")
        .description("打开箱子后无延迟丢出全部物品")
        .defaultValue(false)
        .build()
    );

    private final Map<BlockPos, Integer> openedBlocks = new HashMap<>();
    private final CloseListener closeListener = new CloseListener();

    private int timer = 0;

    public ChestAura() {
        super("翻箱倒柜", "自动开箱");
    }

    @Override
    public void onActivate() {
        timer = 0;
        openedBlocks.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (forget.get() != 0) {
            for (Map.Entry<BlockPos, Integer> e : new HashMap<>(openedBlocks).entrySet()) {
                int time = e.getValue();
                if (time > forget.get()) openedBlocks.remove(e.getKey());
                else openedBlocks.replace(e.getKey(), time + 1);
            }
        }

        if (timer > 0 && MikuCompat.screen() != null) return;

        for (BlockEntity block : Utils.blockEntities()) {
            if (!blocks.get().contains(block.getType())) continue;
            if (mc.player.getEyePosition().distanceTo(Vec3.upFromBottomCenterOf(block.getBlockPos(), block.getBlockPos().getY())) >= range.get()) continue;

            BlockPos pos = block.getBlockPos();
            if (openedBlocks.containsKey(pos)) continue;

            Direction side = BaritoneUtil.getInteractDirection(pos, true);
            if (side == null) {
                side = Direction.UP;
            }

            BaritoneUtil.clickBlock(pos, side, true, InteractionHand.MAIN_HAND, BaritoneUtil.SwingSide.All);

            // Double chest compatibility
            BlockState state = block.getBlockState();
            if (state.hasProperty(ChestBlock.TYPE)) {
                Direction direction = state.getValue(ChestBlock.FACING);
                switch (state.getValue(ChestBlock.TYPE)) {
                    case LEFT -> openedBlocks.put(pos.relative(direction.getClockWise()), 0);
                    case RIGHT -> openedBlocks.put(pos.relative(direction.getCounterClockWise()), 0);
                }
            }

            openedBlocks.put(pos, 0);
            timer = delay.get();
            MeteorClient.EVENT_BUS.subscribe(closeListener);
            break;
        }
        timer--;
    }

    public class CloseListener {
        @EventHandler(priority = EventPriority.HIGH)
        private void onInventory(InventoryEvent event) {
            AbstractContainerMenu handler = mc.player.containerMenu;

            if (Via.getSyncId(event.packet) == handler.containerId) {
                // 如果启用全部丢出功能，先丢出所有物品
                if (dropAll.get()) {
                    dropAllItems(handler);
                }

                switch (closeCondition.get()) {
                    case 空时关闭 -> {
                        NonNullList<ItemStack> stacks = NonNullList.create();
                        IntStream.range(0, SlotUtils.indexToId(SlotUtils.MAIN_START)).mapToObj(handler.slots::get).map(Slot::getItem).forEach(stacks::add);
                        if (stacks.stream().allMatch(ItemStack::isEmpty)) mc.player.closeContainer();
                    }
                    case 始终关闭 -> mc.player.closeContainer();
                    case 偷窃后关闭 -> Modules.get().get(InventoryTweaks.class);
                }
            }
            MeteorClient.EVENT_BUS.unsubscribe(this);
        }

        /**
         * 丢出箱子中的所有物品
         */
        private void dropAllItems(AbstractContainerMenu handler) {
            // 获取箱子的物品槽位（通常是前27个槽位）
            int inventorySize = handler.slots.size();
            int chestSize = Math.min(inventorySize, 54); // 最多54个槽位（双大箱子）

            // 遍历箱子中的所有物品槽位
            for (int i = 0; i < chestSize; i++) {
                Slot slot = handler.slots.get(i);
                ItemStack stack = slot.getItem();

                // 如果物品槽不为空，则丢出物品
                if (!stack.isEmpty()) {
                    // 使用鼠标点击丢出物品（Ctrl+点击丢出整个堆）
                    mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId, i, 0, ContainerInput.PICKUP, mc.player);
                    // 立即丢出
                    mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId, i, 1, ContainerInput.THROW, mc.player);
                }
            }
        }
    }

    public enum CloseCondition {
        始终关闭("始终关闭"),
        空时关闭("空时关闭"),
        偷窃后关闭("偷窃后关闭"),
        从不关闭("从不关闭");

        private final String displayName;

        CloseCondition(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }
}
