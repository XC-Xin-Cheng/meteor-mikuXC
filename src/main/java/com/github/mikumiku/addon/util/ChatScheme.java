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
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * 「Miku 聊天」里的一套聊天方案。
 *
 * <p>一套方案 = 一个方案名 + 一段发送间隔 + 若干条消息。多套方案可以同时启用，
 * 各自的间隔与消息互相独立，例如“打招呼”和“夸夸”两套同时跑。</p>
 *
 * <p>消息里有 {@code %s} 时怎么处理，由「Miku 聊天」的「范围内搜索玩家」开关决定：
 * 开启后要在附近找到一个玩家，才把 {@code %s} 替换成那名玩家的名字（和「自动社交」的写法
 * 一致），找不到就这一次先不发；关闭（默认）时不做搜索，直接把 {@code %s} 去掉后发送。</p>
 *
 * <p>{@code nextSendAt} / {@code index} / {@code wasEnabled} 是运行期状态，
 * 只在内存里用，不进配置（{@link #toTag()} 不写它们）。</p>
 */
public class ChatScheme {

    /** 消息列表在配置里的分隔符（界面上一个输入框里填多条消息时用）。 */
    public static final String MESSAGE_SEPARATOR = "|";

    /** 间隔的硬下限：200 毫秒，避免手滑填 0 刷屏。 */
    public static final int MIN_INTERVAL_MS = 200;
    /**
     * 间隔的硬上限：10 小时。
     *
     * <p>这是三档单位里最大的一档（600 分 = 10 小时）换算出来的毫秒数。界面上具体能用多大
     * 由当前单位决定（毫秒最长 10 分钟、秒最长 5 小时、分最长 10 小时，见
     * {@link ChatIntervalUnit}），这里只兜底防止配置里出现离谱的数值。</p>
     */
    public static final int HARD_MAX_INTERVAL_MS = 36_000_000;

    /** 把任意毫秒间隔夹进 {@link #MIN_INTERVAL_MS} ~ {@link #HARD_MAX_INTERVAL_MS}。 */
    public static int clampIntervalMs(int ms) {
        return Math.max(MIN_INTERVAL_MS, Math.min(HARD_MAX_INTERVAL_MS, ms));
    }

    // ---- 配置项（会保存） ----
    /** 方案名，只用于在界面上区分。 */
    public String name = "新方案";
    /** 是否启用这套方案；多套可以同时启用。 */
    public boolean enabled = true;
    /** 这套方案两条消息之间至少间隔多少毫秒。 */
    public int intervalMs = 1500;
    /**
     * 这套方案在配置界面里填间隔用的单位（毫秒 / 秒 / 分）。
     *
     * <p>每套方案各选各的，互不影响。换档只改界面上显示与输入的口径，
     * {@link #intervalMs} 里始终按毫秒存，所以随便切也不会把间隔写坏。</p>
     */
    public ChatIntervalUnit intervalUnit = ChatIntervalUnit.MILLIS;
    /** 是否随机挑消息（不勾选则按顺序轮播）。 */
    public boolean random = false;
    /** 随机挑消息时，是否避免和上一条重复（即「自动社交」的挑选方式）。 */
    public boolean avoidRepeat = false;
    /** 这套方案要发的消息，至少一条。 */
    public List<String> messages = new ArrayList<>();

    // ---- 运行期状态（不保存） ----
    /** 下一条消息该在什么时间发出（毫秒时间戳）。 */
    public transient long nextSendAt = 0L;
    /** 顺序发送时轮到第几条。 */
    public transient int index = 0;
    /** 随机发送时上一条用的是第几条，用来避免连续重复；-1 表示还没发过。 */
    public transient int lastPicked = -1;
    /** 上一 tick 的启用状态，用来识别“刚被勾上”并重新计时。 */
    public transient boolean wasEnabled = false;

    private static final Random RANDOM = new Random();

    public ChatScheme() {
    }

    public ChatScheme(String name) {
        this.name = name;
    }

    public ChatScheme(String name, boolean enabled, int intervalMs, boolean random, String... messages) {
        this.name = name;
        this.enabled = enabled;
        this.intervalMs = intervalMs;
        this.random = random;
        for (String message : messages) {
            if (message != null && !message.isBlank()) this.messages.add(message);
        }
    }

    /** 发送间隔，夹在 200 ms ~ 10 h 之间，避免手滑填 0 刷屏或填太大永远不发。 */
    public int clampedIntervalMs() {
        return clampIntervalMs(intervalMs);
    }

    /** 这套方案填间隔用的单位，永远不为 null。 */
    public ChatIntervalUnit intervalUnit() {
        return intervalUnit == null ? ChatIntervalUnit.MILLIS : intervalUnit;
    }

    /**
     * 间隔在界面上的写法：数值后面直接带单位，例如「1500ms」「5s」「2min」，
     * 这样一眼就能看出单位、方便计数。
     */
    public String intervalText() {
        ChatIntervalUnit unit = intervalUnit();
        return unit.toUnits(clampedIntervalMs()) + unit.shortLabel();
    }

    /** 这套方案现在有没有可发的消息。 */
    public boolean hasMessages() {
        return !messages.isEmpty();
    }

    /** 把运行期状态清干净：{@code now} 之前不发，并回到第一条。 */
    public void resetRuntime(long now) {
        nextSendAt = now + clampedIntervalMs();
        index = 0;
        lastPicked = -1;
        wasEnabled = enabled;
    }

    /**
     * 取这一次要发的消息原文（还没做 {@code %s} 替换）。
     *
     * @return 消息原文；没有消息时返回 {@code null}
     */
    public String pickMessage() {
        if (messages.isEmpty()) return null;

        int size = messages.size();
        if (random) {
            int picked = RANDOM.nextInt(size);
            // 和「自动社交」一样：随机时尽量不和上一条撞车，避免连着喊同一句
            if (avoidRepeat && size > 1 && picked == lastPicked) {
                picked = (picked + 1 + RANDOM.nextInt(size - 1)) % size;
            }
            lastPicked = picked;
            return messages.get(picked);
        }

        if (index < 0 || index >= size) index = 0;
        String message = messages.get(index);
        index = (index + 1) % size;
        return message;
    }

    /** 复制一份配置项（运行期状态不带过去）。 */
    public ChatScheme copy() {
        ChatScheme copy = new ChatScheme(name);
        copy.enabled = enabled;
        copy.intervalMs = intervalMs;
        copy.intervalUnit = intervalUnit();
        copy.random = random;
        copy.avoidRepeat = avoidRepeat;
        copy.messages = new ArrayList<>(messages);
        return copy;
    }

    /** 把消息列表拼成界面上那个输入框里的文本。 */
    public String messagesAsText() {
        return String.join(" " + MESSAGE_SEPARATOR + " ", messages);
    }

    /** 把界面上输入的文本拆回消息列表并把 {@code %s} 之类的空白收拾干净。 */
    public void setMessagesFromText(String text) {
        List<String> parsed = new ArrayList<>();
        if (text != null) {
            for (String part : text.split("[|\\n\\r]")) {
                String message = part.trim();
                if (!message.isEmpty()) parsed.add(message);
            }
        }
        this.messages = parsed;
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("name", name == null ? "方案" : name);
        tag.putBoolean("enabled", enabled);
        tag.putInt("intervalMs", intervalMs);
        // 每套方案各选各的填写单位，也一起存
        tag.putString("intervalUnit", intervalUnit().name());
        tag.putBoolean("random", random);
        tag.putBoolean("avoidRepeat", avoidRepeat);

        ListTag listTag = new ListTag();
        for (String message : messages) {
            CompoundTag entry = new CompoundTag();
            entry.putString("m", message);
            listTag.add(entry);
        }
        tag.put("messages", listTag);
        return tag;
    }

    public static ChatScheme fromTag(CompoundTag tag) {
        return fromTag(tag, ChatIntervalUnit.MILLIS);
    }

    /**
     * 从配置读回一套方案。
     *
     * @param defaultUnit 这套方案的配置里没写单位时用什么；读旧配置（r72 及以前单位是全局一份）
     *                    时由调用方把旧的全局单位传进来，避免用户选的档位丢掉
     */
    public static ChatScheme fromTag(CompoundTag tag, ChatIntervalUnit defaultUnit) {
        ChatIntervalUnit fallback = defaultUnit == null ? ChatIntervalUnit.MILLIS : defaultUnit;
        ChatScheme scheme = new ChatScheme();
        scheme.name = tag.getStringOr("name", "方案");
        scheme.enabled = tag.getBooleanOr("enabled", true);
        scheme.intervalMs = tag.getIntOr("intervalMs", 1500);
        scheme.intervalUnit = ChatIntervalUnit.byName(tag.getStringOr("intervalUnit", fallback.name()));
        scheme.random = tag.getBooleanOr("random", false);
        scheme.avoidRepeat = tag.getBooleanOr("avoidRepeat", false);

        ListTag listTag = tag.getList("messages").orElse(new ListTag());
        for (Tag entry : listTag) {
            entry.asCompound().ifPresent(compound -> {
                String message = compound.getStringOr("m", "");
                if (!message.isEmpty()) scheme.messages.add(message);
            });
        }
        return scheme;
    }

    /**
     * 序列化成 Miku-Chat.txt 里的一行，格式：
     * {@code on1 方案名 间隔ms 消息 on2 on3}（消息内部可带空格，多条用 {@code |} 分隔）。
     *
     * <p>界面上选了非毫秒档时，行尾会再补一个英文缩写（{@code s} / {@code min}），
     * 好让每套方案各自的单位跟着方案一起存下来；毫秒档不补，保持老格式不变。</p>
     */
    public String toLine() {
        String safeName = (name == null || name.isBlank()) ? "方案" : name.trim().replaceAll("\\s+", "_");
        String line = (enabled ? "on1" : "off1") + " "
            + safeName + " "
            + clampedIntervalMs() + " "
            + String.join(MESSAGE_SEPARATOR, messages) + " "
            + (random ? "on2" : "off2") + " "
            + (avoidRepeat ? "on3" : "off3");
        if (intervalUnit() != ChatIntervalUnit.MILLIS) {
            line += " " + intervalUnit().shortLabel();
        }
        return line;
    }

    /**
     * 解析 Miku-Chat.txt 里的一行；空行、注释行、字段不足的行返回 {@code null}。
     *
     * <p>消息允许带空格，所以从第 4 个字段到「倒数第 2 个字段」之间的内容都算消息，
     * 最后两个字段固定是 {@code on2/off2}（随机）和 {@code on3/off3}（不重复）。
     * 行尾可能还多一个单位缩写（只有非毫秒档才写），会先摘掉再按上面的规则解析。</p>
     */
    public static ChatScheme fromLine(String line) {
        if (line == null) return null;
        String trimmed = line.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("#")) return null;

        String[] fields = trimmed.split("\\s+");
        if (fields.length < 3) return null;

        ChatScheme scheme = new ChatScheme();
        scheme.enabled = isOn(fields[0]);
        scheme.name = fields[1];

        try {
            scheme.intervalMs = clampIntervalMs(Integer.parseInt(fields[2]));
        } catch (NumberFormatException ignored) {
            scheme.intervalMs = 1500;
        }

        // 新版行尾带的单位缩写只有 ms/s/min 三种；老格式行尾固定是 on2/on3，不会被误判
        int end = fields.length;
        if (end >= 6) {
            ChatIntervalUnit unit = ChatIntervalUnit.fromShortLabel(fields[end - 1]);
            if (unit != null) {
                scheme.intervalUnit = unit;
                end--;
            }
        }

        StringBuilder message = new StringBuilder();
        int messageEnd = end; // 不含
        if (end >= 5) {
            scheme.random = isOn(fields[end - 2]);
            scheme.avoidRepeat = isOn(fields[end - 1]);
            messageEnd = end - 2;
        }
        for (int i = 3; i < messageEnd; i++) {
            if (message.length() > 0) message.append(' ');
            message.append(fields[i]);
        }
        scheme.setMessagesFromText(message.toString());

        return scheme;
    }

    /** {@code on1/on2/on3/on/true/1} 视为开，其余视为关。 */
    private static boolean isOn(String token) {
        if (token == null) return false;
        String value = token.trim().toLowerCase(Locale.ROOT);
        return value.startsWith("on") || value.equals("true") || value.equals("1") || value.equals("yes");
    }

    /** 生成一个可读的方案描述，用于配置里的字符串形式与日志。 */
    @Override
    public String toString() {
        return String.format(Locale.ROOT, "%s[%s %d条%s]",
            name, ChatIntervalUnit.humanReadable(clampedIntervalMs()), messages.size(),
            random ? " 随机" : " 顺序");
    }
}
