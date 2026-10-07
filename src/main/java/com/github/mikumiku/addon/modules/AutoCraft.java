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
package com.github.mikumiku.addon.modules;

import com.github.mikumiku.addon.BaseModule;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ItemListSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.ContainerInput;

import java.util.Arrays;
import java.util.List;

public class AutoCraft extends BaseModule {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<List<Item>> items = sgGeneral.add(new ItemListSetting.Builder()
        .name("物品")
        .description("想要自动合成的物品列表")
        .defaultValue(Arrays.asList())
        .build()
    );

    private final Setting<Boolean> antiDesync = sgGeneral.add(new BoolSetting.Builder()
        .name("防不同步")
        .description("尝试防止物品栏不同步")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> craftAll = sgGeneral.add(new BoolSetting.Builder()
        .name("全部合成")
        .description("每次合成最大可能数量（Shift点击）")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> drop = sgGeneral.add(new BoolSetting.Builder()
        .name("丢弃物品")
        .description("自动丢弃合成物品（背包空间不足时有用）")
        .defaultValue(true)
        .build()
    );

    public AutoCraft() {
        super("喷射合成", "自动合成物品");
    }

    @Override
    public void onActivate() {
        super.onActivate();

    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.gameMode == null) return;
        if (items.get().isEmpty()) return;

        if (!(mc.player.containerMenu instanceof CraftingMenu)) return;


        if (antiDesync.get())
            mc.player.getInventory().tick();

//        CraftingMenu currentScreenHandler = (CraftingMenu) mc.player.currentScreenHandler;
//        List<Item> itemList = items.get();
//        List<RecipeCollection> recipeResultCollectionList = mc.player.getRecipeBook().getOrderedResults();
//        for (RecipeCollection recipeResultCollection : recipeResultCollectionList) {
//            for (RecipeHolder<?> recipe : recipeResultCollection.getRecipes(true)) {
//                if (!itemList.contains(recipe.value().getResult(mc.world.getRegistryManager()).getItem())) {
//                    continue;
//                }
//
//                mc.interactionManager.clickRecipe(currentScreenHandler.syncId, recipe, craftAll.get());
//                mc.interactionManager.clickSlot(currentScreenHandler.syncId, 0, 1,
//                    drop.get() ? ContainerInput.THROW : ContainerInput.QUICK_MOVE, mc.player);
//            }
//        }
    }
}
