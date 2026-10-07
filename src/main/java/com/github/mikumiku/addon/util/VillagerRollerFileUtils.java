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

import com.github.mikumiku.addon.modules.VillagerRoller.RollingEnchantment;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.ListTag;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.function.Consumer;

/**
 * 村民刷取器文件操作工具类
 * 用于保存和加载附魔搜索列表到文件
 */
public class VillagerRollerFileUtils {

    /**
     * 从文件加载搜索附魔列表
     *
     * @param file              要加载的文件
     * @param searchingEnchants 要填充的附魔列表
     * @param errorCallback     错误消息回调函数
     * @return 是否加载成功
     */
    public static boolean loadSearchingFromFile(File file, List<RollingEnchantment> searchingEnchants, Consumer<String> errorCallback) {
        if (!file.exists() || !file.canRead()) {
            errorCallback.accept("文件不存在或无法加载");
            return false;
        }

        CompoundTag nbtData = null;
        try {
            nbtData = NbtIo.read(file.toPath());
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (nbtData == null) {
            errorCallback.accept("从文件加载NBT失败");
            return false;
        }

        ListTag nbtList = nbtData.getListOrEmpty("rolling");
        searchingEnchants.clear();

        for (Tag element : nbtList) {
            if (element.getId() != Tag.TAG_COMPOUND) {
                errorCallback.accept("无效的列表元素");
                return false;
            }
            searchingEnchants.add(new RollingEnchantment().fromTag((CompoundTag) element));
        }

        return true;
    }

    /**
     * 保存搜索附魔列表到文件
     *
     * @param file              要保存的文件
     * @param searchingEnchants 要保存的附魔列表
     * @param errorCallback     错误消息回调函数
     * @return 是否保存成功
     */
    public static boolean saveSearchingToFile(File file, List<RollingEnchantment> searchingEnchants, Consumer<String> errorCallback) {
        ListTag nbtList = new ListTag();
        for (RollingEnchantment enchantment : searchingEnchants) {
            nbtList.add(enchantment.toTag());
        }

        CompoundTag nbtCompound = new CompoundTag();
        nbtCompound.put("rolling", nbtList);

        if (Files.notExists(file.getParentFile().toPath()) && !file.getParentFile().mkdirs()) {
            errorCallback.accept("创建目录失败");
            return false;
        }

        try {
            NbtIo.write(nbtCompound, file.toPath());
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }
}
