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
import com.github.mikumiku.addon.util.Via;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.EntityTypeListSetting;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.Target;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.level.GameType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * 「Miku 合法杀戮光环」——把视角当成真人的鼠标来用。
 *
 * <p>和 {@link KillAuraMiku} 的“瞬间拉枪 + 到点开打”不同，这里每一 tick 只把视角
 * 朝目标转一个小角度（可调「视角速度」，度/tick），转到「瞄准容差」以内、
 * 且原版攻击冷却好了，才补一次点击；两次点击之间还隔一段可自定义的「点击间隔」。
 * 目标按「距离 / 血量 / 综合」筛选，只会选最合适的那一个；综合模式还会先看
 * 威胁优先级（凋零、末影龙、苦力怕最高，其次是女巫、骷髅，其余普通怪物最后）。
 * 另外只有手持「武器类型」选中的武器时才会出手（默认剑、斧、锤、三叉戟都算，
 * 也可选「任意物品」取消限制）。</p>
 *
 * <p>因为视角是逐步转过去的，发往服务器的旋转包也是连续渐变的，不会出现
 * “第 0 tick 还在看 A、第 1 tick 已经 180° 甩到 B”这种机器特征。</p>
 */
public class LegitKillAura extends BaseModule {

    private final SettingGroup sgTargeting = settings.createGroup("目标选择");
    private final SettingGroup sgAim = settings.createGroup("视角与点击");

    /**
     * 本模块自己的「类人化」设置组：旋转手抖 + 点击节奏。
     * 模式默认「跟随全局」，和「类人化输入」模块保持一致。
     */
    private final HumanizedSettings humanized = HumanizedSettings.builder(this, "Miku合法杀戮光环")
        .view().rotateNoise().click().build();

    // 目标选择

    public final Setting<Set<EntityType<?>>> entities = sgTargeting.add(new EntityTypeListSetting.Builder()
        .name("实体类型")
        .description("要攻击的实体类型")
        .onlyAttackable()
        .defaultValue(MikuEntities.getAll(
            "player",
            "zombie", "husk", "drowned", "skeleton", "stray", "creeper", "spider", "cave_spider",
            "enderman", "witch", "slime", "magma_cube", "pillager", "vindicator", "ravager",
            "piglin", "piglin_brute", "hoglin", "zoglin", "blaze", "ghast", "phantom",
            "wither_skeleton", "wither", "ender_dragon", "warden", "silverfish", "endermite", "guardian",
            "elder_guardian", "shulker", "vex", "evoker", "illusioner", "giant"
        ))
        .build()
    );

    /**
     * 「武器类型」——只在手持指定武器时才攻击，和「Miku杀戮光环」的同类设置一致
     * （思路参考 Meteor 本体 KillAura 的 attack-when-holding / selected-weapon-types）。
     * 选「全部武器」时剑、斧、锤、三叉戟都算；选「任意物品」则不限制手持物。
     */
    private final Setting<Weapon> weapon = sgTargeting.add(new EnumSetting.Builder<Weapon>()
        .name("武器类型")
        .description("只在手持指定类型的武器时才出手：剑 / 斧 / 锤 / 三叉戟 / 全部武器 / 任意物品")
        .defaultValue(Weapon.All)
        .build()
    );

    private final Setting<Double> range = sgTargeting.add(new DoubleSetting.Builder()
        .name("攻击范围")
        .description("可攻击实体的最大距离（格）")
        .defaultValue(4.0)
        .min(1.0)
        .sliderRange(1.0, 6.0)
        .build()
    );

    private final Setting<SortMode> sortMode = sgTargeting.add(new EnumSetting.Builder<SortMode>()
        .name("筛选方式")
        .description("多个目标都可打时，先挑哪一个：距离最近 / 血量最低 / 综合（综合会先按凋零、末影龙、苦力怕，再按女巫、骷髅排优先级）")
        .defaultValue(SortMode.Distance)
        .build()
    );

    private final Setting<Double> maxTargetHealth = sgTargeting.add(new DoubleSetting.Builder()
        .name("血量上限")
        .description("只攻击血量不高于这个值的生物；0 表示不限")
        .defaultValue(0.0)
        .min(0.0)
        .sliderRange(0.0, 40.0)
        .build()
    );

