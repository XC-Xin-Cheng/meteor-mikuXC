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

import baritone.api.BaritoneAPI;
import baritone.api.Settings;
import baritone.api.pathing.goals.GoalBlock;
import com.github.mikumiku.addon.BaseModule;
import com.github.mikumiku.addon.util.ChatUtils;
import com.github.mikumiku.addon.util.MikuBlocks;
import com.github.mikumiku.addon.util.PositionCache;
import com.github.mikumiku.addon.util.timer.SyncedTickTimer;
import com.github.mikumiku.addon.util.timer.Timers;
import com.github.mikumiku.addon.util.MikuCompat;
import meteordevelopment.meteorclient.events.entity.player.InteractBlockEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.pathing.BaritoneUtils;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

import java.util.*;

public class AutoMiner extends BaseModule {

    // 工具类型枚举
    public enum ToolType {
        SHOVEL("铲子"),
        PICKAXE("镐子"),
        AXE("斧子"),
        HOE("锄头"),
        ;

        private final String displayName;

        ToolType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }


        @Override
        public String toString() {
            return displayName;
        }
    }

    // 设置组
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgShulkerBoxes = settings.createGroup("潜影盒设置");
    private final SettingGroup sgTools = settings.createGroup("工具管理");

    // 基本设置
    private final Setting<List<Block>> targetBlocks = sgGeneral.add(new BlockListSetting.Builder()
        .name("挖掘目标")
        .description("要挖掘的方块类型 多选")
        .defaultValue(Arrays.asList(
            Blocks.SAND
        ))
        .build()
    );

    // 基本设置
    private final Setting<Integer> miningRange = sgGeneral.add(new IntSetting.Builder()
        .name("挖掘范围")
        .description("搜索目标方块的范围")
        .defaultValue(32)
        .min(4)
        .max(200)
        .sliderMin(8)
        .sliderMax(200)
        .build()
    );


    private final Setting<Boolean> packetMine = sgGeneral.add(new BoolSetting.Builder()
        .name("使用极速包挖")
        .description("使用包挖乱挖周围目标方块，可能捡不到")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("延迟")
        .description("操作之间的延迟（tick）")
        .defaultValue(5)
        .min(0)
        .max(20)
        .sliderMin(0)
        .sliderMax(20)
        .build()
    );

    private final Setting<Boolean> autoReturn = sgGeneral.add(new BoolSetting.Builder()
        .name("自动返回")
        .description("完成存储或取工具后自动返回挖掘")
        .defaultValue(true)
        .build()
    );

    // 潜影盒设置
    private final Setting<Integer> shulkerSearchRadius = sgShulkerBoxes.add(new IntSetting.Builder()
        .name("潜影盒搜索半径")
        .description("搜索潜影盒的半径范围")
        .defaultValue(32)
        .min(4)
        .max(200)
        .sliderMin(8)
        .sliderMax(200)
        .build()
    );

    // 工具管理设置
    private final Setting<ToolType> toolType = sgTools.add(new EnumSetting.Builder<ToolType>()
        .name("工具类型")
        .description("选择使用的工具类型")
        .defaultValue(ToolType.SHOVEL)
        .build()
    );

    private final Setting<Integer> minDurability = sgTools.add(new IntSetting.Builder()
        .name("最低耐久度")
        .description("工具耐久度低于此值时更换")
        .defaultValue(10)
        .min(1)
        .max(100)
        .build()
    );

    // 状态变量
    private enum MinerState {
        WAITING_TOOL_SELECTION, // 等待用户选择工具潜影盒
        MINING,                 // 正在挖掘
        WAITING_FOR_TARGET_BOX,   // 等待目标潜影盒
        INVENTORY_FULL,         // 背包满了，需要存储
        TOOL_BROKEN,            // 工具坏了，需要更换
        GOING_TO_STORAGE,       // 前往存储潜影盒
        GOING_TO_TOOLS,         // 前往工具潜影盒
        STORING_ITEMS,          // 正在存储物品
        GETTING_TOOLS,          // 正在获取工具
        RETURNING               // 返回挖掘位置
    }

    private MinerState currentState = MinerState.WAITING_TOOL_SELECTION;
    private int tickTimer = 0;
    private SyncedTickTimer cacheClearTimer = Timers.tickTimer();
    private BlockPos lastMiningPos = null;
    private BlockPos currentTarget = null;
    private BlockPos toolShulkerPos = null; // 用户选择的工具潜影盒位置
    private Set<BlockPos> protectedShulkerBoxes = new HashSet<>(); // 受保护的潜影盒位置
    private int shulkerInteractionTimer = 0; // 潜影盒交互计时器
    private boolean waitingForShulkerOpen = false; // 等待潜影盒打开


    /**
     * 位置缓存管理器，支持自动过期清理
     */
    private final PositionCache positionCache = new PositionCache(5000L);

    public AutoMiner() {
        super(CATEGORY_MIKU_BUILD,"挖沙挖一切", "自动挖掘指定方块，支持背包管理和工具更换");
    }

    // 重置工具潜影盒选择的方法
    public void resetToolShulkerSelection() {
        toolShulkerPos = null;
        currentState = MinerState.WAITING_TOOL_SELECTION;
        ChatUtils.sendMsg("工具潜影盒选择已重置，请重新右键选择工具潜影盒！");
    }

    @Override
    public void onActivate() {
        super.onActivate();
        if (!BaritoneUtils.IS_AVAILABLE) {
            error("Baritone 不可用！");
            toggle();
            return;
        }

        currentState = MinerState.WAITING_TOOL_SELECTION;
        tickTimer = 0;
        lastMiningPos = null;
        currentTarget = null;
        toolShulkerPos = null;
        protectedShulkerBoxes.clear();
        shulkerInteractionTimer = 0;
        waitingForShulkerOpen = false;
        positionCache.startCleanupThread();

        // 扫描并保护所有潜影盒
        scanAndProtectShulkerBoxes();

        ChatUtils.sendMsg("自动挖掘模块已启动");
        ChatUtils.sendMsg("请右键点击工具存储潜影盒来选择它！");
    }

    @Override
    public void onDeactivate() {
        // 停止baritone
        if (BaritoneUtils.IS_AVAILABLE) {
            cancelBaritone();
            // 清理保护设置
            clearProtectedBlocks();
        }
        positionCache.shutdown(); // 关闭缓存管理器

        protectedShulkerBoxes.clear();
        ChatUtils.sendMsg("自动挖掘模块已停止");
    }

    @EventHandler
    private void onInteractBlock(InteractBlockEvent event) {
        if (currentState != MinerState.WAITING_TOOL_SELECTION) return;

        BlockPos pos = event.result.getBlockPos();
        Block block = mc.level.getBlockState(pos).getBlock();

        if (block instanceof ShulkerBoxBlock) {
            toolShulkerPos = pos;
            currentState = MinerState.MINING;

            // 更新保护设置
            updateProtectedBlocks();

            // 取消交互事件，防止打开潜影盒
            event.cancel();

            ChatUtils.sendMsg("工具潜影盒已选择: " + pos.toShortString());
            ChatUtils.sendMsg("已保护所有潜影盒免被挖掘");
            ChatUtils.sendMsg("开始自动挖掘！");
        } else {
            warning("请右键点击潜影盒来选择工具存储位置！");
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.level == null) return;

        // 如果还在等待选择工具潜影盒，不执行其他逻辑
        if (currentState == MinerState.WAITING_TOOL_SELECTION) {
            return;
        }

        // 缓存清理现在由后台线程自动处理
        // if (cacheClearTimer.tick(500, true)) {
        //     cleanExpiredCache();
        // }
        // 延迟控制
        if (tickTimer > 0) {
            tickTimer--;
            return;
        }

        switch (currentState) {
            case MINING -> handleMining();
            case INVENTORY_FULL -> handleInventoryFull();
            case TOOL_BROKEN -> handleToolBroken();
            case GOING_TO_STORAGE -> handleGoingToStorage();
            case GOING_TO_TOOLS -> handleGoingToTools();
            case STORING_ITEMS -> handleStoringItems();
            case GETTING_TOOLS -> handleGettingTools();
            case RETURNING -> handleReturning();
        }

        tickTimer = delay.get();
    }


    private void handleMining() {
        // 检查背包是否满了
        if (isInventoryFull()) {
            currentState = MinerState.INVENTORY_FULL;
            return;
        }

        // 检查工具是否需要更换
        if (needNewTool()) {
            currentState = MinerState.TOOL_BROKEN;
            return;
        }

        // 寻找目标方块并开始挖掘
        BlockPos targetPos = findNearestTargetBlock();
        if (targetPos != null) {
            if (currentTarget == null || !currentTarget.equals(targetPos)) {

                if (packetMine.get() && !positionCache.isInCache(targetPos)) {
                    // 只挖掘高于玩家的方块
                    BlockPos playerPos = mc.player.blockPosition();

                    // 计算到玩家的距离
                    // 性能：直接比较平方距离，省掉每次的 Math.pow / Math.sqrt。
                    int ddx = targetPos.getX() - playerPos.getX();
                    int ddy = targetPos.getY() - playerPos.getY();
                    int ddz = targetPos.getZ() - playerPos.getZ();
                    double distanceSq = (double) ddx * ddx + (double) ddy * ddy + (double) ddz * ddz;

                    if (targetPos.getY() >= playerPos.getY() && distanceSq < 4.2 * 4.2) {

                        // 将该位置加入缓存
                        positionCache.addToCache(targetPos);
                        BlockUtils.breakBlock(targetPos, true);
                    }

                }

                currentTarget = targetPos;
                lastMiningPos = mc.player.blockPosition();

                // 使用baritone挖掘
                BaritoneAPI.getProvider()
                    .getPrimaryBaritone()
                    .getMineProcess()
                    .mine(targetBlocks.get().toArray(new Block[0]));

            }
        } else {
            ChatUtils.sendMsg("附近没有找到目标方块");
        }
    }

    private void handleInventoryFull() {
        ChatUtils.sendMsg("背包已满，搜索存储潜影盒");

        BlockPos storageShulker = findSandStorageShulkerBox();
        if (storageShulker != null) {
            currentState = MinerState.GOING_TO_STORAGE;
            cancelBaritone();

            BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(new GoalBlock(storageShulker));
            ChatUtils.sendMsg("找到存储潜影盒: " + storageShulker.toShortString());
        } else {
            error("未找到存储潜影盒！");
            currentState = MinerState.MINING;
        }
    }

    private void handleToolBroken() {
        ToolType selectedToolType = toolType.get();
        ChatUtils.sendMsg("工具耐久度过低，前往工具潜影盒获取" + selectedToolType.getDisplayName());

        if (toolShulkerPos != null) {
            currentState = MinerState.GOING_TO_TOOLS;
            BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(new GoalBlock(toolShulkerPos));
            ChatUtils.sendMsg("前往工具存储潜影盒: " + toolShulkerPos.toShortString());
        } else {
            error("工具潜影盒位置未设置！请重新启动模块并选择工具潜影盒。");
            currentState = MinerState.WAITING_TOOL_SELECTION;
            ChatUtils.sendMsg("请右键点击工具存储潜影盒来选择它！");
        }
    }

    private void handleGoingToStorage() {
        // 检查是否到达任何潜影盒附近
        BlockPos nearbyShulker = findNearbyShulkerBox();
        if (nearbyShulker != null && mc.player.blockPosition().distSqr(nearbyShulker) <= 4*4) {
            currentState = MinerState.STORING_ITEMS;
            cancelBaritone();
        }
    }

    private void handleGoingToTools() {
        // 检查是否到达工具潜影盒附近
        if (toolShulkerPos != null && mc.player.blockPosition().distSqr(toolShulkerPos) <= 4*4) {
            currentState = MinerState.GETTING_TOOLS;
            cancelBaritone();
        }
    }

    private void handleStoringItems() {
        // 检查当前屏幕是否是潜影盒界面
        if (isShulkerBoxOpen()) {
            // 存储沙子到潜影盒
            storeSandToShulkerBox();
            return;
        }
        // 检查是否需要等待潜影盒打开
        if (waitingForShulkerOpen) {
            shulkerInteractionTimer++;
            if (shulkerInteractionTimer > 40) { // 等待2秒
                waitingForShulkerOpen = false;
                shulkerInteractionTimer = 0;
                warning("潜影盒打开超时，重试中...");
            } else {
                return;
            }
        }

        // 检查当前屏幕是否是潜影盒界面
        if (isShulkerBoxOpen()) {
            // 存储沙子到潜影盒
            storeSandToShulkerBox();
        } else {
            // 尝试打开附近的沙子存储潜影盒
            BlockPos nearbyShulker = findNearbyShulkerBox();
            if (nearbyShulker != null) {
                openShulkerBox(nearbyShulker);
                waitingForShulkerOpen = true;
                shulkerInteractionTimer = 0;
            } else {
                error("附近没有找到潜影盒！");
                currentState = MinerState.MINING;
            }
        }
    }

    private void handleGettingTools() {
        // 检查当前屏幕是否是潜影盒界面
        if (isShulkerBoxOpen()) {
            // 从潜影盒获取工具
            getToolFromShulkerBox();
            return;
        }

        // 检查是否需要等待潜影盒打开
        if (waitingForShulkerOpen) {
            shulkerInteractionTimer++;
            if (shulkerInteractionTimer > 40) { // 等待2秒
                waitingForShulkerOpen = false;
                shulkerInteractionTimer = 0;
                warning("工具潜影盒打开超时，重试中...");
            } else {
                return;
            }
        }

        // 检查当前屏幕是否是潜影盒界面
        if (isShulkerBoxOpen()) {
            // 从潜影盒获取工具
            getToolFromShulkerBox();
        } else {
            // 尝试打开工具潜影盒
            if (toolShulkerPos != null) {
                openShulkerBox(toolShulkerPos);
                waitingForShulkerOpen = true;
                shulkerInteractionTimer = 0;
            } else {
                error("工具潜影盒位置未设置！");
                currentState = MinerState.WAITING_TOOL_SELECTION;
            }
        }
    }

    private void handleReturning() {
        if (lastMiningPos != null && mc.player.blockPosition().distSqr(lastMiningPos) <= 3*3) {
            currentState = MinerState.MINING;
            cancelBaritone();
            info("已返回挖掘位置");
        }
    }

    private boolean isInventoryFull() {

        int emptySlots = 0;
        for (int i = 9; i < 36; i++) { // Main inventory slots only
            if (mc.player.getInventory().getItem(i).isEmpty()) {
                emptySlots++;
            }
        }
        return emptySlots <= 2; // 保留2个空槽位
    }

    private boolean needNewTool() {
        ToolType selectedToolType = toolType.get();
        List<Item> toolPriority = getToolPriorityList(selectedToolType);

        FindItemResult tool = InvUtils.find(itemStack -> toolPriority.contains(itemStack.getItem()));
        if (!tool.found()) return true;

        // 获取物品堆栈
        var itemStack = mc.player.getInventory().getItem(tool.slot());

        // 检查物品是否可损坏
        if (!itemStack.isDamageableItem()) return false;

        // 计算剩余耐久度
        int maxDamage = itemStack.getMaxDamage();
        int currentDamage = itemStack.getDamageValue();
        int remainingDurability = maxDamage - currentDamage;

        return remainingDurability <= minDurability.get();
    }

    // 按“到玩家由近到远”排好序的立方体偏移表缓存（默认范围 32 时约 27.5 万项）。
    // 有了它，最近目标搜索可以一命中就返回，不必每次都把整个立方体扫完。
    private int cachedOffsetRange = -1;
    private int[] cachedOffsets;

    /**
     * 生成并缓存 (2range+1)³ 内所有偏移，按平方距离升序排列。
     * 同距离时保持原 x→y→z 遍历顺序，因此选中结果与原实现完全一致。
     * 范围过大时不缓存（返回 null），由调用方退回完整扫描，避免占用过多内存。
     */
    private int[] offsetsByDistance(int range) {
        if (cachedOffsets != null && cachedOffsetRange == range) return cachedOffsets;

        int side = range * 2 + 1;
        long total = (long) side * side * side;
        if (total > 2_000_000L) {
            cachedOffsets = null;
            cachedOffsetRange = -1;
            return null;
        }

        int n = (int) total;
        // 高 32 位放平方距离，低 32 位放原始遍历序号：排序后同距离者自然按原顺序排列。
        long[] keys = new long[n];
        int idx = 0;
        for (int x = -range; x <= range; x++) {
            for (int y = -range; y <= range; y++) {
                for (int z = -range; z <= range; z++) {
                    keys[idx] = ((long) (x * x + y * y + z * z) << 32) | (idx & 0xFFFFFFFFL);
                    idx++;
                }
            }
        }
        Arrays.sort(keys);

        int sideSq = side * side;
        int[] offsets = new int[n];
        for (int i = 0; i < n; i++) {
            int o = (int) keys[i];
            int x = o / sideSq - range;
            int rem = o % sideSq;
            int y = rem / side - range;
            int z = rem % side - range;
            // 每个坐标用 9 位存（坐标 + range 落在 0..400）。
            offsets[i] = (x + range) | ((y + range) << 9) | ((z + range) << 18);
        }

        cachedOffsets = offsets;
        cachedOffsetRange = range;
        return offsets;
    }

    private BlockPos findNearestTargetBlock() {
        BlockPos playerPos = mc.player.blockPosition();

        // 性能：把目标 List 转成 HashSet，避免在内层循环里对 27 万个方块逐个线性查找；
        // 同时复用 MutableBlockPos（原来每格都 new BlockPos），并用整数偏移直接算平方距离。
        Set<Block> targets = new HashSet<>(targetBlocks.get());
        int range = miningRange.get();
        int px = playerPos.getX(), py = playerPos.getY(), pz = playerPos.getZ();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        int[] offsets = offsetsByDistance(range);
        if (offsets != null) {
            // 已按距离由近到远排序：遇到的第一个目标方块必然就是最近的，可以直接返回。
            for (int packed : offsets) {
                int dx = (packed & 0x1FF) - range;
                int dy = ((packed >> 9) & 0x1FF) - range;
                int dz = ((packed >> 18) & 0x1FF) - range;
                if (targets.contains(mc.level.getBlockState(pos.set(px + dx, py + dy, pz + dz)).getBlock())) {
                    return pos.immutable();
                }
            }
            return null;
        }

        // 范围过大（偏移表超限）时退回完整扫描。
        BlockPos nearestBlock = null;
        double nearestDistance = Double.MAX_VALUE;
        for (int x = -range; x <= range; x++) {
            for (int y = -range; y <= range; y++) {
                for (int z = -range; z <= range; z++) {
                    Block block = mc.level.getBlockState(pos.set(px + x, py + y, pz + z)).getBlock();

                    if (targets.contains(block)) {
                        double distance = (double) x * x + (double) y * y + (double) z * z;
                        if (distance < nearestDistance) {
                            nearestDistance = distance;
                            nearestBlock = pos.immutable();
                        }
                    }
                }
            }
        }

        return nearestBlock;
    }

    private BlockPos findSandStorageShulkerBox() {
        BlockPos playerPos = mc.player.blockPosition();
        int radius = shulkerSearchRadius.get();
        int px = playerPos.getX(), py = playerPos.getY(), pz = playerPos.getZ();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Block block = mc.level.getBlockState(pos.set(px + x, py + y, pz + z)).getBlock();

                    // 找到潜影盒，但不是工具潜影盒
                    if (block instanceof ShulkerBoxBlock && !pos.equals(toolShulkerPos)) {
                        return pos.immutable();
                    }
                }
            }
        }

        return null;
    }


    private BlockPos findNearbyShulkerBox() {
        BlockPos playerPos = mc.player.blockPosition();
        int px = playerPos.getX(), py = playerPos.getY(), pz = playerPos.getZ();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int x = -3; x <= 3; x++) {
            for (int y = -3; y <= 3; y++) {
                for (int z = -3; z <= 3; z++) {
                    Block block = mc.level.getBlockState(pos.set(px + x, py + y, pz + z)).getBlock();

                    if (block instanceof ShulkerBoxBlock) {
                        return pos.immutable();
                    }
                }
            }
        }

        return null;
    }

    private void scanAndProtectShulkerBoxes() {
        protectedShulkerBoxes.clear();
        BlockPos playerPos = mc.player.blockPosition();
        int radius = shulkerSearchRadius.get();
        int px = playerPos.getX(), py = playerPos.getY(), pz = playerPos.getZ();
        // 性能：复用 MutableBlockPos，只有真正命中潜影盒时才 immutable()；
        // 原实现 65³ 范围每格都 offset 出一个新 BlockPos。
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        // 扫描范围内的所有潜影盒
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Block block = mc.level.getBlockState(pos.set(px + x, py + y, pz + z)).getBlock();

                    if (block instanceof ShulkerBoxBlock) {
                        protectedShulkerBoxes.add(pos.immutable());
                    }
                }
            }
        }

        ChatUtils.sendMsg("已扫描到 " + protectedShulkerBoxes.size() + " 个潜影盒，将被保护");
    }

    private void updateProtectedBlocks() {
        if (!BaritoneUtils.IS_AVAILABLE) return;

        try {
            Settings settings = BaritoneAPI.getSettings();


            // 将所有潜影盒添加到不可破坏的方块列表
            List<Block> blocksToAvoid = new ArrayList<>();

            // 添加所有潜影盒类型
            blocksToAvoid.add(Blocks.SHULKER_BOX);
            blocksToAvoid.add(MikuBlocks.get("white_shulker_box"));
            blocksToAvoid.add(MikuBlocks.get("orange_shulker_box"));
            blocksToAvoid.add(MikuBlocks.get("magenta_shulker_box"));
            blocksToAvoid.add(MikuBlocks.get("light_blue_shulker_box"));
            blocksToAvoid.add(MikuBlocks.get("yellow_shulker_box"));
            blocksToAvoid.add(MikuBlocks.get("lime_shulker_box"));
            blocksToAvoid.add(MikuBlocks.get("pink_shulker_box"));
            blocksToAvoid.add(MikuBlocks.get("gray_shulker_box"));
            blocksToAvoid.add(MikuBlocks.get("light_gray_shulker_box"));
            blocksToAvoid.add(MikuBlocks.get("cyan_shulker_box"));
            blocksToAvoid.add(MikuBlocks.get("purple_shulker_box"));
            blocksToAvoid.add(MikuBlocks.get("blue_shulker_box"));
            blocksToAvoid.add(MikuBlocks.get("brown_shulker_box"));
            blocksToAvoid.add(MikuBlocks.get("green_shulker_box"));
            blocksToAvoid.add(MikuBlocks.get("red_shulker_box"));
            blocksToAvoid.add(MikuBlocks.get("black_shulker_box"));


            // 设置不可破坏的方块
            settings.blocksToDisallowBreaking.value = blocksToAvoid;

            info("已设置 Baritone 保护所有潜影盒");

        } catch (Exception e) {
            warning("设置 Baritone 保护失败: " + e.getMessage());
        }
    }

    private void clearProtectedBlocks() {
        if (!BaritoneUtils.IS_AVAILABLE) return;

        try {
            var settings = BaritoneAPI.getSettings();

            // 清空不可破坏的方块列表
            settings.blocksToDisallowBreaking.value = new java.util.ArrayList<>();

        } catch (Exception e) {
            warning("清理 Baritone 保护设置失败: " + e.getMessage());
        }
    }

    private void openShulkerBox(BlockPos pos) {
        if (mc.gameMode == null || mc.player == null) return;

        try {
            // 确保玩家在潜影盒附近
            if (mc.player.blockPosition().distSqr(pos) > 5*5) {
                warning("距离潜影盒太远，无法打开");
                return;
            }

            // 确保目标位置确实是潜影盒
            Block block = mc.level.getBlockState(pos).getBlock();
            if (!(block instanceof ShulkerBoxBlock)) {
                warning("目标位置不是潜影盒: " + pos.toShortString());
                return;
            }

            // 计算点击位置
            Vec3 hitVec = Vec3.upFromBottomCenterOf(pos, 0);
            Direction side = Direction.UP; // 默认从上方点击

            // 创建方块命中结果
            BlockHitResult hitResult = new BlockHitResult(hitVec, side, pos, false);

            // 右键点击潜影盒
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hitResult);

            info("正在打开潜影盒: " + pos.toShortString());

        } catch (Exception e) {
            error("打开潜影盒时发生错误: " + e.getMessage());
        }
    }

    private void storeSandToShulkerBox() {
        if (mc.gameMode == null) return;

        int itemsMoved = 0;
        int maxMovesPerTick = 27; // 每tick最多移动x组物品，防止操作过快
        List<Block> targets = targetBlocks.get();
        boolean hasMovedItems = false;

        // 遍历玩家背包，寻找目标方块
        for (int i = 9; i < 36; i++) {
            var stack = mc.player.getInventory().getItem(i);

            if (!stack.isEmpty()) {
                boolean isTargetBlock = false;
                for (Block targetBlock : targets) {
                    if (stack.getItem() == targetBlock.asItem()) {
                        isTargetBlock = true;
                        break;
                    }
                }

                if (isTargetBlock) {
                    // 寻找潜影盒中的空槽位或相同物品槽位
                    int targetSlot = findShulkerSlotForItem(stack);

                    if (targetSlot != -1) {
                        // 移动物品到潜影盒
                        moveItemToShulker(i, targetSlot);
                        itemsMoved++;
                        hasMovedItems = true;
                        info("移动" + stack.getDisplayName().getString() + "到潜影盒，数量: " + stack.getCount());

                        if (itemsMoved >= maxMovesPerTick) {
                            break; // 本tick达到最大操作数，下个tick继续
                        }
                    } else {
                        // 当前潜影盒满了，寻找下一个潜影盒
                        BlockPos nextShulker = findNextEmptyShulkerBox();
                        if (nextShulker != null) {
                            closeShulkerBox();
                            openShulkerBox(nextShulker);
                            waitingForShulkerOpen = true;
                            shulkerInteractionTimer = 0;
                            return; // 等待潜影盒打开
                        } else {
                            // 没有找到更多潜影盒，存储完成
                            closeShulkerBox();

                            if (autoReturn.get() && lastMiningPos != null) {
                                currentState = MinerState.RETURNING;
                                BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(new GoalBlock(lastMiningPos));
                            } else {
                                currentState = MinerState.MINING;
                            }

                            info("物品存储完成");
                            return;
                        }
                    }
                }
            }
        }

        // 如果没有更多物品需要存储，关闭潜影盒并继续
        if (!hasMoreTargetBlocksToStore()) {
            closeShulkerBox();

            if (autoReturn.get() && lastMiningPos != null) {
                currentState = MinerState.RETURNING;
                BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(new GoalBlock(lastMiningPos));
            } else {
                currentState = MinerState.MINING;
            }

            info("物品存储完成");
        }
    }

    private void getToolFromShulkerBox() {
        if (mc.gameMode == null) return;

        // 寻找潜影盒中的铲子
        int toolSlot = findToolInShulker();

        if (toolSlot != -1) {
            // 寻找玩家背包中的空槽位或损坏的工具槽位
            int targetSlot = findPlayerSlotForTool();

            if (targetSlot != -1) {
                // 移动工具到玩家背包
                moveItemFromShulker(toolSlot, targetSlot);
                info("获取新工具成功");

                // 关闭潜影盒并继续
                closeShulkerBox();

                if (autoReturn.get() && lastMiningPos != null) {
                    currentState = MinerState.RETURNING;
                    BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(new GoalBlock(lastMiningPos));
                } else {
                    currentState = MinerState.MINING;
                }
            } else {
                warning("背包已满，无法获取新工具！");
                closeShulkerBox();
                currentState = MinerState.MINING;
            }
        } else {
            ToolType selectedToolType = toolType.get();
            warning("潜影盒中没有找到可用的" + selectedToolType.getDisplayName() + "！");
            closeShulkerBox();
            currentState = MinerState.MINING;
        }
    }

    /**
     * 寻找下一个未满的潜影盒用于存储物品
     *
     * @return 找到的潜影盒位置，如果没有找到返回null
     */
    private BlockPos findNextEmptyShulkerBox() {
        BlockPos playerPos = mc.player.blockPosition();
        int radius = shulkerSearchRadius.get();
        int px = playerPos.getX(), py = playerPos.getY(), pz = playerPos.getZ();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Block block = mc.level.getBlockState(pos.set(px + x, py + y, pz + z)).getBlock();

                    // 找到潜影盒，但不是工具潜影盒
                    if (block instanceof ShulkerBoxBlock && !pos.equals(toolShulkerPos)) {
                        // 可以添加额外逻辑检查潜影盒是否有空间
                        return pos.immutable();
                    }
                }
            }
        }

        return null;
    }

    private int findShulkerSlotForItem(ItemStack stack) {
        if (!(MikuCompat.screen() instanceof ShulkerBoxScreen)) {
            return -1;
        }


        var handler = mc.player.containerMenu;

        // 潜影盒槽位通常是前27个槽位（0-26）
        for (int i = 0; i < 27; i++) {
            if (i >= handler.slots.size()) break;

            var slotStack = handler.getSlot(i).getItem();

            // 寻找空槽位或相同物品的槽位
            if (slotStack.isEmpty()) {
                return i; // 优先返回完全空的槽位
            } else if (slotStack.getItem() == stack.getItem() && slotStack.getCount() < slotStack.getMaxStackSize()) {
                return i;
            }
        }

        return -1; // 潜影盒已满
    }

    private int findToolInShulker() {
        if (!(MikuCompat.screen() instanceof ShulkerBoxScreen)) {
            return -1;
        }

        var handler = mc.player.containerMenu;
        ToolType selectedToolType = toolType.get();

        // 按材质优先级顺序寻找工具（下界合金 > 钻石 > 铁 > 石头 > 木头）
        List<Item> toolPriority = getToolPriorityList(selectedToolType);

        for (Item preferredTool : toolPriority) {
            for (int i = 0; i < 27; i++) {
                if (i >= handler.slots.size()) break;

                var slotStack = handler.getSlot(i).getItem();

                if (!slotStack.isEmpty() && slotStack.getItem() == preferredTool) {
                    // 检查工具耐久度
                    if (!slotStack.isDamageableItem() ||
                        (slotStack.getMaxDamage() - slotStack.getDamageValue()) > minDurability.get()) {
                        return i;
                    }
                }
            }
        }

        return -1;
    }

    private int findPlayerSlotForTool() {
        ToolType selectedToolType = toolType.get();

        // 首先寻找损坏的工具槽位
        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
            var stack = mc.player.getInventory().getItem(i);


            List<Item> toolPriority = getToolPriorityList(selectedToolType);

            if (!stack.isEmpty() && toolPriority.contains(stack.getItem())) {
                if (stack.isDamageableItem() &&
                    (stack.getMaxDamage() - stack.getDamageValue()) <= minDurability.get()) {
                    return i + 27; // 转换为屏幕槽位索引
                }
            }
        }

        // 然后寻找空槽位
        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
            if (mc.player.getInventory().getItem(i).isEmpty()) {
                return i + 27; // 转换为屏幕槽位索引
            }
        }

        return -1;
    }

    private void moveItemToShulker(int playerSlot, int shulkerSlot) {
        if (mc.gameMode == null) return;

        // 转换玩家槽位索引
        int screenSlot = playerSlot + 27;

        // Shift+左键快速移动
        mc.gameMode.handleContainerInput(
            mc.player.containerMenu.containerId,
            screenSlot,
            0,
            ContainerInput.QUICK_MOVE,
            mc.player
        );
    }

    private void moveItemFromShulker(int shulkerSlot, int playerSlot) {
        if (mc.gameMode == null) return;

        // Shift+左键快速移动
        mc.gameMode.handleContainerInput(
            mc.player.containerMenu.containerId,
            shulkerSlot,
            0,
            ContainerInput.QUICK_MOVE,
            mc.player
        );
    }

    private boolean hasMoreTargetBlocksToStore() {
        List<Block> targets = targetBlocks.get();

        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
            var stack = mc.player.getInventory().getItem(i);
            if (!stack.isEmpty()) {
                for (Block targetBlock : targets) {
                    if (stack.getItem() == targetBlock.asItem()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void cancelBaritone() {
        BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().cancelEverything();

    }

    private void closeShulkerBox() {
        if (MikuCompat.screen() instanceof ShulkerBoxScreen) {
            mc.player.closeContainer();
            ChatUtils.sendMsg("关闭潜影盒");
        }
    }

    private boolean isShulkerBoxOpen() {

        // 使用正确的方法检测潜影盒界面
        return MikuCompat.screen() instanceof ShulkerBoxScreen || MikuCompat.screen() instanceof AbstractContainerScreen;
    }

    private List<Item> getToolPriorityList(ToolType toolType) {
        return switch (toolType) {
            case SHOVEL -> Arrays.asList(
                Items.NETHERITE_SHOVEL,
                Items.DIAMOND_SHOVEL,
                Items.IRON_SHOVEL,
                Items.STONE_SHOVEL,
                Items.WOODEN_SHOVEL
            );
            case PICKAXE -> Arrays.asList(
                Items.NETHERITE_PICKAXE,
                Items.DIAMOND_PICKAXE,
                Items.IRON_PICKAXE,
                Items.STONE_PICKAXE,
                Items.WOODEN_PICKAXE
            );
            case AXE -> Arrays.asList(
                Items.NETHERITE_AXE,
                Items.DIAMOND_AXE,
                Items.IRON_AXE,
                Items.STONE_AXE,
                Items.WOODEN_AXE
            );
            case HOE -> Arrays.asList(
                Items.NETHERITE_HOE,
                Items.DIAMOND_HOE,
                Items.IRON_HOE,
                Items.STONE_HOE,
                Items.WOODEN_HOE
            );
        };
    }
}
