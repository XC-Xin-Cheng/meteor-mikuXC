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

import net.minecraft.util.Mth;

import java.util.Random;

/**
 * 「类人化输入」引擎。
 *
 * <p>这是给所有自动化输入（视角旋转、鼠标点击、按键松开）加“人味”的公共工具。
 * 每次调用都读一个 {@link Profile} 档位：档位由模块界面里的「类人化」设置组写入
 * （见 {@link HumanizedSettings}）。档位关闭时对应方法退化成原来的机械行为，
 * 绝不影响没开启的玩家。</p>
 *
 * <p>提供四类能力：</p>
 * <ul>
 *   <li><b>贝塞尔视角</b>：{@link BezierPath} 用三次贝塞尔曲线把视角从当前值
 *       平滑推到目标值，并带自然过冲与修正；</li>
 *   <li><b>生物噪声</b>：{@link #jitter(Profile, float)} 注入均值为 0 的高斯“手抖”，
 *       可加在角度或时间上，打破完美的数据包模式；</li>
 *   <li><b>可变延迟</b>：{@link #latencyMs(Profile)} 模拟神经与硬件延迟；</li>
 *   <li><b>非均匀节奏</b>：{@link #clickInterval(Profile, long)} 让连点间隔呈钟形分布，
 *       而不是均匀随机；{@link #releaseStaggerMs(Profile)} 让按键松开时间错开。</li>
 * </ul>
 *
 * <p>档位分两种：{@link #GLOBAL} 是全局档，由「类人化输入」模块整体控制；
 * 各模块也可以带自己的自定义档（{@link HumanizedSettings} 的“自定义”模式）。
 * 没有自定义档的模块一律读全局档，所以行为和以前完全一致。</p>
 */
public final class Humanized {

    private Humanized() {
    }

    /**
     * 一组类人化参数。模块各自持有一份，互不干扰。
     *
     * <p>字段默认值与上一版的全局静态默认值保持一致；{@code enabled} 默认 {@code false}，
     * 也就是“没人明确打开就不生效”。</p>
     */
    public static final class Profile {

        /** 档位名，只用于提示/日志。 */
        public String label = "全局";

        /** 总开关：为 false 时下面所有能力都不生效。 */
        public boolean enabled = false;

        // ---- 分组 ----
        /** 是否作用于视角（旋转、抬头欺骗）。 */
        public boolean viewInput = true;
        /** 是否作用于鼠标（连点节奏、按键松开）。 */
        public boolean clickInput = true;

        // ---- 视角 ----
        /** 是否用贝塞尔曲线代替线性/瞬间转头。 */
        public boolean bezierEnabled = true;
        /** 是否给发往服务器的视角包注入噪声。 */
        public boolean affectPackets = true;
        /** 贝塞尔曲线走完用几个 tick。 */
        public int curveTicks = 4;
        /** 过冲幅度（0~0.45，占转角的比例）。 */
        public float overshoot = 0.15f;
        /** 视角手抖幅度（度）。 */
        public float noiseDegrees = 0.6f;
        /** 定时重发视角的间隔抖动比例（0~0.6）。 */
        public float resendJitter = 0.35f;

        // ---- 连点 ----
        /** 是否使用非均匀（钟形）连点间隔。 */
        public boolean nonUniformClicks = true;
        /** 连点间隔的抖动比例（0~0.6）。 */
        public float clickJitter = 0.35f;

        // ---- 延迟 ----
        /** 是否注入可变延迟。 */
        public boolean variableLatency = true;
        /** 延迟下限（毫秒）。 */
        public int latencyMinMs = 10;
        /** 延迟上限（毫秒）。 */
        public int latencyMaxMs = 40;

        // ---- 松开 ----
        /** 是否让按键松开时间错开。 */
        public boolean staggerRelease = true;
        /** 松开错开上限（毫秒）。 */
        public int staggerMaxMs = 12;

        public Profile() {
        }

        public Profile label(String label) {
            this.label = label;
            return this;
        }

        /** 复制一份配置。 */
        public Profile copy() {
            Profile p = new Profile();
            p.copyFrom(this);
            return p;
        }

        /** 把另一份档位的所有参数（含开关）抄过来，{@code label} 保持不变。 */
        public void copyFrom(Profile other) {
            if (other == null) return;
            this.enabled = other.enabled;
            this.viewInput = other.viewInput;
            this.clickInput = other.clickInput;
            this.bezierEnabled = other.bezierEnabled;
            this.affectPackets = other.affectPackets;
            this.curveTicks = other.curveTicks;
            this.overshoot = other.overshoot;
            this.noiseDegrees = other.noiseDegrees;
            this.resendJitter = other.resendJitter;
            this.nonUniformClicks = other.nonUniformClicks;
            this.clickJitter = other.clickJitter;
            this.variableLatency = other.variableLatency;
            this.latencyMinMs = other.latencyMinMs;
            this.latencyMaxMs = other.latencyMaxMs;
            this.staggerRelease = other.staggerRelease;
            this.staggerMaxMs = other.staggerMaxMs;
        }

