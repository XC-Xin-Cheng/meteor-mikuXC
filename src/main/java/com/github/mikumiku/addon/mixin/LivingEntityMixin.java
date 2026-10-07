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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;


@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin extends Entity {

    public LivingEntityMixin(EntityType<?> type, Level world) {
        super(type, world);
    }

    @Shadow
    public abstract Brain<?> getBrain();

    @Inject(at = @At("HEAD"), method = "isFallFlying", require = 0, cancellable = true)
    private void isFallFlying(CallbackInfoReturnable<Boolean> cir) {
        // 性能：isFallFlying 每 tick 每实体都会调用。先判最便宜的开关；再改用引用相等判断“是不是本地玩家”，
        // 省掉原来两次 getBrain() 调用（对生物可能触发 Brain 懒加载），语义与 getBrain() 恒等比较一致。
        if (!MagicMix.eflyenabled()) return;
        Minecraft mc = Minecraft.getInstance();
        if ((Object) this == mc.player) {
            cir.setReturnValue(true);
        }
    }

    // 26.1：原 isGliding 已不存在（isFallFlying 就是滑翔判定），上面那条已覆盖，这里不再重复注入
}
