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
import com.github.mikumiku.addon.util.MikuCompat;
import com.github.mikumiku.addon.util.MikuEntities;
import com.github.mikumiku.addon.util.MikuItems;
import com.github.mikumiku.addon.util.MikuUtil;
import com.github.mikumiku.addon.util.Via;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.settings.EntityTypeListSetting.Builder;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.misc.Names;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.*;
import java.util.Map.Entry;

public class EntityList extends BaseModule {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    public final Setting<Set<EntityType<?>>> allEntitys = sgGeneral.add(
        new Builder()
            .name("通用实体")
            .description("所有世界显示的实体")
            // 26.2 移除了 EntityType.EXPERIENCE_ORB 等静态字段，默认值改为按注册名解析，避免初始化时崩溃。
            .defaultValue(MikuEntities.getAll("experience_orb", "ender_pearl", "zombified_piglin"))
            .build()
    );

    public final Setting<Set<EntityType<?>>> entitys = sgGeneral.add(
        new Builder()
            .name("主世界实体")
            .description("仅在主世界显示的实体")
            .defaultValue(MikuEntities.getAll("experience_orb", "ender_pearl", "zombified_piglin"))
            .build()
    );

    public final Setting<Set<EntityType<?>>> netherEntitys = sgGeneral.add(
        new Builder()
            .name("下界实体")
            .description("仅在下界显示的实体")
            .defaultValue(MikuEntities.getAll(
                "experience_orb", "cow", "sheep", "pig",
                "horse", "zombie", "creeper", "bogged",
                "husk", "slime", "villager", "spider",
                "cave_spider", "drowned", "zombie_villager"
            ))
            .build()
    );

    public final Setting<SettingColor> entitysColor = sgGeneral.add(
        new ColorSetting.Builder()
            .name("实体颜色")
            .defaultValue(new SettingColor(138, 180, 248, 255)) // #8AB4F8 - 现代蓝色
            .build()
    );

    public final Setting<SettingColor> playerColor = sgGeneral.add(
        new ColorSetting.Builder()
            .name("玩家颜色")
            .defaultValue(new SettingColor(129, 201, 149, 255)) // #81C995 - 现代绿色
            .build()
    );

    private final SettingGroup itemGroup = settings.createGroup("物品");

    public final Setting<List<Item>> items1 = itemGroup.add(
        new ItemListSetting.Builder()
            .name("物品1")
            .defaultValue(new Item[]{
                Items.ELYTRA, MikuItems.get("white_shulker_box"), MikuItems.get("orange_shulker_box"),
                MikuItems.get("magenta_shulker_box"), MikuItems.get("light_blue_shulker_box"), MikuItems.get("yellow_shulker_box"),
                MikuItems.get("lime_shulker_box"), MikuItems.get("pink_shulker_box"), MikuItems.get("gray_shulker_box"),
                MikuItems.get("light_gray_shulker_box"), MikuItems.get("cyan_shulker_box"), MikuItems.get("purple_shulker_box"),
                MikuItems.get("blue_shulker_box"), MikuItems.get("brown_shulker_box"), MikuItems.get("green_shulker_box"),
                MikuItems.get("red_shulker_box"), MikuItems.get("black_shulker_box"), Items.BUNDLE,
                Items.ANCIENT_DEBRIS, Items.NETHERITE_SCRAP, Items.NETHERITE_INGOT,
                Items.NETHERITE_BLOCK, Items.NETHERITE_SWORD, Items.NETHERITE_AXE,
                Items.NETHERITE_HOE, Items.NETHERITE_PICKAXE, Items.NETHERITE_SHOVEL,
                Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS,
                Items.NETHERITE_BOOTS
            })
            .build()
    );

    public final Setting<SettingColor> items1Color = itemGroup.add(
        new ColorSetting.Builder()
            .name("物品1颜色")
            .defaultValue(new SettingColor(255, 107, 107, 255))
            .build()
    );

