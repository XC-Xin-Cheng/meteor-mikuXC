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
import com.github.mikumiku.addon.util.HumanizedSettings;
import com.github.mikumiku.addon.util.Rotation;
import com.github.mikumiku.addon.util.RotationManager;
import com.github.mikumiku.addon.util.Via;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import java.util.ArrayList;
import java.util.List;

public class AutoHoleFill extends BaseModule {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgPlace = settings.createGroup("放置设置");
    private final SettingGroup sgRange = settings.createGroup("范围设置");
    private final SettingGroup sgMisc = settings.createGroup("其他设置");

    /**
     * 本模块自己的「类人化」设置组：旋转注入手抖。模式默认「跟随全局」。
     */
    private final HumanizedSettings humanized = HumanizedSettings.builder(this, "AutoHoleFill").view().rotateNoise().build();

    // 通用设置
    private final Setting<Integer> placeDelay = sgGeneral.add(new IntSetting.Builder()
        .name("放置延迟")
        .description("每次放置方块之间的延迟时间")
        .defaultValue(50)
        .min(0)
        .max(500)
        .sliderMax(500)
        .build()
    );

    private final Setting<Integer> blocksPer = sgGeneral.add(new IntSetting.Builder()
        .name("每次放置数量")
        .description("每次tick放置的方块数量")
        .defaultValue(1)
        .min(1)
        .max(8)
        .sliderMax(8)
        .build()
    );

