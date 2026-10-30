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

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.mixininterface.IMultiPlayerGameMode;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.SlotUtils;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.InteractionHand;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public class BagUtil {
    private static final Action ACTION = new Action();
    public static int previousSlot = -1;
    static int lastSlot = -1;
    static int lastSelect = -1;

    private BagUtil() {
    }

    public static void doSwap(int slot) {
        inventorySwap(slot, Via.getSelectedSlot());
    }

    public static void doSwapOnTruth(int slot) {
        inventorySwap(slot, Via.getSelectedSlot());
    }

    public static void doSwapOffHand(int slot) {
        inventorySwapAtTruth(slot, 45);
    }

    public static void inventorySwap(int slot, int selectedSlot) {
        if (slot == lastSlot) {
            switchToSlot(lastSelect);
            lastSlot = -1;
            lastSelect = -1;
        } else if (slot - 36 != selectedSlot) {

            MeteorClient.mc
                .gameMode
                .handleContainerInput(MeteorClient.mc.player.containerMenu.containerId, slot, selectedSlot, ContainerInput.SWAP, MeteorClient.mc.player);
        }
    }

    public static void inventorySwapAtTruth(int solt, int slotButton) {
        MeteorClient.mc
            .gameMode
            .handleContainerInput(MeteorClient.mc.player.containerMenu.containerId, SlotUtils.indexToId(solt), 0, ContainerInput.PICKUP, MeteorClient.mc.player);
        MeteorClient.mc
            .gameMode
            .handleContainerInput(MeteorClient.mc.player.containerMenu.containerId, slotButton, 0, ContainerInput.PICKUP, MeteorClient.mc.player);
        MeteorClient.mc
            .gameMode
            .handleContainerInput(MeteorClient.mc.player.containerMenu.containerId, SlotUtils.indexToId(solt), 0, ContainerInput.PICKUP, MeteorClient.mc.player);
        Via.sendReleaseShift();
    }

    public static void sync() {
        MeteorClient.mc.player.connection.send(new ServerboundContainerClosePacket(MeteorClient.mc.player.containerMenu.containerId));
    }


    public static void quickUse(Item item) {
        FindItemResult result = find(item);
        if (result.found()) {
            int selectedSlot = Via.getSelectedSlot();
            int itemSlot = result.slot();
            boolean wasHeld = result.isMainHand();
            if (!wasHeld) {
                quickSwap().fromId(selectedSlot).to(itemSlot);
            }

            MeteorClient.mc.gameMode.useItem(MeteorClient.mc.player, InteractionHand.MAIN_HAND);
            if (!wasHeld) {
                quickSwap().fromId(selectedSlot).to(itemSlot);
            }
        }
    }

    public static int findBlockInventorySlotGrim(Block block) {
        return findItemInventorySlot(Item.byBlock(block));
    }

    public static int findItemInventorySlotGrim(Item item) {
        return findItemInventorySlot(item);
    }

    public static int findItemInventorySlot(Item item) {
        Predicate<ItemStack> isGood = stack -> stack.getItem() == item;
        return findItemInventorySlot(isGood);
    }

    public static int findItemInventorySlot(Predicate<ItemStack> isGood) {
        for (int i = 0; i < 45; i++) {
            ItemStack stack = MeteorClient.mc.player.getInventory().getItem(i);
            if (isGood.test(stack)) {
                return i < 9 ? i + 36 : i;
            }
        }

        return -1;
    }

    public static int findItemInvSlotGrim(Item item) {
        for (int i = 9; i < 45; i++) {
            ItemStack stack = MeteorClient.mc.player.getInventory().getItem(i);
            if (stack.getItem() == item) {
                return i;
            }
        }

        return -1;
    }

    public static int findItemHotBarInventorySlotGrim(Item item) {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = MeteorClient.mc.player.getInventory().getItem(i);
            if (stack.getItem() == item) {
                return i + 36;
            }
        }

        return -1;
    }

    public static int findItemHotBarInventorySlotGrim(Predicate<ItemStack> isGood) {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = MeteorClient.mc.player.getInventory().getItem(i);
            if (isGood.test(stack)) {
                return i + 36;
            }
        }

        return -1;
    }

    public static int findClassInventorySlotGrim(Class clazz) {
        for (int i = 0; i < 45; i++) {
            ItemStack stack = MeteorClient.mc.player.getInventory().getItem(i);
            if (stack != ItemStack.EMPTY) {
                if (clazz.isInstance(stack.getItem())) {
                    return i < 9 ? i + 36 : i;
                }

                if (stack.getItem() instanceof BlockItem && clazz.isInstance(((BlockItem) stack.getItem()).getBlock())) {
                    return i < 9 ? i + 36 : i;
                }
            }
        }

        return -1;
    }

    public static int findKeyWordInventorySlotGrim(String KeyWord) {
        for (int i = 0; i < 45; i++) {
            ItemStack stack = MeteorClient.mc.player.getInventory().getItem(i);
            if (stack != ItemStack.EMPTY) {
                if (stack.getItem().getDescriptionId().contains(KeyWord)) {
                    return i < 9 ? i + 36 : i;
                }

                if (stack.getItem() instanceof BlockItem) {
                    return i < 9 ? i + 36 : i;
                }
            }
        }

        return -1;
    }

    public static boolean testInMainHand(Predicate<ItemStack> predicate) {
        return predicate.test(MeteorClient.mc.player.getMainHandItem());
    }

    public static boolean testInMainHand(Item... items) {
        return testInMainHand(itemStack -> {
            for (Item item : items) {
                if (itemStack.is(item)) {
                    return true;
                }
            }

            return false;
        });
    }

    public static boolean testInOffHand(Predicate<ItemStack> predicate) {
        return predicate.test(MeteorClient.mc.player.getOffhandItem());
    }

    public static boolean testInOffHand(Item... items) {
        return testInOffHand(itemStack -> {
            for (Item item : items) {
                if (itemStack.is(item)) {
                    return true;
                }
            }

            return false;
        });
    }

    public static boolean testInHands(Predicate<ItemStack> predicate) {
        return testInMainHand(predicate) || testInOffHand(predicate);
    }

    public static boolean testInHands(Item... items) {
        return testInMainHand(items) || testInOffHand(items);
    }

    public static boolean testInHotbar(Predicate<ItemStack> predicate) {
        if (testInHands(predicate)) {
            return true;
        } else {
            for (int i = 0; i < 8; i++) {
                ItemStack stack = MeteorClient.mc.player.getInventory().getItem(i);
                if (predicate.test(stack)) {
                    return true;
                }
            }

            return false;
        }
    }

    public static boolean testInHotbar(Item... items) {
        return testInHotbar(itemStack -> {
            for (Item item : items) {
                if (itemStack.is(item)) {
                    return true;
                }
            }

            return false;
        });
    }

    public static FindItemResult findEmpty() {
        return find(ItemStack::isEmpty);
    }

    public static FindItemResult findInHotbar(Item... items) {
        return findInHotbar(itemStack -> {
            for (Item item : items) {
                if (itemStack.getItem() == item) {
                    return true;
                }
            }

            return false;
        });
    }

    public static FindItemResult findInHotbar(boolean offHand, Item... items) {
        Predicate<ItemStack> isGood = itemStack -> {
            for (Item item : items) {
                if (itemStack.getItem() == item) {
                    return true;
                }
            }

            return false;
        };
        if (offHand) {
            return findInHotbar(isGood);
        } else {
            return testInMainHand(isGood)
                ? new FindItemResult(Via.getSelectedSlot(), MeteorClient.mc.player.getMainHandItem().getCount())
                : find(isGood, 0, 8);
        }
    }

    public static FindItemResult findElseInHotbar(Item item) {
        return find(itemStack -> itemStack.getItem() != item, 0, 8);
    }

    public static FindItemResult findInINV(Item item) {
        return find(itemStack -> itemStack.getItem() != item, 9, 35);
    }

    public static FindItemResult findInHotbar(Predicate<ItemStack> isGood) {
        if (testInOffHand(isGood)) {
            return new FindItemResult(45, MeteorClient.mc.player.getOffhandItem().getCount());
        } else {
            return testInMainHand(isGood)
                ? new FindItemResult(Via.getSelectedSlot(), MeteorClient.mc.player.getMainHandItem().getCount())
                : find(isGood, 0, 8);
        }
    }

    public static FindItemResult find(Item... items) {
        return find(itemStack -> {
            for (Item item : items) {
                if (itemStack.getItem() == item) {
                    return true;
                }
            }

            return false;
        });
    }

    public static List<Integer> findSolts(Item item) {
        List<Integer> result = new ArrayList<>();
        if (!find(item).found()) {
            return result;
        } else {
            for (int i = 0; i < 36; i++) {
                if (MeteorClient.mc.player.getInventory().getItem(i).getItem() == item) {
                    result.add(i);
                }
            }

            return result;
        }
    }

    public static List<Integer> findSoltsByItemClass(Class itemClass) {
        List<Integer> result = new ArrayList<>();

        for (int i = 0; i < 36; i++) {
            if (itemClass.isInstance(MeteorClient.mc.player.getInventory().getItem(i).getItem())) {
                result.add(i);
            }
        }

        return result;
    }

    public static List<Integer> findSolts(Predicate<ItemStack> isGood) {
        List<Integer> result = new ArrayList<>();

        for (int i = 0; i < 36; i++) {
            if (isGood.test(MeteorClient.mc.player.getInventory().getItem(i))) {
                result.add(i);
            }
        }

        return result;
    }

    public static int findSoltByItemClass(Class itemClass) {
        for (int i = 0; i < 36; i++) {
            if (itemClass.isInstance(MeteorClient.mc.player.getInventory().getItem(i).getItem())) {
                return i;
            }
        }

        return -1;
    }

    public static int findSoltByBlockClass(Class blockClass) {
        for (int i = 0; i < 36; i++) {
            ItemStack stack = MeteorClient.mc.player.getInventory().getItem(i);
            if (!stack.isEmpty()
                && stack.getItem() instanceof BlockItem
                && blockClass.isInstance(((BlockItem) MeteorClient.mc.player.getInventory().getItem(i).getItem()).getBlock())) {
                return i;
            }
        }

        return -1;
    }

    public static FindItemResult find(Predicate<ItemStack> isGood) {
        return MeteorClient.mc.player == null ? new FindItemResult(0, 0) : find(isGood, 0, MeteorClient.mc.player.getInventory().getContainerSize());
    }

    public static FindItemResult find(Predicate<ItemStack> isGood, int start, int end) {
        if (MeteorClient.mc.player == null) {
            return new FindItemResult(0, 0);
        } else {
            int slot = -1;
            int count = 0;

            for (int i = start; i <= end; i++) {
                ItemStack stack = MeteorClient.mc.player.getInventory().getItem(i);
                if (isGood.test(stack)) {
                    if (slot == -1) {
                        slot = i;
                    }

                    count += stack.getCount();
                }
            }

            return new FindItemResult(slot, count);
        }
    }

    public static FindItemResult findFastestTool(BlockState state) {
        float bestScore = 1.0F;
        int slot = -1;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = MeteorClient.mc.player.getInventory().getItem(i);
            if (stack.isCorrectToolForDrops(state)) {
                float score = stack.getDestroySpeed(state);
                if (score > bestScore) {
                    bestScore = score;
                    slot = i;
                }
            }
        }

        return new FindItemResult(slot, 1);
    }

    public static boolean swap(int slot, boolean swapBack) {
        if (slot == 45) {
            return true;
        } else if (slot >= 0 && slot <= 8) {
            if (swapBack && previousSlot == -1) {
                previousSlot = Via.getSelectedSlot();
            } else if (!swapBack) {
                previousSlot = -1;
            }

            Via.setSelectedSlot(slot);
            ((IMultiPlayerGameMode) MeteorClient.mc.gameMode).meteor$syncSelected();
            return true;
        } else {
            return false;
        }
    }

    public static boolean swapBack() {
        if (previousSlot == -1) {
            return false;
        } else {
            boolean return_ = swap(previousSlot, false);
            previousSlot = -1;
            return return_;
        }
    }

    public static Action move() {
        ACTION.type = ContainerInput.PICKUP;
        ACTION.two = true;
        return ACTION;
    }

    public static Action click() {
        ACTION.type = ContainerInput.PICKUP;
        return ACTION;
    }

    public static Action quickSwap() {
        ACTION.type = ContainerInput.SWAP;
        return ACTION;
    }

    public static Action shiftClick() {
        ACTION.type = ContainerInput.QUICK_MOVE;
        return ACTION;
    }

    public static Action drop() {
        ACTION.type = ContainerInput.THROW;
        ACTION.data = 1;
        return ACTION;
    }

    public static void dropHand() {
        if (!MeteorClient.mc.player.containerMenu.getCarried().isEmpty()) {
            MeteorClient.mc
                .gameMode
                .handleContainerInput(MeteorClient.mc.player.containerMenu.containerId, -999, 0, ContainerInput.PICKUP, MeteorClient.mc.player);
        }
    }

    public static void switchToSlot(int slot) {
        Via.setSelectedSlot(slot);
        MeteorClient.mc.getConnection().send(new ServerboundSetCarriedItemPacket(slot));
    }


    public static class Action {
        private ContainerInput type = null;
        private boolean two = false;
        private int from = -1;
        private int to = -1;
        private int data = 0;
        private boolean isRecursive = false;

        private Action() {
        }

        public Action fromId(int id) {
            this.from = id;
            return this;
        }

        public Action from(int index) {
            return this.fromId(SlotUtils.indexToId(index));
        }

        public Action fromHotbar(int i) {
            return this.from(0 + i);
        }

        public Action fromOffhand() {
            return this.from(45);
        }

        public Action fromMain(int i) {
            return this.from(9 + i);
        }

        public Action fromArmor(int i) {
            return this.from(36 + (3 - i));
        }

        public void toId(int id) {
            this.to = id;
            this.run();
        }

        public void to(int index) {
            this.toId(SlotUtils.indexToId(index));
        }

        public void toHotbar(int i) {
            this.to(0 + i);
        }

        public void toOffhand() {
            this.to(45);
        }

        public void toMain(int i) {
            this.to(9 + i);
        }

        public void toArmor(int i) {
            this.to(36 + (3 - i));
        }

        public void slotId(int id) {
            this.from = this.to = id;
            this.run();
        }

        public void slot(int index) {
            this.slotId(SlotUtils.indexToId(index));
        }

        public void slotHotbar(int i) {
            this.slot(0 + i);
        }

        public void slotOffhand() {
            this.slot(45);
        }

        public void slotMain(int i) {
            this.slot(9 + i);
        }

        public void slotArmor(int i) {
            this.slot(36 + (3 - i));
        }

        private void run() {
            boolean hadEmptyCursor = MeteorClient.mc.player.containerMenu.getCarried().isEmpty();
            if (this.type == ContainerInput.SWAP) {
                this.data = this.from;
                this.from = this.to;
            }

            if (this.type != null && this.from != -1 && this.to != -1) {
                this.click(this.from);
                if (this.two) {
                    this.click(this.to);
                }
            }

            ContainerInput preType = this.type;
            boolean preTwo = this.two;
            int preFrom = this.from;
            int preTo = this.to;
            this.type = null;
            this.two = false;
            this.from = -1;
            this.to = -1;
            this.data = 0;
            if (!this.isRecursive
                && hadEmptyCursor
                && preType == ContainerInput.PICKUP
                && preTwo
                && preFrom != -1
                && preTo != -1
                && !MeteorClient.mc.player.containerMenu.getCarried().isEmpty()) {
                this.isRecursive = true;
                BagUtil.click().slotId(preFrom);
                this.isRecursive = false;
            }
        }

        private void click(int id) {
            MeteorClient.mc.gameMode.handleContainerInput(MeteorClient.mc.player.containerMenu.containerId, id, this.data, this.type, MeteorClient.mc.player);
        }
    }
}