    public final Setting<List<Item>> items2 = itemGroup.add(
        new ItemListSetting.Builder()
            .name("物品2")
            .build()
    );

    public final Setting<SettingColor> items2Color = itemGroup.add(
        new ColorSetting.Builder()
            .name("物品2颜色")
            .defaultValue(new SettingColor(78, 205, 196, 255))
            .build()
    );

    //物品1: #FF6B6B - 柔和珊瑚红（替代刺眼的纯红）
    //物品2: #4ECDC4 - 现代青绿色（替代过亮的青色）
    //默认: #FFC14D - 温暖琥珀黄（替代纯黄色）
    //实体: #8AB4F8 - 现代蓝色（替代洋红色）
    //玩家: #81C995 - 现代绿色
    public final Setting<SettingColor> itemsColor = itemGroup.add(
        new ColorSetting.Builder()
            .name("物品默认颜色")
            .defaultValue(new SettingColor(255, 193, 77, 255))
            .build()
    );

    public final Setting<List<Item>> blackList = itemGroup.add(
        new ItemListSetting.Builder()
            .name("黑名单")
            .build()
    );

    private final SettingGroup ui = settings.createGroup("界面");

    public final Setting<Integer> xOffset = ui.add(
        new IntSetting.Builder()
            .name("X偏移")
            .min(0)
            .sliderMax(2048)
            .defaultValue(20)
            .build()
    );

    public final Setting<Integer> yOffset = ui.add(
        new IntSetting.Builder()
            .name("Y偏移")
            .min(0)
            .sliderMax(2048)
            .defaultValue(500)
            .build()
    );

    public final Setting<Integer> lineHeight = ui.add(
        new IntSetting.Builder()
            .name("行高")
            .min(0)
            .sliderMax(100)
            .defaultValue(20)
            .build()
    );

    public final Setting<Double> scale = ui.add(
        new DoubleSetting.Builder()
            .name("字体大小")
            .min(0.0)
            .sliderMax(6.0)
            .defaultValue(1.0)
            .build()
    );

    public EntityList() {
        super("实体列表Plus", "显示实体、玩家、凋落物列表.");
    }

    @Override
    public void onActivate() {
        super.onActivate();
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (!Utils.isLoading() && isActive()) {
            Set<Item> items1Set = new HashSet<>(items1.get());
            Set<Item> items2Set = new HashSet<>(items2.get());
            Set<Item> blackListSet = new HashSet<>(blackList.get());
            Map<Item, Integer> items1Map = new HashMap<>();
            Map<Item, Integer> items2Map = new HashMap<>();
            Map<Item, Integer> itemsMap = new HashMap<>();
            Map<EntityType<?>, Integer> entitysMap = new HashMap<>();
            Map<String, Double> playersMap = new HashMap<>();
            Map<String, String> playerArmorMap = new HashMap<>();
            ResourceKey<Level> registryKey = Via.getEntityWorld(mc.player).dimension();

            for (Entity entity : mc.level.entitiesForRendering()) {
                if (entity instanceof ItemEntity itemEntity) {
                    ItemStack stack = itemEntity.getItem();
                    Item item = stack.getItem();

                    if (!blackListSet.contains(item)) {
                        Map<Item, Integer> map;
                        if (items1Set.contains(item)) {
                            map = items1Map;
                        } else if (items2Set.contains(item)) {
                            map = items2Map;
                        } else {
                            map = itemsMap;
                        }

                        int count = map.containsKey(item) ? stack.getCount() + map.get(item) : stack.getCount();
                        map.put(item, count);
                    }
                } else if (entity instanceof Player player && entity != mc.player) {
                    double distance = mc.player.distanceTo(player);
                    String playerName = Via.getGameProfileName(player);
                    String armorSetName = getArmorSetName(player);
                    playersMap.put(playerName, distance);
                    playerArmorMap.put(playerName, armorSetName);
                } else {
                    EntityType<?> entityType = entity.getType();
                    Set<EntityType<?>> entityTypes;
                    Set<EntityType<?>> allEntitysSet = allEntitys.get();

                    // 首先检查是否在通用实体列表中
                    if (allEntitysSet.contains(entityType)) {
                        int qty = entitysMap.getOrDefault(entityType, 0);
                        entitysMap.put(entityType, qty + 1);
                        continue;
                    }

                    // 如果不在通用列表中，再根据维度检查
                    if (registryKey == ServerLevel.NETHER) {
                        entityTypes = netherEntitys.get();
                    } else if (registryKey == ServerLevel.OVERWORLD) {
                        entityTypes = entitys.get();
                    } else {
                        entityTypes = Collections.emptySet();
                    }

                    if (entityTypes.contains(entityType)) {
                        int qty = entitysMap.getOrDefault(entityType, 0);
                        entitysMap.put(entityType, qty + 1);
                    }
                }
            }

            int y = yOffset.get();
            y = drawPlayer(playersMap, playerArmorMap, y, playerColor.get(), event.graphics);
            y = draw(items1Map, y, items1Color.get(), event.graphics);
            y = draw(items2Map, y, items2Color.get(), event.graphics);
            y = draw(itemsMap, y, itemsColor.get(), event.graphics);
            y = drawEntity(entitysMap, y, entitysColor.get(), event.graphics);
        }
    }

