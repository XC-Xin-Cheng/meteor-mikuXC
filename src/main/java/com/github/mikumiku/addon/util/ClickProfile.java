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

import net.minecraft.nbt.CompoundTag;

import java.util.Locale;

/**
 * 「Miku 连点器」里的一套连点方案。
 *
 * <p>一套方案 = 一个鼠标键 + 一段速度区间 + 可选的按住时长与触发条件。
 * 点击方式也可以每套单独选（{@link Method}）：Miku 走按键队列、按 CPS 区间，
 * Meteor 走 Meteor 的直接点击、按 tick 间隔。
 * 模块可以同时启用多套方案（例如“快速左键”和“慢速右键”各一套），
 * 每套的连点节奏互相独立，互不影响。</p>
 *
 * <p>{@code nextClickAt} / {@code releaseAt} / {@code pressedByUs} / {@code wasEnabled}
 * 是运行期状态，只在内存里用，不进配置（{@link #toTag()} 不写它们）。</p>
 */
public class ClickProfile {

    /** 连点用的鼠标键。 */
    public enum Button {
        LEFT("左键"),
        RIGHT("右键");

        public final String displayName;

        Button(String displayName) {
            this.displayName = displayName;
        }

        public Button next() {
            return this == LEFT ? RIGHT : LEFT;
        }

        public static Button byName(String name) {
            if (name == null) return LEFT;
            for (Button button : values()) {
                if (button.name().equalsIgnoreCase(name) || button.displayName.equals(name)) return button;
            }
            return LEFT;
        }
    }

    /** 点击往哪条路投递。每套方案各选各的，互不影响。 */
    public enum Method {
        /** Miku：给游戏按键队列补点击，由游戏自己处理，按 CPS 速度区间来。 */
        MIKU("Miku"),
        /** Meteor：像 Meteor AutoClicker 那样直接触发一次原版点击，按 tick 间隔来。 */
        VANILLA("Meteor");

        public final String displayName;

        Method(String displayName) {
            this.displayName = displayName;
        }

        public Method next() {
            return this == MIKU ? VANILLA : MIKU;
        }

        public static Method byName(String name) {
            if (name == null) return MIKU;
            for (Method method : values()) {
                if (method.name().equalsIgnoreCase(name) || method.displayName.equals(name)) return method;
            }
            return MIKU;
        }
    }

    // ---- 配置项（会保存） ----
    /** 方案名，只用于在界面上区分。 */
    public String name = "新方案";
    /** 是否启用这套方案；多套可以同时启用。 */
    public boolean enabled = true;
    /** 这套方案走哪种点击方式。 */
    public Method method = Method.MIKU;
    /** 用哪个鼠标键连点。 */
    public Button button = Button.LEFT;
    /** 速度区间下限（每秒点击次数），Miku 方式用。 */
    public int minCps = 8;
    /** 速度区间上限（每秒点击次数），Miku 方式用。 */
    public int maxCps = 12;
    /** Meteor 方式的点击间隔（tick，1 tick = 50 ms），Meteor 方式用。 */
    public int intervalTicks = 2;
    /** 每次点击按住多少毫秒再松开，0 表示只补一次瞬时点击。 */
    public int holdMs = 0;
    /** 只在准星指着方块或实体时才连点，避免对着空气挥砍。 */
    public boolean requireTarget = false;
    /**
     * 这套方案要不要类人化节奏。
     *
     * <p>勾上时，点击间隔做非均匀抖动、再叠加一段可变延迟，松开也错开；
     * 具体参数来自所属模块的「类人化」设置组（关闭/跟随全局/自定义）。
     * 取消勾选则这套方案始终保持等距的机械节奏，方便做精确测试。</p>
     */
    public boolean humanized = true;

