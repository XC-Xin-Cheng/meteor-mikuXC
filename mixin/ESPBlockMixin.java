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

import com.github.mikumiku.addon.util.MeteorXrayBridge;
import meteordevelopment.meteorclient.systems.modules.render.blockesp.ESPBlock;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 让 Meteor 本体的方块透视读方块状态时，优先采用种子矿透算出来的矿石。
 *
 * <p>本体 block-esp 在 {@code update()} 里从客户端世界读该格的方块状态，
 * 在 {@code isNeighbour}/{@code isNeighbourDiagonal} 里也读相邻格来判断要不要连线。
 * 服务器把矿石混淆成石头时这三处都会读到石头，于是框是石头的颜色、相邻的矿石也连不起来。
 * 只要把这三处的读取换成叠加层，本体的渲染、分组、配色就都会按真实矿石走。
 *
 * <p>叠加层里没有该坐标时原样返回世界方块，所以没开联动时这是纯粹的直通。
 */
@Mixin(ESPBlock.class)
public class ESPBlockMixin {

    @Redirect(method = "update",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/ClientLevel;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockState meteorMiku$updateState(ClientLevel level, BlockPos pos) {
        return stateOrWorld(level, pos);
    }

    @Redirect(method = "isNeighbour",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/ClientLevel;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockState meteorMiku$neighbourState(ClientLevel level, BlockPos pos) {
        return stateOrWorld(level, pos);
    }

    @Redirect(method = "isNeighbourDiagonal",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/ClientLevel;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockState meteorMiku$diagonalState(ClientLevel level, BlockPos pos) {
        return stateOrWorld(level, pos);
    }

    private static BlockState stateOrWorld(ClientLevel level, BlockPos pos) {
        BlockState state = MeteorXrayBridge.stateAt(pos.getX(), pos.getY(), pos.getZ());
        return state != null ? state : level.getBlockState(pos);
    }
}
