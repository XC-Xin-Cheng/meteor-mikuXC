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
package com.github.mikumiku.addon.modules.sorter;


import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.ChestMenu;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 箱子信息类
 */
public class ChestInfo {
    public final ChestLocation location;
    public ChestLocation pairedChest = null; // 大箱子的另一半

    public String dedicatedItemType; // 专用物品类型
    public Map<String, Integer> itemCounts = new HashMap<>(); // 物品类型 -> 数量
    public long lastUpdated;
    public boolean isEmpty;

    public ChestInfo(ChestLocation location) {
        this.location = location;
        this.itemCounts = new HashMap<>();
        this.lastUpdated = System.currentTimeMillis();
        this.isEmpty = true;
    }

    public ChestInfo(ChestLocation location, ChestLocation pairedChest) {
        this.location = location;
        this.itemCounts = new HashMap<>();
        this.lastUpdated = System.currentTimeMillis();
        this.isEmpty = true;
        this.pairedChest = pairedChest;
    }

    public ChestInfo(String location) {
        ChestLocation location1 = ChestLocation.fromJson(location);
        this.location = location1;
        this.itemCounts = new HashMap<>();
        this.lastUpdated = System.currentTimeMillis();
        this.isEmpty = true;
    }

    /**
     * 更新箱子内容
     */
    public void updateContents(ChestMenu container, ItemClassifier classifier) {
        itemCounts.clear();
        isEmpty = true;
        //大箱子 0 - 53
        //小箱子 0 - 26
        int maxslot = 26;
        if (container.getContainer().getContainerSize() > 62) {
            maxslot = 53;
        }
        // 统计箱子内的物品
        for (int slot = 0; slot <= maxslot; slot++) {
            ItemStack item = container.getContainer().getItem(slot);
            if (item != null && !item.isEmpty()) {
                isEmpty = false;

                String itemType = classifier.classifyItem(item);
                itemCounts.put(itemType, itemCounts.getOrDefault(itemType, 0) + item.getCount());
            }
        }

        // 确定专用物品类型
        if (isEmpty) {
            dedicatedItemType = null;
        } else if (itemCounts.size() == 1) {
            // 只有一种物品类型
            dedicatedItemType = itemCounts.keySet().iterator().next();
        } else {
            // 多种物品类型，选择数量最多的
            dedicatedItemType = itemCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
        }

        lastUpdated = System.currentTimeMillis();
    }

    /**
     * 检查是否可以存储指定类型的物品
     */
    public boolean canStore(String itemType) {
        return isEmpty || Objects.equals(dedicatedItemType, itemType);
    }

    /**
     * 获取剩余空间（估算）
     */
    public int getAvailableSpace() {
        // 根据箱子类型确定容量
        int maxCapacity;
        if (location.isDoubleChest) {
            maxCapacity = 54 * 64; // 大箱子54格
        } else {
            maxCapacity = 27 * 64; // 单箱子27格
        }

        if (isEmpty) return maxCapacity;

        int totalItems = itemCounts.values().stream().mapToInt(Integer::intValue).sum();
        return Math.max(0, maxCapacity - totalItems); // 简化计算
    }

    @Override
    public String toString() {
        return "ChestInfo{" +
            "location=" + location +
            ", dedicatedItemType='" + dedicatedItemType + '\'' +
            ", isEmpty=" + isEmpty +
            ", itemCounts=" + itemCounts +
            '}';
    }
}
