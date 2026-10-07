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

import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;

/**
 * 往任意模块的配置界面里塞一个「类人化」设置组。
 *
 * <p>以前只有「类人化输入」一个总开关，在连点器/战斗模块的界面上看不到任何相关选项。
 * 现在每个会移动视角或鼠标的模块都能挂一组自己的类人化设置，模式三选一：</p>
 * <ul>
 *   <li><b>关闭</b>：本模块保持机械输入，不受全局影响；</li>
 *   <li><b>跟随全局</b>：用「类人化输入」模块的整体设置（默认值，行为与以前一致）；</li>
 *   <li><b>自定义</b>：在本模块里单独调贝塞尔曲线、手抖、延迟、错开松开等参数。</li>
 * </ul>
 *
 * <p>用法：在模块构造里存一个字段 {@code private final HumanizedSettings humanized =
 * HumanizedSettings.builder(this, "连点器").click().build();}，在用到输入的地方调
 * {@link #profile()} 拿到当前生效的 {@link Humanized.Profile} 再传给引擎。</p>
 */
public final class HumanizedSettings {

    /** 类人化模式。 */
    public enum Mode {
        OFF("关闭"),
        FOLLOW_GLOBAL("跟随全局"),
        CUSTOM("自定义");

        private final String title;

        Mode(String title) {
            this.title = title;
        }

        @Override
        public String toString() {
            return title;
        }
    }

    private final String label;
    private final boolean view;
    private final boolean click;
    private final boolean resend;
    private final boolean rotateNoise;

    /** 每次 {@link #profile()} 都刷新这个实例，调用方可以直接持有。 */
    private final Humanized.Profile resolved = new Humanized.Profile();

    private final Setting<Mode> mode;

    // 视角
    private final Setting<Boolean> viewInput;
    private final Setting<Boolean> bezier;
    private final Setting<Integer> curveTicks;
    private final Setting<Double> overshoot;
    private final Setting<Double> noiseDegrees;
    private final Setting<Boolean> rotateNoiseSetting;
    private final Setting<Integer> resendJitter;

    // 鼠标
    private final Setting<Boolean> clickInput;
    private final Setting<Boolean> nonUniform;
    private final Setting<Double> clickJitter;
    private final Setting<Boolean> variableLatency;
    private final Setting<Integer> latencyMin;
    private final Setting<Integer> latencyMax;
    private final Setting<Boolean> stagger;
    private final Setting<Integer> staggerMax;

    private HumanizedSettings(Module module, String label,
                              boolean view, boolean click, boolean resend, boolean rotateNoise) {
        this.label = label;
        this.view = view;
        this.click = click;
        this.resend = resend;
        this.rotateNoise = rotateNoise;
        this.resolved.label = label;

        SettingGroup group = module.settings.createGroup("类人化");

        this.mode = group.add(new EnumSetting.Builder<Mode>()
            .name("类人化模式")
            .description("关闭=本模块保持机械输入；跟随全局=用「类人化输入」模块的设置；自定义=在下面单独调")
            .defaultValue(Mode.FOLLOW_GLOBAL)
            .build()
        );

        // ---- 视角 ----
        this.viewInput = group.add(new BoolSetting.Builder()
            .name("视角类人化")
            .description("对本模块的视角旋转生效（贝塞尔曲线、手抖、重发节奏）")
            .defaultValue(true)
            .visible(() -> view && custom())
            .build()
        );

        this.bezier = group.add(new BoolSetting.Builder()
            .name("贝塞尔曲线视角")
            .description("用三次贝塞尔曲线过渡视角，带自然过冲与修正，而不是瞬间拉枪或匀速直线")
            .defaultValue(true)
            .visible(() -> view && custom() && viewInput.get())
            .build()
        );

        this.curveTicks = group.add(new IntSetting.Builder()
            .name("曲线时长")
            .description("一条贝塞尔曲线用几个 tick 走完，越长越缓")
            .defaultValue(4)
            .min(1)
            .max(20)
            .sliderRange(1, 12)
            .visible(() -> view && custom() && viewInput.get() && bezier.get())
            .build()
        );

        this.overshoot = group.add(new DoubleSetting.Builder()
            .name("过冲幅度")
            .description("视角越过目标再回落的比例（%），0 表示不过冲")
            .defaultValue(15.0)
            .min(0.0)
            .max(45.0)
            .sliderRange(0.0, 45.0)
            .visible(() -> view && custom() && viewInput.get() && bezier.get())
            .build()
        );

        this.noiseDegrees = group.add(new DoubleSetting.Builder()
            .name("视角手抖")
            .description("视角上叠加的高斯噪声幅度（度），0 表示不加")
            .defaultValue(0.6)
            .min(0.0)
            .max(3.0)
            .sliderRange(0.0, 2.0)
            .visible(() -> view && custom() && viewInput.get())
            .build()
        );

        this.rotateNoiseSetting = group.add(new BoolSetting.Builder()
            .name("旋转注入手抖")
            .description("本模块发往服务器的旋转也带一点手抖，打破“每一包都精确对准中心”的完美数据")
            .defaultValue(true)
            .visible(() -> rotateNoise && custom() && viewInput.get())
            .build()
        );

        this.resendJitter = group.add(new IntSetting.Builder()
            .name("重发节奏抖动")
            .description("定时重发视角的间隔抖动比例（%）")
            .defaultValue(35)
            .min(0)
            .max(60)
            .sliderRange(0, 60)
            .visible(() -> resend && custom() && viewInput.get())
            .build()
        );

        // ---- 鼠标 ----
        this.clickInput = group.add(new BoolSetting.Builder()
            .name("鼠标类人化")
            .description("对本模块的点击节奏生效（非均匀间隔、可变延迟、错开松开）")
            .defaultValue(true)
            .visible(() -> click && custom())
            .build()
        );

        this.nonUniform = group.add(new BoolSetting.Builder()
            .name("非均匀连点节奏")
            .description("点击间隔按钟形分布抖动，而不是每次都在区间里均匀随机")
            .defaultValue(true)
            .visible(() -> click && custom() && clickInput.get())
            .build()
        );

        this.clickJitter = group.add(new DoubleSetting.Builder()
            .name("节奏抖动")
            .description("点击间隔的抖动比例（%），越大越不规律")
            .defaultValue(35.0)
            .min(0.0)
            .max(60.0)
            .sliderRange(0.0, 60.0)
            .visible(() -> click && custom() && clickInput.get() && nonUniform.get())
            .build()
        );

        this.variableLatency = group.add(new BoolSetting.Builder()
            .name("可变延迟")
            .description("每次点击/出手前叠加一段神经与硬件延迟，模拟真人的反应时间")
            .defaultValue(true)
            .visible(() -> click && custom() && clickInput.get())
            .build()
        );

        this.latencyMin = group.add(new IntSetting.Builder()
            .name("最低延迟")
            .description("可变延迟下限（毫秒）")
            .defaultValue(10)
            .min(0)
            .max(60)
            .sliderRange(0, 60)
            .visible(() -> click && custom() && clickInput.get() && variableLatency.get())
            .build()
        );

        this.latencyMax = group.add(new IntSetting.Builder()
            .name("最高延迟")
            .description("可变延迟上限（毫秒），取值偏向低端，偶尔慢一下")
            .defaultValue(40)
            .min(0)
            .max(120)
            .sliderRange(0, 120)
            .visible(() -> click && custom() && clickInput.get() && variableLatency.get())
            .build()
        );

        this.stagger = group.add(new BoolSetting.Builder()
            .name("错开按键松开")
            .description("按住后的松开时间左右错开，避免所有按键同一刻整齐松开")
            .defaultValue(true)
            .visible(() -> click && custom() && clickInput.get())
            .build()
        );

        this.staggerMax = group.add(new IntSetting.Builder()
            .name("松开错开上限")
            .description("松开时间最多错开多少毫秒")
            .defaultValue(12)
            .min(0)
            .max(50)
            .sliderRange(0, 50)
            .visible(() -> click && custom() && clickInput.get() && stagger.get())
            .build()
        );
    }

