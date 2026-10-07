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
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.util.Mth;

/**
 * 抬头？—— 只骗服务器，不骗自己。
 *
 * <p>原理：客户端每 tick 只在“位置或朝向发生变化”时才向服务器发送移动包
 * （{@code LocalPlayer#sendPosition}）。若只额外发一次朝向包，之后玩家移动/转头时
 * 发出去的真实朝向会把伪造值覆盖掉。因此这里在 {@code sendPosition} 的 TAIL
 * 注入（见 {@code mixin/LookUpMixin}），在客户端真实移动包之后立刻补发一个
 * 只带旋转的 {@link ServerboundMovePlayerPacket.Rot}，让服务器最后看到的俯仰角
 * 永远是伪造的“抬头”角度，而本地视角保持不变。</p>
 *
 * <p>为了不被反作弊的“瞬间大幅转头”检测命中，默认从当前真实俯仰角平滑过渡到
 * 目标角度。另外每隔若干 tick 主动重发一次，防止服务器端状态被其它包重置。</p>
 */
public class LookUpModule extends BaseModule {

    // 供 mixin 读取的静态欺骗状态（模块只有一个实例）
    private static volatile boolean spoofing = false;
    private static volatile float spoofPitch = -90.0f;
    private static volatile boolean smoothEnabled = true;
    private static volatile float smoothStep = 15.0f;

    // 「类人化输入」开启时用贝塞尔曲线取代线性过渡
    private static Humanized.BezierPath lookPath = null;
    private static float lookPathTarget = 0.0f;
    // 模块当前生效的类人化档位，供 mixin（静态入口）读取
    private static volatile Humanized.Profile lookProfile = Humanized.OFF;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    /**
     * 本模块自己的「类人化」设置组：贝塞尔曲线视角、视角手抖、重发节奏抖动。
     * 模式默认「跟随全局」，与以前的行为一致。
     */
    private final HumanizedSettings humanized = HumanizedSettings.builder(this, "抬头？").view().resend().build();

    private final Setting<Double> targetPitch = sgGeneral.add(new DoubleSetting.Builder()
        .name("抬头角度")
        .description("欺骗服务器看到的俯仰角：-90 为正上方（抬头看天），0 为平视")
        .defaultValue(-90.0)
        .min(-90.0)
        .max(90.0)
        .sliderMin(-90.0)
        .sliderMax(90.0)
        .build()
    );

    private final Setting<Boolean> smooth = sgGeneral.add(new BoolSetting.Builder()
        .name("平滑抬头")
        .description("从当前真实角度逐步转到目标角度，避免瞬间大幅转头被反作弊判定为异常")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> smoothSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("抬头速度")
        .description("平滑模式下每 tick 转动的角度（度/tick）")
        .defaultValue(15.0)
        .min(1.0)
        .max(90.0)
        .sliderRange(1.0, 45.0)
        .visible(smooth::get)
        .build()
    );

    private final Setting<Integer> interval = sgGeneral.add(new IntSetting.Builder()
        .name("保持间隔")
        .description("每隔多少 tick 重发一次伪造朝向，防止服务器端朝向被其它数据包覆盖")
        .defaultValue(10)
        .min(1)
        .sliderRange(1, 40)
        .build()
    );

    private final Setting<Boolean> syncClient = sgGeneral.add(new BoolSetting.Builder()
        .name("同步本地视角")
        .description("同时把自己客户端的视角也转到抬头方向；关闭时只骗服务器，本地画面不变")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> restoreOnDisable = sgGeneral.add(new BoolSetting.Builder()
        .name("关闭时恢复")
        .description("关闭模块时把真实朝向发回服务器，避免服务器一直认为你在抬头")
        .defaultValue(true)
        .build()
    );

    private int tickCounter = 0;

    public LookUpModule() {
        super(BaseModule.CATEGORY_MIKU_PRO, "抬头？", "欺骗服务器，让服务器以为你一直在抬头看天");
    }

    @Override
    public void onActivate() {
        super.onActivate();

        smoothEnabled = smooth.get();
        smoothStep = smoothSpeed.get().floatValue();
        // 从当前真实角度出发，第一帧不产生突变
        spoofPitch = mc.player != null ? mc.player.getXRot() : targetPitch.get().floatValue();
        lookPath = null;
        lookProfile = humanized.profile();
        tickCounter = 0;
        spoofing = true;

        sendSpoof(mc.player);
    }

    @Override
    public void onDeactivate() {
        spoofing = false;
        lookPath = null;
        lookProfile = Humanized.OFF;

        if (restoreOnDisable.get() && mc.player != null) {
            // 把真实朝向还给服务器，否则站着不动时服务器会一直保留伪造角度
            mc.player.connection.send(new ServerboundMovePlayerPacket.Rot(
                mc.player.getYRot(),
                mc.player.getXRot(),
                mc.player.onGround(),
                mc.player.horizontalCollision));
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!spoofing || mc.player == null || mc.level == null) return;

        smoothEnabled = smooth.get();
        smoothStep = smoothSpeed.get().floatValue();

        // 每次 tick 刷新一遍本模块的类人化档位，改设置立即生效
        Humanized.Profile profile = humanized.profile();
        lookProfile = profile;

        advancePitch(profile);

        if (syncClient.get()) {
            mc.player.setXRot(spoofPitch);
        }

        if (++tickCounter >= Humanized.jitterTicks(profile, interval.get(), profile.resendJitter)) {
            tickCounter = 0;
            sendSpoof(mc.player);
        }
    }

    /**
     * 让伪造俯仰角向目标角度靠拢。
     *
     * <p>「类人化输入」开启且允许贝塞尔视角时，走一条带过冲的三次贝塞尔曲线；
     * 否则维持原来的“每 tick 匀速转一点”的线性方式。</p>
     */
    private void advancePitch(Humanized.Profile profile) {
        float target = targetPitch.get().floatValue();
        if (!smoothEnabled) {
            spoofPitch = target;
            lookPath = null;
            return;
        }

        if (profile.bezierActive()) {
            // 目标变了就当前角度重新起一条曲线，避免拐出奇怪的折线
            if (lookPath == null || Math.abs(lookPathTarget - target) > 0.05f) {
                lookPath = new Humanized.BezierPath(profile, 0.0f, spoofPitch, 0.0f, target,
                    profile.curveTicks, profile.overshoot);
                lookPathTarget = target;
            }

            if (lookPath.done()) {
                spoofPitch = target;
            } else {
                spoofPitch = lookPath.next()[1];
            }
            return;
        }

        lookPath = null;
        float delta = target - spoofPitch;
        if (Math.abs(delta) <= smoothStep) {
            spoofPitch = target;
        } else {
            spoofPitch += Math.copySign(smoothStep, delta);
        }
    }

    /**
     * 补发一个只带旋转的移动包，把伪造的俯仰角推给服务器。
     * 由模块自身（首次启用/定时保持）与 {@code LookUpMixin}（每次真实移动包之后）调用。
     *
     * <p>「类人化输入」开启时，在真正发出去的俯仰角上叠加一点高斯手抖，
     * 让服务器看到的不是一条笔直的完美曲线。</p>
     */
    public static void sendSpoof(LocalPlayer player) {
        if (!spoofing || player == null) return;

        Humanized.Profile profile = lookProfile;
        float pitch = Mth.clamp(spoofPitch + Humanized.jitter(profile, profile.noiseDegrees), -90.0f, 90.0f);
        player.connection.send(new ServerboundMovePlayerPacket.Rot(
            player.getYRot(),
            pitch,
            player.onGround(),
            player.horizontalCollision));
    }
}
