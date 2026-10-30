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
package com.github.mikumiku.addon.util;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.utils.player.Rotations;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

/**
 * 旋转管理器 - 用于管理玩家视角旋转的核心组件
 *
 * <p>主要功能：</p>
 * <ul>
 *   <li>统一管理所有模块的视角旋转需求</li>
 *   <li>基于优先级系统处理旋转冲突</li>
 *   <li>提供平滑的视角过渡和同步机制</li>
 *   <li>确保旋转操作的线程安全性</li>
 * </ul>
 *
 * <p>使用场景：</p>
 * <ul>
 *   <li>自动建造模块需要精确朝向目标方块</li>
 *   <li>战斗模块需要快速转向敌人</li>
 *   <li>种植模块需要面向种植位置</li>
 *   <li>挖掘模块需要对准目标方块</li>
 * </ul>
 *
 * <p>工作原理：</p>
 * <ol>
 *   <li>模块通过 {@link #register(Rotation)} 注册旋转请求</li>
 *   <li>系统根据优先级决定是否接受请求</li>
 *   <li>发送网络包更新服务器端玩家朝向</li>
 *   <li>同步客户端摄像机视角</li>
 *   <li>操作完成后调用 {@link #sync()} 恢复原始朝向</li>
 * </ol>
 *
 * @author GGB Helper
 * @since 1.0.0
 */
public class RotationManager {
    Minecraft mc = Minecraft.getInstance();

    // 单例实例，使用 volatile 确保多线程环境下的可见性
    private static volatile RotationManager instance;

    public Rotation currentRotation = null;
    Timer timer = new Timer();

    // 私有构造函数，防止外部直接实例化
    private RotationManager() {
        MeteorClient.EVENT_BUS.subscribe(this);
        mc = Minecraft.getInstance();

    }

    /**
     * 获取 RotationManager 的单例实例
     * 使用双重检查锁定模式实现线程安全的懒加载
     *
     * @return RotationManager 的唯一实例
     */
    public static RotationManager getInstance() {
        if (instance == null) {
            synchronized (RotationManager.class) {
                if (instance == null) {
                    instance = new RotationManager();
                }
            }
        }
        return instance;
    }

    /**
     * 注册一个旋转请求到旋转管理器
     *
     * <p>该方法是旋转系统的核心，负责处理所有模块的旋转需求。
     * 系统采用优先级机制来解决多个模块同时请求旋转的冲突。</p>
     *
     * <p>执行流程：</p>
     * <ol>
     *   <li>检查当前是否有更高优先级的旋转正在执行</li>
     *   <li>如果优先级足够，接受新的旋转请求</li>
     *   <li>向服务器发送 PlayerMoveC2SPacket 更新玩家朝向</li>
     *   <li>同步客户端摄像机视角，确保视觉一致性</li>
     *   <li>重置内部计时器，开始新的旋转周期</li>
     * </ol>
     *
     * <p>优先级说明：</p>
     * <ul>
     *   <li>数值越大优先级越高</li>
     *   <li>战斗相关操作通常具有最高优先级</li>
     *   <li>建造和种植操作具有中等优先级</li>
     *   <li>移动和导航操作具有较低优先级</li>
     * </ul>
     *
     * @param rotation 要注册的旋转对象，包含目标偏航角、俯仰角和优先级
     * @return {@code true} 如果旋转请求被成功接受并执行；
     * {@code false} 如果当前有更高优先级的旋转正在执行，请求被拒绝
     * @see Rotation#getPriority() 获取旋转优先级
     * @see #sync() 完成旋转后的同步操作
     */
    public boolean register(Rotation rotation) {
        return register(rotation, Humanized.GLOBAL);
    }

    /**
     * 带类人化档位的旋转请求。
     *
     * <p>各模块可以在自己的「类人化」设置组里决定旋转要不要加手抖，以及抖多大；
     * 没给档位时走全局档。档位关闭时行为与原来完全一致。</p>
     *
     * @param rotation 要注册的旋转对象，包含目标偏航角、俯仰角和优先级
     * @param profile  本模块的类人化档位，{@code null} 表示不加手抖
     * @return 与 {@link #register(Rotation)} 相同
     */
    public boolean register(Rotation rotation, Humanized.Profile profile) {

        this.currentRotation = rotation;
        this.timer.reset();

        // 「类人化输入」开启时，给发往服务器的视角叠加一点高斯手抖，
        // 打破“每一包都精确对准方块中心”的完美数据。噪声幅度很小，
        // 默认 0.6 度，在常规交互距离下偏移不到 0.05 格，不影响正常放置/攻击。
        float yaw = rotation.getYaw();
        float pitch = rotation.getPitch();
        if (profile != null && profile.enabled && profile.viewInput && profile.affectPackets) {
            yaw += Humanized.jitter(profile, profile.noiseDegrees);
            pitch = Mth.clamp(pitch + Humanized.jitter(profile, profile.noiseDegrees * 0.7f), -90.0f, 90.0f);
        }

        mc.player
            .connection
            .send(
                Via.getFull(
                    mc.player.getX(),
                    mc.player.getY(),
                    mc.player.getZ(),
                    yaw,
                    pitch,
                    mc.player.onGround()
                )
            );
        Rotations.setCamRotation(yaw, pitch);
        return true;
    }

    /**
     * 同步并重置旋转状态
     *
     * <p>该方法在完成旋转操作后调用，用于清理旋转状态并确保
     * 客户端和服务器的玩家朝向保持一致。</p>
     *
     * <p>执行操作：</p>
     * <ol>
     *   <li>向服务器发送当前玩家的真实朝向（而非旋转管理器设置的朝向）</li>
     *   <li>清除当前旋转状态，允许新的旋转请求</li>
     *   <li>恢复玩家的自然视角控制</li>
     * </ol>
     *
     * <p>调用时机：</p>
     * <ul>
     *   <li>方块放置操作完成后</li>
     *   <li>攻击动作执行完毕后</li>
     *   <li>交互操作结束后</li>
     *   <li>任何需要精确朝向的操作完成后</li>
     * </ul>
     *
     * <p><strong>重要提醒：</strong></p>
     * <p>每次调用 {@link #register(Rotation)} 后都应该调用此方法，
     * 否则可能导致玩家视角被锁定在特定方向，影响正常游戏体验。</p>
     *
     * @see #register(Rotation) 注册旋转请求
     */
    public void sync() {
        mc.player
            .connection
            .send(
                Via.getFull(
                    mc.player.getX(),
                    mc.player.getY(),
                    mc.player.getZ(),
                    mc.player.getYRot(),
                    mc.player.getXRot(),
                    mc.player.onGround()
                )
            );
        this.currentRotation = null;
    }
}
