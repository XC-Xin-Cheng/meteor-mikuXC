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

import java.util.Locale;

/**
 * 种子库版本选择。
 *
 * <p>seedfinding 的 {@link MCVersion} 只收录到 1.21，没有 26.1 / 26.1.1 / 26.1.2
 * 和 26.2 这些常量，而本模组跑在 26.1 ~ 26.2 上，所以枚举无法直接扩展。这里在
 * {@code MCVersion} 之外包了一层：前四项 {@link #v26_2}、{@link #v26_1_2}、
 * {@link #v26_1_1}、{@link #v26_1} 就是 26.2、26.1.2、26.1.1、26.1，其余各项
 * 与种子库一一对应。
 *
 * <p>26.x 的矿物模拟不走种子库，而是直接调用本版游戏原生的世界生成算法
 * （与上游 meteor-rejects 的 {@code OreSim} 做法一致，见 {@code SeedMine}）；
 * 补丁号之间世界生成算法相同，选项只用于记录种子来源版本。选到 26.x 时，
 * 种子库里用最接近的 1.21 作为元数据，只用于其它模块读取 {@link Seed#version}
 * 时的兼容回退，不影响矿位计算。
 *
 * <p>1.21.1 ~ 1.21.11 这些补丁号同样没有对应的库常量（库里 1.21 系列只有
 * {@link MCVersion#v1_21}）。它们被单列出来是为了让用户能如实填入自己的版本，
 * 但 {@link #hasExactLibraryData()} 返回 {@code false}：种子库里没有这些版本自己的
 * 数据，也不允许拿 1.21 的数据顶替，所以结构搜索等模块会明确跳过并提示。
 */
public enum SeedVersion {

