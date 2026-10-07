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

import meteordevelopment.meteorclient.settings.IVisible;
import meteordevelopment.meteorclient.settings.Setting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

public class BlockPosListSetting extends Setting<List<BlockPos>> {

    public BlockPosListSetting(String name, String description, List<BlockPos> defaultValue,
                               Consumer<List<BlockPos>> onChanged,
                               Consumer<Setting<List<BlockPos>>> onModuleActivated,
                               IVisible visible) {
        super(name, description, defaultValue, onChanged, onModuleActivated, visible);
    }

    @Override
    protected List<BlockPos> parseImpl(String str) {
        // 格式: x1,y1,z1;x2,y2,z2;x3,y3,z3
        String[] entries = str.split(";");
        List<BlockPos> positions = new ArrayList<>(entries.length);

        try {
            for (String entry : entries) {
                String[] coords = entry.trim().split(",");
                if (coords.length != 3) continue;

                int x = Integer.parseInt(coords[0].trim());
                int y = Integer.parseInt(coords[1].trim());
                int z = Integer.parseInt(coords[2].trim());
                positions.add(new BlockPos(x, y, z));
            }
        } catch (NumberFormatException ignored) {
        }

        return positions;
    }

    @Override
    protected void resetImpl() {
        value = new ArrayList<>(defaultValue);
    }

    @Override
    protected boolean isValueValid(List<BlockPos> value) {
        return true;
    }

    @Override
    protected CompoundTag save(CompoundTag tag) {
        ListTag valueTag = new ListTag();

        for (BlockPos pos : get()) {
            valueTag.add(new IntArrayTag(new int[]{pos.getX(), pos.getY(), pos.getZ()}));
        }

        tag.put("value", valueTag);
        return tag;
    }

    @Override
    protected List<BlockPos> load(CompoundTag tag) {
        get().clear();

        ListTag valueTag = tag.getList("value").get();
        for (Tag element : valueTag) {
            int[] coords = ((IntArrayTag) element).getAsIntArray();
            if (coords.length == 3) {
                get().add(new BlockPos(coords[0], coords[1], coords[2]));
            }
        }

        return get();
    }

    public static class Builder extends SettingBuilder<Builder, List<BlockPos>, BlockPosListSetting> {

        public Builder() {
            super(new ArrayList<>(0));
        }

        public Builder defaultValue(BlockPos... defaults) {
            return defaultValue(defaults != null ? Arrays.asList(defaults) : new ArrayList<>());
        }

        public Builder defaultValue(List<BlockPos> defaults) {
            this.defaultValue = new ArrayList<>(defaults);
            return this;
        }

        @Override
        public BlockPosListSetting build() {
            return new BlockPosListSetting(name, description, defaultValue, onChanged, onModuleActivated, visible);
        }
    }
}
