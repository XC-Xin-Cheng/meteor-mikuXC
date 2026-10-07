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

import baritone.api.BaritoneAPI;
import baritone.api.pathing.goals.GoalBlock;
import com.github.mikumiku.addon.BaseModule;
import com.github.mikumiku.addon.util.BaritoneUtil;
import com.github.mikumiku.addon.util.BagUtil;
import com.github.mikumiku.addon.util.MikuEntities;
import com.github.mikumiku.addon.util.Via;
import com.github.mikumiku.addon.util.seeds.Seed;
import com.github.mikumiku.addon.util.seeds.SeedVersion;
import com.github.mikumiku.addon.util.seeds.Seeds;
import com.seedfinding.mcbiome.source.BiomeSource;
import com.seedfinding.mccore.rand.ChunkRand;
import com.seedfinding.mccore.state.Dimension;
import com.seedfinding.mccore.util.pos.CPos;
import com.seedfinding.mccore.version.MCVersion;
import com.seedfinding.mcfeature.structure.EndCity;
import com.seedfinding.mcfeature.structure.generator.structure.EndCityGenerator;
import com.seedfinding.mcterrain.TerrainGenerator;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.movement.elytrafly.ElytraFlightModes;
import meteordevelopment.meteorclient.systems.modules.movement.elytrafly.ElytraFly;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import static meteordevelopment.meteorclient.utils.world.Dimension.End;

public class AutoElytraHarvester extends BaseModule {
    // --- 设置分组 ---
    private final SettingGroup sgGeneral = settings.getDefaultGroup();


    // 种子设置
    private final Setting<String> seedInput = sgGeneral.add(new StringSetting.Builder()
        .name("种子")
        .description("输入服务器种子(默认3C3U种子)")
        .defaultValue("-7346913998703726680")
        .build()
    );

    private final Setting<String> mcVersionInput = sgGeneral.add(new StringSetting.Builder()
        .name("MC版本")
        .description("手动输入 Minecraft 版本，支持 1.12.2 ~ 26.2（例如 26.2、1.21.4、1.20.1、1.12.2）。低于 1.12.2、高于 26.2 或认不出来的写法按空白处理：不会用手动种子启动末地船搜索。与「结构搜索」不同，本模块不走 cubiomes：cubiomes 只给末地城的位置，判断末地城里到底有没有末地船（鞘翅）必须把结构真正生成一遍，这一步用的是种子库的末地城生成器。所以搜索只用该版本自己的种子库数据，种子库没收录的版本（如 26.x、1.21.1 ~ 1.21.11）会明确跳过，不会拿别的版本的数据顶替。")
        .defaultValue(SeedVersion.MAX_INPUT_VERSION)
        .build()
    );

    private final Setting<Integer> lowY = sgGeneral.add(new IntSetting.Builder()
        .name("低Y下线")
        .description("当Y坐标低于此值时自动下线保护")
        .defaultValue(10)
        .min(0)
        .max(255)
        .build()
    );

    private final Setting<Integer> cruiseY = sgGeneral.add(new IntSetting.Builder()
        .name("巡航Y高度")
        .description("起飞阶段爬升到此高度后转入巡航飞行。")
        .defaultValue(320)
        .sliderRange(160, 1000)
        .build()
    );

    private final Setting<Integer> fireworkInterval = sgGeneral.add(new IntSetting.Builder()
        .name("烟花间隔")
        .description("起飞爬升阶段自动释放烟花的间隔（tick，20=1秒）。")
        .defaultValue(25)
        .min(5)
        .sliderMax(100)
        .build()
    );

