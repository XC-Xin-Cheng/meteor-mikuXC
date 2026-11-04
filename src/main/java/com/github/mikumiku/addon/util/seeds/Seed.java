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

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.ChatFormatting;

import java.util.Optional;

public class Seed {
    public final Long seed;
    /** 选定的 Minecraft 版本；26.1 ~ 26.2 也是合法取值（见 {@link SeedVersion}）。 */
    public final SeedVersion version;

    public Seed(Long seed, SeedVersion version) {
        this.seed = seed;
        if (version == null)
            version = SeedVersion.latest();
        this.version = version;
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.put("seed", LongTag.valueOf(seed));
        tag.put("version", StringTag.valueOf(version.name));
        return tag;
    }

    public static Seed fromTag(CompoundTag tag) {
        // 修复：1.21.11 getString/getLong 返回 Optional，键缺失时 Optional.get() 抛 NoSuchElementException；
        // 改用带兜底的 getStringOr/getLongOr（有数据时结果一致）。
        String key = tag.getStringOr("version", "");
        Long key2 = tag.getLongOr("seed", 0L);

        return new Seed(
            key2,
            SeedVersion.fromString(key)
        );
    }

//    public Component toText() {
//        MutableComponent text = Component.literal(String.format("[%s%s%s] (%s)",
//            ChatFormatting.GREEN,
//            seed.toString(),
//            ChatFormatting.WHITE,
//            version.toString()
//        ));
//        text.setStyle(text.getStyle()
//            .withClickEvent(new ClickEvent(
//                ClickEvent.Action.COPY_TO_CLIPBOARD,
//                seed.toString()
//            ))
//            .withHoverEvent(new HoverEvent(
//                HoverEvent.Action.SHOW_TEXT,
//                Component.literal("Copy to clipboard")
//            ))
//        );
//        return text;
//    }
}
