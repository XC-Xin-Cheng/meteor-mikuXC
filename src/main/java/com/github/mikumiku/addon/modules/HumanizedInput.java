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
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.orbit.EventHandler;

/**
 * 「类人化输入」——给自动化输入加一层“人味”。
 *
 * <p>开启后，本模组自己的视角旋转与鼠标连点不再追求完美精度：</p>
 * <ul>
 *   <li><b>贝塞尔曲线视角</b>：转头走三次贝塞尔曲线，带自然的过冲与回落，
 *       而不是瞬间拉枪或匀速直线；</li>
 *   <li><b>生物噪声</b>：视角与节奏里注入均值为 0 的高斯“手抖”，
 *       打破每一包都一模一样的模式；</li>
 *   <li><b>可变延迟</b>：连点间隔上叠加 10~40ms 的神经/硬件延迟；</li>
 *   <li><b>错开松开</b>：按住后松开按键的时间左右错开，不做“齐步走”。</li>
 * </ul>
 *
 * <p>本模块默认关闭，只有你主动打开才生效；关掉后所有相关行为立即恢复成
 * 原来的机械方式。分组开关可以只对视角或只对鼠标生效。</p>
 */
public class HumanizedInput extends BaseModule {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgView = settings.createGroup("视角");
    private final SettingGroup sgClick = settings.createGroup("鼠标连点");

    // ---- 通用 ----

