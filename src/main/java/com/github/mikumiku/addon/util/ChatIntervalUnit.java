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

/**
 * 「Miku 聊天」里发送间隔的时间单位，共三档：毫秒 / 秒 / 分。
 *
 * <p>每一档有各自的取值范围：</p>
 *
 * <ul>
 *   <li>{@link #MILLIS 毫秒}：200 ~ 600000 毫秒（最长 10 分钟）</li>
 *   <li>{@link #SECONDS 秒}：1 ~ 18000 秒（最长 5 小时）</li>
 *   <li>{@link #MINUTES 分}：1 ~ 600 分（最长 10 小时）</li>
 * </ul>
 *
 * <p>不管界面上用的是哪一档，{@code Miku-Chat.txt} 与配置里存的<b>永远是毫秒</b>，
 * 换档只改变界面上显示/输入的口径，不会把方案写坏。</p>
 */
public enum ChatIntervalUnit {

    /** 毫秒，最长 10 分钟。 */
    MILLIS("毫秒", "ms", 1L, 200L, 600_000L),
    /** 秒，最长 5 小时。 */
    SECONDS("秒", "s", 1_000L, 1L, 18_000L),
    /** 分，最长 10 小时。 */
    MINUTES("分", "min", 60_000L, 1L, 600L);

    private final String label;
    private final String shortLabel;
    /** 一个单位等于多少毫秒。 */
    private final long factorMs;
    /** 本档允许的最小数值（以本档单位计）。 */
    private final long minUnits;
    /** 本档允许的最大数值（以本档单位计）。 */
    private final long maxUnits;

    ChatIntervalUnit(String label, String shortLabel, long factorMs, long minUnits, long maxUnits) {
        this.label = label;
        this.shortLabel = shortLabel;
        this.factorMs = factorMs;
        this.minUnits = minUnits;
        this.maxUnits = maxUnits;
    }

    /** 中文档位名（毫秒 / 秒 / 分）。 */
    public String label() {
        return label;
    }

    /** 英文缩写（ms / s / min），间隔数值后面用它。 */
    public String shortLabel() {
        return shortLabel;
    }

    /** 下拉框里的选项文字：中文单位 + 英文缩写，例如 {@code 毫秒/ms}。 */
    public String displayName() {
        return label + "/" + shortLabel;
    }

    public long factorMs() {
        return factorMs;
    }

    public long minUnits() {
        return minUnits;
    }

    public long maxUnits() {
        return maxUnits;
    }

    /** 本档对应的毫秒上限。 */
    public int maxMs() {
        return (int) (maxUnits * factorMs);
    }

    /** 毫秒换算成本档数值：四舍五入，并夹在本档的上下限内。 */
    public int toUnits(int ms) {
        long units = Math.round((double) ms / factorMs);
        return (int) Math.max(minUnits, Math.min(maxUnits, units));
    }

    /** 本档数值换算成毫秒，并夹在本档的上下限内。 */
    public int toMs(int units) {
        long clamped = Math.max(minUnits, Math.min(maxUnits, units));
        return (int) (clamped * factorMs);
    }

    /** 本档的取值范围说明，用于输入框提示。 */
    public String rangeText() {
        switch (this) {
            case SECONDS:
                return minUnits + " ~ " + maxUnits + " 秒（最长 5 小时）";
            case MINUTES:
                return minUnits + " ~ " + maxUnits + " 分（最长 10 小时）";
            default:
                return minUnits + " ~ " + maxUnits + " 毫秒（最长 10 分钟）";
        }
    }

    /**
     * 只按英文缩写（{@code ms / s / min}）还原档位，用在方案文本行的行尾。
     *
     * <p>和 {@link #byName(String)} 不同，这里认不出来时返回 {@code null} 而不是兜底成毫秒，
     * 这样解析旧格式的行时才能判断行尾到底有没有单位，不会把 {@code on2/on3} 误当成单位。</p>
     */
    public static ChatIntervalUnit fromShortLabel(String name) {
        if (name == null) return null;
        String trimmed = name.trim();
        for (ChatIntervalUnit unit : values()) {
            if (unit.shortLabel.equalsIgnoreCase(trimmed)) return unit;
        }
        return null;
    }

    /** 从保存的名字（枚举名、中文名、ms/s/min 或「毫秒/ms」这种写法）还原档位，认不出来时回到毫秒。 */
    public static ChatIntervalUnit byName(String name) {
        if (name != null) {
            String trimmed = name.trim();
            for (ChatIntervalUnit unit : values()) {
                if (unit.name().equalsIgnoreCase(trimmed)
                    || unit.label.equals(trimmed)
                    || unit.shortLabel.equalsIgnoreCase(trimmed)
                    || unit.displayName().equalsIgnoreCase(trimmed)) {
                    return unit;
                }
            }
        }
        return MILLIS;
    }

    /**
     * 把一个毫秒间隔写成好读的文字：整分显示成「X 分」，整秒显示成「X 秒」，
     * 其余保持「X 毫秒」。旧方案集详情界面上用它展示间隔。
     */
    public static String humanReadable(int ms) {
        int value = Math.max(0, ms);
        if (value >= 60_000 && value % 60_000 == 0) return (value / 60_000) + " 分";
        if (value >= 1_000 && value % 1_000 == 0) return (value / 1_000) + " 秒";
        return value + " 毫秒";
    }

    @Override
    public String toString() {
        // 下拉框直接拿 toString 当选项文字，显示成「毫秒/ms」「秒/s」「分/min」，
        // 中文看得懂、英文缩写又方便和间隔数值后面的单位对上。
        return displayName();
    }
}
