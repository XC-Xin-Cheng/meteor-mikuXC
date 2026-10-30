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

import com.mojang.brigadier.StringReader;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.mixininterface.IChatHud;
import meteordevelopment.meteorclient.systems.config.Config;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

public class ChatUtils {

    static String last = "";

    public static Component getPrefix() {
        String prefix = "MikuMiku";
        Color startColor = new Color(0, 255, 247);
        Color endColor = new Color(48, 155, 186);
        char[] chars = prefix.toCharArray();
        MutableComponent result = Component.empty();
        int count = chars.length;

        for (int index = 0; index < count; index++) {
            char c = chars[index];
            double ratio = (double) index / (count - 1);
            Color color = ColorUtil.fadeColor(startColor, endColor, ratio);
            result.append(Component.literal(String.valueOf(c)).setStyle(Style.EMPTY.withColor(color.getRGB())));
        }
        result = Component.literal("[").append(result).append("] ");

        return result;
    }

    public static void sendMsg(Component msg) {
        if (MeteorClient.mc.level == null) return;

        MutableComponent message = Component.empty();
        message.setStyle(Style.EMPTY.applyFormat(ChatFormatting.GRAY));
        message.append(getPrefix());
        message.append(msg);

        ((IChatHud) MikuCompat.chat()).meteor$add(message, 0);
    }

    public static void sendMsg(Component msg, int id) {
        if (MeteorClient.mc.level == null) return;

        MutableComponent message = Component.empty();
        message.setStyle(Style.EMPTY.applyFormat(ChatFormatting.GRAY));
        message.append(getPrefix());
        message.append(msg);

        try {
            ((IChatHud) MikuCompat.chat()).meteor$add(message, id);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void sendMsg(String msg, int id) {
        sendMsg(Component.literal(msg), id);
    }

    public static void sendMsg(String message) {
        if (message == null) {
            return;
        }
        if (message.equals(last)) {
            return;
        }
        sendMsg(Component.literal(message));
        last = message;
    }

    public static void sendMsgDebounce(String message) {
        if (message == null) {
            return;
        }
        if (message.equals(last)) {
            return;
        }

        DebounceLimiter limiter = new DebounceLimiter(50); // 1秒防抖
        limiter.run(() -> {
            sendMsg(Component.literal(message));
        });

        last = message;
    }

    public static void sendMsg(String prefix, Component message) {
        sendMsg(0, prefix, ChatFormatting.LIGHT_PURPLE, message);
    }

    public static void sendMsg(ChatFormatting color, String message, Object... args) {
        sendMsg(0, null, null, color, message, args);
    }

    public static void sendMsg(int id, ChatFormatting color, String message, Object... args) {
        sendMsg(id, null, null, color, message, args);
    }

    public static void sendMsg(int id, @Nullable String prefixTitle, @Nullable ChatFormatting prefixColor, ChatFormatting messageColor, String messageContent, Object... args) {
        MutableComponent message = formatMsg(String.format(messageContent, args), messageColor);
        sendMsg(id, prefixTitle, prefixColor, message);
    }

    public static void sendMsg(int id, @Nullable String prefixTitle, @Nullable ChatFormatting prefixColor, String messageContent, ChatFormatting messageColor) {
        MutableComponent message = formatMsg(messageContent, messageColor);
        sendMsg(id, prefixTitle, prefixColor, message);
    }

    public static void sendMsg(int id, @Nullable String prefixTitle, @Nullable ChatFormatting prefixColor, Component msg) {
        if (MeteorClient.mc.level == null) return;

        MutableComponent message = Component.empty();
        message.append(getPrefix());
        if (prefixTitle != null) message.append(getCustomPrefix(prefixTitle, prefixColor));
        message.append(msg);

        if (!Config.get().deleteChatFeedback.get()) id = 0;

        ((IChatHud) MikuCompat.chat()).meteor$add(message, id);
    }

    private static MutableComponent getCustomPrefix(String prefixTitle, ChatFormatting prefixColor) {
        MutableComponent prefix = Component.empty();
        prefix.setStyle(prefix.getStyle().applyFormat(ChatFormatting.GRAY));

        prefix.append("[");

        MutableComponent moduleTitle = Component.literal(prefixTitle);
        moduleTitle.setStyle(moduleTitle.getStyle().applyFormat(prefixColor));
        prefix.append(moduleTitle);

        prefix.append("] ");

        return prefix;
    }

    private static MutableComponent formatMsg(String message, ChatFormatting defaultColor) {
        StringReader reader = new StringReader(message);
        MutableComponent text = Component.empty();
        Style style = Style.EMPTY.applyFormat(defaultColor);
        StringBuilder result = new StringBuilder();
        boolean formatting = false;
        while (reader.canRead()) {
            char c = reader.read();
            if (c == '(') {
                text.append(Component.literal(result.toString()).setStyle(style));
                result.setLength(0);
                result.append(c);
                formatting = true;
            } else {
                result.append(c);

                if (formatting && c == ')') {
                    switch (result.toString()) {
                        case "(default)" -> {
                            style = style.applyFormat(defaultColor);
                            result.setLength(0);
                        }
                        case "(highlight)" -> {
                            style = style.applyFormat(ChatFormatting.WHITE);
                            result.setLength(0);
                        }
                        case "(underline)" -> {
                            style = style.applyFormat(ChatFormatting.UNDERLINE);
                            result.setLength(0);
                        }
                        case "(bold)" -> {
                            style = style.applyFormat(ChatFormatting.BOLD);
                            result.setLength(0);
                        }
                    }
                    formatting = false;
                }
            }
        }

        if (!result.isEmpty()) text.append(Component.literal(result.toString()).setStyle(style));

        return text;
    }

    public static void info(String s) {
        if (s == null) {
            return;
        }
        if (s.equals(last)) {
            return;
        }
        sendMsg(s);
        last = s;
    }

    public static void warning(String s) {
        if (s == null) {
            return;
        }
        if (s.equals(last)) {
            return;
        }
        sendMsg("§c " + s);
        last = s;
    }

    public static void success(String s) {
        if (s == null) {
            return;
        }
        if (s.equals(last)) {
            return;
        }
        sendMsg("§a " + s);
        last = s;
    }
}