    private final Setting<Boolean> notify = sgGeneral.add(new BoolSetting.Builder()
        .name("启用提示")
        .description("启用/关闭时在聊天栏提示当前生效的类人化项")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> viewEnabled = sgGeneral.add(new BoolSetting.Builder()
        .name("视角类人化")
        .description("对视角旋转（贝塞尔曲线、手抖、重发节奏）生效")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> clickEnabled = sgGeneral.add(new BoolSetting.Builder()
        .name("鼠标类人化")
        .description("对鼠标连点（非均匀节奏、可变延迟、错开松开）生效")
        .defaultValue(true)
        .build()
    );

    // ---- 视角 ----

    private final Setting<Boolean> bezier = sgView.add(new BoolSetting.Builder()
        .name("贝塞尔曲线视角")
        .description("用三次贝塞尔曲线过渡视角，带自然过冲与修正，而不是瞬间拉枪")
        .defaultValue(true)
        .visible(viewEnabled::get)
        .build()
    );

    private final Setting<Integer> curveTicks = sgView.add(new IntSetting.Builder()
        .name("曲线时长")
        .description("一条贝塞尔曲线用几个 tick 走完，越长越缓")
        .defaultValue(4)
        .min(1)
        .max(20)
        .sliderRange(1, 12)
        .visible(() -> viewEnabled.get() && bezier.get())
        .build()
    );

    private final Setting<Double> overshoot = sgView.add(new DoubleSetting.Builder()
        .name("过冲幅度")
        .description("视角越过目标再回落的比例（%），0 表示不过冲")
        .defaultValue(15.0)
        .min(0.0)
        .max(45.0)
        .sliderRange(0.0, 45.0)
        .visible(() -> viewEnabled.get() && bezier.get())
        .build()
    );

    private final Setting<Double> noiseDegrees = sgView.add(new DoubleSetting.Builder()
        .name("视角手抖")
        .description("发往服务器的视角上叠加的高斯噪声幅度（度），0 表示不加")
        .defaultValue(0.6)
        .min(0.0)
        .max(3.0)
        .sliderRange(0.0, 2.0)
        .visible(viewEnabled::get)
        .build()
    );

    private final Setting<Boolean> affectPackets = sgView.add(new BoolSetting.Builder()
        .name("注入其它模块旋转")
        .description("让本模组其它模块发给服务器的视角也带上手抖（战斗/建造瞄准）")
        .defaultValue(true)
        .visible(viewEnabled::get)
        .build()
    );

    private final Setting<Integer> resendJitter = sgView.add(new IntSetting.Builder()
        .name("重发节奏抖动")
        .description("「抬头？」这类定时重发的间隔抖动比例（%）")
        .defaultValue(35)
        .min(0)
        .max(60)
        .sliderRange(0, 60)
        .visible(viewEnabled::get)
        .build()
    );

    // ---- 鼠标 ----

    private final Setting<Boolean> nonUniform = sgClick.add(new BoolSetting.Builder()
        .name("非均匀连点节奏")
        .description("连点间隔按钟形分布抖动，而不是每次都在区间里均匀随机")
        .defaultValue(true)
        .visible(clickEnabled::get)
        .build()
    );

    private final Setting<Double> clickJitter = sgClick.add(new DoubleSetting.Builder()
        .name("节奏抖动")
        .description("连点间隔的抖动比例（%），越大越不规律")
        .defaultValue(35.0)
        .min(0.0)
        .max(60.0)
        .sliderRange(0.0, 60.0)
        .visible(() -> clickEnabled.get() && nonUniform.get())
        .build()
    );

    private final Setting<Boolean> variableLatency = sgClick.add(new BoolSetting.Builder()
        .name("可变延迟")
        .description("在每次点击前叠加一段神经/硬件延迟，模拟真人的反应时间")
        .defaultValue(true)
        .visible(clickEnabled::get)
        .build()
    );

    private final Setting<Integer> latencyMin = sgClick.add(new IntSetting.Builder()
        .name("最低延迟")
        .description("可变延迟下限（毫秒）")
        .defaultValue(10)
        .min(0)
        .max(60)
        .sliderRange(0, 60)
        .visible(() -> clickEnabled.get() && variableLatency.get())
        .build()
    );

    private final Setting<Integer> latencyMax = sgClick.add(new IntSetting.Builder()
        .name("最高延迟")
        .description("可变延迟上限（毫秒），取值偏向低端，偶尔慢一下")
        .defaultValue(40)
        .min(0)
        .max(120)
        .sliderRange(0, 120)
        .visible(() -> clickEnabled.get() && variableLatency.get())
        .build()
    );

    private final Setting<Boolean> stagger = sgClick.add(new BoolSetting.Builder()
        .name("错开按键松开")
        .description("按住后的松开时间左右错开，避免所有按键同一刻整齐松开")
        .defaultValue(true)
        .visible(clickEnabled::get)
        .build()
    );

    private final Setting<Integer> staggerMax = sgClick.add(new IntSetting.Builder()
        .name("松开错开上限")
        .description("松开时间最多错开多少毫秒")
        .defaultValue(12)
        .min(0)
        .max(50)
        .sliderRange(0, 50)
        .visible(() -> clickEnabled.get() && stagger.get())
        .build()
    );

    public HumanizedInput() {
        super(BaseModule.CATEGORY_MIKU_PRO, "类人化输入",
            "给视角旋转与鼠标连点加“人味”：贝塞尔曲线视角、生物噪声、可变延迟、错开按键松开；默认关闭，可分组开关");
    }

    @Override
    public void onActivate() {
        super.onActivate();
        apply();

        if (notify.get()) {
            info("类人化输入已启用：视角%s，鼠标%s",
                viewEnabled.get() ? "开" : "关",
                clickEnabled.get() ? "开" : "关");
        }
    }

    @Override
    public void onDeactivate() {
        // 全局档：关掉后所有“跟随全局”的模块立即恢复机械输入
        Humanized.GLOBAL.enabled = false;
        Humanized.GLOBAL.viewInput = false;
        Humanized.GLOBAL.clickInput = false;

        if (notify.get()) {
            info("类人化输入已关闭，跟随全局的模块恢复机械方式");
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        // 设置改了立即生效，不用重开模块
        apply();
    }

    /** 把界面上的设置同步到 {@link Humanized#GLOBAL 全局档}。 */
    private void apply() {
        Humanized.Profile p = Humanized.GLOBAL;
        p.label = "全局";
        p.enabled = true;
        p.viewInput = viewEnabled.get();
        p.clickInput = clickEnabled.get();

        p.bezierEnabled = bezier.get();
        p.curveTicks = curveTicks.get();
        p.overshoot = overshoot.get().floatValue() / 100.0f;
        p.noiseDegrees = noiseDegrees.get().floatValue();
        p.affectPackets = affectPackets.get();
        p.resendJitter = resendJitter.get() / 100.0f;

        p.nonUniformClicks = nonUniform.get();
        p.clickJitter = clickJitter.get().floatValue() / 100.0f;
        p.variableLatency = variableLatency.get();
        p.latencyMinMs = latencyMin.get();
        p.latencyMaxMs = latencyMax.get();
        p.staggerRelease = stagger.get();
        p.staggerMaxMs = staggerMax.get();
    }
}
