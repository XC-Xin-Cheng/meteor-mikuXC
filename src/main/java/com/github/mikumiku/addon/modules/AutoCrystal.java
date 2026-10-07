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
import com.github.mikumiku.addon.util.*;
import com.github.mikumiku.addon.util.timer.SyncedTickTimer;
import com.github.mikumiku.addon.util.timer.Timers;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.utils.entity.DamageUtils;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.NametagUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.level.block.Blocks;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.PotionItem;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class AutoCrystal extends BaseModule {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgPlace = settings.createGroup("放置");
    private final SettingGroup sgBreak = settings.createGroup("破坏");
    private final SettingGroup sgDamage = settings.createGroup("伤害");
    private final SettingGroup base = settings.createGroup("底座");
    private final SettingGroup sgRender = settings.createGroup("渲染");

    /**
     * 本模块自己的「类人化」设置组：破坏/放置水晶前的旋转注入手抖。
     * 模式默认「跟随全局」，和以前一样听「类人化输入」模块的。
     */
    private final HumanizedSettings humanized = HumanizedSettings.builder(this, "自动水晶").view().rotateNoise().build();


    private final Setting<Set<EntityType<?>>> entities = sgGeneral.add(new EntityTypeListSetting.Builder()
        .name("目标")
        .description("要攻击的目标")
        .onlyAttackable()
        // 26.2 移除了部分 EntityType 静态字段，默认值按注册名解析，避免初始化时崩溃。
        .defaultValue(MikuEntities.getAll("player", "warden", "wither"))
        .build()
    );

    // General
    private final Setting<Double> targetRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("目标范围")
        .description("搜索目标的范围")
        .defaultValue(10)
        .min(0)
        .sliderMax(20)
        .build()
    );
    private final Setting<Boolean> ignoreNakeds = sgGeneral.add(new BoolSetting.Builder()
        .name("忽略裸吊")
        .description("忽略没有装备的玩家。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> whileMining = sgGeneral.add(new BoolSetting.Builder()
        .name("挖掘时攻击")
        .description("允许在挖掘方块时进行攻击")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> pauseOnEat = sgGeneral.add(new BoolSetting.Builder()
        .name("进食暂停")
        .description("进食时暂停")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> pauseOnDrink = sgGeneral.add(new BoolSetting.Builder()
        .name("喝药暂停")
        .description("喝药时暂停")
        .defaultValue(true)
        .build()
    );

    private final Setting<SortPriority> priority = sgGeneral.add(new EnumSetting.Builder<SortPriority>()
        .name("目标优先级")
        .description("如何选择目标")
        .defaultValue(SortPriority.LowestHealth)
        .build()
    );
    // 添加智能目标选择设置
    private final Setting<Boolean> smartTargeting = sgGeneral.add(new BoolSetting.Builder()
        .name("智能目标选择")
        .description("根据威胁程度智能选择目标")
        .defaultValue(true)
        .build()
    );

    // Place
    private final Setting<Boolean> place = sgPlace.add(new BoolSetting.Builder()
        .name("放置")
        .description("是否放置水晶")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> placeSpeed = sgPlace.add(new DoubleSetting.Builder()
        .name("放置速度")
        .description("每秒放置水晶的次数")
        .defaultValue(18)
        .min(0)
        .sliderMax(20)
        .visible(place::get)
        .build()
    );

    private final Setting<Double> placeRange = sgPlace.add(new DoubleSetting.Builder()
        .name("放置范围")
        .description("放置水晶的范围")
        .defaultValue(4.5)
        .min(0)
        .sliderMax(6)
        .visible(place::get)
        .build()
    );

    private final Setting<Double> placeWallRange = sgPlace.add(new DoubleSetting.Builder()
        .name("穿墙放置范围")
        .description("穿墙放置水晶的范围")
        .defaultValue(4.5)
        .min(0)
        .sliderMax(6)
        .visible(place::get)
        .build()
    );

    // 添加自适应速度设置
    private final Setting<Boolean> adaptiveSpeed = sgPlace.add(new BoolSetting.Builder()
        .name("自适应速度")
        .description("根据服务器延迟自动调整放置速度")
        .defaultValue(true)
        .visible(place::get)
        .build()
    );


    private final Setting<Boolean> strictDirection = sgPlace.add(new BoolSetting.Builder()
        .name("严格方向")
        .description("只放置可见方向的水晶")
        .defaultValue(false)
        .visible(place::get)
        .build()
    );

    private final Setting<SupportMode> support = sgPlace.add(new EnumSetting.Builder<SupportMode>()
        .name("放置底座")
        .description("当没有合适位置时，放置底座方块。")
        .defaultValue(SupportMode.Disabled)
        .build()
    );
    private final Setting<Integer> supportDelay = sgPlace.add(new IntSetting.Builder()
        .name("底座延迟")
        .description("放置底座方块后的延迟刻数。")
        .defaultValue(1)
        .min(0)
        .visible(() -> support.get() != SupportMode.Disabled)
        .build()
    );

    // Break
    private final Setting<Double> breakSpeed = sgBreak.add(new DoubleSetting.Builder()
        .name("破坏速度")
        .description("每秒破坏水晶的次数")
        .defaultValue(13)
        .min(0)
        .sliderMax(20)
        .build()
    );

    private final Setting<Double> breakRange = sgBreak.add(new DoubleSetting.Builder()
        .name("破坏范围")
        .description("破坏水晶的范围")
        .defaultValue(4.5)
        .min(0)
        .sliderMax(6)
        .build()
    );

    private final Setting<Double> breakWallRange = sgBreak.add(new DoubleSetting.Builder()
        .name("穿墙破坏范围")
        .description("穿墙破坏水晶的范围")
        .defaultValue(4)
        .min(0)
        .sliderMax(6)
        .build()
    );

    private final Setting<Boolean> antiWeakness = sgBreak.add(new BoolSetting.Builder()
        .name("反虚弱")
        .description("当有虚弱效果时自动切换到可以破坏水晶的工具。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> removeInhibit = sgBreak.add(new BoolSetting.Builder()
        .name("移除抑制")
        .description("防止多次攻击同一水晶")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> removeInhibitDelay = sgBreak.add(new IntSetting.Builder()
        .name("抑制延迟")
        .description("抑制延迟(tick)")
        .defaultValue(5)
        .min(0)
        .sliderMax(20)
        .visible(removeInhibit::get)
        .build()
    );

    // Damage
    private final Setting<Double> minDamage = sgDamage.add(new DoubleSetting.Builder()
        .name("最小伤害")
        .description("攻击目标所需的最低伤害")
        .defaultValue(4)
        .min(0)
        .sliderMax(20)
        .build()
    );

    private final Setting<Double> maxSelfDamage = sgDamage.add(new DoubleSetting.Builder()
        .name("最大自伤")
        .description("允许的最大自身伤害")
        .defaultValue(19)
        .min(0)
        .sliderMax(20)
        .build()
    );

    private final Setting<Boolean> antiSuicide = sgDamage.add(new BoolSetting.Builder()
        .name("防自杀")
        .description("防止自杀")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> armorBreaker = sgDamage.add(new BoolSetting.Builder()
        .name("破甲")
        .description("优先攻击低耐久护甲的敌人")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> armorScale = sgDamage.add(new DoubleSetting.Builder()
        .name("护甲耐久阈值")
        .description("护甲耐久百分比")
        .defaultValue(10)
        .min(0)
        .sliderMax(100)
        .visible(armorBreaker::get)
        .build()
    );

    // Render
    private final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder()
        .name("渲染")
        .description("渲染放置位置")
        .defaultValue(true)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("形状模式")
        .description("渲染的形状")
        .defaultValue(ShapeMode.Both)
        .visible(render::get)
        .build()
    );

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("侧面颜色")
        .description("渲染的侧面颜色")
        .defaultValue(new SettingColor(255, 0, 255, 40))
        .visible(render::get)
        .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("线条颜色")
        .description("渲染的线条颜色")
        .defaultValue(new SettingColor(255, 0, 255, 255))
        .visible(render::get)
        .build()
    );

    private final Setting<Boolean> renderDamage = sgRender.add(new BoolSetting.Builder()
        .name("渲染伤害")
        .description("渲染伤害值")
        .defaultValue(true)
        .visible(render::get)
        .build()
    );

    private final Setting<Boolean> debug = sgRender.add(new BoolSetting.Builder()
        .name("debug")
        .description("debug")
        .defaultValue(false)
        .visible(render::get)
        .build()
    );

    // Variables
    private BlockPos renderPos;
    private double renderedDamage;
    private final SyncedTickTimer placeTimer = Timers.tickTimer();
    private final SyncedTickTimer breakTimer = Timers.tickTimer();
    private final Map<Integer, Long> attackedCrystals = new ConcurrentHashMap<>();
    private final Map<BlockPos, Long> placedCrystals = new ConcurrentHashMap<>();
    private LivingEntity target;
    private final List<LivingEntity> targets = new ArrayList<>();

    public AutoCrystal() {
        super(BaseModule.CATEGORY_MIKU_COMBAT, "Miku水晶", "自动放置和破坏末影水晶");
    }

    @Override
    public void onActivate() {
        renderPos = null;
        renderedDamage = 0;
        attackedCrystals.clear();
        placedCrystals.clear();
        placeTimer.reset();
        breakTimer.reset();
        target = null;
        targets.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {

//        TargetUtils.getPlayerTarget(targetRange.get(), priority.get());

        findTargets();

        if (targets.isEmpty()) {
            return;
        }
        // Find target
        target = getNearestTarget();

        // Check pause conditions
        if (shouldPause()) return;

        // Break crystals
        if (breakTimer.tick(20 - breakSpeed.get().intValue())) {
            EndCrystal crystal = findBestCrystal();
            if (crystal != null) {
                breakCrystal(crystal);
                breakTimer.reset();
            }
        }

        // Place crystals
        if (place.get() && placeTimer.tick(20 - placeSpeed.get().intValue())) {
            BlockPos pos = findBestPlacePos();
            if (pos != null) {
                placeCrystal(pos);
            }
            placeTimer.reset();
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!render.get() || renderPos == null) return;

        event.renderer.box(renderPos, sideColor.get(), lineColor.get(), shapeMode.get(), 0);

        if (renderDamage.get() && renderedDamage > 0) {
            String text = String.format("%.1f", this.renderedDamage);
//            event.renderer.text(text, , lineColor.get(), true);

            PoseStack matrices = event.matrices;
            // 示例位置：BlockPos(100, 64, 100) 的正中
            Vec3 pos = new Vec3(renderPos.getX() + 0.5, renderPos.getY() + 0.5, renderPos.getZ() + 0.5); // 注意用 center 坐标
//            renderTextInWorld(matrices, pos, "Hello Meteor 3D", 0.02f, lineColor.get().getVec3f(), true, true);
        }
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (renderDamage.get() && renderedDamage > 0) {

            Vector3d vec3 = new Vector3d();
            vec3.set(renderPos.getX() + 0.5, renderPos.getY() + 0.5, renderPos.getZ() + 0.5);
            if (NametagUtils.to2D(vec3, 1.25)) {
                NametagUtils.begin(vec3, event.graphics);
                MikuCompat.beginText(TextRenderer.get(), event.graphics, 1.0, true);

                String text = String.format("%.1f", renderedDamage);
                double w = TextRenderer.get().getWidth(text) / 2;
                TextRenderer.get().render(text, -w, 0, lineColor.get(), true);

                TextRenderer.get().end();
                NametagUtils.end(event.graphics);
            }
        }

    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (event.packet instanceof ClientboundAddEntityPacket packet) {
            if (packet.getType() == MikuEntities.endCrystal()) {
                BlockPos pos = BlockPos.containing(packet.getX(), packet.getY() - 1, packet.getZ());
                placedCrystals.remove(pos);
            }
        }

        if (event.packet instanceof ClientboundRemoveEntitiesPacket packet) {
            for (int id : packet.getEntityIds()) {
                attackedCrystals.remove(id);
            }
        }

        if (event.packet instanceof ClientboundSoundPacket packet) {
            if (packet.getSound().value() == SoundEvents.GENERIC_EXPLODE.value()
                && packet.getSource() == SoundSource.BLOCKS) {
                Vec3 pos = new Vec3(packet.getX(), packet.getY(), packet.getZ());
                mc.level.entitiesForRendering().forEach(e -> {
                    if (e instanceof EndCrystal && Via.getEntityPos(e).distanceTo(pos) < 12) {
                        attackedCrystals.remove(e.getId());
                    }
                });
            }
        }
    }

    private boolean shouldPause() {
        if (pauseOnEat.get() && mc.player.isUsingItem() && mc.player.getActiveItem().getItem() != Items.END_CRYSTAL)
            return true;
        if (pauseOnDrink.get() && mc.player.isUsingItem() && mc.player.getActiveItem().getItem() instanceof PotionItem)
            return true;
        if (!whileMining.get() && mc.gameMode.isDestroying()) return true;
        return false;
    }

    private void findTargets() {
        targets.clear();

        // Living Entities
        for (Entity entity : mc.level.entitiesForRendering()) {
            // Ignore non-living
            if (!(entity instanceof LivingEntity livingEntity)) {
                continue;
            }

            // Player
            if (livingEntity instanceof Player player) {
                if (player.getAbilities().instabuild || livingEntity == mc.player) continue;
                if (!player.isAlive() || !Friends.get().shouldAttack(player)) continue;

                if (ignoreNakeds.get()) {
                    ItemStack boots = player.getItemBySlot(EquipmentSlot.FEET);
                    ItemStack leggings = player.getItemBySlot(EquipmentSlot.LEGS);
                    ItemStack chestplate = player.getItemBySlot(EquipmentSlot.CHEST);
                    ItemStack helmet = player.getItemBySlot(EquipmentSlot.HEAD);

                    if (player.getOffhandItem().isEmpty()
                        && player.getMainHandItem().isEmpty()
                        && boots.isEmpty()
                        && leggings.isEmpty()
                        && chestplate.isEmpty()
                        && helmet.isEmpty()
                    ) continue;
                }
            }

            // Animals, water animals, monsters, bats, misc
            if (!(entities.get().contains(livingEntity.getType()))) continue;

            // Close enough to damage
            if (livingEntity.distanceToSqr(mc.player) > targetRange.get() * targetRange.get()) continue;

            targets.add(livingEntity);
        }
    }

    private LivingEntity getNearestTarget() {
        LivingEntity nearestTarget = null;
        double nearestDistance = Double.MAX_VALUE;

        for (LivingEntity target : targets) {
            double distance = PlayerUtils.squaredDistanceTo(target);

            if (distance < nearestDistance) {
                nearestTarget = target;
                nearestDistance = distance;
            }
        }

        return nearestTarget;
    }

    private EndCrystal findBestCrystal() {
        EndCrystal best = null;
        double bestDamage = 0;

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof EndCrystal crystal)) continue;
            if (!crystal.isAlive()) continue;

            // Range check
            if (mc.player.getEyePosition().distanceTo(Via.getEntityPos(crystal)) > breakRange.get()) continue;

            // Wall check
            if (!canSee(Via.getEntityPos(crystal)) && mc.player.getEyePosition().distanceTo(Via.getEntityPos(crystal)) > breakWallRange.get())
                continue;

            // Already attacked check
            if (removeInhibit.get() && attackedCrystals.containsKey(crystal.getId())) {
                long time = attackedCrystals.get(crystal.getId());
                if (System.currentTimeMillis() - time < removeInhibitDelay.get() * 50L) continue;
            }

            // Calculate damage
            double targetDamage = DamageUtils.crystalDamage(target, Via.getEntityPos(crystal));
            double selfDamage = DamageUtils.crystalDamage(mc.player, Via.getEntityPos(crystal));

            if (targetDamage < minDamage.get()) continue;
            if (selfDamage > maxSelfDamage.get()) continue;
            if (antiSuicide.get() && selfDamage >= EntityUtils.getTotalHealth(mc.player)) continue;

            if (targetDamage > bestDamage) {
                best = crystal;
                bestDamage = targetDamage;
            }
        }

        return best;
    }

    private BlockPos findBestPlacePos() {
        BlockPos best = null;
        double bestDamage = 0;

        // 性能：眼位在整轮循环内不变，提到循环外；同一个 up 位置也复用一次 Vec3（原来每候选最多构造 3 次）。
        Vec3 eyePos = mc.player.getEyePosition();
        for (BlockPos pos : WorldUtils.getSphere(placeRange.get())) {
            if (!canPlaceCrystal(pos)) continue;

            Vec3 upPos = Vec3.upFromBottomCenterOf(pos, 0);
            double eyeDist = eyePos.distanceTo(upPos);

            // Range check
            if (eyeDist > placeRange.get()) continue;

            // Wall check
            if (!canSee(upPos) && eyeDist > placeWallRange.get())
                continue;

            // Calculate damage
            Vec3 crystalPos = Vec3.atLowerCornerOf(pos).add(0.5, 1, 0.5);
            double targetDamage = DamageUtils.crystalDamage(target, crystalPos);
            double selfDamage = DamageUtils.crystalDamage(mc.player, crystalPos);

            if (targetDamage < minDamage.get()) continue;
            if (selfDamage > maxSelfDamage.get()) continue;
            if (antiSuicide.get() && selfDamage >= EntityUtils.getTotalHealth(mc.player)) continue;

            if (armorBreaker.get()) {
                targetDamage += getArmorDamageBonus(target);
            }

            if (targetDamage > bestDamage) {
                best = pos;
                bestDamage = targetDamage;
                renderPos = pos;
                this.renderedDamage = targetDamage;
            }
        }
        return best;
    }

    private double getArmorDamageBonus(LivingEntity player) {
        // 性能：原来每个候选位置都要 new 一个长度 4 的 ItemStack 数组，改为逐个槽位直接计算。
        double scale = armorScale.get();
        double bonus = 0;
        bonus += armorDamageBonus(player.getItemBySlot(EquipmentSlot.HEAD), scale);
        bonus += armorDamageBonus(player.getItemBySlot(EquipmentSlot.CHEST), scale);
        bonus += armorDamageBonus(player.getItemBySlot(EquipmentSlot.LEGS), scale);
        bonus += armorDamageBonus(player.getItemBySlot(EquipmentSlot.FEET), scale);
        return bonus;
    }

    private static double armorDamageBonus(ItemStack armor, double scale) {
        if (armor.isEmpty()) return 0;
        double durability = (armor.getMaxDamage() - armor.getDamageValue()) / (double) armor.getMaxDamage() * 100;
        return durability < scale ? 2 : 0;
    }

    private void breakCrystal(EndCrystal crystal) {
        // Anti-weakness
        int slot = findWeaponSlot();
        if (antiWeakness.get() && mc.player.hasEffect(MobEffects.WEAKNESS)) {
            if (slot != -1) {
                BagUtil.doSwap(slot);
            }
        }
        Vec3 hitPos = Vec3.upFromBottomCenterOf(crystal.blockPosition(), crystal.blockPosition().getY());

        Rotation rotation = new Rotation(((float) Rotations.getYaw(Via.getEntityPos(crystal))),
            (float) Rotations.getPitch(Via.getEntityPos(crystal)));
        boolean registered = RotationManager.getInstance().register(rotation, humanized.profile());

        if (registered) {
            attackCrystal(crystal);
        }

        // Restore
        if (antiWeakness.get() && mc.player.hasEffect(MobEffects.WEAKNESS)) {
            if (slot != -1) {
                BagUtil.doSwap(slot);
            }
        }
    }

    private void attackCrystal(EndCrystal crystal) {
        mc.gameMode.attack(mc.player, crystal);
        mc.player.swing(InteractionHand.MAIN_HAND);
        attackedCrystals.put(crystal.getId(), System.currentTimeMillis());
    }

    private void placeCrystal(BlockPos pos) {
        // Switch to crystal
        int slot = BagUtil.findItemInventorySlot(Items.END_CRYSTAL);
        if (slot == -1) return;

        BagUtil.doSwap(slot);

        // Rotate
        Vec3 vec = Vec3.upFromBottomCenterOf(pos, 0);

        boolean registered = RotationManager.getInstance().register(new Rotation(vec), humanized.profile());
        if (registered) {
            placeBlock(pos);
        }

        // Restore
        BagUtil.doSwap(slot);

    }

    private void placeBlock(BlockPos pos) {
        Direction direction = getPlaceDirection(pos);
        BlockHitResult result = new BlockHitResult(Vec3.upFromBottomCenterOf(pos, 0), direction, pos, false);

        sendSequencedPacket(id -> new ServerboundUseItemOnPacket(InteractionHand.MAIN_HAND, result, id));
        mc.player.swing(InteractionHand.MAIN_HAND);
        placedCrystals.put(pos, System.currentTimeMillis());
    }

    private Direction getPlaceDirection(BlockPos blockPos) {
        int x = blockPos.getX();
        int y = blockPos.getY();
        int z = blockPos.getZ();
        if (strictDirection.get()) {
            if (mc.player.getY() >= blockPos.getY()) {
                return Direction.UP;
            }
            BlockHitResult result = mc.level.clip(new ClipContext(
                mc.player.getEyePosition(), new Vec3(x + 0.5, y + 0.5, z + 0.5),
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE, mc.player));
            if (result != null && result.getType() == HitResult.Type.BLOCK) {
                return result.getDirection();
            }
        } else {
            if (mc.level.isInWorldBounds(blockPos)) {
                return Direction.DOWN;
            }
            BlockHitResult result = mc.level.clip(new ClipContext(
                mc.player.getEyePosition(), new Vec3(x + 0.5, y + 0.5, z + 0.5),
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE, mc.player));
            if (result != null && result.getType() == HitResult.Type.BLOCK) {
                return result.getDirection();
            }
        }
        return Direction.UP;
    }


    private boolean canPlaceCrystal(BlockPos pos) {
        // Check block
        if (!mc.level.getBlockState(pos).is(Blocks.OBSIDIAN)
            && !mc.level.getBlockState(pos).is(Blocks.BEDROCK)) {
            return false;
        }

        // Check space
        BlockPos pos1 = pos.above();

        if (!mc.level.getBlockState(pos1).isAir() && !mc.level.getBlockState(pos1).is(Blocks.FIRE)) {
            return false;
        }

        // Check entities
        double d = pos1.getX();
        double e = pos1.getY();
        double f = pos1.getZ();
        AABB bb = new AABB(0.0, 0.0, 0.0, 1.0, 2.0, 1.0);

        AABB box = new AABB(d, e, f, d + bb.maxX, e + bb.maxY, f + bb.maxZ);

        return noEntitiesBlockingCrystal(box);
    }


    private boolean noEntitiesBlockingCrystal(AABB box) {
        // 性能：原实现把实体列表复制进 CopyOnWriteArrayList，再在循环里 remove，
        // 每次 remove 都会整体复制数组，退化成 O(n^2)，而且这是每个放置候选位置都调用一次。
        // 该方法的最终判定只取决于“是否存在存活且与 box 相交的末影水晶”，改为直接一遍扫描、命中即返回，语义等价。
        for (Entity entity : mc.level.getEntities(null, box)) {
            if (entity == null
                || !entity.isAlive()
                || entity instanceof ArmorStand
                || entity instanceof ExperienceOrb
                || entity instanceof ItemEntity && entity.tickCount <= 10) {
                continue;
            }
            if (entity instanceof EndCrystal entity1
                && entity1.getBoundingBox().intersects(box)) {

                return false;
            }
        }
        return true;
    }

    private boolean canSee(Vec3 pos) {
        return mc.level.clip(new ClipContext(
            mc.player.getEyePosition(),
            pos,
            ClipContext.Block.COLLIDER,
            ClipContext.Fluid.NONE,
            mc.player
        )).getType() == HitResult.Type.MISS;
    }

    private int findWeaponSlot() {

        return BagUtil.findItemInventorySlot(stack -> MikuUtil.isSwordItem(stack.getItem())
            || stack.getItem() instanceof MaceItem
            || stack.getItem() instanceof AxeItem);
    }

    private void debug(String message) {
        if (debug.get()) {
            info(message);
        }
    }

    // Enums
    public enum AutoSwitch {
        Normal,
        Silent,
        None
    }

    public enum Support1_12 {
        Full,
        Semi,
        None
    }

    public static enum SupportMode {
        Disabled,
        Accurate,
        Fast;

        private SupportMode() {
        }
    }
}
