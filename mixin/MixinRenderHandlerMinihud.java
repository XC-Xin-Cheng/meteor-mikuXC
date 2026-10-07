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
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;

@Pseudo
@Mixin(targets = "fi.dy.masa.minihud.event.RenderHandler")
public class MixinRenderHandlerMinihud {

    @Redirect(method = "addLine(Lfi/dy/masa/minihud/config/InfoToggle;)V",
        slice = @Slice(
            from = @At(value = "FIELD", target = "Lfi/dy/masa/minihud/config/InfoToggle;COORDINATES:Lfi/dy/masa/minihud/config/InfoToggle;"),
            to = @At(value = "INVOKE", target = "Ljava/lang/StringBuilder;toString()Ljava/lang/String;")
        ),
        require = 0,
        at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;getX()D", ordinal = 0))
    private double redirectCoordinateX(Entity entity) {

        if (MagicMix.coordinatesisActive()) {
            return MagicMix.x; // 修改X坐标为固定值
        } else {
            return entity.getX();
        }
    }

    @Redirect(method = "addLine(Lfi/dy/masa/minihud/config/InfoToggle;)V",
        slice = @Slice(
            from = @At(value = "FIELD", target = "Lfi/dy/masa/minihud/config/InfoToggle;COORDINATES:Lfi/dy/masa/minihud/config/InfoToggle;"),
            to = @At(value = "INVOKE", target = "Ljava/lang/StringBuilder;toString()Ljava/lang/String;")
        ),
        require = 0,
        at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;getZ()D"))
    private double redirectCoordinateZ(Entity entity) {
        if (MagicMix.coordinatesisActive()) {
            return MagicMix.z; // 修改X坐标为固定值
        } else {
            return entity.getZ();
        }
    }


}
