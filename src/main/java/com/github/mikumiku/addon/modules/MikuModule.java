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

import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

import static com.github.mikumiku.addon.util.ChatUtils.sendMsg;

@SuppressWarnings("unused")
public class MikuModule extends Module {
    private boolean active;
    private final int priority;
    protected Minecraft mc = Minecraft.getInstance();


    public MikuModule(Category Category, String name, String desc) {
        super(Category, name, desc);
        this.priority = 100;
        mc = Minecraft.getInstance();

    }

    public void sendToggledMsg() {
        Component onMsg = Component.empty().setStyle(Style.EMPTY.applyFormat(ChatFormatting.GREEN)).append("ON");
        Component offMsg = Component.empty().setStyle(Style.EMPTY.applyFormat(ChatFormatting.RED)).append("OFF");
        ChatUtils.forceNextPrefixClass(getClass());
        MutableComponent toggledMsg = Component.empty();
        toggledMsg.append(Component.empty().setStyle(Style.EMPTY.applyFormat(ChatFormatting.WHITE)).append(title));
        toggledMsg.append(" ");
        toggledMsg.append(Component.empty().setStyle(Style.EMPTY.applyFormat(ChatFormatting.GRAY)).append("toggled "));
        toggledMsg.append((isActive() ? onMsg : offMsg));

        sendMsg(toggledMsg, hashCode());
    }

}
