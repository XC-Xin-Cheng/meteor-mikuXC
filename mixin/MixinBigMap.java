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

import com.github.mikumiku.addon.mixinface.MagicMix;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 世界地图显示坐标信息
 */
@Pseudo
@Mixin(targets = "xaero.map.graphics.MapRenderHelper")
public class MixinBigMap {

    @ModifyVariable(
        method = "drawCenteredStringWithBackground",
        at = @At("HEAD"),
        argsOnly = true,
        require = 0
    )
    private static String modifyCoordinatesStringOnly(String text) {

        if (MagicMix.coordinatesisActive() && text.contains("X:") && text.contains("Z:")) {
            text = text
                .replaceAll("X:\\s*-?\\d+", "X: " + ((int) MagicMix.x))
                .replaceAll("Z:\\s*-?\\d+", "Z: " + ((int) MagicMix.z));
        }

        return text; // 返回修改后的文本
    }
}