    // ---- 运行期状态（不保存） ----
    /** 下一次该点击的时间戳（毫秒）。 */
    public transient long nextClickAt = 0L;
    /** 我们按下的键该在什么时间松开。 */
    public transient long releaseAt = 0L;
    /** 当前这个键是不是被本方案按下的（真实玩家自己按着时不算）。 */
    public transient boolean pressedByUs = false;
    /** 上一 tick 的启用状态，用来识别“刚被勾上”并重新计时。 */
    public transient boolean wasEnabled = false;

    public ClickProfile() {
    }

    public ClickProfile(String name) {
        this.name = name;
    }

    public ClickProfile(String name, boolean enabled, Button button, int minCps, int maxCps, int holdMs, boolean requireTarget) {
        this.name = name;
        this.enabled = enabled;
        this.button = button;
        this.minCps = minCps;
        this.maxCps = maxCps;
        this.holdMs = holdMs;
        this.requireTarget = requireTarget;
    }

    /** 速度区间下限，保证不小于 1。 */
    public int lowerCps() {
        return Math.max(1, Math.min(minCps, maxCps));
    }

    /** 速度区间上限，保证不小于下限。 */
    public int upperCps() {
        return Math.max(lowerCps(), Math.max(minCps, maxCps));
    }

    /** Meteor 方式的点间隔，限制在 1~40 tick（50 ms ~ 2 s）。 */
    public int clampedIntervalTicks() {
        return Math.max(1, Math.min(40, intervalTicks));
    }

    /** 把运行期状态清干净：{@code now} 之前不点击、不按住。 */
    public void resetRuntime(long now) {
        nextClickAt = 0L;
        releaseAt = 0L;
        pressedByUs = false;
        wasEnabled = enabled;
    }

    /** 复制一份配置项（运行期状态不带过去）。 */
    public ClickProfile copy() {
        ClickProfile copy = new ClickProfile(name, enabled, button, minCps, maxCps, holdMs, requireTarget);
        copy.method = method;
        copy.intervalTicks = intervalTicks;
        copy.humanized = humanized;
        return copy;
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("name", name == null ? "方案" : name);
        tag.putBoolean("enabled", enabled);
        tag.putString("method", method.name());
        tag.putString("button", button.name());
        tag.putInt("minCps", minCps);
        tag.putInt("maxCps", maxCps);
        tag.putInt("intervalTicks", intervalTicks);
        tag.putInt("holdMs", holdMs);
        tag.putBoolean("requireTarget", requireTarget);
        tag.putBoolean("humanized", humanized);
        return tag;
    }

    public static ClickProfile fromTag(CompoundTag tag) {
        ClickProfile profile = new ClickProfile();
        profile.name = tag.getStringOr("name", "方案");
        profile.enabled = tag.getBooleanOr("enabled", true);
        profile.method = Method.byName(tag.getStringOr("method", "MIKU"));
        profile.button = Button.byName(tag.getStringOr("button", "LEFT"));
        profile.minCps = tag.getIntOr("minCps", 8);
        profile.maxCps = tag.getIntOr("maxCps", 12);
        profile.intervalTicks = Math.max(1, Math.min(40, tag.getIntOr("intervalTicks", 2)));
        profile.holdMs = tag.getIntOr("holdMs", 0);
        profile.requireTarget = tag.getBooleanOr("requireTarget", false);
        profile.humanized = tag.getBooleanOr("humanized", true);
        return profile;
    }

    /** 生成一个可读的方案描述，用于配置里的字符串形式与日志。 */
    @Override
    public String toString() {
        String human = humanized ? " 类人化" : " 机械";
        if (method == Method.VANILLA) {
            return String.format(Locale.ROOT, "%s[Meteor 每%d tick 按住%dms%s%s]",
                name, clampedIntervalTicks(), holdMs, requireTarget ? " 仅瞄准" : "", human);
        }
        return String.format(Locale.ROOT, "%s[Miku %s %d-%dCPS 按住%dms%s%s]",
            name, button.displayName, lowerCps(), upperCps(), holdMs, requireTarget ? " 仅瞄准" : "", human);
    }
}