    private int draw(Map<Item, Integer> grayMap, int y, Color color, Object graphics) {
        if (grayMap.isEmpty()) {
            return y;
        }
        TextRenderer textRenderer = TextRenderer.get();
        int x = xOffset.get();

        for (Entry<Item, Integer> entry : grayMap.entrySet()) {
            String text = String.format("[%s] x %s", Names.get(entry.getKey()), entry.getValue());
            MikuCompat.beginText(textRenderer, graphics, scale.get(), false);
            textRenderer.render(text, x, y, color, true);
            textRenderer.end();
            y += (int) (lineHeight.get() * scale.get());
        }

        return y;
    }

    private int drawEntity(Map<EntityType<?>, Integer> grayMap, int y, Color color, Object graphics) {
        TextRenderer textRenderer = TextRenderer.get();
        int x = xOffset.get();

        for (Entry<EntityType<?>, Integer> entry : grayMap.entrySet()) {
            String text = String.format("[%s] x %s", Names.get(entry.getKey()), entry.getValue());
            MikuCompat.beginText(textRenderer, graphics, scale.get(), false);
            textRenderer.render(text, x, y, color, true);
            textRenderer.end();
            y += (int) (lineHeight.get() * scale.get());
        }

        return y;
    }

    private int drawPlayer(Map<String, Double> playersMap, Map<String, String> playerArmorMap, int y, Color color, Object graphics) {
        TextRenderer textRenderer = TextRenderer.get();
        int x = xOffset.get();

        for (Entry<String, Double> entry : playersMap.entrySet()) {

            String name = entry.getKey();
            String armor = playerArmorMap.get(name);
            String text = String.format("%s [%s][%.1fm]", name, armor, entry.getValue());
            MikuCompat.beginText(textRenderer, graphics, scale.get(), false);
            textRenderer.render(text, x, y, color, true);
            textRenderer.end();
            y += (int) (lineHeight.get() * scale.get());
        }

        return y;
    }


//    private ItemStack getItem(Player entity, int index) {
//        return switch (index) {
//            case 0 -> entity.getMainHandStack();
//            case 1 -> entity.getInventory().armor.get(3); // 头盔
//            case 2 -> entity.getInventory().armor.get(2); // 胸甲
//            case 3 -> entity.getInventory().armor.get(1); // 护腿
//            case 4 -> entity.getInventory().armor.get(0); // 靴子
//            case 5 -> entity.getOffHandStack();
//            default -> ItemStack.EMPTY;
//        };
//    }

