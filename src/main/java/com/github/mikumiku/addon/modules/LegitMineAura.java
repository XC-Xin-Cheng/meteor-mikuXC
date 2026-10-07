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
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BlockListSetting;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.RenderUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * 「Miku 合法挖掘光环」——把视角当成真人的鼠标来用的自动挖掘。
 *
 * <p>每 tick 从设定范围里挑一个最近的目标方块，把视角按「视角速度」逐步转过去，
 * 转到「瞄准容差」以内才开始挖；换目标之间还隔一段「挖掘间隔」。因为转头是
 * 连续的、挖掘是原版的 {@code startDestroyBlock/continueDestroyBlock} 流程，
 * 发出去的旋转和挖掘数据都跟真人手动挖一样，不会出现瞬间甩头、隔墙连挖的机器特征。</p>
 */
public class LegitMineAura extends BaseModule {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgAim = settings.createGroup("视角与挖掘");
    private final SettingGroup sgRender = settings.createGroup("渲染");

    /** 本模块自己的「类人化」设置组：旋转手抖。模式默认「跟随全局」。 */
    private final HumanizedSettings humanized = HumanizedSettings.builder(this, "Miku合法挖掘光环")
        .view().rotateNoise().build();

    // 通用

    private final Setting<List<Block>> blocks = sgGeneral.add(new BlockListSetting.Builder()
        .name("挖掘目标")
        .description("要挖的方块类型，多选；默认是各种矿石")
        .defaultValue(Arrays.asList(
            Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE,
            Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE,
            Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE,
            Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE, Blocks.NETHER_GOLD_ORE,
            Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE,
            Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE,
            Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE,
            Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE,
            Blocks.NETHER_QUARTZ_ORE,
            Blocks.ANCIENT_DEBRIS
        ))
        .build()
    );

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("挖掘范围")
        .description("可挖方块的最大距离（格）")
        .defaultValue(4.5)
        .min(1.0)
        .sliderRange(1.0, 6.0)
        .build()
    );

    private final Setting<Boolean> pauseOnUse = sgGeneral.add(new BoolSetting.Builder()
        .name("使用物品时暂停")
        .description("吃东西、拉弓、举盾时不转视角也不挖")
        .defaultValue(true)
        .build()
    );

    // 视角与挖掘

    private final Setting<Double> aimSpeed = sgAim.add(new DoubleSetting.Builder()
        .name("视角速度")
        .description("每 tick 最多朝目标方块转多少度；越小越像真人")
        .defaultValue(22.0)
        .min(1.0)
        .sliderRange(1.0, 60.0)
        .build()
    );

    private final Setting<Double> aimTolerance = sgAim.add(new DoubleSetting.Builder()
        .name("瞄准容差")
        .description("视角和目标方块中心相差多少度以内才开始挖")
        .defaultValue(10.0)
        .min(1.0)
        .sliderRange(1.0, 30.0)
        .build()
    );

    private final Setting<Integer> mineDelayMs = sgAim.add(new IntSetting.Builder()
        .name("挖掘间隔")
        .description("换一个方块后至少等多少毫秒再开始挖")
        .defaultValue(80)
        .min(0)
        .sliderRange(0, 1000)
        .build()
    );

    private final Setting<Boolean> swing = sgAim.add(new BoolSetting.Builder()
        .name("摆手")
        .description("挖掘时摆动手臂")
        .defaultValue(true)
        .build()
    );

    // 渲染

    private final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder()
        .name("渲染目标")
        .description("高亮当前正在挖的方块")
        .defaultValue(true)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("渲染模式")
        .description("目标方块的渲染方式")
        .defaultValue(ShapeMode.Both)
        .visible(render::get)
        .build()
    );

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("填充颜色")
        .description("目标方块的填充颜色")
        .defaultValue(new SettingColor(255, 200, 60, 60))
        .visible(render::get)
        .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("轮廓颜色")
        .description("目标方块的轮廓颜色")
        .defaultValue(new SettingColor(255, 200, 60, 255))
        .visible(render::get)
        .build()
    );

    private final List<BlockPos> candidates = new ArrayList<>();

    private BlockPos currentTarget;
    private long nextMineAt = 0L;

    public LegitMineAura() {
        super(BaseModule.CATEGORY_MIKU_LEGIT, "Miku合法挖掘光环",
            "合法挖掘光环：视角按真人速度逐步移到指定方块上再开原版挖掘流程，换目标有可调间隔");
    }

    @Override
    public void onDeactivate() {
        candidates.clear();
        currentTarget = null;
        nextMineAt = 0L;
        if (mc.gameMode != null && mc.player != null && mc.gameMode.isDestroying()) {
            mc.gameMode.stopDestroyBlock();
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.level == null) return;
        if (!mc.player.isAlive() || PlayerUtils.getGameMode() == GameType.SPECTATOR) return;
        // 注意：这里不能把 mc.gameMode.isDestroying() 也算成“正在用物品”，
        // 否则我们自己一旦开始挖，下一 tick 就会被这条判定挡住，永远挖不下去。
        if (pauseOnUse.get() && mc.player.isUsingItem()) return;

        findTarget();
        if (currentTarget == null) return;

        Humanized.Profile hp = humanized.profile();

        float targetYaw = (float) Rotations.getYaw(currentTarget);
        float targetPitch = Mth.clamp((float) Rotations.getPitch(currentTarget), -90.0f, 90.0f);

        float currentYaw = mc.player.getYRot();
        float currentPitch = mc.player.getXRot();

        float dYaw = Mth.wrapDegrees(targetYaw - currentYaw);
        float dPitch = targetPitch - currentPitch;

        float step = aimSpeed.get().floatValue();
        if (hp.enabled && hp.viewInput) {
            step *= 1.0f + Humanized.jitter(hp, 0.15f);
            step = Math.max(1.0f, step);
        }

        float newYaw = currentYaw + Mth.clamp(dYaw, -step, step);
        float newPitch = Mth.clamp(currentPitch + Mth.clamp(dPitch, -step, step), -90.0f, 90.0f);

        mc.player.setYRot(newYaw);
        mc.player.setXRot(newPitch);

        float remainingYaw = Math.abs(Mth.wrapDegrees(targetYaw - newYaw));
        float remainingPitch = Math.abs(targetPitch - newPitch);
        if (remainingYaw > aimTolerance.get() || remainingPitch > aimTolerance.get()) return;

        long now = System.currentTimeMillis();
        if (now < nextMineAt) return;

        Direction direction = BlockUtils.getDirection(currentTarget);
        if (mc.gameMode.isDestroying()) {
            mc.gameMode.continueDestroyBlock(currentTarget, direction);
        } else {
            mc.gameMode.startDestroyBlock(currentTarget, direction);
        }
        if (swing.get()) mc.player.swing(InteractionHand.MAIN_HAND);
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!render.get() || currentTarget == null) return;
        RenderUtils.renderTickingBlock(currentTarget, sideColor.get(), lineColor.get(), shapeMode.get(), 0, 8, true, false);
    }

    /** 在范围内找最近的、在列表里的可挖方块；目标没变就保持不动，避免来回甩头。 */
    private void findTarget() {
        // 当前目标还有效就不换
        if (currentTarget != null && isValid(currentTarget)) return;

        candidates.clear();

        BlockPos center = mc.player.blockPosition();
        int r = (int) Math.ceil(range.get());
        double rangeSq = range.get() * range.get();
        Vec3 eye = mc.player.getEyePosition();

        for (BlockPos pos : BlockPos.betweenClosed(
            center.getX() - r, center.getY() - r, center.getZ() - r,
            center.getX() + r, center.getY() + r, center.getZ() + r)) {

            if (Vec3.atCenterOf(pos).distanceToSqr(eye) > rangeSq) continue;
            if (!blocks.get().contains(mc.level.getBlockState(pos).getBlock())) continue;
            if (!BlockUtils.canBreak(pos, mc.level.getBlockState(pos))) continue;

            candidates.add(pos.immutable());
        }

        if (candidates.isEmpty()) {
            currentTarget = null;
            if (mc.gameMode.isDestroying()) mc.gameMode.stopDestroyBlock();
            return;
        }

        candidates.sort(Comparator.comparingDouble(pos -> Vec3.atCenterOf(pos).distanceToSqr(eye)));
        currentTarget = candidates.getFirst();
        nextMineAt = System.currentTimeMillis() + mineDelayMs.get();
    }

    /** 方块还在、还能挖、还在范围内。 */
    private boolean isValid(BlockPos pos) {
        BlockState state = mc.level.getBlockState(pos);
        if (!blocks.get().contains(state.getBlock())) return false;
        if (!BlockUtils.canBreak(pos, state)) return false;
        return mc.player.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos)) <= range.get() * range.get();
    }
}
