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
import com.github.mikumiku.addon.util.Via;
import meteordevelopment.meteorclient.events.entity.EntityAddedEvent;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import java.util.*;

public class PearlMark extends BaseModule {

    public PearlMark() {
        super("谁的珍珠", "为末影珍珠标记主人的名字");
    }

    // 设置组
    private final SettingGroup generalSettings = settings.getDefaultGroup();

    // 智能缓存设置
    private final Setting<Boolean> smartCache = generalSettings.add(new BoolSetting.Builder()
        .name("智能缓存")
        .description("启用智能缓存功能，记住珍珠主人的名字")
        .defaultValue(true)
        .build()
    );

    // 缓存系统 - 存储珍珠ID和对应的主人名字
    public final Map<UUID, String> pearlOwnerCache = new HashMap<>();

    // 缓存NBT标签键
    public static String CACHE_TAG = "miku_pearl_owner_cache";

    // 缓存清理计数器
    public int cleanupCounter = 0;
    public static final int CLEANUP_INTERVAL = 200000000; // tick清理一次


    public void onEntityAddedooo(EntityAddedEvent event) {
        if (mc.level == null) return;

        Entity entity = event.entity;
        if (entity instanceof ThrownEnderpearl pearl) {
            mc.level.players().stream()
                .min(Comparator.comparingDouble(p ->
                    Via.getEntityPos(p).distanceTo(new Vec3(pearl.getX(), pearl.getY(), pearl.getZ()))
                ))
                .ifPresent(player -> {
                    pearl.setCustomName(player.getName());
                    pearl.setCustomNameVisible(true);
                });
        }
    }

    @EventHandler
    private void onEntityAdded(EntityAddedEvent event) {
        if (mc.level == null) return;

        Entity entity = event.entity;
        if (entity instanceof ThrownEnderpearl pearl) {
            // 生成珍珠的唯一ID
            UUID pearlId = pearl.getUUID();

            if (smartCache.get()) {
                // 检查缓存中是否已有记录
                String cachedOwner = pearlOwnerCache.get(pearlId);
                if (cachedOwner != null) {
                    // 使用缓存的名字
                    pearl.setCustomName(Component.literal(cachedOwner));
                    pearl.setCustomNameVisible(true);
                    return;
                }
            }

            // 查找最近的玩家
            mc.level.players().stream()
                .filter(p ->
                    Via.getEntityPos(p).distanceTo(new Vec3(pearl.getX(), pearl.getY(), pearl.getZ())) < 2
                )
                .min(Comparator.comparingDouble(p ->
                    Via.getEntityPos(p).distanceTo(new Vec3(pearl.getX(), pearl.getY(), pearl.getZ()))
                ))
                .ifPresent(player -> {
                    String ownerName = Via.getGameProfileName(player);
                    pearl.setCustomName(Component.literal(ownerName));
                    pearl.setCustomNameVisible(true);

                    // 如果启用了智能缓存，保存到缓存中
                    if (smartCache.get()) {
                        pearlOwnerCache.put(pearlId, ownerName);
                    }
                });
        }
    }


    private void onRender2Dooo(Render2DEvent event) {
        if (!Utils.isLoading() && isActive()) {

            for (Entity entity : mc.level.entitiesForRendering()) {
                if (entity instanceof ThrownEnderpearl pearlEntity) {
                    Entity owner = pearlEntity.getOwner();
                    if (owner != null) {
                        if (owner instanceof Player player) {
                            String pearlOwnerName = Via.getGameProfileName(player);
                            pearlEntity.setCustomName(player.getName());
                            pearlEntity.setCustomNameVisible(true);
                        } else {
                            pearlEntity.setCustomName(owner.getName());
                            pearlEntity.setCustomNameVisible(true);
                        }
                    } else {
                        pearlEntity.setCustomName(Component.literal("野生珍珠"));
                        pearlEntity.setCustomNameVisible(true);
                    }
                }
            }

        }
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (!Utils.isLoading() && isActive()) {

            for (Entity entity : mc.level.entitiesForRendering()) {
                if (entity instanceof ThrownEnderpearl pearlEntity) {
                    UUID pearlId = pearlEntity.getUUID();
                    String displayName = null;


                    // 尝试通过owner获取
                    Entity owner = pearlEntity.getOwner();
                    if (owner != null) {
                        if (owner instanceof Player player) {
                            displayName = Via.getGameProfileName(player);
                            // 如果启用了缓存，保存到缓存中
                            if (smartCache.get()) {
                                pearlOwnerCache.put(pearlId, displayName);
                            }
                        } else {
                            displayName = owner.getName().getString();
                        }
                    }

                    // 如果启用了智能缓存， 检查缓存
                    if (smartCache.get() && displayName == null) {
                        displayName = pearlOwnerCache.get(pearlId);
                    }

                    // 设置显示名称
                    if (displayName != null) {
                        pearlEntity.setCustomName(Component.literal(displayName));
                        pearlEntity.setCustomNameVisible(true);
                    } else {
                        // 只有在没有缓存且找不到owner时才显示"野生珍珠"

                        pearlEntity.setCustomName(Component.literal("野生珍珠"));
                        pearlEntity.setCustomNameVisible(true);
                    }
                }
            }

        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!smartCache.get()) return;

        // 定期清理缓存
        cleanupCounter++;
        if (cleanupCounter >= CLEANUP_INTERVAL) {
            cleanupCounter = 0;
            cleanCache();
        }
    }

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = super.toTag();

        if (tag != null && smartCache.get()) {
            // 保存缓存数据到NBT
            CompoundTag cacheTag = new CompoundTag();
            pearlOwnerCache.forEach((pearlId, ownerName) -> {
                cacheTag.putString(pearlId.toString(), ownerName);
            });
            tag.put(CACHE_TAG, cacheTag);
        }

        return tag;
    }

    @Override
    public Module fromTag(CompoundTag tag) {
        super.fromTag(tag);

        if (tag != null && tag.contains(CACHE_TAG)) {
            // 从NBT加载缓存数据
            CompoundTag cacheTag = Via.getNbtCompound(tag, CACHE_TAG);
            pearlOwnerCache.clear();

            cacheTag.keySet().forEach(key -> {
                try {
                    UUID pearlId = UUID.fromString(key);
                    Object string = cacheTag.getString(key);
                    String ownerName;
                    if (string instanceof Optional<?> s) {
                        ownerName = (String) s.get();
                    } else {
                        ownerName = (String) string;
                    }

                    if (!ownerName.isEmpty()) {
                        pearlOwnerCache.put(pearlId, ownerName);
                    }
                } catch (IllegalArgumentException e) {
                    // 忽略无效的UUID
                }
            });
        }
        return this;
    }

    /**
     * 清理缓存 - 移除已经不存在的珍珠的缓存项
     */
    private void cleanCache() {
        if (mc.level == null) return;

        // 获取当前世界的所有珍珠
        Map<UUID, Boolean> existingPearls = new HashMap<>();
        mc.level.entitiesForRendering().forEach(entity -> {
            if (entity instanceof ThrownEnderpearl) {
                existingPearls.put(entity.getUUID(), true);
            }
        });

        // 移除不存在的珍珠的缓存项
        pearlOwnerCache.keySet().removeIf(pearlId -> !existingPearls.containsKey(pearlId));
    }

    @Override
    public void onDeactivate() {
        super.onDeactivate();
        // 模块关闭时清理缓存
//        if (!smartCache.get()) {
//            pearlOwnerCache.clear();
//        }
    }
}