    private String getArmorSetName(Player player) {
        // 获取四个护甲槽
        ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        ItemStack legs = player.getItemBySlot(EquipmentSlot.LEGS);
        ItemStack feet = player.getItemBySlot(EquipmentSlot.FEET);

        // 全为空
        if (head.isEmpty() && chest.isEmpty() && legs.isEmpty() && feet.isEmpty()) {
            return "裸吊";
        }

        // 记录每种材质的数量
        Map<String, Integer> typeCount = new HashMap<>();
        ItemStack[] items = {head, chest, legs, feet};

        for (ItemStack item : items) {
            if (MikuUtil.isArmor(item.getItem())) {
                String matId = BuiltInRegistries.ITEM.getKey(item.getItem()).toString();

                // 转中文标签
                String type;
                if (matId.contains("netherite")) type = "合金";
                else if (matId.contains("diamond")) type = "钻石";
                else if (matId.contains("iron")) type = "铁";
                else if (matId.contains("gold")) type = "金";
                else if (matId.contains("chain")) type = "锁链";
                else if (matId.contains("leather")) type = "皮革";
                else if (matId.contains("turtle")) type = "海龟";
                else if (matId.contains("armadillo")) type = "犰狳";
                else type = matId; // 支持模组护甲

                typeCount.merge(type, 1, Integer::sum);
            }
        }

        // 没有任何护甲
        if (typeCount.isEmpty()) return "裸吊";

        // 找出现最多的材质
        String mainType = null;
        int maxCount = 0;
        for (Map.Entry<String, Integer> e : typeCount.entrySet()) {
            if (e.getValue() > maxCount) {
                mainType = e.getKey();
                maxCount = e.getValue();
            }
        }

        // 判断是否为混合套
        if (typeCount.size() == 1) {
            return mainType + "套";
        } else {
            return mainType + "套" + "(混)";
        }
    }

//    public static Text getArmorSetDisplayName(ItemStack armor) {
//        if (!(armor.getItem() instanceof ArmorItem armorItem)) return Text.literal("Unknown");
//
//        RegistryEntry<ArmorMaterial> material = armorItem.getMaterial();
//        ArmorMaterial material = armorItem.getComponents().get(DataComponentTypes.ma);
//
//        String name;
//        if (material.matches(ArmorMaterials.LEATHER)) name = "Leather";
//        else if (material.matches(ArmorMaterials.CHAIN)) name = "Chainmail";
//        else if (material.matches(ArmorMaterials.IRON)) name = "Iron";
//        else if (material.matches(ArmorMaterials.GOLD)) name = "Gold";
//        else if (material.matches(ArmorMaterials.DIAMOND)) name = "Diamond";
//        else if (material.matches(ArmorMaterials.TURTLE)) name = "Turtle";
//        else if (material.matches(ArmorMaterials.NETHERITE)) name = "Netherite";
//        else if (material.matches(ArmorMaterials.ARMADILLO)) name = "Armadillo";
//        else name = material.getIdAsString();
//
//        return Text.literal(name);
//    }
//
//    public static String getArmorSetName(ItemStack armor) {
//        if (!(armor.getItem() instanceof ArmorItem armorItem)) return "Unknown";
//
//        RegistryEntry<ArmorMaterial> material = armorItem.getMaterial();
//
//        if (material.matches(ArmorMaterials.LEATHER)) {
//            return "Leather";
//        } else if (material.matches(ArmorMaterials.CHAIN)) {
//            return "Chainmail";
//        } else if (material.matches(ArmorMaterials.IRON)) {
//            return "Iron";
//        } else if (material.matches(ArmorMaterials.GOLD)) {
//            return "Gold";
//        } else if (material.matches(ArmorMaterials.DIAMOND)) {
//            return "Diamond";
//        }   else if (material.matches(ArmorMaterials.NETHERITE)) {
//            return "Netherite";
//        }   else {
//            // 输出原始注册ID，方便调试自定义材质
//            return material.getIdAsString();
//        }
//    }

}