        /** 视角类人化是否真的生效。 */
        public boolean viewActive() {
            return enabled && viewInput;
        }

        /** 鼠标类人化是否真的生效。 */
        public boolean clickActive() {
            return enabled && clickInput;
        }

        /** 贝塞尔视角是否生效。 */
        public boolean bezierActive() {
            return enabled && viewInput && bezierEnabled;
        }
    }

    /** 全局档：「类人化输入」模块写这里；没有自定义档的模块都读它。 */
    public static final Profile GLOBAL = new Profile();

    private static final Random RANDOM = new Random();

    /** 一个永远不生效的档位，用来表达“这套方案不类人化”。 */
    public static final Profile OFF = new Profile();

    static {
        OFF.enabled = false;
    }

    // ---- 兼容旧签名的全局档快捷方法 ----

    /** 贝塞尔视角是否生效（全局档）。 */
    public static boolean bezierActive() {
        return bezierActive(GLOBAL);
    }

    /** 贝塞尔视角是否生效（指定档位；{@code null} 视为不生效）。 */
    public static boolean bezierActive(Profile profile) {
        return profile != null && profile.bezierActive();
    }

    /** 均值为 0 的高斯“手抖”，幅度为 {@code amplitude}（全局档）。 */
    public static float jitter(float amplitude) {
        return jitter(GLOBAL, amplitude);
    }

    /** 均值为 0 的高斯“手抖”，幅度为 {@code amplitude}。未开启时返回 0。 */
    public static float jitter(Profile profile, float amplitude) {
        if (profile == null || !profile.enabled || !profile.viewInput || amplitude <= 0f) return 0f;
        return (float) (RANDOM.nextGaussian() * amplitude);
    }

    /**
     * 把基准 tick 数抖一下：返回 {@code base * (1 ± spread)} 的整数值，最小 1。
     * 用来让“每隔 N tick 重发一次”这类节奏不再精确到 tick。
     */
    public static int jitterTicks(int base, float spread) {
        return jitterTicks(GLOBAL, base, spread);
    }

    /** 指定档位的 tick 数抖动。 */
    public static int jitterTicks(Profile profile, int base, float spread) {
        int safeBase = Math.max(1, base);
        if (profile == null || !profile.enabled || !profile.viewInput || spread <= 0f) return safeBase;
        float factor = 1f + (float) (RANDOM.nextGaussian() * spread);
        if (factor < 0.5f) factor = 0.5f;
        if (factor > 1.6f) factor = 1.6f;
        return Math.max(1, Math.round(safeBase * factor));
    }

    /** 人类神经/硬件延迟（毫秒，全局档）。 */
    public static long latencyMs() {
        return latencyMs(GLOBAL);
    }

    /**
     * 人类神经/硬件延迟（毫秒）。取值偏向低端：多数时候反应很快，偶尔慢一下，
     * 比均匀随机更像真人。未开启时返回 0。
     */
    public static long latencyMs(Profile profile) {
        if (profile == null || !profile.enabled || !profile.clickInput || !profile.variableLatency) return 0L;
        int min = Math.max(0, Math.min(profile.latencyMinMs, profile.latencyMaxMs));
        int max = Math.max(min, Math.max(profile.latencyMinMs, profile.latencyMaxMs));
        if (max == min) return min;
        // 取两个均匀随机数的较小值 → 概率密度向左偏，长尾在右侧
        double u = Math.min(RANDOM.nextDouble(), RANDOM.nextDouble());
        return Math.round(min + u * (max - min));
    }

    /** 非均匀连点间隔（全局档）。 */
    public static long clickInterval(long baseMs) {
        return clickInterval(GLOBAL, baseMs);
    }

    /**
     * 非均匀连点间隔：以基准间隔为中心做钟形抖动。
     * 三个均匀随机数求平均后近似正态，避免出现“每一拍都一样”的机械节奏。
     */
    public static long clickInterval(Profile profile, long baseMs) {
        long safeBase = Math.max(1L, baseMs);
        if (profile == null || !profile.enabled || !profile.clickInput || !profile.nonUniformClicks) return safeBase;
        float spread = Math.max(0f, Math.min(0.6f, profile.clickJitter));
        if (spread <= 0f) return safeBase;
        double bell = (RANDOM.nextDouble() + RANDOM.nextDouble() + RANDOM.nextDouble()) / 3.0;
        double factor = 1.0 + (bell - 0.5) * 2.0 * spread;
        return Math.max(1L, Math.round(safeBase * factor));
    }

    /** 松开按键时额外错开的时间（毫秒，全局档）。 */
    public static long releaseStaggerMs() {
        return releaseStaggerMs(GLOBAL);
    }

