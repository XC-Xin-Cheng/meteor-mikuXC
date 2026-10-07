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

import baritone.api.utils.BlockOptionalMetaLookup;
import baritone.pathing.movement.CalculationContext;
import com.github.mikumiku.addon.mixinface.MagicMix;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Pseudo
@Mixin(targets = "baritone.process.MineProcess")
public class MineProcessMixin {

    @Shadow(remap = false)
    private List<BlockPos> a; // knownOreLocations

    @Inject(method = "a(Ljava/util/List;Lbaritone/pathing/movement/CalculationContext;)V", at = @At("HEAD"), cancellable = true, remap = false)
    private void onRescan(List<BlockPos> already, CalculationContext context, CallbackInfo ci) {
        if (MagicMix.oreSimBaritone()) {
            a = MagicMix.oreGoals;
            ci.cancel();
        }
    }

    @Redirect(method = "a(Lbaritone/pathing/movement/CalculationContext;Lbaritone/api/utils/BlockOptionalMetaLookup;Ljava/util/List;Lnet/minecraft/util/math/BlockPos;)Z",
        at = @At(value = "INVOKE", target = "Lbaritone/api/utils/BlockOptionalMetaLookup;has(Lnet/minecraft/block/BlockState;)Z"))
    private static boolean onPruneStream(BlockOptionalMetaLookup instance, BlockState blockState) {
        if (!MagicMix.oreSimBaritone()) {
            return instance.has(blockState);
        }
        return !blockState.isAir();
    }

}
