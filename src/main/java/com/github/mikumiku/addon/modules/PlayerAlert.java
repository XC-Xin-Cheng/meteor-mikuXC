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
import com.github.mikumiku.addon.util.ChatUtils;
import com.github.mikumiku.addon.util.MikuUtil;
import com.github.mikumiku.addon.util.Via;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

import java.util.*;


/**
 * 玩家提醒模块
 * 当有玩家进入设定范围时播放声音并发送聊天提醒
 */
public class PlayerAlert extends BaseModule {


    /**
     * 声音选择枚举
     */
    enum SoundChoice {
        BELL(SoundEvents.NOTE_BLOCK_BELL.value()),
        BELL_USE(SoundEvents.BELL_BLOCK),
        DING(SoundEvents.NOTE_BLOCK_PLING.value()),
        WARNING(SoundEvents.NOTE_BLOCK_BASS.value()),
        ALERT(SoundEvents.EXPERIENCE_ORB_PICKUP),
        DANGER(SoundEvents.ENDERMAN_TELEPORT);

        public final SoundEvent soundEvent;

        SoundChoice(SoundEvent soundEvent) {
            this.soundEvent = soundEvent;
        }
    }

    // 用于跟踪已知玩家，避免重复提醒
    private final Set<String> knownPlayers = new HashSet<>();

    // 用于跟踪视距内的玩家
    private final Set<String> playersInRenderDistance = new HashSet<>();

    // 用于跟踪靠近检测距离内的玩家
    private final Set<String> playersInCloseRange = new HashSet<>();

    // 用于缓存玩家套装信息（离开时使用）
    private final Map<String, String> playerArmorCache = new HashMap<>();

    // 模块设置
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgSound = settings.createGroup("声音设置");
    private final SettingGroup sgChat = settings.createGroup("聊天设置");

