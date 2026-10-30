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
import com.github.mikumiku.addon.util.Humanized;
import com.github.mikumiku.addon.util.HumanizedSettings;
import com.github.mikumiku.addon.util.MikuEntities;
import com.github.mikumiku.addon.util.MikuUtil;
import com.github.mikumiku.addon.util.Rotation;
import com.github.mikumiku.addon.util.RotationManager;
import com.github.mikumiku.addon.util.Via;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.pathing.PathManagers;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.combat.CrystalAura;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.Target;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.world.TickRate;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.zombie.ZombifiedPiglin;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.AABB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.GameType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class KillAuraMiku extends BaseModule {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTargeting = settings.createGroup("目标选择");
    private final SettingGroup sgTiming = settings.createGroup("时机控制");

    /**
     * 本模块自己的「类人化」设置组：旋转注入手抖 + 出手前的可变延迟。
     * 模式默认「跟随全局」，和以前一样听「类人化输入」模块的。
     */
    private final HumanizedSettings humanized = HumanizedSettings.builder(this, "Miku杀戮光环")
        .view().rotateNoise().click().build();

    /** 类人化可变延迟的落点（毫秒时间戳）；0 表示当前没有待出手的攻击。 */
    private long attackReadyAt = 0L;

    // 通用设置

    private final Setting<Weapon> weapon = sgGeneral.add(new EnumSetting.Builder<Weapon>()
        .name("武器类型")
        .description("仅在手持指定武器时攻击实体")
        .defaultValue(Weapon.All)
        .build()
    );

    private final Setting<RotationMode> rotation = sgGeneral.add(new EnumSetting.Builder<RotationMode>()
        .name("视角旋转")
        .description("决定何时将视角转向目标")
        .defaultValue(RotationMode.OnHit)
        .build()
    );

    private final Setting<Boolean> autoSwitch = sgGeneral.add(new BoolSetting.Builder()
        .name("自动切换")
        .description("攻击目标时自动切换到选定的武器")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> packetAttack = sgGeneral.add(new BoolSetting.Builder()
        .name("发包攻击")
        .description("更好的模式")
        .defaultValue(false)
        .build()
    );


    private final Setting<Boolean> stopSprint = sgGeneral.add(new BoolSetting.Builder()
        .name("停止疾跑")
        .description("攻击前停止疾跑以保持原版行为")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> stopShield = sgGeneral.add(new BoolSetting.Builder()
        .name("停止格挡")
        .description("攻击前自动处理盾牌格挡")
        .defaultValue(false)
        .build()
    );


    private final Setting<Boolean> onlyOnClick = sgGeneral.add(new BoolSetting.Builder()
        .name("仅在点击时")
        .description("仅在按住鼠标左键时攻击")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> onlyOnLook = sgGeneral.add(new BoolSetting.Builder()
        .name("仅在注视时")
        .description("仅在注视实体时攻击")
        .defaultValue(false)
        .build()
    );

    public final Setting<Boolean> pauseOnCombat = sgGeneral.add(new BoolSetting.Builder()
        .name("暂停Baritone")
        .description("在攻击实体时暂时冻结 Baritone 自动寻路")
        .defaultValue(true)
        .build()
    );

    // 目标选择
    public final Setting<Set<EntityType<?>>> entities = sgTargeting.add(new EntityTypeListSetting.Builder()
        .name("实体类型")
        .description("要攻击的实体类型")
        .onlyAttackable()
        // 26.2 移除了部分 EntityType 静态字段，默认值一律按注册名解析，避免初始化时崩溃。
        .defaultValue(MikuEntities.getAll(
            "player",
            "blaze",             // 烈焰人
            "husk",              // 尸壳
            "wind_charge",       // 风弹（旋风人投射物）
            "cave_spider",       // 洞穴蜘蛛
            "creeper",           // 苦力怕
            "drowned",           // 溺尸
            "elder_guardian",    // 远古守卫者
            "ender_dragon",      // 末影龙
            "enderman",          // 末影人
            "endermite",         // 末影螨
            "evoker",            // 唤魔者
            "ghast",             // 恶魂
            "giant",             // 巨人
            "guardian",          // 守卫者
            "hoglin",            // 疣猪兽
            "illusioner",        // 幻术师
            "magma_cube",        // 岩浆怪
            "phantom",           // 幻翼
            "piglin",            // 猪灵
            "piglin_brute",      // 猪灵蛮兵
            "pillager",          // 掠夺者
            "ravager",           // 劫掠兽
            "shulker",           // 潜影贝
            "silverfish",        // 蠹虫
            "skeleton",          // 骷髅
            "slime",             // 史莱姆
            "spider",            // 蜘蛛
            "stray",             // 流浪者
            "vex",               // 恼鬼
            "vindicator",        // 卫道士
            "warden",            // 监察者
            "witch",             // 女巫
            "wither",            // 凋灵
            "wither_skeleton",   // 凋灵骷髅
            "zombie",            // 僵尸
            "zombified_piglin",  // 僵尸猪灵
            "zoglin",            // 僵尸疣猪兽
            "fireball",          // 火球
            "shulker_bullet"     // 潜影贝导弹
        ))
        .build()
    );

    private final Setting<SortPriority> priority = sgTargeting.add(new EnumSetting.Builder<SortPriority>()
        .name("优先级")
        .description("范围内目标的筛选方式")
        .defaultValue(SortPriority.ClosestAngle)
        .build()
    );

    private final Setting<Integer> maxTargets = sgTargeting.add(new IntSetting.Builder()
        .name("最大目标数")
        .description("同时锁定的实体数量")
        .defaultValue(1)
        .min(1)
        .sliderRange(1, 5)
        .visible(() -> !onlyOnLook.get())
        .build()
    );

    private final Setting<Double> range = sgTargeting.add(new DoubleSetting.Builder()
        .name("攻击范围")
        .description("可攻击实体的最大距离")
        .defaultValue(3.1)
        .min(3)
        .sliderMax(7)
        .build()
    );

    private final Setting<Double> wallsRange = sgTargeting.add(new DoubleSetting.Builder()
        .name("穿墙范围")
        .description("可穿墙攻击实体的最大距离")
        .defaultValue(4.5)
        .min(2)
        .sliderMax(7)
        .build()
    );

    private final Setting<EntityAge> mobAgeFilter = sgTargeting.add(new EnumSetting.Builder<EntityAge>()
        .name("生物年龄过滤")
        .description("决定要攻击的生物年龄（幼年、成年或全部）")
        .defaultValue(EntityAge.Both)
        .build()
    );

    private final Setting<Boolean> ignoreNamed = sgTargeting.add(new BoolSetting.Builder()
        .name("忽略命名生物")
        .description("是否攻击拥有自定义名称的生物")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> ignorePassive = sgTargeting.add(new BoolSetting.Builder()
        .name("忽略被动生物")
        .description("仅在被动型生物主动攻击你时才进行反击.如猪人、小黑、狼")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> ignoreTamed = sgTargeting.add(new BoolSetting.Builder()
        .name("忽略驯服生物")
        .description("避免攻击你驯服的生物")
        .defaultValue(true)
        .build()
    );

    // 时机控制
    private final Setting<Boolean> pauseOnLag = sgTiming.add(new BoolSetting.Builder()
        .name("卡顿时暂停")
        .description("服务器卡顿时暂停攻击")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> pauseOnUse = sgTiming.add(new BoolSetting.Builder()
        .name("使用物品时暂停")
        .description("使用物品时不进行攻击")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> pauseOnCA = sgTiming.add(new BoolSetting.Builder()
        .name("水晶光环时暂停")
        .description("水晶光环放置时不进行攻击")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> tpsSync = sgTiming.add(new BoolSetting.Builder()
        .name("TPS同步")
        .description("尝试将攻击延迟与服务器 TPS 同步")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> customDelay = sgTiming.add(new BoolSetting.Builder()
        .name("自定义延迟")
        .description("使用自定义延迟而非原版冷却时间")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> hitDelay = sgTiming.add(new IntSetting.Builder()
        .name("攻击延迟")
        .description("攻击实体的速度（以刻为单位）")
        .defaultValue(13)
        .min(0)
        .sliderMax(60)
        .visible(customDelay::get)
        .build()
    );

    private final Setting<Integer> switchDelay = sgTiming.add(new IntSetting.Builder()
        .name("切换延迟")
        .description("切换快捷栏后等待多少刻才能攻击实体")
        .defaultValue(0)
        .min(0)
        .sliderMax(10)
        .build()
    );

    private final List<Entity> targets = new ArrayList<>();
    private int switchTimer, hitTimer;
    private boolean wasPathing = false;
    public boolean attacking;

    public KillAuraMiku() {
        super(BaseModule.CATEGORY_MIKU_COMBAT, "Miku杀戮光环", "超级强力的杀敌光环,自动攻击你周围指定的实体,不卡脚");
    }

    @Override
    public void onDeactivate() {
        targets.clear();
        attacking = false;
        attackReadyAt = 0L;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!mc.player.isAlive() || PlayerUtils.getGameMode() == GameType.SPECTATOR) return;
        if (pauseOnUse.get() && (mc.gameMode.isDestroying() || mc.player.isUsingItem())) return;
        if (onlyOnClick.get() && !mc.options.keyAttack.isDown()) return;
        if (TickRate.INSTANCE.getTimeSinceLastTick() >= 1f && pauseOnLag.get()) return;
        if (pauseOnCA.get() && Modules.get().get(CrystalAura.class).isActive() && Modules.get().get(CrystalAura.class).kaTimer > 0)
            return;

        if (onlyOnLook.get()) {
            Entity targeted = mc.crosshairPickEntity;

            if (targeted == null) return;
            if (!entityCheck(targeted)) return;

            targets.clear();
            targets.add(mc.crosshairPickEntity);
        } else {
            targets.clear();
            TargetUtils.getList(targets, this::entityCheck, priority.get(), maxTargets.get());
        }

        if (targets.isEmpty()) {
            attacking = false;
            attackReadyAt = 0L;
            if (wasPathing) {
                PathManagers.get().resume();
                wasPathing = false;
            }
            return;
        }

        Entity primary = targets.getFirst();

        if (autoSwitch.get()) {
            Predicate<ItemStack> predicate = switch (weapon.get()) {
                case Axe -> stack -> stack.getItem() instanceof AxeItem;
                case Sword -> stack -> MikuUtil.isSwordItem(stack.getItem());
                case Mace -> stack -> stack.getItem() instanceof MaceItem;
                case Trident -> stack -> stack.getItem() instanceof TridentItem;
                case All ->
                    stack -> stack.getItem() instanceof AxeItem || MikuUtil.isSwordItem(stack.getItem()) || stack.getItem() instanceof MaceItem || stack.getItem() instanceof TridentItem;
                default -> o -> true;
            };
            FindItemResult weaponResult = InvUtils.findInHotbar(predicate);

            InvUtils.swap(weaponResult.slot(), false);
        }

        if (!itemInHand()) return;

        attacking = true;
        if (rotation.get() == RotationMode.Always) {
            // 「类人化」打开时给持续旋转也加一点手抖，避免每 tick 都精确盯死目标中心
            Humanized.Profile hp = humanized.profile();
            float yaw = (float) Rotations.getYaw(primary);
            float pitch = (float) Rotations.getPitch(primary, Target.Body);
            if (hp.enabled && hp.viewInput && hp.affectPackets) {
                yaw += Humanized.jitter(hp, hp.noiseDegrees);
                pitch = Mth.clamp(pitch + Humanized.jitter(hp, hp.noiseDegrees * 0.7f), -90.0f, 90.0f);
            }
            Rotations.rotate(yaw, pitch);
        }
        if (pauseOnCombat.get() && PathManagers.get().isPathing() && !wasPathing) {
            PathManagers.get().pause();
            wasPathing = true;
        }

        // 出手前的可变延迟：档位关闭时 actionDelayMs 返回 0，行为和原来完全一致
        if (delayCheck()) {
            long now = System.currentTimeMillis();
            if (attackReadyAt == 0L) attackReadyAt = now + Humanized.actionDelayMs(humanized.profile());
            if (now >= attackReadyAt) {
                attackReadyAt = 0L;
                targets.forEach(this::attack);
            }
        } else {
            attackReadyAt = 0L;
        }
    }

    @EventHandler
    private void onSendPacket(PacketEvent.Send event) {
        if (event.packet instanceof ServerboundSetCarriedItemPacket) {
            switchTimer = switchDelay.get();
        }
    }

    private boolean shouldShieldBreak() {


        return false;
    }

    private boolean entityCheck(Entity entity) {
        if (entity.equals(mc.player)) return false;
        if ((entity instanceof LivingEntity livingEntity && livingEntity.isDeadOrDying()) || !entity.isAlive()) return false;

        AABB hitbox = entity.getBoundingBox();
        if (!PlayerUtils.isWithin(
            Mth.clamp(mc.player.getX(), hitbox.minX, hitbox.maxX),
            Mth.clamp(mc.player.getY(), hitbox.minY, hitbox.maxY),
            Mth.clamp(mc.player.getZ(), hitbox.minZ, hitbox.maxZ),
            range.get()
        )) return false;

        if (!entities.get().contains(entity.getType())) return false;
        if (ignoreNamed.get() && entity.hasCustomName()) return false;
        if (!PlayerUtils.canSeeEntity(entity) && !PlayerUtils.isWithin(entity, wallsRange.get())) return false;
        if (ignoreTamed.get()) {
//            if (entity instanceof Tameable tameable
//                && tameable.getOwnerUuid() != null
//                && tameable.getOwnerUuid().equals(mc.player.getUuid())
//            ) {
//
//                return false;
//            }
        }
        if (ignorePassive.get()) {
            if (entity instanceof EnderMan enderman && !enderman.isAngry()) return false;
            if (entity instanceof ZombifiedPiglin piglin && !piglin.isAggressive()) return false;
            if (entity instanceof Wolf wolf && !wolf.isAggressive()) return false;
        }
        if (entity instanceof Player player) {
            if (player.isCreative()) return false;
            if (!Friends.get().shouldAttack(player)) return false;

        }
        if (entity instanceof Animal animal) {
            return switch (mobAgeFilter.get()) {
                case Baby -> animal.isBaby();
                case Adult -> !animal.isBaby();
                case Both -> true;
            };
        }
        return true;
    }

    private boolean delayCheck() {
        if (switchTimer > 0) {
            switchTimer--;
            return false;
        }

        float delay = (customDelay.get()) ? hitDelay.get() : 0.5f;
        if (tpsSync.get()) delay /= (TickRate.INSTANCE.getTickRate() / 20);

        if (customDelay.get()) {
            if (hitTimer < delay) {
                hitTimer++;
                return false;
            } else {
                return true;
            }
        } else {
            return mc.player.getAttackStrengthScale(delay) >= 1;
        }
    }

    private void attack(Entity target) {
//         Rotations.getYaw(target), Rotations.getPitch(target, Target.Body) ;


        if (stopSprint.get()) {
            if (mc.player.isShiftKeyDown()) {
//                PlayerInputC2SPacket
                Via.sendReleaseShift();
            }
            if (mc.player.isSprinting()) {
                mc.getConnection().send(new ServerboundPlayerCommandPacket(mc.player, ServerboundPlayerCommandPacket.Action.STOP_SPRINTING));
            }
        }

        Vec3 feetPos = Via.getEntityPos(target);

        Vec3 torsoPos = feetPos.add(0.0, target.getBbHeight() / 2.0f, 0.0);
        Vec3 eyesPos = target.getEyePosition();
        Vec3 hitVec = Stream.of(feetPos, torsoPos, eyesPos)
            .min(Comparator.comparing(pos -> mc.player.getEyePosition().distanceToSqr(pos)))
            .orElse(eyesPos);

        Rotation rotation = new Rotation(hitVec).setPriority(10);
        Rotation rotation1 = new Rotation((float) Rotations.getYaw(target), (float) Rotations.getPitch(target, Target.Body));
        RotationManager.getInstance().register(rotation, humanized.profile());

        mc.gameMode.attack(mc.player, target);
        mc.player.swing(InteractionHand.MAIN_HAND);

        if (packetAttack.get()) {

            mc.gameMode.attack(mc.player, target);
//            mc.getNetworkHandler().send(new HandSwingC2SPacket(InteractionHand.MAIN_HAND));
            mc.player.swing(InteractionHand.MAIN_HAND);

        } else {

            mc.gameMode.attack(mc.player, target);
            mc.player.swing(InteractionHand.MAIN_HAND);

        }

        hitTimer = 0;

        if (this.rotation.get() == RotationMode.OnHit) {
            RotationManager.getInstance().sync();
        }
    }

    private boolean itemInHand() {
        Item item = mc.player.getMainHandItem().getItem();
        if (shouldShieldBreak()) return item instanceof AxeItem;

        return switch (weapon.get()) {
            case Axe -> item instanceof AxeItem;
            case Sword -> MikuUtil.isSwordItem(item);
            case Mace -> item instanceof MaceItem;
            case Trident -> item instanceof TridentItem;
            case All ->
                item instanceof AxeItem || MikuUtil.isSwordItem(item) || item instanceof MaceItem || item instanceof TridentItem;
            default -> true;
        };
    }

    public Entity getTarget() {
        if (!targets.isEmpty()) return targets.getFirst();
        return null;
    }

    @Override
    public String getInfoString() {
        if (!targets.isEmpty()) return EntityUtils.getName(getTarget());
        return null;
    }

    public enum Weapon {
        Sword("剑"),
        Axe("斧"),
        Mace("锤"),
        Trident("三叉戟"),
        All("全部武器"),
        Any("任意物品");

        private final String displayName;

        Weapon(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    public enum RotationMode {
        Always("始终旋转"),
        OnHit("攻击时旋转"),
        None("不旋转");

        private final String displayName;

        RotationMode(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }


    public enum EntityAge {
        Baby("幼年"),
        Adult("成年"),
        Both("全部");

        private final String displayName;

        EntityAge(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

}
