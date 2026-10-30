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
package com.github.mikumiku.addon.mixin;

import com.github.mikumiku.addon.modules.MikuMessageSettings;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 把 Meteor 聊天栏前缀里固定的紫色 {@code [Meteor]} 换成「Miku 消息设置」里的文字与颜色。
 *
 * <p>Meteor 的 {@code ChatUtils.getPrefix()} 会在每条聊天消息前面拼一段前缀：默认是
 * {@code [Meteor]}，用的是 addon 自己的颜色。这里挂在它的返回处：只要「Miku 消息设置」
 * 里填了前缀文字，就换成那边拼好的组件（单色或渐变、带不带方括号都由模块决定）；
 * 关掉模块或文字留空时原样返回，不影响 Meteor 自己的行为。</p>
 */
@Mixin(value = ChatUtils.class, remap = false)
public abstract class MeteorChatPrefixMixin {

    @Inject(method = "getPrefix()Lnet/minecraft/network/chat/Component;", at = @At("RETURN"), cancellable = true)
    private static void miku$customPrefix(CallbackInfoReturnable<Component> cir) {
        Component custom = MikuMessageSettings.buildPrefix();
        if (custom != null) {
            cir.setReturnValue(custom);
        }
    }}