    private final Setting<Boolean> logoutOnFull = sgGeneral.add(new BoolSetting.Builder()
        .name("背包满下线")
        .description("当背包彻底装满时自动下线")
        .defaultValue(true)
        .build()
    );
    private final Setting<Boolean> autoResupplyFirework = sgGeneral.add(new BoolSetting.Builder()
        .name("自动补充烟花")
        .description("自动从背包潜影盒补充烟花")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> minFireworks = sgGeneral.add(new IntSetting.Builder()
        .name("最少烟花")
        .description("背包烟花数量低于此值时触发一键补给（仅在地面且未滑翔时触发）。")
        .defaultValue(5)
        .min(0)
        .max(64)
        .sliderMax(64)
        .visible(autoResupplyFirework::get)
        .build()
    );

    private final Setting<Boolean> autokill = sgGeneral.add(new BoolSetting.Builder()
        .name("配置Miku杀戮")
        .description("配置Miku杀戮")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> autodrop = sgGeneral.add(new BoolSetting.Builder()
        .name("配置自动丢垃圾")
        .description("设置自动丢潜影壳、紫伯块 等")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> autoElytraUnbreak = sgGeneral.add(new BoolSetting.Builder()
        .name("配置无消耗鞘翅耐久")
        .description("设置")
        .defaultValue(true)
        .build()
    );
    private final Setting<Boolean> autoxp = sgGeneral.add(new BoolSetting.Builder()
        .name("配置自动丢XP")
        .description("稿子耐久低于50自动丢XP")
        .defaultValue(true)
        .build()
    );
    private final Setting<Boolean> grabDiamond = sgGeneral.add(new BoolSetting.Builder()
        .name("顺手拿点钻石")
        .description("设置")
        .defaultValue(false)
        .build()
    );

    // --- 状态机定义 ---
    private enum State {
        GET_TARGET, TAKE_OFF, FLYING, LANDING, DIG_DOWN, CHECK_FRAME, AIM_LOOT, LOOT,
        PLACE_SHULKER, OPEN_SHULKER, FILL_SHULKER, BREAK_SHULKER,
        PLACE_ECHEST, OPEN_ECHEST, FILL_ECHEST, BREAK_ECHEST, ESCAPE
    }

    private State currentState = State.GET_TARGET;
    private BlockPos targetShipPos = null;
    private BlockPos targetInteractPos = null;
    private BlockPos landingPos = null; // 降落点（找到展示框后取其 x,z），LANDING 阶段对准此处
    private BlockPos targetFramePos = null; // 缓存的展示框坐标，DIG_DOWN 阶段交给 Baritone 寻路
    private boolean baritoneGoalSet = false; // DIG_DOWN 是否已下发 Baritone 目标（避免每 tick 重置）
    private BlockPos currentShipCandidate = null; // 当前目标对应的原始候选点，用于结束时标记已访问
    private int tickDelay = 0;
    private int jumpTimer = 0;        // 起飞脉冲跳跃计时
    private int fireworkCooldown = 0; // 起飞阶段烟花释放冷却
    private int landingTimer = 0;     // LANDING 状态等待落地计时（含超时兜底）
    private boolean isRefilling = false; // 一键补给进行中标志
    // 启动时配置的模块追踪（onDeactivate 仅关闭我们开启的，不误关用户手动启动的）
    private boolean toggledKillAura = false;
    private boolean toggledKillAuraItem = false;
    private boolean toggledAutoTrash = false;
    private boolean toggledUnbreak = false;
    private int blockShulkerSlot = -1; // PLACE 用：背包里找到的潜影盒槽位

    // --- 末地船搜索相关状态 ---
    // 缓存的"最近未访问末地船"坐标，由异步螺旋搜索写入后供主线程只读
    private volatile BlockPos nextShipPos = null;
    private volatile boolean searchInProgress = false;
    private CompletableFuture<Void> searchTask = null;
    // 已访问（已搜割或已判定无鞘翅）的候选点；异步搜索线程也会读，故使用并发集合
    private final Set<Long> visitedShips = ConcurrentHashMap.newKeySet();
    // 螺旋搜索的安全上限（环数），防止无船时无限外扩
    private static final int MAX_SEARCH_RINGS = 128;

    public AutoElytraHarvester() {
        super("鞘翅进货", "自动搜刮末地船鞘翅并收纳至潜影盒、末影箱。无需烟花，使用无烟飞行。");
    }

    @Override
    public void onActivate() {
        currentState = State.GET_TARGET;
        targetShipPos = null;
        targetInteractPos = null;
        landingPos = null;
        targetFramePos = null;
        baritoneGoalSet = false;
        currentShipCandidate = null;
        tickDelay = 0;
        jumpTimer = 0;
        fireworkCooldown = 0;
        landingTimer = 0;
        isRefilling = false;
        blockShulkerSlot = -1;
        // 重置搜索状态，重新发现候选点
        nextShipPos = null;
        searchInProgress = false;
        searchTask = null;
        visitedShips.clear();
        // 启用配套模块
        toggledKillAura = false;
        toggledAutoTrash = false;
        toggledUnbreak = false;
        if (autokill.get()) {
            toggledKillAura = toggleModuleIfNeeded(KillAuraMiku.class, true);
            Set<EntityType<?>> entityTypes = Modules.get().get(KillAuraMiku.class).entities.get();
            // 26.2 移除了部分 EntityType 静态字段，这里统一按注册名解析；不存在的会被自动跳过。
            MikuEntities.add(entityTypes, "shulker_bullet", "shulker");

            if (!MikuEntities.contains(entityTypes, "item_frame")) {
                MikuEntities.add(entityTypes, "item_frame", "item_display");
                toggledKillAuraItem = true;
            }
        }
        if (autodrop.get()) toggledAutoTrash = toggleModuleIfNeeded(AutoTrashModule.class, true);
        if (autoElytraUnbreak.get()) toggledUnbreak = toggleModuleIfNeeded(ElytraUnbreak.class, true);
        ChatUtils.info("鞘翅自动进货 已启动！");
    }

    @Override
    public void onDeactivate() {
        // 释放可能残留的按键，避免下线/切换后跳跃键卡住
        mc.options.keyJump.setDown(false);
        // 仅关闭本模块启动时启用的配套模块，不误关用户手动开启的
        if (toggledKillAura) toggleModuleIfNeeded(KillAuraMiku.class, false);
        if (toggledAutoTrash) toggleModuleIfNeeded(AutoTrashModule.class, false);
        if (toggledUnbreak) toggleModuleIfNeeded(ElytraUnbreak.class, false);

        if (toggledKillAuraItem) {
            toggledKillAura = toggleModuleIfNeeded(KillAuraMiku.class, true);
            Set<EntityType<?>> entityTypes = Modules.get().get(KillAuraMiku.class).entities.get();
            if (MikuEntities.contains(entityTypes, "item_frame")) {
                MikuEntities.remove(entityTypes, "item_frame", "item_display");
            }
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.level == null) return;

        // 1. 全局安全退出拦截
        if (mc.player.getY() < lowY.get()) {
            disconnect("触发低Y线保护，安全下线。");
            return;
        }
        if (logoutOnFull.get() && isInventoryFull()) {
            disconnect("所有背包空间已满，安全下线。");
            return;
        }

        // 2. 烟花自动补给
        //    补给期间暂停整个飞行流程；仅在地面且未滑翔时触发，避免空中打断飞行
        if (isRefilling) {
            checkRefillComplete();
            return;
        }
        if (autoResupplyFirework.get() && !Via.isFallFlying(mc) && mc.player.onGround()
            && countFireworks() < minFireworks.get()) {
            startAutoRefill();
            return;
        }

        // 3. 延迟计数器（给发包和方块更新留出反应时间）
        if (tickDelay > 0) {
            tickDelay--;
            return;
        }

        // 4. 核心状态机逻辑
        switch (currentState) {
            case GET_TARGET:
                targetShipPos = getNextShipPos(); // 调用基于种子算法的最近未访问候选点
                if (targetShipPos == null) {
                    if (searchInProgress) {
                        return; // 异步搜索尚未完成，下一 tick 重试
                    }
                    ChatUtils.warning("未找到下一个末地船坐标，模块自动关闭。");
                    toggle();
                    return;
                }
                currentShipCandidate = targetShipPos; // 记录原始候选点，结束时标记为已访问
                landingPos = null; // 新目标，重置降落点
                targetFramePos = null;
                baritoneGoalSet = false;
                currentState = State.TAKE_OFF;
                break;

            case TAKE_OFF: {
                if (targetShipPos == null) {
                    currentState = State.GET_TARGET;
                    break;
                }
                // 锁定点：目标船正上方巡航高度处，起飞阶段始终对准该点
                BlockPos lockTarget = new BlockPos(targetShipPos.getX(), cruiseY.get(), targetShipPos.getZ());
                aimAt(lockTarget);

                if (!Via.isFallFlying(mc)) {
                    // 脉冲式跳跃：按 2 tick 释放一次，比持续按住更易触发鞘翅滑翔
                    if (jumpTimer <= 0) {
                        mc.options.keyJump.setDown(true);
                        jumpTimer = 2;
                    }
                    if (jumpTimer > 0) {
                        if (jumpTimer == 1) mc.options.keyJump.setDown(false);
                        jumpTimer--;
                    }
                    break;
                }

                // 已进入滑翔
                mc.options.keyJump.setDown(false);
                if (mc.player.getY() >= cruiseY.get()) {
                    currentState = State.FLYING; // 达到巡航高度，转交 Pitch40 巡航
                    break;
                }
                // 高度不足 -> 释放烟花推进（含快捷栏切换 / 背包快速换栏），冷却由独立计数器控制
                if (fireworkCooldown > 0) {
                    fireworkCooldown--;
                } else {
                    if (!releaseFirework()) {
                        ChatUtils.warning("背包无烟花，无法继续爬升！强行进入巡航。");
                        currentState = State.FLYING;
                    } else {
                        fireworkCooldown = fireworkInterval.get();
                    }
                }
                break;
            }
            case FLYING:
                if (targetShipPos == null) {
                    currentState = State.GET_TARGET;
                    return;
                }
                // 自动对准目标 XZ 轴调整 Yaw
                double diffX = targetShipPos.getX() + 0.5 - mc.player.getX();
                double diffZ = targetShipPos.getZ() + 0.5 - mc.player.getZ();
                float targetYaw = (float) Math.toDegrees(Math.atan2(diffZ, diffX)) - 90;
                mc.player.setYRot(targetYaw);
                startPitch40();

                double horizontalDistSq = diffX * diffX + diffZ * diffZ;

                // 【新增变动】当水平距离目标点小于 80 格（80*80=6400）时，在空中提前检索物品框
                if (horizontalDistSq < 6400) {
                    ItemFrame frame = null;
                    // 遍历全局实体，寻找区块已经加载出来的物品框
                    for (Entity entity : mc.level.entitiesForRendering()) {
                        if (entity instanceof ItemFrame) {
                            frame = (ItemFrame) entity;
                            break;
                        }
                    }

                    if (frame != null) {
                        // 如果有物品框但里面没有鞘翅 -> 直接放弃，去下一个末地船
                        if (frame.getItem().getItem() != Items.ELYTRA) {
                            ChatUtils.info("空中检测：该末地船无鞘翅，直接跳过！");
                            markVisited(currentShipCandidate);
                            currentState = State.GET_TARGET;
                            return;
                        } else {
                            // 如果有鞘翅 -> 精准修正目标点为物品框的正上方（保持当前巡航高度）
                            targetShipPos = new BlockPos(frame.blockPosition().getX(), targetShipPos.getY(), frame.blockPosition().getZ());
                            // 降落点设为展示框的 x,z，LANDING 阶段对准此处
                            landingPos = new BlockPos(frame.blockPosition().getX(), targetShipPos.getY(), frame.blockPosition().getZ());
                            // 缓存展示框坐标，DIG_DOWN 阶段交给 Baritone 寻路
                            targetFramePos = frame.blockPosition();
                        }
                    }
                }

                // 判断是否到达目标正上方（水平距离小于 3 格）
                if (horizontalDistSq < 9) {
                    // 不再立即关闭飞行自由落体；转入 LANDING 等待 ElytraFly 自然落地后再关闭
                    // 若未检测到展示框，降落点回退为目标点
                    if (landingPos == null) landingPos = targetShipPos;
                    landingTimer = 0;
                    currentState = State.LANDING;
                }
                break;

            case LANDING:
                // 保持 ElytraFly 活跃，调整 yaw 始终对准降落点，等待自然落地后再关闭飞行
                landingTimer++;
                if (landingPos != null) {
                    double ldx = landingPos.getX() + 0.5 - mc.player.getX();
                    double ldz = landingPos.getZ() + 0.5 - mc.player.getZ();
                    mc.player.setYRot((float) Math.toDegrees(Math.atan2(ldz, ldx)) - 90);
                }
                if (mc.player.onGround() || landingTimer > 400) {
                    ElytraFly elytraFly = Modules.get().get(ElytraFly.class);
                    if (elytraFly.isActive()) elytraFly.toggle();
                    baritoneGoalSet = false; // 即将进入 DIG_DOWN，重置寻路标志
                    currentState = State.DIG_DOWN;
                }
                break;

            case DIG_DOWN:
                // 落地后用 Baritone 寻路至展示框，不再手动挖掘
                ItemFrame loadedFrame = findItemFrame();
                // 已感知到展示框且接近 -> 停止 Baritone，进入判定
                if (loadedFrame != null && Math.abs(mc.player.getY() - loadedFrame.getY()) < 6) {
                    stopBaritone();
                    currentState = State.CHECK_FRAME;
                    break;
                }
                // 保底机制：路径过深仍未找到就直接去判断
                if (mc.player.getY() <= 50) {
                    stopBaritone();
                    currentState = State.CHECK_FRAME;
                    break;
                }
                // 下发一次 Baritone 目标：优先展示框坐标，否则朝降落点正下方寻路
                if (!baritoneGoalSet) {
                    BlockPos goalPos = targetFramePos != null
                        ? targetFramePos
                        : new BlockPos(landingPos.getX(), 55, landingPos.getZ());
                    try {
                        BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess()
                            .setGoalAndPath(new GoalBlock(goalPos));
                        baritoneGoalSet = true;
                        ChatUtils.info("Baritone 寻路至展示框: " + goalPos.toShortString());
                    } catch (Exception e) {
                        ChatUtils.warning("Baritone 不可用，跳过挖掘: " + e.getMessage());
                        currentState = State.CHECK_FRAME;
                    }
                }
                break;

            case CHECK_FRAME:
                ItemFrame frame = findItemFrame();
                // 如果附近有加载出的物品框，且里面没有鞘翅 -> 说明已被拿，直接跳走
                if (frame != null && frame.getItem().getItem() != Items.ELYTRA) {
                    ChatUtils.info("该末地船无鞘翅，准备前往下一处。");
                    markVisited(currentShipCandidate);
                    currentState = State.ESCAPE;
                } else if (frame != null && frame.getItem().getItem() == Items.ELYTRA) {
                    currentState = State.AIM_LOOT; // 先对准再攻击
                } else {
                    // 如果到这一步由于未知原因连框都没找到，保守起见直接尝试脱离
                    markVisited(currentShipCandidate); // 标记已访问，避免死循环重复选取同一目标
                    currentState = State.ESCAPE;
                }
                break;

            case AIM_LOOT: {
                // 第一步：对准展示框（参考 ItemFrameSearch，设置 yaw + pitch 确保服务端认可命中）
                ItemFrame aimFrame = findItemFrame();
                if (aimFrame == null) {
                    currentState = State.PLACE_SHULKER;
                    break;
                }
                double fdx = aimFrame.getX() - mc.player.getX();
                double fdy = aimFrame.getY() - (mc.player.getY() + mc.player.getEyeHeight(mc.player.getPose()));
                double fdz = aimFrame.getZ() - mc.player.getZ();
                double fh = Math.sqrt(fdx * fdx + fdz * fdz);
                mc.player.setYRot((float) Math.toDegrees(Math.atan2(fdz, fdx)) - 90.0f);
                mc.player.setXRot((float) -Math.toDegrees(Math.atan2(fdy, fh)));
                // 第二步：延迟 2 tick 确保旋转包被服务端处理后，再攻击
                tickDelay = 2;
                currentState = State.LOOT;
                break;
            }

            case LOOT: {
                // tickDelay 已归零（延迟结束），执行攻击
                ItemFrame harvestFrame = findItemFrame();
                if (harvestFrame != null) {
                    mc.gameMode.attack(mc.player, harvestFrame);
                    mc.player.swing(InteractionHand.MAIN_HAND);
                }
                markVisited(currentShipCandidate);
                tickDelay = 15; // 等待 0.75 秒让物品吸入背包
                currentState = State.PLACE_SHULKER;
                break;
            }

            case PLACE_SHULKER: {
                // 参考 ShulkerBoxItemFetcher#placeShulkerBox：找潜影盒槽位 + 找放置位置 + BaritoneUtil 放置
                blockShulkerSlot = findShulkerBoxSlot();
                if (blockShulkerSlot == -1) {
                    ChatUtils.warning("背包中没有潜影盒，直接尝试存末影箱。");
                    currentState = State.PLACE_ECHEST;
                    break;
                }
                BlockPos placePos = findAdjacentPlacePos(mc.player.blockPosition());
                if (placePos == null) {
                    ChatUtils.warning("附近无可用位置放置潜影盒，直接尝试存末影箱。");
                    currentState = State.PLACE_ECHEST;
                    break;
                }
                targetInteractPos = placePos;
                BaritoneUtil.placeItem(placePos, blockShulkerSlot);
                tickDelay = 5; // 等放置生效
                currentState = State.OPEN_SHULKER;
                break;
            }

            case OPEN_SHULKER:
                // 参考 ShulkerBoxItemFetcher#openShulkerBox：对准 + 右键
                if (targetInteractPos == null) {
                    currentState = State.PLACE_ECHEST;
                    break;
                }
                interactBlock(targetInteractPos);
                tickDelay = 10; // 等潜影盒 GUI 加载
                currentState = State.FILL_SHULKER;
                break;

            case FILL_SHULKER:
                // 参考 ShulkerBoxItemFetcher#extractItems：遍历容器槽位，逐个 shift-click 鞘翅
                if (!(mc.player.containerMenu instanceof ShulkerBoxMenu)) break;
                boolean moved = false;
                for (int i = 0; i < mc.player.containerMenu.slots.size() - 36; i++) {
                    if (mc.player.containerMenu.getSlot(i).getItem().getItem() == Items.ELYTRA) {
                        InvUtils.shiftClick().slot(i);
                        moved = true;
                        break; // 每次只移一个，等服务端同步后再移下一个
                    }
                }
                if (moved) {
                    tickDelay = 5; // 等服务端处理
                } else {
                    // 潜影盒已满或背包无鞘翅
                    mc.player.closeContainer();
                    tickDelay = 5;
                    currentState = State.BREAK_SHULKER;
                }
                break;

            case BREAK_SHULKER:
                // 参考 ShulkerBoxItemFetcher#breakShulkerBox：BaritoneUtil.breakBlock
                if (targetInteractPos != null) {
                    BaritoneUtil.breakBlock(targetInteractPos);
                }
                tickDelay = 15; // 等掉落物拾取
                // 潜影盒已满才需要末影箱；若还有空位说明鞘翅已全部装下，直接脱离
                boolean shulkerFull = !hasElytraInInventory();
                currentState = shulkerFull ? State.PLACE_ECHEST : State.ESCAPE;
                break;

            case PLACE_ECHEST: {
                // 鞘翅有剩余且潜影盒已满，用末影箱存储装满鞘翅的潜影盒
                FindItemResult eChest = InvUtils.findInHotbar(Blocks.ENDER_CHEST.asItem());
                if (!eChest.isHotbar()) {
                    ChatUtils.warning("快捷栏没有末影箱，直接进入脱离阶段。");
                    currentState = State.ESCAPE;
                    break;
                }
                BlockPos ePos = findAdjacentPlacePos(mc.player.blockPosition());
                if (ePos == null) {
                    currentState = State.ESCAPE;
                    break;
                }
                targetInteractPos = ePos;
                BlockUtils.place(ePos, eChest, true, 0);
                tickDelay = 5;
                currentState = State.OPEN_ECHEST;
                break;
            }

            case OPEN_ECHEST:
                if (targetInteractPos != null) {
                    interactBlock(targetInteractPos);
                    tickDelay = 10; // 等末影箱 GUI 加载
                    currentState = State.FILL_ECHEST;
                }
                break;

            case FILL_ECHEST:
                // 把装满鞘翅的潜影盒 shift-click 存入末影箱
                if (mc.player.containerMenu instanceof ChestMenu) {
                    boolean eChestMoved = false;
                    for (int i = 0; i < 36; i++) {
                        ItemStack stack = mc.player.getInventory().getItem(i);
                        if (!stack.isEmpty() && stack.getItem() instanceof BlockItem bi
                            && bi.getBlock() instanceof ShulkerBoxBlock) {
                            // 背包槽位 i → 容器槽位 = 36 + i（offset 36）
                            mc.gameMode.handleContainerInput(
                                mc.player.containerMenu.containerId,
                                36 + i, 0, ContainerInput.QUICK_MOVE, mc.player
                            );
                            eChestMoved = true;
                            break;
                        }
                    }
                    if (eChestMoved) {
                        tickDelay = 5; // 等服务端处理
                    } else {
                        mc.player.closeContainer();
                        tickDelay = 5;
                        currentState = State.BREAK_ECHEST;
                    }
                }
                break;

            case BREAK_ECHEST:
                if (targetInteractPos != null) {
                    BaritoneUtil.breakBlock(targetInteractPos);
                }
                tickDelay = 15;
                currentState = State.ESCAPE;
                break;

            case ESCAPE:
                // 往头顶开掘，直到重见天日（或者达到能安全起飞的高度）
                BlockPos blockAboveHead = mc.player.blockPosition().above(2);
                if (anySolidAbove(blockAboveHead) && blockAboveHead.getY() < 120) {
                    FindItemResult pick = InvUtils.findInHotbar(Items.NETHERITE_PICKAXE, Items.DIAMOND_PICKAXE);
                    InvUtils.swap(pick.slot(), false);
                    BlockUtils.breakBlock(blockAboveHead, true);
                    tickDelay = 2;
                } else {
                    // 已经回到开阔区域，重置状态，寻找下一个目标开启循环
                    currentState = State.GET_TARGET;
                }
                break;
        }
    }

    // --- 基于 Seed 螺旋搜索最近未访问末地船的核心逻辑  ---
    private BlockPos getNextShipPos() {
        if (mc.player == null) return null;

        // 缓存仍有效（未被访问）则直接返回
        if (nextShipPos != null && !visitedShips.contains(nextShipPos.asLong())) {
            return nextShipPos;
        }

        // 缓存失效 -> 触发异步螺旋搜索，本 tick 返回 null 让状态机等待
        if (!searchInProgress) {
            startShipSearch();
        }
        return null;
    }

    /**
     * 启动异步螺旋搜索：以玩家当前所在 region 为中心，按环 r=0,1,2,... 向外扩展，
     * 复用 ElytraFinder 的种子算法逐 region 判定 hasShip，跟踪全局最近未访问候选。
     * 当下一环的最近可能距离已 >= 当前最佳距离时即可停止——保证找到的就是最近者。
     */
    private void startShipSearch() {
        if (searchInProgress) return;

        Seed worldSeed = Seeds.get().getSeed();
        if (worldSeed == null || worldSeed.seed == null || worldSeed.version == null) {
            SeedVersion version = SeedVersion.parseInput(mcVersionInput.get());
            if (version == null) {
                // 版本输入为空或超出 1.12.2 ~ 26.2：按空白处理，不用手动种子启动搜索
                return;
            }
            String s = seedInput.get().trim();
            try {
                long parsed = Long.parseLong(s);
                worldSeed = new Seed(parsed, version);
            } catch (NumberFormatException e) {
                ChatUtils.error("无法获取世界种子，请在种子设置中正确配置。");
                return;
            }
        }

        if (PlayerUtils.getDimension() != End) {
            ChatUtils.warning("建议在末地使用此功能以获得准确结果。");
        }

        // 种子库里没有该版本自己的数据时跳过：26.x/1.21.1 ~ 1.21.11 的库常量并不存在，
        // 不能拿 1.21 的库数据顶替，本版也没有它们的原生末地城算法。
        if (!worldSeed.version.hasExactLibraryData()) {
            ChatUtils.error("所选版本 " + worldSeed.version.name + " 在种子库里没有对应的数据（种子库只收录到 1.21）；"
                + "已跳过末地船搜索，不会用别的版本的数据顶替。");
            return;
        }

        final BlockPos playerPos = mc.player.blockPosition();
        final Seed seed = worldSeed;
        searchInProgress = true;
        ChatUtils.info("开始螺旋搜索最近末地船...");

        searchTask = CompletableFuture.runAsync(() -> {
            try {
                BlockPos found = spiralFindNearestShip(seed, playerPos);
                nextShipPos = found;
                if (found != null) {
                    ChatUtils.info("已锁定最近末地船: " + formatPos(found));
                } else {
                    ChatUtils.warning("在搜索范围内未找到末地船。");
                }
            } catch (Exception e) {
                ChatUtils.error("末地船搜索失败: " + e.getMessage());
            } finally {
                searchInProgress = false;
            }
        });
    }

    /**
     * 螺旋向外搜索，返回距离玩家最近且未访问的带船末地城坐标。
     * 以玩家所在 region 为中心，按环 r=0,1,2,... 向外扩展；每环处理完后判断：
     * 若 r 环的最近可能块距 >= 当前最佳距离，则 r 及更外环都不可能更近，停止搜索。
     */
    private BlockPos spiralFindNearestShip(Seed worldSeed, BlockPos playerPos) {
        long seed = worldSeed.seed;
        // 只有种子库确实收录了这个版本时，库里的末地城数据才是该版本自己的数据；
        // 否则直接返回，不拿别的版本（例如 1.21）的数据顶替。
        if (!worldSeed.version.hasExactLibraryData()) {
            return null;
        }
        MCVersion version = worldSeed.version.toMCVersion();

        BiomeSource biomeSource = BiomeSource.of(Dimension.END, version, seed);
        TerrainGenerator generator = TerrainGenerator.of(Dimension.END, biomeSource);
        EndCity endCity = new EndCity(version);
        EndCityGenerator endCityGenerator = new EndCityGenerator(version);
        ChunkRand rand = new ChunkRand();

        int spacing = endCity.getSpacing();
        if (spacing <= 0) return null;

        int playerChunkX = playerPos.getX() >> 4;
        int playerChunkZ = playerPos.getZ() >> 4;
        int playerRegionX = Math.floorDiv(playerChunkX, spacing);
        int playerRegionZ = Math.floorDiv(playerChunkZ, spacing);

        BlockPos bestPos = null;
        double bestDist = Double.MAX_VALUE;

        for (int r = 0; r <= MAX_SEARCH_RINGS; r++) {
            // 停止判定：r 环的最近可能块距 >= best -> r 及更外环都不可能更近
            if (r > 0) {
                double minPossibleBlocks = ((double) (r - 1) * spacing + 1) * 16.0;
                if (minPossibleBlocks >= bestDist) break;
            }

            // 环 r 的上边 (dz=-r) 与下边 (dz=+r)
            for (int dx = -r; dx <= r; dx++) {
                bestPos = tryRegion(endCity, endCityGenerator, rand, biomeSource, generator, seed,
                    playerRegionX + dx, playerRegionZ - r, playerPos, bestPos, bestDist);
                bestDist = bestPos == null ? Double.MAX_VALUE : distOf(bestPos, playerPos);
                bestPos = tryRegion(endCity, endCityGenerator, rand, biomeSource, generator, seed,
                    playerRegionX + dx, playerRegionZ + r, playerPos, bestPos, bestDist);
                bestDist = bestPos == null ? Double.MAX_VALUE : distOf(bestPos, playerPos);
            }
            // 环 r 的左边 (dx=-r) 与右边 (dx=+r)，跳过已处理的角点
            for (int dz = -r + 1; dz <= r - 1; dz++) {
                bestPos = tryRegion(endCity, endCityGenerator, rand, biomeSource, generator, seed,
                    playerRegionX - r, playerRegionZ + dz, playerPos, bestPos, bestDist);
                bestDist = bestPos == null ? Double.MAX_VALUE : distOf(bestPos, playerPos);
                bestPos = tryRegion(endCity, endCityGenerator, rand, biomeSource, generator, seed,
                    playerRegionX + r, playerRegionZ + dz, playerPos, bestPos, bestDist);
                bestDist = bestPos == null ? Double.MAX_VALUE : distOf(bestPos, playerPos);
            }
        }
        return bestPos;
    }

    private double distOf(BlockPos pos, BlockPos playerPos) {
        return Math.hypot(pos.getX() - playerPos.getX(), pos.getZ() - playerPos.getZ());
    }

    /**
     * 处理单个 region：若该 region 生成带船末地城且未访问，且距离优于当前最佳，则更新最佳。
     * 返回更新后的最佳 BlockPos（可能不变）。
     */
    private BlockPos tryRegion(EndCity endCity, EndCityGenerator gen, ChunkRand rand,
                               BiomeSource biomeSource, TerrainGenerator generator, long seed,
                               int regionX, int regionZ, BlockPos playerPos,
                               BlockPos bestPos, double bestDist) {
        try {
            CPos pos = endCity.getInRegion(seed, regionX, regionZ, rand);
            if (pos == null) return bestPos;

            if (!endCity.canSpawn(pos, biomeSource) || !endCity.canGenerate(pos, generator)) {
                return bestPos;
            }
            gen.generate(generator, pos, rand);
            if (!gen.hasShip()) {
                gen.reset();
                return bestPos;
            }
            gen.reset();

            int bx = pos.getX() * 16;
            int bz = pos.getZ() * 16;
            BlockPos candidate = new BlockPos(bx, 100, bz);
            if (visitedShips.contains(candidate.asLong())) return bestPos;

            double dist = Math.hypot(bx - playerPos.getX(), bz - playerPos.getZ());
            if (dist < bestDist) {
                return candidate;
            }
            return bestPos;
        } catch (Exception ignored) {
            return bestPos;
        }
    }

    private String formatPos(BlockPos pos) {
        return "(" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")";
    }

    /**
     * 将指定候选点标记为已访问（已搜割或已判定无鞘翅），后续不再选取。
     */
    private void markVisited(BlockPos pos) {
        if (pos != null) visitedShips.add(pos.asLong());
    }

    // --- 辅助工具函数 ---
    private ItemFrame findItemFrame() {
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof ItemFrame) {
                return (ItemFrame) entity;
            }
        }
        return null;
    }

    // 26.1 移除了 Level#hasNoBlocksAbove，这里做等价检查：竖直向上到 120 是否有实体方块
    private boolean anySolidAbove(BlockPos pos) {
        for (int y = pos.getY() + 1; y <= 120; y++) {
            if (!mc.level.getBlockState(pos.atY(y)).isAir()) {
                return true;
            }
        }
        return false;
    }

    private boolean isInventoryFull() {
        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
            if (mc.player.getInventory().getItem(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** 在背包中找一个潜影盒方块物品的槽位（用于 BaritoneUtil.placeItem）。 */
    private int findShulkerBoxSlot() {
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.getItem() instanceof BlockItem bi
                && bi.getBlock() instanceof ShulkerBoxBlock) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 在玩家 3 格半径内找一个可放置方块的位置（参考 ShulkerBoxItemFetcher#findSuitablePlacePosition）。
     * 优先面朝方向 → 相邻 → 上下 → 对角线。
     */
    private BlockPos findAdjacentPlacePos(BlockPos playerPos) {
        // 面朝方向优先
        Direction facing = mc.player.getDirection();
        BlockPos front = playerPos.relative(facing);
        if (isValidPlace(front)) return front;
        // 相邻水平方向
        for (Direction d : new Direction[]{facing.getClockWise(),
            facing.getCounterClockWise(), facing.getOpposite()}) {
            BlockPos p = playerPos.relative(d);
            if (isValidPlace(p)) return p;
        }
        // 上方、下方
        if (isValidPlace(playerPos.above())) return playerPos.above();
        if (isValidPlace(playerPos.below())) return playerPos.below();
        // 对角线
        for (int dist = 1; dist <= 3; dist++) {
            for (int x = -dist; x <= dist; x++) {
                for (int z = -dist; z <= dist; z++) {
                    if (Math.abs(x) < dist && Math.abs(z) < dist) continue;
                    for (int y = -1; y <= 1; y++) {
                        BlockPos p = playerPos.offset(x, y, z);
                        if (isValidPlace(p)) return p;
                    }
                }
            }
        }
        return null;
    }

    private boolean isValidPlace(BlockPos pos) {
        return mc.level.getBlockState(pos).isAir()
            && BlockUtils.canPlace(pos)
            && !mc.level.getBlockState(pos.below()).isAir();
    }

    /** 背包中是否还有鞘翅（用于判断潜影盒是否装满）。 */
    private boolean hasElytraInInventory() {
        for (int i = 0; i < 36; i++) {
            if (mc.player.getInventory().getItem(i).getItem() == Items.ELYTRA) {
                return true;
            }
        }
        return false;
    }

    private void interactBlock(BlockPos pos) {
        BaritoneUtil.clickBlock(pos, Direction.UP, true, InteractionHand.MAIN_HAND, BaritoneUtil.SwingSide.All);
    }

    private void disconnect(String reason) {
        if (mc.getConnection() != null) {
            mc.getConnection().getConnection().disconnect(Component.literal("[鞘翅进货] " + reason));
        }
        toggle();
    }


    private void startPitch40() {
        ElytraFly elytraFly = Modules.get().get(ElytraFly.class);
        elytraFly.flightMode.set(ElytraFlightModes.Pitch40);
        elytraFly.pitch40lowerBounds.set(180D);
        elytraFly.pitch40upperBounds.set(220D);
        if (!elytraFly.isActive()) {
            elytraFly.toggle();
        }
    }

    /**
     * 将玩家视线对准目标点（yaw + pitch）。
     * 起飞阶段对准"目标船正上方巡航高度"的锁定点，使烟花推进方向正确。
     */
    private void aimAt(BlockPos target) {
        double dx = target.getX() + 0.5 - mc.player.getX();
        double dy = target.getY() + 0.5 - (mc.player.getY() + mc.player.getEyeHeight(mc.player.getPose()));
        double dz = target.getZ() + 0.5 - mc.player.getZ();
        double horiz = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, horiz));
        mc.player.setYRot(yaw);
        mc.player.setXRot(pitch);
    }

    /**
     * 停止 Baritone 寻路并重置目标下发标志。
     */
    private void stopBaritone() {
        try {
            BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().cancelEverything();
        } catch (Exception ignored) {
            // Baritone 不可用时忽略
        }
        baritoneGoalSet = false;
    }

    /**
     * 安全切换模块状态。
     * enable=true 时：若模块未开启则开启，返回 true（表示由本模块激活）；
     * enable=false 时：若模块处于目标状态则关闭。
     */
    private boolean toggleModuleIfNeeded(Class<? extends Module> clazz, boolean enable) {
        Module m = Modules.get().get(clazz);
        if (m == null) return false;
        if (enable && !m.isActive()) {
            m.toggle();
            return true;
        }
        if (!enable && m.isActive()) {
            m.toggle();
        }
        return false;
    }

    /**
     * 释放一发烟花进行爬升推进。优先使用快捷栏中的烟花；若仅在主背包，
     * 通过 quickSwap 临时换到手中使用后再换回
     * 返回是否成功释放。
     */
    private boolean releaseFirework() {
        FindItemResult result = InvUtils.find(Items.FIREWORK_ROCKET);
        if (!result.found()) return false;

        Modules.get().get(OnekeyFireWork.class).toggle();
        return true;
    }

    /**
     * 统计背包中烟花总数。
     */
    private int countFireworks() {
        int count = 0;
        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.getItem() == Items.FIREWORK_ROCKET) {
                count += stack.getCount();
            }
        }
        return count;
    }

