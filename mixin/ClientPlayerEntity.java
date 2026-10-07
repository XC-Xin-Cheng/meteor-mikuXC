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

import com.github.mikumiku.addon.mixinface.MoveEvent;
import com.mojang.authlib.GameProfile;
import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = net.minecraft.client.player.LocalPlayer.class, priority = 800)
public abstract class ClientPlayerEntity extends AbstractClientPlayer {

    // 26.1：yarn 时代的 getPitch(float) 在官方映射里叫 getViewXRot(float)
    @Shadow
    public abstract float getViewXRot(float tickDelta);

    private ClientPlayerEntity(ClientLevel world, GameProfile profile) {
        super(world, profile);
    }

    @Inject(method = "move", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/AbstractClientPlayer;move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V"), cancellable = true)
    public void onMoveHook(MoverType movementType, Vec3 movement, CallbackInfo ci) {
        MoveEvent event = new MoveEvent(movement.x, movement.y, movement.z);
        MeteorClient.EVENT_BUS.post(event);
        if (event.isCancelled()) {
            super.move(movementType, new Vec3(event.getX(), event.getY(), event.getZ()));
            ci.cancel();
        }
    }


//    @Inject(method = "sendMovementPackets", at = @At("HEAD"), cancellable = true)
//    private void sendMovementPacketsHook(CallbackInfo info) {
//        SyncEvent event = new SyncEvent(getYaw(), getPitch());
//        MeteorClient.EVENT_BUS.post(event);
//        if (event.isCancelled()) info.cancel();
//    }
}