    /** Minecraft 26.2（本版原生算法，推荐）。种子库未收录，元数据回退到 1.21。 */
    v26_2("26.2", MCVersion.v1_21, true),
    /** Minecraft 26.1.2（本版原生算法）。种子库未收录，元数据回退到 1.21。 */
    v26_1_2("26.1.2", MCVersion.v1_21, true),
    /** Minecraft 26.1.1（本版原生算法）。种子库未收录，元数据回退到 1.21。 */
    v26_1_1("26.1.1", MCVersion.v1_21, true),
    /** Minecraft 26.1（本版原生算法）。种子库未收录，元数据回退到 1.21。 */
    v26_1("26.1", MCVersion.v1_21, true),
    /** Minecraft 1.21.11（种子库没有该版本自己的数据，不参与任何计算，也不拿 1.21 顶替）。 */
    v1_21_11("1.21.11", MCVersion.v1_21, false),
    /** Minecraft 1.21.10（种子库没有该版本自己的数据，不参与任何计算，也不拿 1.21 顶替）。 */
    v1_21_10("1.21.10", MCVersion.v1_21, false),
    /** Minecraft 1.21.9（种子库没有该版本自己的数据，不参与任何计算，也不拿 1.21 顶替）。 */
    v1_21_9("1.21.9", MCVersion.v1_21, false),
    /** Minecraft 1.21.8（种子库没有该版本自己的数据，不参与任何计算，也不拿 1.21 顶替）。 */
    v1_21_8("1.21.8", MCVersion.v1_21, false),
    /** Minecraft 1.21.7（种子库没有该版本自己的数据，不参与任何计算，也不拿 1.21 顶替）。 */
    v1_21_7("1.21.7", MCVersion.v1_21, false),
    /** Minecraft 1.21.6（种子库没有该版本自己的数据，不参与任何计算，也不拿 1.21 顶替）。 */
    v1_21_6("1.21.6", MCVersion.v1_21, false),
    /** Minecraft 1.21.5（种子库没有该版本自己的数据，不参与任何计算，也不拿 1.21 顶替）。 */
    v1_21_5("1.21.5", MCVersion.v1_21, false),
    /** Minecraft 1.21.4（种子库没有该版本自己的数据，不参与任何计算，也不拿 1.21 顶替）。 */
    v1_21_4("1.21.4", MCVersion.v1_21, false),
    /** Minecraft 1.21.3（种子库没有该版本自己的数据，不参与任何计算，也不拿 1.21 顶替）。 */
    v1_21_3("1.21.3", MCVersion.v1_21, false),
    /** Minecraft 1.21.2（种子库没有该版本自己的数据，不参与任何计算，也不拿 1.21 顶替）。 */
    v1_21_2("1.21.2", MCVersion.v1_21, false),
    /** Minecraft 1.21.1（种子库没有该版本自己的数据，不参与任何计算，也不拿 1.21 顶替）。 */
    v1_21_1("1.21.1", MCVersion.v1_21, false),
    /** Minecraft 1.21（种子库版本，仅作元数据）。 */
    v1_21("1.21", MCVersion.v1_21, false),
    /** Minecraft 1.20.6（种子库版本，仅作元数据）。 */
    v1_20_6("1.20.6", MCVersion.v1_20_6, false),
    /** Minecraft 1.20.5（种子库版本，仅作元数据）。 */
    v1_20_5("1.20.5", MCVersion.v1_20_5, false),
    /** Minecraft 1.20.4（种子库版本，仅作元数据）。 */
    v1_20_4("1.20.4", MCVersion.v1_20_4, false),
    /** Minecraft 1.20.3（种子库版本，仅作元数据）。 */
    v1_20_3("1.20.3", MCVersion.v1_20_3, false),
    /** Minecraft 1.20.2（种子库版本，仅作元数据）。 */
    v1_20_2("1.20.2", MCVersion.v1_20_2, false),
    /** Minecraft 1.20.1（种子库版本，仅作元数据）。 */
    v1_20_1("1.20.1", MCVersion.v1_20_1, false),
    /** Minecraft 1.20（种子库版本，仅作元数据）。 */
    v1_20("1.20", MCVersion.v1_20, false),
    /** Minecraft 1.19.4（种子库版本，仅作元数据）。 */
    v1_19_4("1.19.4", MCVersion.v1_19_4, false),
    /** Minecraft 1.19.3（种子库版本，仅作元数据）。 */
    v1_19_3("1.19.3", MCVersion.v1_19_3, false),
    /** Minecraft 1.19.2（种子库版本，仅作元数据）。 */
    v1_19_2("1.19.2", MCVersion.v1_19_2, false),
    /** Minecraft 1.19.1（种子库版本，仅作元数据）。 */
    v1_19_1("1.19.1", MCVersion.v1_19_1, false),
    /** Minecraft 1.19（种子库版本，仅作元数据）。 */
    v1_19("1.19", MCVersion.v1_19, false),
    /** Minecraft 1.18.2（种子库版本，仅作元数据）。 */
    v1_18_2("1.18.2", MCVersion.v1_18_2, false),
    /** Minecraft 1.18.1（种子库版本，仅作元数据）。 */
    v1_18_1("1.18.1", MCVersion.v1_18_1, false),
    /** Minecraft 1.18（种子库版本，仅作元数据）。 */
    v1_18("1.18", MCVersion.v1_18, false),
    /** Minecraft 1.17.1（种子库版本，仅作元数据）。 */
    v1_17_1("1.17.1", MCVersion.v1_17_1, false),
    /** Minecraft 1.17（种子库版本，仅作元数据）。 */
    v1_17("1.17", MCVersion.v1_17, false),
    /** Minecraft 1.16.5（种子库版本，仅作元数据）。 */
    v1_16_5("1.16.5", MCVersion.v1_16_5, false),
    /** Minecraft 1.16.4（种子库版本，仅作元数据）。 */
    v1_16_4("1.16.4", MCVersion.v1_16_4, false),
    /** Minecraft 1.16.3（种子库版本，仅作元数据）。 */
    v1_16_3("1.16.3", MCVersion.v1_16_3, false),
    /** Minecraft 1.16.2（种子库版本，仅作元数据）。 */
    v1_16_2("1.16.2", MCVersion.v1_16_2, false),
    /** Minecraft 1.16.1（种子库版本，仅作元数据）。 */
    v1_16_1("1.16.1", MCVersion.v1_16_1, false),
    /** Minecraft 1.16（种子库版本，仅作元数据）。 */
    v1_16("1.16", MCVersion.v1_16, false),
    /** Minecraft 1.15.2（种子库版本，仅作元数据）。 */
    v1_15_2("1.15.2", MCVersion.v1_15_2, false),
    /** Minecraft 1.15.1（种子库版本，仅作元数据）。 */
    v1_15_1("1.15.1", MCVersion.v1_15_1, false),
    /** Minecraft 1.15（种子库版本，仅作元数据）。 */
    v1_15("1.15", MCVersion.v1_15, false),
    /** Minecraft 1.14.4（种子库版本，仅作元数据）。 */
    v1_14_4("1.14.4", MCVersion.v1_14_4, false),
    /** Minecraft 1.14.3（种子库版本，仅作元数据）。 */
    v1_14_3("1.14.3", MCVersion.v1_14_3, false),
    /** Minecraft 1.14.2（种子库版本，仅作元数据）。 */
    v1_14_2("1.14.2", MCVersion.v1_14_2, false),
    /** Minecraft 1.14.1（种子库版本，仅作元数据）。 */
    v1_14_1("1.14.1", MCVersion.v1_14_1, false),
    /** Minecraft 1.14（种子库版本，仅作元数据）。 */
    v1_14("1.14", MCVersion.v1_14, false),
    /** Minecraft 1.13.2（种子库版本，仅作元数据）。 */
    v1_13_2("1.13.2", MCVersion.v1_13_2, false),
    /** Minecraft 1.13.1（种子库版本，仅作元数据）。 */
    v1_13_1("1.13.1", MCVersion.v1_13_1, false),
    /** Minecraft 1.13（种子库版本，仅作元数据）。 */
    v1_13("1.13", MCVersion.v1_13, false),
    /** Minecraft 1.12.2（种子库版本，仅作元数据）。 */
    v1_12_2("1.12.2", MCVersion.v1_12_2, false),
    /** Minecraft 1.12.1（种子库版本，仅作元数据）。 */
    v1_12_1("1.12.1", MCVersion.v1_12_1, false),
    /** Minecraft 1.12（种子库版本，仅作元数据）。 */
    v1_12("1.12", MCVersion.v1_12, false),
    /** Minecraft 1.11.2（种子库版本，仅作元数据）。 */
    v1_11_2("1.11.2", MCVersion.v1_11_2, false),
    /** Minecraft 1.11.1（种子库版本，仅作元数据）。 */
    v1_11_1("1.11.1", MCVersion.v1_11_1, false),
    /** Minecraft 1.11（种子库版本，仅作元数据）。 */
    v1_11("1.11", MCVersion.v1_11, false),
    /** Minecraft 1.10.2（种子库版本，仅作元数据）。 */
    v1_10_2("1.10.2", MCVersion.v1_10_2, false),
    /** Minecraft 1.10.1（种子库版本，仅作元数据）。 */
    v1_10_1("1.10.1", MCVersion.v1_10_1, false),
    /** Minecraft 1.10（种子库版本，仅作元数据）。 */
    v1_10("1.10", MCVersion.v1_10, false),
    /** Minecraft 1.9.4（种子库版本，仅作元数据）。 */
    v1_9_4("1.9.4", MCVersion.v1_9_4, false),
    /** Minecraft 1.9.3（种子库版本，仅作元数据）。 */
    v1_9_3("1.9.3", MCVersion.v1_9_3, false),
    /** Minecraft 1.9.2（种子库版本，仅作元数据）。 */
    v1_9_2("1.9.2", MCVersion.v1_9_2, false),
    /** Minecraft 1.9.1（种子库版本，仅作元数据）。 */
    v1_9_1("1.9.1", MCVersion.v1_9_1, false),
    /** Minecraft 1.9（种子库版本，仅作元数据）。 */
    v1_9("1.9", MCVersion.v1_9, false),
    /** Minecraft 1.8.9（种子库版本，仅作元数据）。 */
    v1_8_9("1.8.9", MCVersion.v1_8_9, false),
    /** Minecraft 1.8.8（种子库版本，仅作元数据）。 */
    v1_8_8("1.8.8", MCVersion.v1_8_8, false),
    /** Minecraft 1.8.7（种子库版本，仅作元数据）。 */
    v1_8_7("1.8.7", MCVersion.v1_8_7, false),
    /** Minecraft 1.8.6（种子库版本，仅作元数据）。 */
    v1_8_6("1.8.6", MCVersion.v1_8_6, false),
    /** Minecraft 1.8.5（种子库版本，仅作元数据）。 */
    v1_8_5("1.8.5", MCVersion.v1_8_5, false),
    /** Minecraft 1.8.4（种子库版本，仅作元数据）。 */
    v1_8_4("1.8.4", MCVersion.v1_8_4, false),
    /** Minecraft 1.8.3（种子库版本，仅作元数据）。 */
    v1_8_3("1.8.3", MCVersion.v1_8_3, false),
    /** Minecraft 1.8.2（种子库版本，仅作元数据）。 */
    v1_8_2("1.8.2", MCVersion.v1_8_2, false),
    /** Minecraft 1.8.1（种子库版本，仅作元数据）。 */
    v1_8_1("1.8.1", MCVersion.v1_8_1, false),
    /** Minecraft 1.8（种子库版本，仅作元数据）。 */
    v1_8("1.8", MCVersion.v1_8, false),
    /** Minecraft 1.7.10（种子库版本，仅作元数据）。 */
    v1_7_10("1.7.10", MCVersion.v1_7_10, false),
    /** Minecraft 1.7.9（种子库版本，仅作元数据）。 */
    v1_7_9("1.7.9", MCVersion.v1_7_9, false),
    /** Minecraft 1.7.8（种子库版本，仅作元数据）。 */
    v1_7_8("1.7.8", MCVersion.v1_7_8, false),
    /** Minecraft 1.7.7（种子库版本，仅作元数据）。 */
    v1_7_7("1.7.7", MCVersion.v1_7_7, false),
    /** Minecraft 1.7.6（种子库版本，仅作元数据）。 */
    v1_7_6("1.7.6", MCVersion.v1_7_6, false),
    /** Minecraft 1.7.5（种子库版本，仅作元数据）。 */
    v1_7_5("1.7.5", MCVersion.v1_7_5, false),
    /** Minecraft 1.7.4（种子库版本，仅作元数据）。 */
    v1_7_4("1.7.4", MCVersion.v1_7_4, false),
    /** Minecraft 1.7.3（种子库版本，仅作元数据）。 */
    v1_7_3("1.7.3", MCVersion.v1_7_3, false),
    /** Minecraft 1.7.2（种子库版本，仅作元数据）。 */
    v1_7_2("1.7.2", MCVersion.v1_7_2, false),
    /** Minecraft 1.6.4（种子库版本，仅作元数据）。 */
    v1_6_4("1.6.4", MCVersion.v1_6_4, false),
    /** Minecraft 1.6.2（种子库版本，仅作元数据）。 */
    v1_6_2("1.6.2", MCVersion.v1_6_2, false),
    /** Minecraft 1.6.1（种子库版本，仅作元数据）。 */
    v1_6_1("1.6.1", MCVersion.v1_6_1, false),
    /** Minecraft 1.5.2（种子库版本，仅作元数据）。 */
    v1_5_2("1.5.2", MCVersion.v1_5_2, false),
    /** Minecraft 1.5.1（种子库版本，仅作元数据）。 */
    v1_5_1("1.5.1", MCVersion.v1_5_1, false),
    /** Minecraft 1.4.7（种子库版本，仅作元数据）。 */
    v1_4_7("1.4.7", MCVersion.v1_4_7, false),
    /** Minecraft 1.4.6（种子库版本，仅作元数据）。 */
    v1_4_6("1.4.6", MCVersion.v1_4_6, false),
    /** Minecraft 1.4.5（种子库版本，仅作元数据）。 */
    v1_4_5("1.4.5", MCVersion.v1_4_5, false),
    /** Minecraft 1.4.4（种子库版本，仅作元数据）。 */
    v1_4_4("1.4.4", MCVersion.v1_4_4, false),
    /** Minecraft 1.4.2（种子库版本，仅作元数据）。 */
    v1_4_2("1.4.2", MCVersion.v1_4_2, false),
    /** Minecraft 1.3.2（种子库版本，仅作元数据）。 */
    v1_3_2("1.3.2", MCVersion.v1_3_2, false),
    /** Minecraft 1.3.1（种子库版本，仅作元数据）。 */
    v1_3_1("1.3.1", MCVersion.v1_3_1, false),
    /** Minecraft 1.2.5（种子库版本，仅作元数据）。 */
    v1_2_5("1.2.5", MCVersion.v1_2_5, false),
    /** Minecraft 1.2.4（种子库版本，仅作元数据）。 */
    v1_2_4("1.2.4", MCVersion.v1_2_4, false),
    /** Minecraft 1.2.3（种子库版本，仅作元数据）。 */
    v1_2_3("1.2.3", MCVersion.v1_2_3, false),
    /** Minecraft 1.2.2（种子库版本，仅作元数据）。 */
    v1_2_2("1.2.2", MCVersion.v1_2_2, false),
    /** Minecraft 1.2.1（种子库版本，仅作元数据）。 */
    v1_2_1("1.2.1", MCVersion.v1_2_1, false),
    /** Minecraft 1.1（种子库版本，仅作元数据）。 */
    v1_1("1.1", MCVersion.v1_1, false),
    /** Minecraft 1.0（种子库版本，仅作元数据）。 */
    v1_0("1.0", MCVersion.v1_0, false),
    /** Minecraft b1.8.1（种子库版本，仅作元数据）。 */
    vb1_8_1("b1.8.1", MCVersion.vb1_8_1, false),
    /** Minecraft b1.8（种子库版本，仅作元数据）。 */
    vb1_8("b1.8", MCVersion.vb1_8, false),
    /** Minecraft b1.7.3（种子库版本，仅作元数据）。 */
    vb1_7_3("b1.7.3", MCVersion.vb1_7_3, false),
    /** Minecraft b1.7.2（种子库版本，仅作元数据）。 */
    vb1_7_2("b1.7.2", MCVersion.vb1_7_2, false),
    /** Minecraft b1.7（种子库版本，仅作元数据）。 */
    vb1_7("b1.7", MCVersion.vb1_7, false),
    /** Minecraft b1.6.6（种子库版本，仅作元数据）。 */
    vb1_6_6("b1.6.6", MCVersion.vb1_6_6, false),
    /** Minecraft b1.6.5（种子库版本，仅作元数据）。 */
    vb1_6_5("b1.6.5", MCVersion.vb1_6_5, false),
    /** Minecraft b1.6.4（种子库版本，仅作元数据）。 */
    vb1_6_4("b1.6.4", MCVersion.vb1_6_4, false),
    /** Minecraft b1.6.3（种子库版本，仅作元数据）。 */
    vb1_6_3("b1.6.3", MCVersion.vb1_6_3, false),
    /** Minecraft b1.6.2（种子库版本，仅作元数据）。 */
    vb1_6_2("b1.6.2", MCVersion.vb1_6_2, false),
    /** Minecraft b1.6.1（种子库版本，仅作元数据）。 */
    vb1_6_1("b1.6.1", MCVersion.vb1_6_1, false),
    /** Minecraft b1.6（种子库版本，仅作元数据）。 */
    vb1_6("b1.6", MCVersion.vb1_6, false),
    /** Minecraft b1.5_01（种子库版本，仅作元数据）。 */
    vb1_5_01("b1.5_01", MCVersion.vb1_5_01, false),
    /** Minecraft b1.5（种子库版本，仅作元数据）。 */
    vb1_5("b1.5", MCVersion.vb1_5, false),
    /** Minecraft b1.4_01（种子库版本，仅作元数据）。 */
    vb1_4_01("b1.4_01", MCVersion.vb1_4_01, false),
    /** Minecraft b1.4（种子库版本，仅作元数据）。 */
    vb1_4("b1.4", MCVersion.vb1_4, false),
    /** Minecraft b1.3_01（种子库版本，仅作元数据）。 */
    vb1_3_01("b1.3_01", MCVersion.vb1_3_01, false),
    /** Minecraft b1.3b（种子库版本，仅作元数据）。 */
    vb1_3b("b1.3b", MCVersion.vb1_3b, false),
    /** Minecraft b1.2_02（种子库版本，仅作元数据）。 */
    vb1_2_02("b1.2_02", MCVersion.vb1_2_02, false),
    /** Minecraft b1.2_01（种子库版本，仅作元数据）。 */
    vb1_2_01("b1.2_01", MCVersion.vb1_2_01, false),
    /** Minecraft b1.2（种子库版本，仅作元数据）。 */
    vb1_2("b1.2", MCVersion.vb1_2, false),
    /** Minecraft b1.1_02（种子库版本，仅作元数据）。 */
    vb1_1_02("b1.1_02", MCVersion.vb1_1_02, false),
    /** Minecraft b1.1_01（种子库版本，仅作元数据）。 */
    vb1_1_01("b1.1_01", MCVersion.vb1_1_01, false),
    /** Minecraft b1.0.2（种子库版本，仅作元数据）。 */
    vb1_0_2("b1.0.2", MCVersion.vb1_0_2, false),
    /** Minecraft b1.0_01（种子库版本，仅作元数据）。 */
    vb1_0_01("b1.0_01", MCVersion.vb1_0_01, false),
    /** Minecraft b1.0（种子库版本，仅作元数据）。 */
    vb1_0("b1.0", MCVersion.vb1_0, false),
    /** Minecraft a1.2.6（种子库版本，仅作元数据）。 */
    va1_2_6("a1.2.6", MCVersion.va1_2_6, false),
    /** Minecraft a1.2.5（种子库版本，仅作元数据）。 */
    va1_2_5("a1.2.5", MCVersion.va1_2_5, false),
    /** Minecraft a1.2.4_01（种子库版本，仅作元数据）。 */
    va1_2_4_01("a1.2.4_01", MCVersion.va1_2_4_01, false),
    /** Minecraft a1.2.3_04（种子库版本，仅作元数据）。 */
    va1_2_3_04("a1.2.3_04", MCVersion.va1_2_3_04, false),
    /** Minecraft a1.2.3_02（种子库版本，仅作元数据）。 */
    va1_2_3_02("a1.2.3_02", MCVersion.va1_2_3_02, false),
    /** Minecraft a1.2.3_01（种子库版本，仅作元数据）。 */
    va1_2_3_01("a1.2.3_01", MCVersion.va1_2_3_01, false),
    /** Minecraft a1.2.3（种子库版本，仅作元数据）。 */
    va1_2_3("a1.2.3", MCVersion.va1_2_3, false),
    /** Minecraft a1.2.2b（种子库版本，仅作元数据）。 */
    va1_2_2b("a1.2.2b", MCVersion.va1_2_2b, false),
    /** Minecraft a1.2.2a（种子库版本，仅作元数据）。 */
    va1_2_2a("a1.2.2a", MCVersion.va1_2_2a, false),
    /** Minecraft a1.2.1_01（种子库版本，仅作元数据）。 */
    va1_2_1_01("a1.2.1_01", MCVersion.va1_2_1_01, false),
    /** Minecraft a1.2.1（种子库版本，仅作元数据）。 */
    va1_2_1("a1.2.1", MCVersion.va1_2_1, false),
    /** Minecraft a1.2.0_02（种子库版本，仅作元数据）。 */
    va1_2_0_02("a1.2.0_02", MCVersion.va1_2_0_02, false),
    /** Minecraft a1.2.0_01（种子库版本，仅作元数据）。 */
    va1_2_0_01("a1.2.0_01", MCVersion.va1_2_0_01, false),
    /** Minecraft a1.2.0（种子库版本，仅作元数据）。 */
    va1_2_0("a1.2.0", MCVersion.va1_2_0, false),
    /** Minecraft a1.1.2_01（种子库版本，仅作元数据）。 */
    va1_1_2_01("a1.1.2_01", MCVersion.va1_1_2_01, false),
    /** Minecraft a1.1.2（种子库版本，仅作元数据）。 */
    va1_1_2("a1.1.2", MCVersion.va1_1_2, false),
    /** Minecraft a1.1.0（种子库版本，仅作元数据）。 */
    va1_1_0("a1.1.0", MCVersion.va1_1_0, false),
    /** Minecraft a1.0.17_04（种子库版本，仅作元数据）。 */
    va1_0_17_04("a1.0.17_04", MCVersion.va1_0_17_04, false),
    /** Minecraft a1.0.17_02（种子库版本，仅作元数据）。 */
    va1_0_17_02("a1.0.17_02", MCVersion.va1_0_17_02, false),
    /** Minecraft a1.0.16（种子库版本，仅作元数据）。 */
    va1_0_16("a1.0.16", MCVersion.va1_0_16, false),
    /** Minecraft a1.0.15（种子库版本，仅作元数据）。 */
    va1_0_15("a1.0.15", MCVersion.va1_0_15, false),
    /** Minecraft a1.0.14（种子库版本，仅作元数据）。 */
    va1_0_14("a1.0.14", MCVersion.va1_0_14, false),
    /** Minecraft a1.0.11（种子库版本，仅作元数据）。 */
    va1_0_11("a1.0.11", MCVersion.va1_0_11, false),
    /** Minecraft a1.0.5_01（种子库版本，仅作元数据）。 */
    va1_0_5_01("a1.0.5_01", MCVersion.va1_0_5_01, false),
    /** Minecraft a1.0.4（种子库版本，仅作元数据）。 */
    va1_0_4("a1.0.4", MCVersion.va1_0_4, false);