    // 通用设置
    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("靠近检测距离")
        .description("检测玩家的靠近")
        .defaultValue(20.0)
        .min(1.0)
        .max(200.0)
        .sliderMin(1.0)
        .sliderMax(200.0)
        .build()
    );

    private final Setting<Integer> checkInterval = sgGeneral.add(new IntSetting.Builder()
        .name("检测间隔")
        .description("检测玩家的间隔时间(tick)")
        .defaultValue(20)
        .min(1)
        .max(100)
        .sliderMin(1)
        .sliderMax(60)
        .build()
    );

    private final Setting<Boolean> onlyHostile = sgGeneral.add(new BoolSetting.Builder()
        .name("仅非队友玩家")
        .description("只提醒非队友玩家")
        .defaultValue(false)
        .build()
    );

    // 声音设置
    private final Setting<Boolean> playSound = sgSound.add(new BoolSetting.Builder()
        .name("播放声音")
        .description("检测到玩家时播放声音")
        .defaultValue(true)
        .build()
    );

    private final Setting<SoundChoice> soundType = sgSound.add(new EnumSetting.Builder<SoundChoice>()
        .name("进入声音")
        .description("播放的声音类型")
        .defaultValue(SoundChoice.ALERT)
        .build()
    );

    private final Setting<SoundChoice> soundTypeLeave = sgSound.add(new EnumSetting.Builder<SoundChoice>()
        .name("离开声音")
        .description("播放的声音类型")
        .defaultValue(SoundChoice.ALERT)
        .build()
    );
    private final Setting<SoundChoice> soundTypeNearby = sgSound.add(new EnumSetting.Builder<SoundChoice>()
        .name("靠近声音")
        .description("播放的声音类型")
        .defaultValue(SoundChoice.ALERT)
        .build()
    );

    private final Setting<Double> soundVolume = sgSound.add(new DoubleSetting.Builder()
        .name("音量")
        .description("声音播放音量")
        .defaultValue(1.0d)
        .min(0.1d)
        .max(10.0d)
        .sliderMin(0.1d)
        .sliderMax(10.0d)
        .build()
    );

    private final Setting<Double> soundPitch = sgSound.add(new DoubleSetting.Builder()
        .name("音调")
        .description("声音播放音调")
        .defaultValue(1.0d)
        .min(0.5d)
        .max(2.0d)
        .sliderMin(0.5d)
        .sliderMax(2.0d)
        .build()
    );


    // 聊天设置
    private final Setting<Boolean> chatAlert = sgChat.add(new BoolSetting.Builder()
        .name("聊天提醒")
        .description("在聊天框显示玩家提醒")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> showDistance = sgChat.add(new BoolSetting.Builder()
        .name("显示距离")
        .description("在提醒中显示玩家距离")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> showCoordinates = sgChat.add(new BoolSetting.Builder()
        .name("显示坐标")
        .description("在提醒中显示玩家坐标")
        .defaultValue(false)
        .build()
    );

    // 计数器用于控制检测频率
    private int tickCounter = 0;

    public PlayerAlert() {
        super(BaseModule.CATEGORY_MIKU_COMBAT, "看门狗", "当有玩家进入附近时播放声音并提醒");
    }

    @Override
    public void onActivate() {
        super.onActivate();
        knownPlayers.clear();
        playersInRenderDistance.clear();
        playersInCloseRange.clear();
        playerArmorCache.clear();
        tickCounter = 0;
        ChatUtils.sendMsg("看门狗已启动，靠近检测距离: " + range.get().intValue());
    }

    @Override
    public void onDeactivate() {
        knownPlayers.clear();
        playersInRenderDistance.clear();
        playersInCloseRange.clear();
        playerArmorCache.clear();
        ChatUtils.sendMsg("看门狗已关闭");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {

        if (mc == null || mc.player == null || mc.level == null) return;

        // 控制检测频率
        tickCounter++;
        if (tickCounter < checkInterval.get()) {
            return;
        }

        tickCounter = 0;

        checkForPlayers();
    }

    /**
     * 检测附近的玩家
     */
    private void checkForPlayers() {
        Set<String> currentRenderDistancePlayers = new HashSet<>();
        Set<String> currentCloseRangePlayers = new HashSet<>();

        // 遍历世界中的所有玩家 (视距内的玩家)
        for (Player player : mc.level.players()) {
            // 跳过自己
            if (player.getUUID().equals(mc.player.getUUID())) {
                continue;
            }

            String playerName = Via.getGameProfileName(player);
            double distance = mc.player.distanceTo(player);
            boolean friend = Friends.get().isFriend(player);
            // 检查是否为敌对玩家
            if (onlyHostile.get() && isFriendly(player)) continue;

            // 所有视距内的玩家
            currentRenderDistancePlayers.add(playerName);

            // 检查是否进入视距 (新玩家)
            if (!playersInRenderDistance.contains(playerName)) {
                alertPlayerEnterRenderDistance(player, distance);
            }

            // 检查是否在靠近检测距离内
            if (distance <= range.get()) {
                currentCloseRangePlayers.add(playerName);

                // 检查是否进入靠近检测距离 (新玩家进入靠近范围)
                if (!playersInCloseRange.contains(playerName)) {
                    alertPlayerEnterCloseRange(player, distance);
                }
            }
        }

        // 检查离开视距的玩家
        for (String playerName : playersInRenderDistance) {
            if (!currentRenderDistancePlayers.contains(playerName)) {
                alertPlayerLeaveRenderDistance(playerName);
            }
        }

        // 更新玩家列表
        playersInRenderDistance.clear();
        playersInRenderDistance.addAll(currentRenderDistancePlayers);

        playersInCloseRange.clear();
        playersInCloseRange.addAll(currentCloseRangePlayers);

        // 保持向后兼容性
        knownPlayers.clear();
        knownPlayers.addAll(currentCloseRangePlayers);
    }

    /**
     * 玩家进入视距提醒
     */
    private void alertPlayerEnterRenderDistance(Player player, double distance) {
        String playerName = Via.getGameProfileName(player);

        // 缓存玩家套装信息
        playerArmorCache.put(playerName, getArmorSetName(player));

        // 播放声音
        if (playSound.get()) {
            playEnterSound();
        }

        // 聊天框提醒
        if (chatAlert.get()) {
            sendChatAlert(player, distance, "进入视距");
        }
    }

    /**
     * 玩家离开视距提醒
     */
    private void alertPlayerLeaveRenderDistance(String playerName) {
        // 播放声音
        if (playSound.get()) {
            playLeaveSound();
        }

        // 聊天框提醒
        if (chatAlert.get()) {
            sendChatAlertLeave(playerName, "离开视距");
        }

        // 清除缓存
        playerArmorCache.remove(playerName);
    }

    /**
     * 玩家进入靠近检测距离提醒
     */
    private void alertPlayerEnterCloseRange(Player player, double distance) {
        String playerName = Via.getGameProfileName(player);

        // 更新缓存玩家套装信息
        playerArmorCache.put(playerName, getArmorSetName(player));

        // 播放声音
        if (playSound.get()) {
            playNearbySound();
        }

        // 聊天框提醒
        if (chatAlert.get()) {
            sendChatAlert(player, distance, "进入靠近范围");
        }
    }

    /**
     * 播放进入声音
     */
    private void playEnterSound() {
        mc.executeIfPossible(() -> {

            if (mc.getSoundManager() != null) {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(
                    soundType.get().soundEvent,
                    soundPitch.get().floatValue(),
                    soundVolume.get().floatValue()
                ));
            }
        });

    }

    /**
     * 播放离开声音
     */
    private void playLeaveSound() {
        mc.executeIfPossible(() -> {
            if (mc.getSoundManager() != null) {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(
                    soundTypeLeave.get().soundEvent,
                    soundPitch.get().floatValue(),
                    soundVolume.get().floatValue()
                ));
            }
        });
    }

    /**
     * 播放靠近声音
     */
    private void playNearbySound() {
        mc.executeIfPossible(() -> {
            if (mc.getSoundManager() != null) {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(
                    soundTypeNearby.get().soundEvent,
                    soundPitch.get().floatValue(),
                    soundVolume.get().floatValue()
                ));
            }
        });
    }

    /**
     * 发送聊天提醒 (进入)
     */
    private void sendChatAlert(Player player, double distance, String alertType) {
        String playerName = Via.getGameProfileName(player);
        String armorSet = getArmorSetName(player);
        StringBuilder message = new StringBuilder();

        message.append("§c[看门狗] §f").append(alertType).append(": §e").append(playerName);

        // 添加套装信息
        if (!armorSet.equals("裸吊")) {
            message.append(" §7[").append(armorSet).append("]");
        }

        if (showDistance.get()) {
            message.append(" §7(距离: §a").append(String.format("%.1f", distance)).append("§7)");
        }

        if (showCoordinates.get()) {
            message.append(" §7[坐标: §b")
                .append(String.format("%.0f, %.0f, %.0f",
                    player.getX(), player.getY(), player.getZ()))
                .append("§7]");
        }

        // 发送本地聊天消息
        ChatUtils.sendMsg(message.toString());
    }

    /**
     * 发送聊天提醒 (离开)
     */
    private void sendChatAlertLeave(String playerName, String alertType) {
        String armorSet = playerArmorCache.getOrDefault(playerName, "");
        StringBuilder message = new StringBuilder();
        message.append("§c[看门狗] §f").append(alertType).append(": §e").append(playerName);

        // 添加套装信息
        if (!armorSet.isEmpty() && !armorSet.equals("裸吊")) {
            message.append(" §7[").append(armorSet).append("]");
        }

        // 发送本地聊天消息
        ChatUtils.sendMsg(message.toString());
    }

    /**
     * 判断玩家是否为友好玩家
     */
    private boolean isFriendly(Player player) {

        boolean friend = Friends.get().isFriend(player);

        return friend;
    }

    /**
     * 获取格式化的距离字符串
     */
    private String getDistanceString(double distance) {
        if (distance < 10) {
            return String.format("%.1f", distance);
        } else {
            return String.format("%.0f", distance);
        }
    }

    /**
     * 手动刷新已知玩家列表
     */
    public void refreshPlayerList() {
        knownPlayers.clear();
        playersInRenderDistance.clear();
        playersInCloseRange.clear();
        playerArmorCache.clear();
        ChatUtils.sendMsg("已刷新玩家列表");
    }

    /**
     * 获取玩家套装名称
     */
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
}
