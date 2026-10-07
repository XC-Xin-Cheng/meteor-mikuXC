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

import net.minecraft.client.Minecraft;
import net.minecraft.client.main.GameConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin class.
 * For more resources, visit:
 * <ul>
 * <li><a href="https://fabricmc.net/wiki/tutorial:mixin_introduction">The FabricMC wiki</a></li>
 * <li><a href="https://github.com/SpongePowered/Mixin/wiki">The Mixin wiki</a></li>
 * <li><a href="https://github.com/LlamaLad7/MixinExtras/wiki">The MixinExtras wiki</a></li>
 * <li><a href="https://jenkins.liteloader.com/view/Other/job/Mixin/javadoc/allclasses-noframe.html">The Mixin javadoc</a></li>
 * <li><a href="https://github.com/2xsaiko/mixin-cheatsheet">The Mixin cheatsheet</a></li>
 * </ul>
 */
@Mixin(Minecraft.class)
public abstract class MikuMixin {
    /**
     * Example Mixin injection targeting the {@code <init>} method (the constructor) at {@code TAIL} (end of method).
     */
    @Inject(method = "<init>", at = @At("TAIL"))
    private void onGameLoaded(GameConfig args, CallbackInfo ci) {
//        MikuMikuAddon.LOG.info("Hello from ExampleMixin!");
    }
}