    /** 供界面显示的 Minecraft 版本号，例如 {@code 26.1.2}。 */
    public final String name;

    /** 传给 seedfinding 的库版本；26.x 会回退到最接近的 1.21。 */
    public final MCVersion libraryVersion;

    /** 是否为“游戏本版原生算法”选项（目前是 26.2 / 26.1.2 / 26.1.1 / 26.1）。 */
    public final boolean nativeWorldgen;

    /** 「游戏版本」输入框允许的最低版本（含）；比它更老的输入一律按空白处理。 */
    public static final String MIN_INPUT_VERSION = "1.12.2";

    /** 「游戏版本」输入框允许的最高版本（含）；比它更新的输入一律按空白处理。 */
    public static final String MAX_INPUT_VERSION = "26.2";

    SeedVersion(String name, MCVersion libraryVersion, boolean nativeWorldgen) {
        this.name = name;
        this.libraryVersion = libraryVersion;
        this.nativeWorldgen = nativeWorldgen;
    }

    /** 转成种子库能用的版本；26.x 回退到 1.21，仅作为元数据。 */
    public MCVersion toMCVersion() {
        return libraryVersion;
    }

    /** 当前应默认选中的版本：本模组支持 26.1 ~ 26.2，默认最新的 26.2。 */
    public static SeedVersion latest() {
        return v26_2;
    }

