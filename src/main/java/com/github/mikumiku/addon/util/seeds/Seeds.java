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
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.systems.System;
import meteordevelopment.meteorclient.systems.config.Config;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

import java.util.HashMap;
import java.util.Optional;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class Seeds extends System<Seeds> {
    private static final Seeds INSTANCE = new Seeds();

    public HashMap<String, Seed> seeds = new HashMap<>();

    public Seeds() {
        super("seeds");
        init();
        load(MeteorClient.FOLDER);
    }

    public static Seeds get() {
        return INSTANCE;
    }

    public Seed getSeed() {
        if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null) {
            // 26.x 的服务器版本字符串是 "26.1" / "26.2"（或带补丁号），
            // seedfinding 的 MCVersion.fromString 认不出来，这里必须用本模组的
            // SeedVersion。
            SeedVersion version = SeedVersion.fromString(mc.getSingleplayerServer().getServerVersion());
            if (version == null)
                version = SeedVersion.latest();
            return new Seed(mc.getSingleplayerServer().overworld().getSeed(), version);
        }

        return seeds.get(Utils.getWorldName());
    }

    public void setSeed(String seed, SeedVersion version) {
        if (mc.hasSingleplayerServer()) return;

        long numSeed = toSeed(seed);
        seeds.put(Utils.getWorldName(), new Seed(numSeed, version));
    }

    /** 兼容旧调用：种子库版本转成 {@link SeedVersion} 后保存。 */
    public void setSeed(String seed, MCVersion version) {
        setSeed(seed, SeedVersion.fromMCVersion(version));
    }

    public void setSeed(String seed) {
        if (mc.hasSingleplayerServer()) return;

        ServerData server = mc.getCurrentServer();
        SeedVersion ver = null;
        if (server != null)
            ver = SeedVersion.fromString(server.version.getString());
        if (ver == null) {
            String targetVer = "unknown";
            if (server != null) targetVer = server.version.getString();
            sendInvalidVersionWarning(seed, targetVer);
            ver = SeedVersion.latest();
        }
        setSeed(seed, ver);
    }

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        seeds.forEach((key, seed) -> {
            if (seed == null) return;
            tag.put(key, seed.toTag());
        });
        return tag;
    }

    @Override
    public Seeds fromTag(CompoundTag tag) {
        tag.keySet().forEach(key -> {
            Object compound = tag.getCompound(key);
            CompoundTag key2;
            if (compound instanceof Optional<?> optional) {
                key2 = (CompoundTag) optional.get();
            } else {
                key2 = (CompoundTag) compound;
            }

            seeds.put(key, Seed.fromTag(key2));
        });
        return this;
    }

    // https://minecraft.wiki/w/Seed_(level_generation)#Java_Edition
    /** 把玩家输入的种子文本转成数值：能解析成整数就按整数，否则按原版一样取字符串哈希。 */
    public static long toSeed(String inSeed) {
        try {
            return Long.parseLong(inSeed);
        } catch (NumberFormatException e) {
            return inSeed.strip().hashCode();
        }
    }

    private static void sendInvalidVersionWarning(String seed, String targetVer) {
        MutableComponent msg = Component.literal(String.format("无法解析 Minecraft 版本 \"%s\". 改用 %s. 如果您想更改版本，请运行: ", targetVer, SeedVersion.latest().name));
        String cmd = String.format("%sseed %s ", Config.get().prefix, seed);
        MutableComponent cmdText = Component.literal(cmd + "<version>");
//        cmdText.setStyle(cmdText.getStyle()
//            .withUnderline(true)
//            .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, cmd))
//            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("run command")))
//        );
        msg.append(cmdText);
        msg.setStyle(msg.getStyle()
            .withColor(ChatFormatting.YELLOW)
        );
        ChatUtils.sendMsg("Seed", msg);
    }
}