    private final Setting<Boolean> ignoreNamed = sgTargeting.add(new BoolSetting.Builder()
        .name("忽略命名生物")
        .description("不攻击有自定义名称的生物")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> ignoreFriends = sgTargeting.add(new BoolSetting.Builder()
        .name("忽略好友")
        .description("不攻击 Meteor 好友列表里的玩家")
        .defaultValue(true)
        .build()
    );

    // 视角与点击

    private final Setting<Double> aimSpeed = sgAim.add(new DoubleSetting.Builder()
        .name("视角速度")
        .description("每 tick 最多朝目标转多少度；越小越像真人，越大越快锁上")
        .defaultValue(18.0)
        .min(1.0)
        .sliderRange(1.0, 60.0)
        .build()
    );

    private final Setting<Double> aimTolerance = sgAim.add(new DoubleSetting.Builder()
        .name("瞄准容差")
        .description("视角和目标中心相差多少度以内就算“瞄准了”，可以出手")
        .defaultValue(8.0)
        .min(1.0)
        .sliderRange(1.0, 30.0)
        .build()
    );

    private final Setting<Integer> clickIntervalMs = sgAim.add(new IntSetting.Builder()
        .name("点击间隔")
        .description("两次点击之间至少隔多少毫秒（还会叠加原版攻击冷却）")
        .defaultValue(180)
        .min(30)
        .sliderRange(30, 1000)
        .build()
    );

    private final Setting<Boolean> pauseOnUse = sgAim.add(new BoolSetting.Builder()
        .name("使用物品时暂停")
        .description("吃东西、拉弓、举盾时不转视角也不点击")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> swing = sgAim.add(new BoolSetting.Builder()
        .name("摆手")
        .description("攻击时摆动手臂")
        .defaultValue(true)
        .build()
    );

    private final List<Entity> targets = new ArrayList<>();
    private long nextClickAt = 0L;
    private boolean attacking;

    public LegitKillAura() {
        super(BaseModule.CATEGORY_MIKU_LEGIT, "Miku合法杀戮光环",
            "合法杀戮光环：视角按真人速度逐步移到符合条件（距离/血量）的目标上，再用可自定义的间隔补点击");
    }

    @Override
    public void onDeactivate() {
        targets.clear();
        attacking = false;
        nextClickAt = 0L;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.level == null) return;
        if (!mc.player.isAlive() || PlayerUtils.getGameMode() == GameType.SPECTATOR) return;
        if (pauseOnUse.get() && (mc.gameMode.isDestroying() || mc.player.isUsingItem())) return;

        targets.clear();
        TargetUtils.getList(targets, this::entityCheck, SortPriority.ClosestAngle, 64);

        if (targets.isEmpty()) {
            attacking = false;
            nextClickAt = 0L;
            return;
        }

        // 手持物不符合「武器类型」就不转视角也不出手，避免拿错东西时还甩枪
        if (!weaponInHand()) {
            attacking = false;
            nextClickAt = 0L;
            return;
        }

        targets.sort(comparator());
        Entity primary = targets.getFirst();
        attacking = true;

        Humanized.Profile hp = humanized.profile();

        float targetYaw = (float) Rotations.getYaw(primary);
        float targetPitch = Mth.clamp((float) Rotations.getPitch(primary, Target.Body), -90.0f, 90.0f);

        float currentYaw = mc.player.getYRot();
        float currentPitch = mc.player.getXRot();

        float dYaw = Mth.wrapDegrees(targetYaw - currentYaw);
        float dPitch = targetPitch - currentPitch;

        // 每 tick 的转角上限：类人化开启时让它每次略有不同，而不是完美匀速
        float step = aimSpeed.get().floatValue();
        if (hp.enabled && hp.viewInput) {
            step *= 1.0f + Humanized.jitter(hp, 0.15f);
            step = Math.max(1.0f, step);
        }

        float newYaw = currentYaw + Mth.clamp(dYaw, -step, step);
        float newPitch = Mth.clamp(currentPitch + Mth.clamp(dPitch, -step, step), -90.0f, 90.0f);

        mc.player.setYRot(newYaw);
        mc.player.setXRot(newPitch);
        // 先把这次渐变后的视角发出去，再点击，服务器看到的就是“已经看准了才打”
        mc.player.connection.send(Via.get(newYaw, newPitch, mc.player.onGround()));

        float remainingYaw = Math.abs(Mth.wrapDegrees(targetYaw - newYaw));
        float remainingPitch = Math.abs(targetPitch - newPitch);
        boolean aimed = remainingYaw <= aimTolerance.get() && remainingPitch <= aimTolerance.get();
        if (!aimed) return;

        long now = System.currentTimeMillis();
        if (now < nextClickAt) return;
        // 原版攻击冷却没好就先不打，保证每一下都是满蓄力
        if (mc.player.getAttackStrengthScale(0.5f) < 1.0f) return;

        mc.gameMode.attack(mc.player, primary);
        if (swing.get()) mc.player.swing(InteractionHand.MAIN_HAND);

        nextClickAt = now + Humanized.clickInterval(hp, clickIntervalMs.get()) + Humanized.latencyMs(hp);
    }

    /** 手持物是否符合所选「武器类型」；「任意物品」表示不限制。 */
    private boolean weaponInHand() {
        Item item = mc.player.getMainHandItem().getItem();
        return switch (weapon.get()) {
            case Axe -> item instanceof AxeItem;
            case Sword -> MikuUtil.isSwordItem(item);
            case Mace -> item instanceof MaceItem;
            case Trident -> item instanceof TridentItem;
            case All ->
                item instanceof AxeItem || MikuUtil.isSwordItem(item) || item instanceof MaceItem || item instanceof TridentItem;
            case Any -> true;
        };
    }

    private Comparator<Entity> comparator() {
        return switch (sortMode.get()) {
            case Health -> Comparator.<Entity>comparingDouble(e -> e instanceof LivingEntity living ? living.getHealth() : Double.MAX_VALUE)
                .thenComparingDouble(e -> mc.player.distanceToSqr(e));
            case Combined -> Comparator.<Entity>comparingInt(this::threatTier)
                .thenComparingDouble(this::combinedScore)
                .thenComparingInt(Entity::getId);
            case Distance -> Comparator.<Entity>comparingDouble(e -> mc.player.distanceToSqr(e));
        };
    }

    /**
     * 综合模式下的威胁档位，越小越优先：
     * <ul>
     *     <li>0 档：凋零、末影龙、苦力怕——最危险或会自爆，先处理；</li>
     *     <li>1 档：女巫、骷髅（含流浪者、凋灵骷髅）；</li>
     *     <li>2 档：其余普通怪物与玩家。</li>
     * </ul>
     */
    private int threatTier(Entity entity) {
        EntityType<?> type = entity.getType();
        if (type == EntityType.WITHER || type == EntityType.ENDER_DRAGON || type == EntityType.CREEPER) return 0;
        if (type == EntityType.WITCH || type == EntityType.SKELETON
            || type == EntityType.STRAY || type == EntityType.WITHER_SKELETON) return 1;
        return 2;
    }

    /** 综合分：同一威胁档位内，距离占比 + 血量占比，越小越优先。 */
    private double combinedScore(Entity entity) {
        double maxDist = Math.max(0.5, range.get());
        double distScore = Math.sqrt(mc.player.distanceToSqr(entity)) / maxDist;

        double healthScore = 0.5;
        if (entity instanceof LivingEntity living) {
            double maxHealth = Math.max(1.0, living.getMaxHealth());
            healthScore = living.getHealth() / maxHealth;
        }

        return distScore + healthScore;
    }

    private boolean entityCheck(Entity entity) {
        if (entity == mc.player) return false;
        if (!(entity instanceof LivingEntity living)) return false;
        if (living.isDeadOrDying() || !entity.isAlive()) return false;

        if (!entities.get().contains(entity.getType())) return false;
        if (ignoreNamed.get() && entity.hasCustomName()) return false;
        if (!PlayerUtils.isWithin(entity, range.get())) return false;

        double maxHealth = maxTargetHealth.get();
        if (maxHealth > 0 && living.getHealth() > maxHealth) return false;

        if (entity instanceof Player player) {
            if (player.isCreative()) return false;
            if (!Friends.get().shouldAttack(player)) return false;
            if (ignoreFriends.get() && Friends.get().isFriend(player)) return false;
        }

        return true;
    }

    @Override
    public String getInfoString() {
        if (targets.isEmpty()) return null;
        return EntityUtils.getName(targets.getFirst());
    }

    public boolean isAttacking() {
        return attacking;
    }

    /** 可选的「武器类型」，显示名与「Miku杀戮光环」保持一致。 */
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

    public enum SortMode {
        Distance("距离最近"),
        Health("血量最低"),
        Combined("综合");
        private final String displayName;

        SortMode(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }
}