    /**
     * 把外部版本字符串解析成枚举项。
     *
     * <p>精确匹配 {@code 26.2}、{@code 26.1.2}、{@code 26.1.1}、{@code 26.1}、
     * {@code 1.21.1} ~ {@code 1.21.11} 等写法，也兼容旧配置里保存的库版本号
     * （例如 {@code 1.21}）。26.1 / 26.2 系列里没列出的补丁号（例如 {@code 26.1.3}、
     * {@code 26.2.1}）按主次版本归到对应的原生选项；其它未列出的 26.x 归到最新的
     * 原生选项。1.21 系列里没列出的补丁号（例如将来的 {@code 1.21.12}）统一归到库
     * 版本 {@link #v1_21}。
     */
    public static SeedVersion fromString(String version) {
        if (version == null) return null;
        String trimmed = version.trim();
        if (trimmed.isEmpty()) return null;

        for (SeedVersion value : values()) {
            if (value.name.equalsIgnoreCase(trimmed) || value.name().equalsIgnoreCase(trimmed)) {
                return value;
            }
        }

        // 26.x 系列里没列出的补丁号归到对应的原生选项（同一个 26 家族共用本版原生数据）
        if (trimmed.startsWith("26.2")) return v26_2;
        if (trimmed.startsWith("26.1")) return v26_1_2;
        if (trimmed.startsWith("26.")) return latest();

        // 1.21 系列里没列出的补丁号（例如 1.21.12）种子库里没有对应数据，也不允许
        // 用 1.21 的数据顶替，返回 null 让调用方按“无该版本数据”处理。
        return null;
    }

