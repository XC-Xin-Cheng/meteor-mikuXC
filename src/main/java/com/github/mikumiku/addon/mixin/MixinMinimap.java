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
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xaero.hud.minimap.Minimap;
import xaero.hud.minimap.module.MinimapSession;

/**
 * 小地图
 */
@Pseudo
@Mixin(targets = "xaero.hud.minimap.info.render.InfoDisplayRenderer")
public class MixinMinimap {

    // Xaero 26.5.0（MC 26.1）的真实签名：render(MinimapSession, Minimap, int, int, BlockPos, int, int, float, GuiGraphicsExtractor)
    @Inject(method = "render", at = @At("HEAD"), require = 0)
    private void modifyPlayerPosForCompile(
        MinimapSession session,
        Minimap minimap,
        int height,
        int size,
        BlockPos playerPos,
        int scaledX,
        int scaledY,
        float mapScale,
        GuiGraphicsExtractor guiGraphics,
        CallbackInfo ci) {

        if (MagicMix.coordinatesisActive()) {
            // 强转为 mutableBlockPos 并设置固定坐标值
            if (playerPos instanceof BlockPos.MutableBlockPos) {
                BlockPos.MutableBlockPos mutablePos = (BlockPos.MutableBlockPos) playerPos;
                mutablePos.set(MagicMix.x, playerPos.getY(), MagicMix.z); // 设置为固定坐标
            }
        }
    }
}