    private boolean custom() {
        return mode.get() == Mode.CUSTOM;
    }

    /**
     * 拿到当前生效的档位。每次调用都按界面上的设置刷新一遍，改设置立即生效。
     * 关闭模式返回一个不生效的档位，跟随全局模式返回全局档的副本。
     */
    public Humanized.Profile profile() {
        Mode m = mode.get();

        if (m == Mode.OFF) {
            resolved.copyFrom(Humanized.OFF);
            resolved.label = label;
            return resolved;
        }

        if (m == Mode.FOLLOW_GLOBAL) {
            resolved.copyFrom(Humanized.GLOBAL);
            resolved.label = label;
            return resolved;
        }

        resolved.enabled = true;

        if (view) {
            resolved.viewInput = viewInput.get();
            resolved.bezierEnabled = bezier.get();
            resolved.curveTicks = curveTicks.get();
            resolved.overshoot = overshoot.get().floatValue() / 100.0f;
            resolved.noiseDegrees = noiseDegrees.get().floatValue();
            resolved.affectPackets = rotateNoiseSetting.get();
            resolved.resendJitter = resendJitter.get() / 100.0f;
        } else {
            resolved.viewInput = false;
        }

        if (click) {
            resolved.clickInput = clickInput.get();
            resolved.nonUniformClicks = nonUniform.get();
            resolved.clickJitter = clickJitter.get().floatValue() / 100.0f;
            resolved.variableLatency = variableLatency.get();
            resolved.latencyMinMs = latencyMin.get();
            resolved.latencyMaxMs = latencyMax.get();
            resolved.staggerRelease = stagger.get();
            resolved.staggerMaxMs = staggerMax.get();
        } else {
            resolved.clickInput = false;
        }

        return resolved;
    }

    /** 当前档位是否真的生效。 */
    public boolean active() {
        return profile().enabled;
    }

    /** 模式设置的显示名，用于聊天提示。 */
    public String modeName() {
        return mode.get().toString();
    }

    /** 本组设置挂在哪个模块名上。 */
    public String label() {
        return label;
    }

    public static Builder builder(Module module, String label) {
        return new Builder(module, label);
    }

    /** 按模块实际用得到的能力挑选项，用不到的不会出现在界面上。 */
    public static final class Builder {
        private final Module module;
        private final String label;
        private boolean view;
        private boolean click;
        private boolean resend;
        private boolean rotateNoise;

        private Builder(Module module, String label) {
            this.module = module;
            this.label = label;
        }

        /** 挂上视角相关选项。 */
        public Builder view() {
            this.view = true;
            return this;
        }

        /** 挂上鼠标/点击相关选项。 */
        public Builder click() {
            this.click = true;
            return this;
        }

        /** 额外挂上“定时重发节奏抖动”（抬头？这类模块用）。 */
        public Builder resend() {
            this.resend = true;
            return this;
        }

        /** 额外挂上“旋转注入手抖”（走 RotationManager 的模块用）。 */
        public Builder rotateNoise() {
            this.rotateNoise = true;
            return this;
        }

        public HumanizedSettings build() {
            return new HumanizedSettings(module, label, view, click, resend, rotateNoise);
        }
    }
}