    /**
     * 由种子库版本反查本枚举项；找不到时回退到 {@link #latest()}。
     *
     * <p>先按游戏版本号精确匹配（库的 {@code 1.21} → {@link #v1_21}），再退回到
     * 库版本相同的第一项。1.21.9~1.21.11 这些条目同样回退到 {@code 1.21}，所以
     * 必须让精确匹配优先，否则旧配置里保存的 {@code 1.21} 会被错误地记成 1.21.11。
     */
    public static SeedVersion fromMCVersion(MCVersion version) {
        if (version == null) return latest();
        for (SeedVersion value : values()) {
            if (value.name.equalsIgnoreCase(version.name)) return value;
        }
        for (SeedVersion value : values()) {
            // 跳过 26.x：它们的库版本只是为了兼容回退用的 1.21
            if (!value.nativeWorldgen && value.libraryVersion == version) return value;
        }
        return latest();
    }

    /**
     * 解析用户在「游戏版本」输入框里手写的版本号。
     *
     * <p>只接受 {@link #MIN_INPUT_VERSION}（1.12.2）到 {@link #MAX_INPUT_VERSION}（26.2）
     * 之间的、且枚举里确实收录的版本：低于 1.12.2、高于 26.2、空串、认不出来、或者
     * 落在范围内但并非真实收录版本（如 {@code 1.20.7}）的写法，一律返回 {@code null}
     * ——也就是需求里的“空白”，调用方拿到 {@code null} 就不做任何搜索、也不往种子库写数据。
     * 这里绝不把 {@code 1.20.7} 就近当成 {@code 1.20.6} 之类的旧版本。
     *
     * <p>能认出的写法：
     * <ul>
     *   <li>游戏版本号：{@code 26.2}、{@code 26.1.2}、{@code 1.21.4}、{@code 1.21.1}、
     *       {@code 1.20.1}、{@code 1.16.5}、{@code 1.12.2} 等；</li>
     *   <li>26.1 / 26.2 的补丁号（如 {@code 26.1.3}）归到对应的原生选项；</li>
     *   <li>旧配置里保存的枚举名（如 {@code v1_20_1}），以及 {@code v1.20.1}、
     *       {@code minecraft 1.20.1} 这类写法。</li>
     * </ul>
     */
    public static SeedVersion parseInput(String version) {
        if (version == null) return null;
        String trimmed = version.trim();
        if (trimmed.isEmpty()) return null;

        // 旧配置保存的是枚举常量名（v1_20_1），先原样匹配，避免被下面的去前缀处理破坏。
        for (SeedVersion value : values()) {
            if (value.name().equalsIgnoreCase(trimmed)) {
                return inInputRange(value.name) ? value : null;
            }
        }

        String normalized = normalizeInput(trimmed);
        if (normalized.isEmpty()) return null;

        // 精确匹配游戏版本号（26.2、1.21.4、1.20.1 …）
        for (SeedVersion value : values()) {
            if (value.name.equalsIgnoreCase(normalized)) {
                return inInputRange(value.name) ? value : null;
            }
        }

        // 1.21 系列里没有单列的补丁号（例如 1.21.12）：种子库没有对应数据，
        // 也不允许用 1.21 顶替，按空白处理。

        // 26.1 / 26.2 的补丁号：归到对应的原生选项；范围之外（如 26.2.1、26.3）返回空白
        if (normalized.startsWith("26.1")) {
            return inInputRange(normalized) ? v26_1_2 : null;
        }
        if (normalized.startsWith("26.2")) {
            return inInputRange(normalized) ? v26_2 : null;
        }

        // 其它写法：既不是已收录的版本，也不在允许的写法里，一律空白。
        // 不在枚举里的版本（例如并不存在的 1.20.7、1.16.6）绝不就近取一个旧版本顶替。
        return null;
    }