    /** 松开按键时额外错开的时间（毫秒），未开启时返回 0。 */
    public static long releaseStaggerMs(Profile profile) {
        if (profile == null || !profile.enabled || !profile.clickInput || !profile.staggerRelease) return 0L;
        int max = Math.max(0, profile.staggerMaxMs);
        return max == 0 ? 0L : RANDOM.nextInt(max + 1);
    }

    /**
     * 一次“动作”前该等多久（毫秒）：即一段偏向低端的可变延迟。未开启时返回 0，
     * 调用方可以据此走原来的立即执行路径。
     */
    public static long actionDelayMs(Profile profile) {
        return latencyMs(profile);
    }

    /**
     * 一条三次贝塞尔视角路径。
     *
     * <p>控制点这样摆：第一个控制点沿转角约三分之一处、向侧向推开，制造自然的
     * 弧线；第二个控制点略微越过目标，制造“过冲”，最后再由终点把视角收回来，
     * 看起来就像真人先甩过头、再修正。偏航角按最短弧处理，不会绕远路。</p>
     *
     * <p>每条路径绑定一个 {@link Profile}：手抖幅度与过冲都从它读，不同模块的
     * 曲线可以各调各的。</p>
     */
    public static final class BezierPath {

        private final Profile profile;

        private final float startYaw;
        private final float startPitch;
        private final float targetYaw;
        private final float targetPitch;
        private final float deltaYaw;
        private final float deltaPitch;

        private final float c1Yaw;
        private final float c1Pitch;
        private final float c2Yaw;
        private final float c2Pitch;

        private final int ticks;
        private int tick;

        public BezierPath(float fromYaw, float fromPitch, float toYaw, float toPitch, int ticks, float overshoot) {
            this(GLOBAL, fromYaw, fromPitch, toYaw, toPitch, ticks, overshoot);
        }

        public BezierPath(Profile profile, float fromYaw, float fromPitch, float toYaw, float toPitch, int ticks, float overshoot) {
            this.profile = profile == null ? OFF : profile;
            this.startYaw = fromYaw;
            this.startPitch = fromPitch;
            this.targetYaw = toYaw;
            this.targetPitch = toPitch;
            this.deltaYaw = Mth.wrapDegrees(toYaw - fromYaw);
            this.deltaPitch = toPitch - fromPitch;
            this.ticks = Math.max(1, ticks);
            this.tick = 0;

            float len = (float) Math.sqrt(deltaYaw * deltaYaw + deltaPitch * deltaPitch);
            float bend = len * Math.max(0f, Math.min(0.45f, overshoot));
            // 转角的垂直方向，用来把弧线推开；随机选一侧，每次移动都不一样
            float perpYaw = len < 1e-4f ? 0f : deltaPitch / len;
            float perpPitch = len < 1e-4f ? 0f : -deltaYaw / len;
            float side = RANDOM.nextBoolean() ? -1f : 1f;

            // P1：三分之一处，向一侧弯出
            this.c1Yaw = startYaw + deltaYaw * 0.30f + perpYaw * bend * side;
            this.c1Pitch = startPitch + deltaPitch * 0.30f + perpPitch * bend * side;
            // P2：越过终点约 6%，形成过冲后回落
            this.c2Yaw = startYaw + deltaYaw * 1.06f - perpYaw * bend * 0.35f * side;
            this.c2Pitch = startPitch + deltaPitch * 1.06f - perpPitch * bend * 0.35f * side;
        }

        public boolean done() {
            return tick >= ticks;
        }

        public int ticks() {
            return ticks;
        }

        public int tick() {
            return tick;
        }

        public float targetYaw() {
            return targetYaw;
        }

        public float targetPitch() {
            return targetPitch;
        }

        /** 沿曲线取下一 tick 的 [yaw, pitch]，并在中途叠加手抖。 */
        public float[] next() {
            if (tick < ticks) tick++;
            float t = ticks <= 1 ? 1f : (float) tick / (float) ticks;
            float[] p = sample(t);

            // 噪声在中段最大、到终点归零：过程像真人，落点仍然准
            float fade = (float) Math.sin(Math.PI * Math.min(1.0f, t));
            if (fade > 0f) {
                p[0] += jitter(profile, profile.noiseDegrees * fade);
                p[1] += jitter(profile, profile.noiseDegrees * fade);
            }
            return p;
        }

        /** 曲线在参数 {@code t}（0~1）处的 [yaw, pitch]。 */
        public float[] sample(float t) {
            float u = 1f - t;
            float w0 = u * u * u;
            float w1 = 3f * u * u * t;
            float w2 = 3f * u * t * t;
            float w3 = t * t * t;

            float yaw = w0 * startYaw + w1 * c1Yaw + w2 * c2Yaw + w3 * targetYaw;
            float pitch = w0 * startPitch + w1 * c1Pitch + w2 * c2Pitch + w3 * targetPitch;
            return new float[]{Mth.wrapDegrees(yaw), Mth.clamp(pitch, -90f, 90f)};
        }
    }
}
