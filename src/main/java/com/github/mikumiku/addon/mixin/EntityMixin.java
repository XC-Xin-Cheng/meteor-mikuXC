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
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;


@Mixin(Entity.class)
public class EntityMixin {
    @Shadow
    protected UUID uuid;


    @Inject(at = @At("HEAD"), method = "getPose()Lnet/minecraft/world/entity/Pose;", require = 0, cancellable = true)
    private void getPose(CallbackInfoReturnable<Pose> cir) {
        // 性能：getPose() 每帧每实体都会被调用，先做最便宜的开关判断，
        // 未启用甲飞时直接返回，避免每次取 Minecraft 实例和比较 UUID。
        if (!MagicMix.eflyenabled()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && (Object) this == mc.player) {
            cir.setReturnValue(Pose.STANDING);
        }
    }

    @Inject(at = @At("HEAD"), method = "isSprinting()Z", require = 0, cancellable = true)
    private void isSprinting(CallbackInfoReturnable<Boolean> cir) {
        if (!MagicMix.eflyenabled()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && (Object) this == mc.player) {
            cir.setReturnValue(true);
        }
    }

    // 26.1：pushAwayFrom(Entity) 已改名为 push(Entity)，这里显式写描述符以免匹配到其它 push 重载
    @Inject(at = @At("HEAD"), method = "push(Lnet/minecraft/world/entity/Entity;)V", require = 0, cancellable = true)
    private void pushAwayFrom(Entity entity, CallbackInfo ci) {
        if (!MagicMix.eflyenabled()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && (Object) this == mc.player && !entity.getUUID().equals(this.uuid)) {
            ci.cancel();
        }
    }
}
