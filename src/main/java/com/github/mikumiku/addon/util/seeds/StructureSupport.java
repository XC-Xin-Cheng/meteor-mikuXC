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
package com.github.mikumiku.addon.util.seeds;

import com.seedfinding.mccore.version.MCVersion;

import java.util.List;

/**
 * 结构搜索与容器战利品搜索的版本支持规则。
 *
 * <p>结构位置、生物群系和容器战利品都由 seedfinding 的 {@code mc_feature} /
 * {@code mc_biome} 计算。每个结构内部都有一张按 {@link MCVersion} 排序的配置表，
 * 取“不新于所选版本的最后一条”；如果所选版本比该结构最早的一条配置还老，
 * 取到的就是 {@code null}，直接构造结构对象会出错。
 *
 * <p>「种子矿透」({@code SeedMine}) 的矿物模拟不走这张配置表，所以它能列出
 * 26.2 一路到 a1.0.4 的完整版本表；结构搜索同样列出这份完整版本表（用的是同一个
 * {@link SeedVersion} 枚举），但真正计算前要先按结构过滤：低于某结构最低版本的
 * 直接在聊天栏说明并跳过，而不是抛异常。
 */
public final class StructureSupport {

    /** 要塞：种子库配置表从 1.0 起。 */
    public static final MCVersion MIN_STRONGHOLD = MCVersion.v1_0;

    /** 村庄、沙漠神殿、丛林神庙、废弃矿井、海底神殿、沼泽小屋、下界要塞：配置表从 1.8 起。 */
    public static final MCVersion MIN_LEGACY = MCVersion.v1_8;

    /** 末地城、雪屋：配置表从 1.9 起。 */
    public static final MCVersion MIN_END_CITY = MCVersion.v1_9;

    /** 林地府邸：配置表从 1.11 起。 */
    public static final MCVersion MIN_MANSION = MCVersion.v1_11;

    /** 沉船、埋藏的宝藏、海底废墟：配置表从 1.13 起。 */
    public static final MCVersion MIN_OCEAN = MCVersion.v1_13;

    /** 掠夺者前哨站：配置表从 1.14 起。 */
    public static final MCVersion MIN_OUTPOST = MCVersion.v1_14;

    /** 堡垒遗迹、下界化石、废弃传送门：配置表从 1.16 起。 */
    public static final MCVersion MIN_NETHER_116 = MCVersion.v1_16;

    /**
     * 古城（远古城市）：1.19 加入。种子库没有它的结构类与容器战利品表，
     * 位置与深暗之域判定改走本版原生的 {@code AncientCityLocator}。
     */
    public static final MCVersion MIN_ANCIENT_CITY = MCVersion.v1_19;

    private StructureSupport() {
    }

    /**
     * 判断某个结构在当前库版本下能不能算。
     *
     * @param version 实际传给种子库的版本
     * @param minimum 该结构最低可用版本，取值见本类的 {@code MIN_*} 常量
     * @param name    结构显示名，用于提示
     * @param skipped 收集被跳过的结构说明的列表（可以为 {@code null}）
     * @return {@code true} 表示可以算；{@code false} 表示版本太老，已记入 {@code skipped}
     */
    public static boolean check(MCVersion version, MCVersion minimum, String name, List<String> skipped) {
        if (version != null && version.isNewerOrEqualTo(minimum)) return true;
        if (skipped != null) {
            skipped.add(name + "（需 " + minimum.name + "+）");
        }
        return false;
    }

    /** 把被跳过的结构拼成一句给聊天栏的提示。 */
    public static String describeSkipped(List<String> skipped) {
        return String.join("、", skipped);
    }
}