    /** 这个版本号是否落在用户可输入的 1.12.2 ~ 26.2 范围内。 */
    public static boolean inInputRange(String version) {
        if (version == null) return false;
        String trimmed = version.trim();
        if (trimmed.isEmpty()) return false;
        return compareVersions(trimmed, MIN_INPUT_VERSION) >= 0
            && compareVersions(trimmed, MAX_INPUT_VERSION) <= 0;
    }

    /** 去掉 {@code minecraft } 前缀、{@code v} 前缀以及 {@code -forge} 之类的后缀，转小写。 */
    private static String normalizeInput(String version) {
        String result = version.trim().toLowerCase(Locale.ROOT);
        if (result.startsWith("minecraft")) {
            result = result.substring("minecraft".length()).trim();
        }
        if (result.startsWith("v") && result.length() > 1) {
            result = result.substring(1).trim();
        }
        int cut = result.indexOf(' ');
        if (cut >= 0) result = result.substring(0, cut).trim();
        int dash = result.indexOf('-');
        if (dash > 0) result = result.substring(0, dash).trim();
        return result;
    }

    /** 按数字段比较两个点分版本号（1.12.10 &gt; 1.12.2）；认不出数字的段按 0 处理。 */
    private static int compareVersions(String a, String b) {
        int[] left = versionParts(a);
        int[] right = versionParts(b);
        int length = Math.max(left.length, right.length);
        for (int i = 0; i < length; i++) {
            int l = i < left.length ? left[i] : 0;
            int r = i < right.length ? right[i] : 0;
            if (l != r) return Integer.compare(l, r);
        }
        return 0;
    }

