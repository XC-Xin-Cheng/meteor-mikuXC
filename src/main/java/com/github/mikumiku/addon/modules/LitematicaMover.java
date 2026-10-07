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
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;


public class LitematicaMover extends BaseModule {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgMovement = this.settings.createGroup("移动设置");

    // 设置项
    private final Setting<Direction> direction = sgMovement.add(new EnumSetting.Builder<Direction>()
        .name("移动方向")
        .description("投影移动的方向。")
        .defaultValue(Direction.NORTH)
        .build()
    );

    private final Setting<Integer> speed = sgMovement.add(new IntSetting.Builder()
        .name("移动速度")
        .description("移动速度（以游戏刻为单位，20刻 = 1秒）。")
        .defaultValue(20)
        .min(1)
        .max(200)
        .sliderMax(100)
        .build()
    );

    private final Setting<Boolean> autoDetect = sgGeneral.add(new BoolSetting.Builder()
        .name("自动检测")
        .description("自动检测并移动当前选中的投影。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> logMovement = sgGeneral.add(new BoolSetting.Builder()
        .name("记录移动")
        .description("在聊天栏中记录移动操作。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> followPlayer = sgGeneral.add(new BoolSetting.Builder()
        .name("跟随玩家")
        .description("让投影跟随玩家的水平移动。")
        .defaultValue(false)
        .build()
    );

    // 内部变量
    private int tickCounter = 0;
    private SchematicPlacement currentPlacement = null;

    // 跟随玩家变量
    private BlockPos lastPlayerPos = null;
    private BlockPos relativeOffset = null;
    private boolean isFollowingInitialized = false;

    public LitematicaMover() {
        super(CATEGORY_MIKU_BUILD, "投影跟随", "自动移动当前选定的 Litematica 投影原理图. 需要安装Litematica.");
    }

    @Override
    public void onActivate() {
        tickCounter = 0;
        isFollowingInitialized = false;
        lastPlayerPos = null;
        relativeOffset = null;

        if (logMovement.get()) {
            info("投影移动器已启用");
        }

        // 激活时尝试获取当前投影
        getCurrentPlacement();

        // 如果启用了跟随玩家功能，则初始化
        if (followPlayer.get()) {
            initializeFollowPlayer();
        }
    }

    @Override
    public void onDeactivate() {
        if (logMovement.get()) {
            info("投影移动器已停用");
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (Minecraft.getInstance().player == null || Minecraft.getInstance().level == null) {
            return;
        }

        // 处理跟随玩家逻辑
        if (followPlayer.get()) {
            handleFollowPlayer();
        } else {
            // 普通移动逻辑
            tickCounter++;

            // 每隔指定刻数移动一次
            if (tickCounter >= speed.get()) {
                tickCounter = 0;
                moveSchematic();
            }
        }
    }

    private SchematicPlacement getCurrentPlacement() {
        if (!autoDetect.get()) {
            return currentPlacement;
        }

        try {
            // 26.1：Litematica 的 DataManager 实现了 malilib 的 IDirectoryCache，
            // 本工程 classpath 上没有 malilib，直接引用会在编译期失败。
            // 改为反射调用：运行时装好 Litematica + malilib 即可正常自动检测。
            Object placementManager = Class.forName("fi.dy.masa.litematica.data.DataManager")
                .getMethod("getSchematicPlacementManager")
                .invoke(null);
            if (placementManager != null) {
                Object selected = placementManager.getClass()
                    .getMethod("getSelectedSchematicPlacement")
                    .invoke(placementManager);
                if (selected instanceof SchematicPlacement placement) {
                    currentPlacement = placement;
                }
            }
        } catch (Exception e) {
            if (logMovement.get()) {
                error("获取当前投影位置失败: " + e.getMessage());
            }
        }

        return currentPlacement;
    }

    private void moveSchematic() {
        SchematicPlacement placement = getCurrentPlacement();

        if (placement == null) {
            if (logMovement.get()) {
                warning("未选择投影");
            }
            return;
        }

        try {
            // 获取当前原点位置
            BlockPos currentOrigin = placement.getOrigin();

            // 根据方向计算新位置
            BlockPos newOrigin = currentOrigin.relative(direction.get());

            // 设置新的原点位置
            setPlacementOrigin(placement, newOrigin);

            if (logMovement.get()) {
                info("投影已从 " + currentOrigin.toShortString() + " 移动到 " + newOrigin.toShortString());
            }

        } catch (Exception e) {
            if (logMovement.get()) {
                error("移动投影失败: " + e.getMessage());
            }
        }
    }

    // 26.1：SchematicPlacement#setOrigin 的第二个参数是 malilib 的 IStringConsumer，
    // 本工程 classpath 上没有 malilib，直接引用会编译失败，因此用反射调用。
    // 运行时装好 Litematica + malilib 即可正常生效。
    private static void setPlacementOrigin(SchematicPlacement placement, BlockPos origin) throws Exception {
        Class<?> consumerType = Class.forName("fi.dy.masa.malilib.interfaces.IStringConsumer");
        placement.getClass().getMethod("setOrigin", BlockPos.class, consumerType)
            .invoke(placement, origin, null);
    }

    // 不同移动方向的工具方法
    public void moveNorth() {
        direction.set(Direction.NORTH);
    }

    public void moveSouth() {
        direction.set(Direction.SOUTH);
    }

    public void moveEast() {
        direction.set(Direction.EAST);
    }

    public void moveWest() {
        direction.set(Direction.WEST);
    }

    public void moveUp() {
        direction.set(Direction.UP);
    }

    public void moveDown() {
        direction.set(Direction.DOWN);
    }

    // 外部访问的获取方法
    public Direction getCurrentDirection() {
        return direction.get();
    }

    public int getCurrentSpeed() {
        return speed.get();
    }

    public boolean isAutoDetectEnabled() {
        return autoDetect.get();
    }

    // 跟随玩家方法
    private void initializeFollowPlayer() {
        if (Minecraft.getInstance().player == null) {
            return;
        }

        SchematicPlacement placement = getCurrentPlacement();
        if (placement == null) {
            if (logMovement.get()) {
                warning("未找到投影，无法初始化跟随玩家功能");
            }
            return;
        }

        try {
            BlockPos playerPos = Minecraft.getInstance().player.blockPosition();
            BlockPos schematicPos = placement.getOrigin();

            // 计算相对偏移量（仅X和Z轴，忽略Y轴）
            relativeOffset = new BlockPos(
                schematicPos.getX() - playerPos.getX(),
                0, // 不跟踪Y轴偏移
                schematicPos.getZ() - playerPos.getZ()
            );

            lastPlayerPos = new BlockPos(playerPos.getX(), 0, playerPos.getZ());
            isFollowingInitialized = true;

            if (logMovement.get()) {
                info("跟随玩家功能已初始化。相对偏移: " + relativeOffset.getX() + ", " + relativeOffset.getZ());
            }
        } catch (Exception e) {
            if (logMovement.get()) {
                error("初始化跟随玩家功能失败: " + e.getMessage());
            }
        }
    }

    private void handleFollowPlayer() {
        if (Minecraft.getInstance().player == null) {
            return;
        }

        // 如果尚未初始化则进行初始化
        if (!isFollowingInitialized) {
            initializeFollowPlayer();
            return;
        }

        try {
            BlockPos currentPlayerPos = Minecraft.getInstance().player.blockPosition();
            BlockPos currentPlayerPosFlat = new BlockPos(currentPlayerPos.getX(), 0, currentPlayerPos.getZ());

            // 检查玩家是否水平移动
            if (lastPlayerPos == null || !lastPlayerPos.equals(currentPlayerPosFlat)) {
                moveSchematicToFollowPlayer(currentPlayerPosFlat);
                lastPlayerPos = currentPlayerPosFlat;
            }
        } catch (Exception e) {
            if (logMovement.get()) {
                error("处理跟随玩家功能失败: " + e.getMessage());
            }
        }
    }

    private void moveSchematicToFollowPlayer(BlockPos playerPos) {
        SchematicPlacement placement = getCurrentPlacement();
        if (placement == null || relativeOffset == null) {
            return;
        }

        try {
            BlockPos currentSchematicPos = placement.getOrigin();

            // 根据玩家位置和相对偏移量计算新的投影位置
            BlockPos newSchematicPos = new BlockPos(
                playerPos.getX() + relativeOffset.getX(),
                currentSchematicPos.getY(), // 保持原始Y位置
                playerPos.getZ() + relativeOffset.getZ()
            );

            // 只有位置真正改变时才移动
            if (!currentSchematicPos.equals(newSchematicPos)) {
                setPlacementOrigin(placement, newSchematicPos);

                if (logMovement.get()) {
                    info("跟随玩家: 投影已移动到 " + newSchematicPos.toShortString());
                }
            }
        } catch (Exception e) {
            if (logMovement.get()) {
                error("跟随玩家移动投影失败: " + e.getMessage());
            }
        }
    }

    // 重置跟随玩家功能的工具方法
    public void resetFollowPlayer() {
        isFollowingInitialized = false;
        lastPlayerPos = null;
        relativeOffset = null;
        if (logMovement.get()) {
            info("跟随玩家功能已重置");
        }
    }

    // 跟随玩家状态的获取方法
    public boolean isFollowPlayerEnabled() {
        return followPlayer.get();
    }

    public boolean isFollowPlayerInitialized() {
        return isFollowingInitialized;
    }

    public BlockPos getRelativeOffset() {
        return relativeOffset;
    }
}