    // 放置设置
    private final Setting<Boolean> rotate = sgPlace.add(new BoolSetting.Builder()
        .name("旋转")
        .description("放置时旋转到目标位置")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> packetPlace = sgPlace.add(new BoolSetting.Builder()
        .name("数据包放置")
        .description("使用数据包进行放置")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> breakCrystal = sgPlace.add(new BoolSetting.Builder()
        .name("破坏水晶")
        .description("放置前破坏水晶")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> eatPause = sgPlace.add(new BoolSetting.Builder()
        .name("进食暂停")
        .description("进食时暂停破坏水晶")
        .defaultValue(true)
        .visible(breakCrystal::get)
        .build()
    );

    private final Setting<Boolean> detectMining = sgPlace.add(new BoolSetting.Builder()
        .name("检测挖掘")
        .description("检测正在挖掘的方块")
        .defaultValue(false)
        .build()
    );

    // 范围设置
    private final Setting<Double> placeRange = sgRange.add(new DoubleSetting.Builder()
        .name("放置范围")
        .description("放置方块的最大范围")
        .defaultValue(5)
        .min(0)
        .max(8)
        .sliderMax(8)
        .build()
    );

    private final Setting<Double> enemyRange = sgRange.add(new DoubleSetting.Builder()
        .name("敌人范围")
        .description("检测敌人的范围")
        .defaultValue(6)
        .min(0)
        .max(8)
        .sliderMax(8)
        .build()
    );

    private final Setting<Double> holeRange = sgRange.add(new DoubleSetting.Builder()
        .name("洞穴范围")
        .description("在敌人周围搜索洞穴的范围")
        .defaultValue(2)
        .min(0)
        .max(8)
        .sliderMax(8)
        .build()
    );

    private final Setting<Double> selfRange = sgRange.add(new DoubleSetting.Builder()
        .name("自身范围")
        .description("距离玩家的最小安全距离")
        .defaultValue(2)
        .min(0)
        .max(8)
        .sliderMax(8)
        .build()
    );

    // 其他设置
    private final Setting<Integer> predictTicks = sgMisc.add(new IntSetting.Builder()
        .name("预测刻数")
        .description("预测敌人位置的tick数")
        .defaultValue(1)
        .min(1)
        .max(8)
        .sliderMax(8)
        .build()
    );

    private final Setting<Boolean> usingPause = sgMisc.add(new BoolSetting.Builder()
        .name("使用物品暂停")
        .description("使用物品时暂停填坑")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> inAirPause = sgMisc.add(new BoolSetting.Builder()
        .name("空中暂停")
        .description("在空中时暂停填坑")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> inventory = sgMisc.add(new BoolSetting.Builder()
        .name("背包切换")
        .description("使用背包物品进行切换")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> web = sgMisc.add(new BoolSetting.Builder()
        .name("蜘蛛网优先")
        .description("优先使用蜘蛛网而不是黑曜石")
        .defaultValue(true)
        .build()
    );

    private long lastPlaceTime = 0;
    private int progress = 0;

    // 性能：复用这些容器/可变坐标，避免每 tick 生成球体时反复分配对象。
    private final LongOpenHashSet visitedPositions = new LongOpenHashSet();
    private final BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
    private final BlockPos.MutableBlockPos belowPos = new BlockPos.MutableBlockPos();
    private final List<Player> cachedEnemies = new ArrayList<>();

    public AutoHoleFill() {
        super(BaseModule.CATEGORY_MIKU_COMBAT, "自动填坑", "自动填充敌人周围的基岩坑");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (System.currentTimeMillis() - lastPlaceTime < placeDelay.get()) return;
        progress = 0;

        int block = getBlock();
        if (block == -1) return;

        if (usingPause.get() && mc.player.isUsingItem()) return;
        if (inAirPause.get() && !mc.player.onGround()) return;

        // 性能：本 tick 内不变的值只取一次；球体按“由近到远”逐点校验，
        // 达到每刻上限就停，省去原来整球 List + stream 的分配，选中的方块与原实现一致。
        final int perTick = blocksPer.get();
        final double range = holeRange.get();
        final double rangeSq = range * range;
        final double selfRangeSq = selfRange.get() * selfRange.get();
        final double placeRangeSq = placeRange.get() * placeRange.get();
        final Vec3 playerPos = Via.getEntityPos(mc.player);

        visitedPositions.clear();
        BlockPos.MutableBlockPos pos = mutablePos;

        enemyLoop:
        for (Player enemy : getEnemies()) {
            Vec3 center = predictPosition(enemy);
            BlockPos centerPos = BlockPos.containing(center);
            int baseX = centerPos.getX();
            int baseY = centerPos.getY();
            int baseZ = centerPos.getZ();
            int radiusInt = (int) Math.ceil(range);

            for (int x = -radiusInt; x <= radiusInt; x++) {
                double dx = center.x - (baseX + x + 0.5);
                double dxSq = dx * dx;
                for (int y = -radiusInt; y <= radiusInt; y++) {
                    double dy = center.y - (baseY + y + 0.5);
                    double dySq = dy * dy;
                    for (int z = -radiusInt; z <= radiusInt; z++) {
                        double dz = center.z - (baseZ + z + 0.5);
                        // 与原 getSphereBlocks 相同的球体判定，省掉每格 pos.getCenter() 的 Vec3 分配。
                        if (dxSq + dySq + dz * dz > rangeSq) continue;

                        pos.set(baseX + x, baseY + y, baseZ + z);
                        // 等价于原流水线里的 distinct()
                        if (!visitedPositions.add(pos.asLong())) continue;
                        if (!isValidHole(pos, playerPos, selfRangeSq)) continue;

                        tryPlaceBlock(pos.immutable(), perTick, placeRangeSq);
                        // 原实现达到上限后仍在空转，这里直接停，结果不变。
                        if (progress >= perTick) break enemyLoop;
                    }
                }
            }
        }
    }

    private void tryPlaceBlock(BlockPos pos, int perTick, double placeRangeSq) {
        if (pos == null) return;
        if (progress >= perTick) return;

        int block = getBlock();
        if (block == -1) return;

        if (!canPlace(pos, placeRangeSq)) return;

        if (breakCrystal.get()) {
            attackCrystal(pos);
        } else if (hasEntity(pos)) return;


        placeBlock(pos);

        progress++;
        lastPlaceTime = System.currentTimeMillis();
    }

    private int getBlock() {
        if (web.get()) {
            int webSlot = BagUtil.findItemInventorySlot(Blocks.COBWEB.asItem());
            if (webSlot != -1) return webSlot;
        }

        return BagUtil.findItemInventorySlot(Blocks.OBSIDIAN.asItem());
    }

    private List<Player> getEnemies() {
        // 性能：复用 List，避免每 tick 用 stream 收集一次。
        cachedEnemies.clear();
        double enemyRangeSq = enemyRange.get() * enemyRange.get();
        for (Player player : mc.level.players()) {
            if (player == mc.player || !player.isAlive()) continue;
            if (player.distanceToSqr(mc.player) > enemyRangeSq) continue;
            cachedEnemies.add(player);
        }
        return cachedEnemies;
    }

    public List<BlockPos> getSphereBlocks(Vec3 center, double radius) {
        // 保留原公开工具方法，但只在实际命中球体时创建 BlockPos（原来整格都建）。
        List<BlockPos> sphere = new ArrayList<>();
        BlockPos centerPos = BlockPos.containing(center);
        int radiusInt = (int) Math.ceil(radius);
        double radiusSq = radius * radius;

        for (int x = -radiusInt; x <= radiusInt; x++) {
            double dx = center.x - (centerPos.getX() + x + 0.5);
            double dxSq = dx * dx;
            for (int y = -radiusInt; y <= radiusInt; y++) {
                double dy = center.y - (centerPos.getY() + y + 0.5);
                double dySq = dy * dy;
                for (int z = -radiusInt; z <= radiusInt; z++) {
                    double dz = center.z - (centerPos.getZ() + z + 0.5);
                    if (dxSq + dySq + dz * dz <= radiusSq) {
                        sphere.add(centerPos.offset(x, y, z));
                    }
                }
            }
        }

        return sphere;
    }

    private Vec3 predictPosition(Player player) {
        Vec3 velocity = new Vec3(
            player.getX() - player.xOld,
            player.getY() - player.yOld,
            player.getZ() - player.zOld
        );
        return Via.getEntityPos(player).add(velocity.scale(predictTicks.get()));
    }

    private boolean isValidHole(BlockPos pos, Vec3 playerPos, double selfRangeSq) {
        double dx = (pos.getX() + 0.5) - playerPos.x;
        double dy = (pos.getY() + 0.5) - playerPos.y;
        double dz = (pos.getZ() + 0.5) - playerPos.z;
        if (dx * dx + dy * dy + dz * dz <= selfRangeSq) return false;
        return isHole(pos);
    }

    private boolean isHole(BlockPos pos) {
        if (!mc.level.getBlockState(pos).isAir()) return false;
        // 复用可变坐标，避免 pos.below() 每格新建 BlockPos。
        BlockPos.MutableBlockPos below = belowPos.set(pos.getX(), pos.getY() - 1, pos.getZ());
        return mc.level.getBlockState(below).isRedstoneConductor(mc.level, below);
    }

    private boolean canPlace(BlockPos pos, double placeRangeSq) {
        Vec3 playerPos = Via.getEntityPos(mc.player);
        double dx = playerPos.x - (pos.getX() + 0.5);
        double dy = playerPos.y - (pos.getY() + 0.5);
        double dz = playerPos.z - (pos.getZ() + 0.5);
        return dx * dx + dy * dy + dz * dz <= placeRangeSq &&
            mc.level.getBlockState(pos).canBeReplaced();
    }

    private boolean hasEntity(BlockPos pos) {
        return !mc.level.getEntities(null,
            new net.minecraft.world.phys.AABB(pos)).isEmpty();
    }

    private void attackCrystal(BlockPos pos) {

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity == null) continue;
            if (!entity.blockPosition().equals(pos)) continue;
            if (entity instanceof EndCrystal) {
                RotationManager.getInstance().register(new Rotation((float) Rotations.getYaw(entity), (float) Rotations.getPitch(entity)), humanized.profile());
                mc.gameMode.attack(mc.player, entity);
                mc.player.swing(InteractionHand.MAIN_HAND);
            }

            // 找到第一个符合条件的实体后执行操作并退出循环

            break; // 模拟 findFirst() 的行为：只处理第一个匹配项
        }


    }

    private void placeBlock(BlockPos pos) {

        int slot = BagUtil.findItemInventorySlot(Blocks.OBSIDIAN.asItem());
        if (slot == -1) {
            return;
        }
        BagUtil.doSwap(slot);
        BaritoneUtil.placeBlock(pos);
        BagUtil.doSwap(slot);
    }
}