    private static int[] versionParts(String version) {
        String[] parts = version.split("\\.");
        int[] result = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                result[i] = Integer.parseInt(parts[i].trim());
            } catch (NumberFormatException e) {
                result[i] = 0;
            }
        }
        return result;
    }

    /**
     * 种子库里是否存在与该版本号完全对应的库数据（游戏版本号与库版本号一致）。
     *
     * <p>这是“能不能拿种子库的数据算这个版本”的唯一判据：只有返回 {@code true} 时，
     * 才允许把 {@link #libraryVersion} 交给 seedfinding 计算。
     *
     * <p>返回 {@code false} 的条目（{@code 1.21.1} ~ {@code 1.21.11} 与 {@code 26.x}）
     * 在种子库里没有自己的数据，按需求绝不允许用别的版本的库数据顶替：调用方应当跳过
     * 对应的结构并在聊天栏说明，除非能用本版运行版本的原生数据算出该版本自己的结果
     * （见 {@link #sharesNativeWorldgen(String)}）。
     */
    public boolean hasExactLibraryData() {
        return name.equalsIgnoreCase(libraryVersion.name);
    }

    /** 这个选项是否要靠种子库回退版本计算（游戏版本号与库版本号不一致）。 */
    public boolean usesLibraryFallback() {
        return !hasExactLibraryData();
    }

    /**
     * 所选版本与“当前运行版本”是否共用同一套本版原生世界生成数据。
     *
     * <p>只有两端都是本版原生算法选项、且主版本段相同（例如运行 26.1、所选 26.2）时
     * 才返回 {@code true}：补丁号之间世界生成算法一致，可以直接拿运行版本的原生数据
     * 计算。1.19 ~ 1.21 这些版本的原生参数（生物群系分布、战利品表等）可能与运行版本
     * 不同，返回 {@code false}，调用方据此避免“用别的版本顶替”。
     *
     * @param runningVersionName 当前运行版本号，例如 {@code SharedConstants.getCurrentVersion().name()}
     */
    public boolean sharesNativeWorldgen(String runningVersionName) {
        if (!nativeWorldgen || runningVersionName == null) return false;
        String running = runningVersionName.trim().toLowerCase(Locale.ROOT);
        if (running.isEmpty()) return false;
        return majorOf(name).equals(majorOf(running));
    }

    /** 版本号的主版本段（{@code 26.1.2} → {@code 26}）；没有点号时返回原值。 */
    private static String majorOf(String version) {
        int dot = version.indexOf('.');
        return dot < 0 ? version : version.substring(0, dot);
    }

    @Override
    public String toString() {
        return name;
    }

    /** 小写版本号，便于日志与命令使用。 */
    public String lowerName() {
        return name.toLowerCase(Locale.ROOT);
    }
}