    /**
     * 启动一键补给补充烟花
     * 配置 ShulkerBoxItemFetcher 的目标物品为烟花、开启 autoClose，
     * 并暂停 ElytraFly 以免补给期间自动飞行干扰。
     */
    private void startAutoRefill() {
        ShulkerBoxItemFetcher fetcher = Modules.get().get(ShulkerBoxItemFetcher.class);
        if (fetcher == null) {
            ChatUtils.warning("找不到一键补给模块，无法补充烟花。");
            return;
        }

        // 暂停 ElytraFly，避免补给期间自动飞行
        ElytraFly elytraFly = Modules.get().get(ElytraFly.class);
        if (elytraFly != null && elytraFly.isActive()) {
            elytraFly.toggle();
        }

        // 配置补给模块：目标物品=烟花，完成后自动关闭
        fetcher.targetItem.set(Items.FIREWORK_ROCKET);
        fetcher.autoClose.set(true);

        isRefilling = true;
        if (!fetcher.isActive()) {
            fetcher.toggle();
            ChatUtils.info("烟花不足，正在使用一键补给补充...");
        }
    }

    /**
     * 检查一键补给是否完成（autoClose=true 时补给模块会自动关闭）。
     * 完成后清除标志，恢复飞行流程。
     */
    private void checkRefillComplete() {
        ShulkerBoxItemFetcher fetcher = Modules.get().get(ShulkerBoxItemFetcher.class);
        if (fetcher == null || !fetcher.isActive()) {
            isRefilling = false;
            ChatUtils.info("烟花补给完成，继续流程。");
        }
    }
}
