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

import com.github.mikumiku.addon.modules.LookUpModule;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 抬头？模块的欺骗核心。
 *
 * <p>客户端只在位置/朝向变化时才调用 {@code LocalPlayer#sendPosition} 发送移动包，
 * 其中可能包含真实俯仰角。这里在其 TAIL 注入：真实移动包发完之后立刻补发一个
 * 只带旋转的 {@code ServerboundMovePlayerPacket.Rot}（伪造抬头角度），
 * 保证服务器最终记录的朝向始终是伪造值。</p>
 */
@Mixin(value = LocalPlayer.class, priority = 900)
public class LookUpMixin {

    @Inject(method = "sendPosition", at = @At("TAIL"))
    private void miku$spoofLookUp(CallbackInfo ci) {
        LookUpModule.sendSpoof((LocalPlayer) (Object) this);
    }
}
