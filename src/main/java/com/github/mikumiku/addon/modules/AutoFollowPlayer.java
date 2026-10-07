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
import com.github.mikumiku.addon.util.Humanized;
import com.github.mikumiku.addon.util.HumanizedSettings;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.Target;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.util.Mth;
import net.minecraft.world.level.GameType;

import java.util.ArrayList;
import java.util.List;

public class AutoFollowPlayer extends BaseModule {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgRender = this.settings.createGroup("渲染设置");

    /**
     * 本模块自己的「类人化」设置组：跟随目标时视角注入手抖。模式默认「跟随全局」。
     */
    private final HumanizedSettings humanized = HumanizedSettings.builder(this, "AutoFollowPlayer").view().rotateNoise().build();

    private final List<Entity> targets = new ArrayList<>();

    // ──────────────────────────────── 通用设置 ────────────────────────────────

    private final Setting<SortPriority> priority = this.sgGeneral.add(
        new EnumSetting.Builder<SortPriority>()
            .name("目标优先级")
            .description("当检测到多个敌人时，按何种顺序优先锁定目标")
            .defaultValue(SortPriority.LowestDistance)
            .build()
    );

    private final Setting<Double> range = this.sgGeneral.add(
        new DoubleSetting.Builder()
            .name("检测范围")
            .description("可锁定敌人的最大距离范围")
            .defaultValue(50.0)
            .range(0.0, 192.0)
            .build()
    );

    private final Setting<Boolean> onlyAir = this.sgGeneral.add(
        new BoolSetting.Builder()
            .name("仅限空中目标")
            .description("只对处于空中的目标进行检测与锁定")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> preventGround = this.sgGeneral.add(
        new BoolSetting.Builder()
            .name("防止落地")
            .description("启用后，角色在执行期间不会触地")
            .defaultValue(true)
            .build()
    );

// ──────────────────────────────── 渲染设置 ────────────────────────────────

    private final Setting<Boolean> render = this.sgRender.add(
        new BoolSetting.Builder()
            .name("启用渲染")
            .description("是否在屏幕上渲染锁定目标的标识")
            .defaultValue(true)
            .build()
    );

    private final Setting<ShapeMode> shapeMode = this.sgRender.add(
        new EnumSetting.Builder<ShapeMode>()
            .name("渲染模式")
            .description("决定目标标识的显示方式（线框 / 填充 / 双重）")
            .defaultValue(ShapeMode.Both)
            .visible(this.render::get)
            .build()
    );

    private final Setting<SettingColor> sideColor = this.sgRender.add(
        new ColorSetting.Builder()
            .name("填充颜色")
            .description("目标区域的填充部分颜色")
            .defaultValue(new SettingColor(160, 0, 225, 35))
            .visible(() -> this.shapeMode.get().sides())
            .build()
    );

    private final Setting<SettingColor> lineColor = this.sgRender.add(
        new ColorSetting.Builder()
            .name("轮廓颜色")
            .description("目标轮廓线的颜色")
            .defaultValue(new SettingColor(255, 255, 255, 50))
            .visible(() -> this.render.get() && this.shapeMode.get().lines())
            .build()
    );


    public AutoFollowPlayer() {
        super(BaseModule.CATEGORY_MIKU_COMBAT, "鞘翅追人", "在空中鞘翅滑翔时，自动对准视角追人");
    }

    @Override
    public void onActivate() {
        this.targets.clear();
        TargetUtils.getList(
            this.targets,
            entity -> {
                if (entity instanceof Player player) {
                    if (Friends.get().isFriend(player)) {
                        return false;
                    } else if (entity == this.mc.player) {
                        return false;
                    } else {
                        AABB hitbox = entity.getBoundingBox();
                        return PlayerUtils.isWithin(
                            Mth.clamp(this.mc.player.getX(), hitbox.minX, hitbox.maxX),
                            Mth.clamp(this.mc.player.getY(), hitbox.minY, hitbox.maxY),
                            Mth.clamp(this.mc.player.getZ(), hitbox.minZ, hitbox.maxZ),
                            this.range.get()
                        );
                    }
                } else {
                    return false;
                }
            },
            this.priority.get(),
            1
        );
        BagUtil.quickUse(Items.FIREWORK_ROCKET);
    }

    @Override
    public void onDeactivate() {
        this.targets.clear();
    }

    @EventHandler
    private void onRender3d(Render3DEvent event) {
        if (this.mc.player.isAlive() && PlayerUtils.getGameMode() != GameType.SPECTATOR) {
            // 如果没有目标，直接返回，不执行任何逻辑
            if (this.targets.isEmpty()) {
                return;
            }

            if (!this.onlyAir.get() || !this.mc.player.onGround()) {
                Entity primary = this.targets.getFirst();

                // 「类人化」打开时给跟随视角加一点手抖，不做每帧都精确锁死的机械转头
                Humanized.Profile hp = humanized.profile();
                float yaw = (float) Rotations.getYaw(primary);
                float pitch = primary.onGround() && this.preventGround.get() ? -90.0F : (float) Rotations.getPitch(primary, Target.Body);
                if (hp.enabled && hp.viewInput && hp.affectPackets) {
                    yaw += Humanized.jitter(hp, hp.noiseDegrees);
                    pitch = Mth.clamp(pitch + Humanized.jitter(hp, hp.noiseDegrees * 0.7f), -90.0f, 90.0f);
                }

                if (!this.preventGround.get() || !primary.onGround()) {
                    MeteorClient.mc.player.setYRot(yaw);
                }

                MeteorClient.mc.player.setXRot(pitch);
            }

            try {
                Entity lastAttackedEntity = this.targets.getFirst();
                if (this.targets.getFirst() != null) {
                    double x = Mth.lerp(event.tickDelta, lastAttackedEntity.xOld, lastAttackedEntity.getX())
                        - lastAttackedEntity.getX();
                    double y = Mth.lerp(event.tickDelta, lastAttackedEntity.yOld, lastAttackedEntity.getY())
                        - lastAttackedEntity.getY();
                    double z = Mth.lerp(event.tickDelta, lastAttackedEntity.zOld, lastAttackedEntity.getZ())
                        - lastAttackedEntity.getZ();
                    AABB box = lastAttackedEntity.getBoundingBox();
                    event.renderer
                        .box(
                            x + box.minX,
                            y + box.minY,
                            z + box.minZ,
                            x + box.maxX,
                            y + box.maxY,
                            z + box.maxZ,
                            this.sideColor.get(),
                            this.lineColor.get(),
                            this.shapeMode.get(),
                            0
                        );
                }
            } catch (Exception var10) {
            }
        }
    }

    @Override
    public String getInfoString() {
        return !this.targets.isEmpty() ? EntityUtils.getName(this.targets.getFirst()) : null;
    }
}
