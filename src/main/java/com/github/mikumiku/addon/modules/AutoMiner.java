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
import baritone.api.Settings;
import baritone.api.pathing.goals.GoalBlock;
import com.github.mikumiku.addon.BaseModule;
import com.github.mikumiku.addon.util.ChatUtils;
import com.github.mikumiku.addon.util.PositionCache;
import com.github.mikumiku.addon.util.timer.SyncedTickTimer;
import com.github.mikumiku.addon.util.timer.Timers;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.Map.Entry;
import meteordevelopment.meteorclient.events.meteor.KeyInputEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.pathing.BaritoneUtils;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.BlockListSetting.Builder;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.meteorclient.utils.misc.input.KeyAction;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import net.minecraft.core.BlockPos.MutableBlockPos;

public class AutoMiner extends BaseModule {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgShulkerBoxes = this.settings.createGroup("潜影盒设置");
   private final SettingGroup sgTools = this.settings.createGroup("工具管理");
   private final SettingGroup sgRender = this.settings.createGroup("渲染");
   private final Setting<List<net.minecraft.world.level.block.Block>> targetBlocks = this.sgGeneral
      .add(((Builder)((Builder)((Builder)new Builder().name("挖掘目标")).description("要挖掘的方块类型 多选")).defaultValue(Arrays.asList(net.minecraft.world.level.block.Blocks.SAND))).build());
   private final Setting<Integer> miningRange = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                     .name("挖掘范围"))
                  .description("搜索目标方块的范围"))
               .defaultValue(32))
            .min(4)
            .max(200)
            .sliderMin(8)
            .sliderMax(200)
            .build()
      );
   private final Setting<Boolean> packetMine = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("使用极速包挖"))
                  .description("使用包挖乱挖周围目标方块，可能捡不到"))
               .defaultValue(false))
            .build()
      );
   private final Setting<Integer> delay = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                     .name("延迟"))
                  .description("操作之间的延迟（tick）"))
               .defaultValue(5))
            .min(0)
            .max(20)
            .sliderMin(0)
            .sliderMax(20)
            .build()
      );
   private final Setting<Boolean> autoReturn = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("自动返回"))
                  .description("完成存储或取工具后自动返回挖掘"))
               .defaultValue(true))
            .build()
      );
   private final Setting<Integer> shulkerSearchRadius = this.sgShulkerBoxes
      .add(
         ((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                     .name("潜影盒搜索半径"))
                  .description("搜索潜影盒的半径范围"))
               .defaultValue(32))
            .min(4)
            .max(200)
            .sliderMin(8)
            .sliderMax(200)
            .build()
      );
   private final Setting<AutoMiner.MarkMode> toolMarkMode = this.sgShulkerBoxes
      .add(
         ((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)new meteordevelopment.meteorclient.settings.EnumSetting.Builder()
                     .name("工具盒标记方式"))
                  .description("工具潜影盒怎么指定：按键标记=准星对准潜影盒按标记键；坐标标记=直接在下面填坐标。"))
               .defaultValue(AutoMiner.MarkMode.KEY))
            .build()
      );
   private final Setting<Keybind> toolMarkKey = this.sgShulkerBoxes
      .add(
         ((meteordevelopment.meteorclient.settings.KeybindSetting.Builder)((meteordevelopment.meteorclient.settings.KeybindSetting.Builder)((meteordevelopment.meteorclient.settings.KeybindSetting.Builder)((meteordevelopment.meteorclient.settings.KeybindSetting.Builder)new meteordevelopment.meteorclient.settings.KeybindSetting.Builder()
                        .name("工具盒标记按键"))
                     .description("准星对准工具潜影盒后按此键即可标记（默认 G）。模块开启期间随时可用。"))
                  .defaultValue(Keybind.fromKey(71)))
               .visible(() -> this.toolMarkMode.get() == AutoMiner.MarkMode.KEY))
            .build()
      );
   private final Setting<String> toolShulkerCoord = this.sgShulkerBoxes
      .add(
         ((meteordevelopment.meteorclient.settings.StringSetting.Builder)((meteordevelopment.meteorclient.settings.StringSetting.Builder)((meteordevelopment.meteorclient.settings.StringSetting.Builder)((meteordevelopment.meteorclient.settings.StringSetting.Builder)new meteordevelopment.meteorclient.settings.StringSetting.Builder()
                        .name("工具盒坐标"))
                     .description("工具潜影盒的坐标，格式：x y z 或 x,y,z。填好后重开模块生效。"))
                  .defaultValue(""))
               .visible(() -> this.toolMarkMode.get() == AutoMiner.MarkMode.COORDINATE))
            .build()
      );
   private final Setting<AutoMiner.MarkMode> storageMarkMode = this.sgShulkerBoxes
      .add(
         ((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)new meteordevelopment.meteorclient.settings.EnumSetting.Builder()
                     .name("储存盒标记方式"))
                  .description("储存潜影盒怎么指定：按键标记=准星对准潜影盒按标记键逐个加入；坐标标记=在下面每行填一个坐标。"))
               .defaultValue(AutoMiner.MarkMode.KEY))
            .build()
      );
   private final Setting<Keybind> storageMarkKey = this.sgShulkerBoxes
      .add(
         ((meteordevelopment.meteorclient.settings.KeybindSetting.Builder)((meteordevelopment.meteorclient.settings.KeybindSetting.Builder)((meteordevelopment.meteorclient.settings.KeybindSetting.Builder)((meteordevelopment.meteorclient.settings.KeybindSetting.Builder)new meteordevelopment.meteorclient.settings.KeybindSetting.Builder()
                        .name("储存盒标记按键"))
                     .description("准星对准储存潜影盒后按此键即可加入储存列表（默认 H）。模块开启期间随时可用。"))
                  .defaultValue(Keybind.fromKey(72)))
               .visible(() -> this.storageMarkMode.get() == AutoMiner.MarkMode.KEY))
            .build()
      );
   private final Setting<List<String>> storageShulkerCoords = this.sgShulkerBoxes
      .add(
         ((meteordevelopment.meteorclient.settings.StringListSetting.Builder)((meteordevelopment.meteorclient.settings.StringListSetting.Builder)((meteordevelopment.meteorclient.settings.StringListSetting.Builder)new meteordevelopment.meteorclient.settings.StringListSetting.Builder()
                     .name("储存盒坐标列表"))
                  .description("储存潜影盒的坐标，每行一个，格式：x y z 或 x,y,z。填好后重开模块生效。"))
               .visible(() -> this.storageMarkMode.get() == AutoMiner.MarkMode.COORDINATE))
            .build()
      );
   private final Setting<AutoMiner.FullCheckMode> fullCheckMode = this.sgShulkerBoxes
      .add(
         ((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)new meteordevelopment.meteorclient.settings.EnumSetting.Builder()
                     .name("满盒判断方式"))
                  .description(
                     "怎么知道盒子满没满。询问世界后再开启=默认，先用「方块还在不在 + 上次识别缓存」判断，判断不出来才开门复核（30 秒最多一次）；仅询问世界=只用实时世界判断（方块是否还在 + 缓存），不为确认专门开门；仅询问世界缓存=连方块都不查，只看上次识别结果，最省事；我全都要=不靠缓存跳过，任务涉及到的盒子都开门亲自看，同一结论要连续确认 3 次才作数，每次开门都会刷新缓存。"
                  ))
               .defaultValue(AutoMiner.FullCheckMode.AUTO))
            .build()
      );
   private final Setting<Boolean> extractOtherItems = this.sgShulkerBoxes
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("取出其他物品"))
                  .description("储存盒里混着不是挖掘目标的东西时，先把它们精准挪回背包腾出格子，再放沙子。背包没有空位时会跳过这个盒子。"))
               .defaultValue(true))
            .build()
      );
   private final Setting<Integer> fillDelay = this.sgShulkerBoxes
      .add(
         ((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                     .name("填充延迟"))
                  .description("往潜影盒里放每一组物品之间的等待刻数（tick）。数值越大放得越慢、越稳；20 刻 = 1 秒。"))
               .defaultValue(2))
            .min(0)
            .max(40)
            .sliderMin(0)
            .sliderMax(20)
            .build()
      );
   private final Setting<Integer> fillDelayJitter = this.sgShulkerBoxes
      .add(
         ((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                     .name("延迟抖动"))
                  .description("在填充延迟基础上随机多等一点时间（最高此百分比），避免操作节奏过于规律。只会更慢，不会更快。0 = 关闭。"))
               .defaultValue(40))
            .min(0)
            .max(150)
            .sliderMin(0)
            .sliderMax(150)
            .build()
      );
   private final Setting<Boolean> renderStorageBox = this.sgRender
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("渲染储存盒状态"))
                  .description("给识别过的潜影盒套一层彩色边框：储存盒空盒=绿色，没满或混有其他物品=黄色，装满了=红色；工具盒=蓝色。储存盒需要先打开过（自动存物品或手动打开）才会显示，工具盒只要标记过就会显示。"))
               .defaultValue(true))
            .build()
      );
   private final Setting<ShapeMode> renderShapeMode = this.sgRender
      .add(
         ((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)new meteordevelopment.meteorclient.settings.EnumSetting.Builder()
                        .name("渲染样式"))
                     .description("彩框在游戏内的显示方式：实心面（Sides）、线条（Lines）或两者都有（Both）。"))
                  .defaultValue(ShapeMode.Both))
               .visible(this.renderStorageBox::get))
            .build()
      );
   private final Setting<SettingColor> fullColor = this.sgRender
      .add(
         ((meteordevelopment.meteorclient.settings.ColorSetting.Builder)((meteordevelopment.meteorclient.settings.ColorSetting.Builder)((meteordevelopment.meteorclient.settings.ColorSetting.Builder)new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                     .name("满盒颜色"))
                  .description("储存盒装满时边框的颜色，默认红色。满了的盒子会被移出储存列表，之后不再去访问它。"))
               .defaultValue(new SettingColor(255, 60, 60, 255))
               .visible(this.renderStorageBox::get))
            .build()
      );
   private final Setting<SettingColor> emptyColor = this.sgRender
      .add(
         ((meteordevelopment.meteorclient.settings.ColorSetting.Builder)((meteordevelopment.meteorclient.settings.ColorSetting.Builder)((meteordevelopment.meteorclient.settings.ColorSetting.Builder)new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                     .name("空盒颜色"))
                  .description("储存盒完全是空的时候边框的颜色，默认绿色。"))
               .defaultValue(new SettingColor(60, 255, 80, 255))
               .visible(this.renderStorageBox::get))
            .build()
      );
   private final Setting<SettingColor> otherItemColor = this.sgRender
      .add(
         ((meteordevelopment.meteorclient.settings.ColorSetting.Builder)((meteordevelopment.meteorclient.settings.ColorSetting.Builder)((meteordevelopment.meteorclient.settings.ColorSetting.Builder)new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                     .name("不满/有其他物品颜色"))
                  .description("储存盒还没装满，或者里面混有非目标物品（不是沙子等挖掘目标）时边框的颜色，默认黄色。"))
               .defaultValue(new SettingColor(255, 215, 0, 255))
               .visible(this.renderStorageBox::get))
            .build()
      );
   private final Setting<SettingColor> toolBoxColor = this.sgRender
      .add(
         ((meteordevelopment.meteorclient.settings.ColorSetting.Builder)((meteordevelopment.meteorclient.settings.ColorSetting.Builder)((meteordevelopment.meteorclient.settings.ColorSetting.Builder)new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                     .name("工具盒颜色"))
                  .description("工具潜影盒的边框颜色，默认蓝色，方便和储存盒（绿/黄/红）一眼区分开。"))
               .defaultValue(new SettingColor(60, 140, 255, 255))
               .visible(this.renderStorageBox::get))
            .build()
      );
   private final Setting<Integer> renderDistance = this.sgRender
      .add(
         ((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                        .name("渲染距离"))
                     .description("多远以内的储存盒才画边框，避免盒子太多时卡顿。"))
                  .defaultValue(64))
               .min(8)
               .max(256)
               .sliderRange(8, 128)
               .visible(this.renderStorageBox::get))
            .build()
      );
   private final Setting<AutoMiner.ToolType> toolType = this.sgTools
      .add(
         ((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)new meteordevelopment.meteorclient.settings.EnumSetting.Builder()
                     .name("工具类型"))
                  .description("选择使用的工具类型"))
               .defaultValue(AutoMiner.ToolType.SHOVEL))
            .build()
      );
   private final Setting<Integer> minDurability = this.sgTools
      .add(
         ((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                     .name("最低耐久度"))
                  .description("工具耐久度低于此值时更换"))
               .defaultValue(10))
            .min(1)
            .max(100)
            .build()
      );
   private AutoMiner.MinerState currentState = AutoMiner.MinerState.WAITING_TOOL_SELECTION;
   private int tickTimer = 0;
   private SyncedTickTimer cacheClearTimer = Timers.tickTimer();
   private net.minecraft.core.BlockPos lastMiningPos = null;
   private net.minecraft.core.BlockPos currentTarget = null;
   private net.minecraft.core.BlockPos toolShulkerPos = null;
   private Set<net.minecraft.core.BlockPos> protectedShulkerBoxes = new HashSet<>();
   private final Set<net.minecraft.core.BlockPos> storageShulkerPositions = new LinkedHashSet<>();
   private final Map<net.minecraft.core.BlockPos, AutoMiner.BoxStatus> storageBoxStatus = new HashMap<>();
   private final Set<net.minecraft.core.BlockPos> knownStorageBoxes = new LinkedHashSet<>();
   private final Set<net.minecraft.core.BlockPos> fullStorageBoxes = new HashSet<>();
   private final Map<net.minecraft.core.BlockPos, Integer> boxFullConfirmCount = new HashMap<>();
   private final Map<net.minecraft.core.BlockPos, Long> boxRecheckTime = new HashMap<>();
   private final Set<net.minecraft.core.BlockPos> allFullRechecked = new HashSet<>();
   private final Map<net.minecraft.core.BlockPos, Long> destroyedFullBoxes = new HashMap<>();
   private net.minecraft.core.BlockPos pendingStorageMark = null;
   private int pendingStorageMarkTimer = 0;
   private net.minecraft.core.BlockPos currentStorageShulker = null;
   private int shulkerInteractionTimer = 0;
   private boolean waitingForShulkerOpen = false;
   private final Random random = new Random();
   private long lastNoTargetWarn = 0L;
   private long lastNoStorageWarn = 0L;
   private long extractStallUntil = 0L;
   private static final long RECHECK_COOLDOWN_MS = 30000L;
   private static final long GUI_RECHECK_COOLDOWN_MS = 5000L;
   private static final int ALL_MODE_CONFIRM_TIMES = 3;
   private static final int PENDING_MARK_TIMEOUT = 60;
   private static final int SHULKER_REOPEN_DELAY = 15;
   private static final int MAX_SHULKER_TASK_ATTEMPTS = 3;
   private static final long DESTROYED_BOX_RESET_MS = 3000L;
   private static final double DESTROYED_BOX_WATCH_RANGE = 32.0;
   private int shulkerReopenTimer = 0;
   private net.minecraft.core.BlockPos pendingShulkerOpen = null;
   private int shulkerTaskAttempts = 0;
   private final PositionCache positionCache = new PositionCache(5000L);
   private final Set<net.minecraft.world.level.block.Block> targetBlockLookup = new HashSet<>();
   private final MutableBlockPos scanMutable = new MutableBlockPos();
   private net.minecraft.core.BlockPos scanBest = null;
   private double scanBestDistSq = Double.MAX_VALUE;
   private net.minecraft.core.BlockPos scanPlayerPos = null;
   private static final List<net.minecraft.world.item.Item> SHOVEL_PRIORITY = List.of(
      net.minecraft.world.item.Items.NETHERITE_SHOVEL, net.minecraft.world.item.Items.DIAMOND_SHOVEL, net.minecraft.world.item.Items.IRON_SHOVEL, net.minecraft.world.item.Items.STONE_SHOVEL, net.minecraft.world.item.Items.WOODEN_SHOVEL
   );
   private static final List<net.minecraft.world.item.Item> PICKAXE_PRIORITY = List.of(
      net.minecraft.world.item.Items.NETHERITE_PICKAXE, net.minecraft.world.item.Items.DIAMOND_PICKAXE, net.minecraft.world.item.Items.IRON_PICKAXE, net.minecraft.world.item.Items.STONE_PICKAXE, net.minecraft.world.item.Items.WOODEN_PICKAXE
   );
   private static final List<net.minecraft.world.item.Item> AXE_PRIORITY = List.of(
      net.minecraft.world.item.Items.NETHERITE_AXE, net.minecraft.world.item.Items.DIAMOND_AXE, net.minecraft.world.item.Items.IRON_AXE, net.minecraft.world.item.Items.STONE_AXE, net.minecraft.world.item.Items.WOODEN_AXE
   );
   private static final List<net.minecraft.world.item.Item> HOE_PRIORITY = List.of(
      net.minecraft.world.item.Items.NETHERITE_HOE, net.minecraft.world.item.Items.DIAMOND_HOE, net.minecraft.world.item.Items.IRON_HOE, net.minecraft.world.item.Items.STONE_HOE, net.minecraft.world.item.Items.WOODEN_HOE
   );

   public AutoMiner() {
      super(CATEGORY_MIKU_BUILD, "挖沙挖一切", "自动挖掘指定方块，支持背包管理和工具更换");
   }

   public void resetToolShulkerSelection() {
      this.toolShulkerPos = null;
      this.currentState = AutoMiner.MinerState.WAITING_TOOL_SELECTION;
      ChatUtils.sendMsg("工具潜影盒选择已重置，请重新用按键标记（或填坐标）指定工具潜影盒！");
   }

   public void onActivate() {
      super.onActivate();
      if (!BaritoneUtils.IS_AVAILABLE) {
         this.error("Baritone 不可用！", new Object[0]);
         this.toggle();
      } else {
         this.currentState = AutoMiner.MinerState.WAITING_TOOL_SELECTION;
         this.tickTimer = 0;
         this.lastMiningPos = null;
         this.currentTarget = null;
         this.toolShulkerPos = null;
         this.protectedShulkerBoxes.clear();
         this.storageShulkerPositions.clear();
         this.knownStorageBoxes.clear();
         this.fullStorageBoxes.clear();
         this.boxFullConfirmCount.clear();
         this.boxRecheckTime.clear();
         this.allFullRechecked.clear();
         this.destroyedFullBoxes.clear();
         this.storageBoxStatus.clear();
         this.pendingStorageMark = null;
         this.pendingStorageMarkTimer = 0;
         this.currentStorageShulker = null;
         this.extractStallUntil = 0L;
         this.shulkerInteractionTimer = 0;
         this.waitingForShulkerOpen = false;
         this.shulkerReopenTimer = 0;
         this.pendingShulkerOpen = null;
         this.shulkerTaskAttempts = 0;
         this.positionCache.startCleanupThread();
         if (this.toolMarkMode.get() == AutoMiner.MarkMode.COORDINATE) {
            net.minecraft.core.BlockPos pos = this.parsePos((String)this.toolShulkerCoord.get());
            if (pos != null) {
               this.toolShulkerPos = pos;
               this.currentState = AutoMiner.MinerState.MINING;
               ChatUtils.sendMsg("工具潜影盒坐标已设置: " + pos.toShortString());
            } else {
               this.warning("工具潜影盒坐标无效，请填 x y z 或 x,y,z", new Object[0]);
            }
         }

         this.refreshStorageShulkersFromCoordinates();
         this.scanAndProtectShulkerBoxes();
         if (this.toolShulkerPos != null) {
            this.updateProtectedBlocks();
         }

         ChatUtils.sendMsg("自动挖掘模块已启动");
         if (this.storageMarkMode.get() == AutoMiner.MarkMode.COORDINATE) {
            ChatUtils.sendMsg("已从坐标列表读取 " + this.storageShulkerPositions.size() + " 个储存潜影盒");
         } else {
            ChatUtils.sendMsg("准星对准储存潜影盒按 " + this.storageMarkKey.get() + " 可标记储存盒");
         }

         if (this.currentState == AutoMiner.MinerState.WAITING_TOOL_SELECTION) {
            if (this.toolMarkMode.get() == AutoMiner.MarkMode.KEY) {
               ChatUtils.sendMsg("准星对准工具潜影盒按 " + this.toolMarkKey.get() + " 可标记工具盒");
            } else {
               ChatUtils.sendMsg("请在下面把「工具盒坐标」填成工具潜影盒的坐标");
            }
         } else {
            ChatUtils.sendMsg("开始自动挖掘！");
         }
      }
   }

   public void onDeactivate() {
      if (BaritoneUtils.IS_AVAILABLE) {
         this.cancelBaritone();
         this.clearProtectedBlocks();
      }

      this.positionCache.shutdown();
      this.protectedShulkerBoxes.clear();
      this.pendingStorageMark = null;
      this.pendingStorageMarkTimer = 0;
      this.waitingForShulkerOpen = false;
      this.shulkerReopenTimer = 0;
      this.pendingShulkerOpen = null;
      this.shulkerTaskAttempts = 0;
      this.destroyedFullBoxes.clear();
      ChatUtils.sendMsg("自动挖掘模块已停止");
   }

   @EventHandler
   private void onKey(KeyInputEvent event) {
      if (event.action == KeyAction.Press) {
         if (this.mc.gui.screen() == null) {
            if (this.toolMarkMode.get() == AutoMiner.MarkMode.KEY && ((Keybind)this.toolMarkKey.get()).isPressed()) {
               net.minecraft.core.BlockPos pos = this.getCrosshairShulkerPos();
               if (pos == null) {
                  this.warning("请把准星对准潜影盒，再按工具盒标记键！", new Object[0]);
               } else {
                  this.setToolShulkerBox(pos);
                  if (this.currentState == AutoMiner.MinerState.WAITING_TOOL_SELECTION) {
                     this.currentState = AutoMiner.MinerState.MINING;
                  }

                  this.updateProtectedBlocks();
                  ChatUtils.sendMsg("工具潜影盒已标记: " + pos.toShortString() + "（已标蓝）");
                  ChatUtils.sendMsg("开始自动挖掘！");
               }
            }

            if (this.storageMarkMode.get() == AutoMiner.MarkMode.KEY && ((Keybind)this.storageMarkKey.get()).isPressed()) {
               net.minecraft.core.BlockPos pos = this.getCrosshairShulkerPos();
               if (pos == null) {
                  this.warning("请把准星对准潜影盒，再按储存盒标记键！", new Object[0]);
               } else if (pos.equals(this.toolShulkerPos)) {
                  this.warning("这是工具潜影盒，不能当作储存盒！", new Object[0]);
               } else {
                  this.startStorageMarkInspection(pos);
               }
            }
         }
      }
   }

   private void startStorageMarkInspection(net.minecraft.core.BlockPos pos) {
      if (this.mc.player != null && this.mc.level != null) {
         if (this.pendingStorageMark != null) {
            net.minecraft.core.BlockPos pending = this.pendingStorageMark;
            this.warning("正在识别 " + pending.toShortString() + "，请等它看完再标记下一个", new Object[0]);
         } else if (!this.mc.player.blockPosition().closerThan(pos, 5.0)) {
            this.warning("离这个潜影盒太远，开不了界面识别", new Object[0]);
         } else {
            this.pendingStorageMark = pos;
            this.pendingStorageMarkTimer = 0;
            this.shulkerTaskAttempts = 0;
            this.knownStorageBoxes.add(pos);
            if (this.storageShulkerPositions.contains(pos)) {
               ChatUtils.sendMsg("该潜影盒已在储存列表里，重新打开做一次界面识别：" + pos.toShortString());
            } else {
               ChatUtils.sendMsg("正在打开 " + pos.toShortString() + " 做界面识别…");
            }

            this.requestShulkerOpen(pos);
         }
      }
   }

   private void finishStorageMarkInspection(net.minecraft.core.BlockPos pos, AutoMiner.BoxInspection result) {
      this.pendingStorageMark = null;
      this.pendingStorageMarkTimer = 0;
      this.waitingForShulkerOpen = false;
      this.shulkerInteractionTimer = 0;
      if (result != null && result.full()) {
         int times = this.boxFullConfirmCount.getOrDefault(pos, 0);
         this.warning(times > 0 ? "这个盒子已经满了，第 " + times + " 次确认，不加入储存列表；等它清空后可以再标记。" : "这个盒子已经满了，不加入储存列表；等它清空后可以再标记。", new Object[0]);
      } else if (result != null && result.hasOther()) {
         ChatUtils.sendMsg("储存盒已标记: " + pos.toShortString() + "（里面有其他物品，存沙子前会先取出来放进背包；共 " + this.storageShulkerPositions.size() + " 个）");
      } else {
         ChatUtils.sendMsg("储存盒已标记: " + pos.toShortString() + "（共 " + this.storageShulkerPositions.size() + " 个）");
      }

      this.closeShulkerBox();
   }

   private net.minecraft.core.BlockPos getCrosshairShulkerPos() {
      if (this.mc.level == null) {
         return null;
      } else if (this.mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult bhr) {
         net.minecraft.core.BlockPos var3 = bhr.getBlockPos();
         return this.mc.level.getBlockState(var3).getBlock() instanceof net.minecraft.world.level.block.ShulkerBoxBlock ? var3 : null;
      } else {
         return null;
      }
   }

   private net.minecraft.core.BlockPos parsePos(String raw) {
      if (raw == null) {
         return null;
      } else {
         String[] parts = raw.trim().split("[\\s,]+");
         if (parts.length != 3) {
            return null;
         } else {
            try {
               return new net.minecraft.core.BlockPos(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()), Integer.parseInt(parts[2].trim()));
            } catch (NumberFormatException var4) {
               return null;
            }
         }
      }
   }

   private void setToolShulkerBox(net.minecraft.core.BlockPos pos) {
      this.toolShulkerPos = pos;
      if (pos != null) {
         this.knownStorageBoxes.remove(pos);
         this.fullStorageBoxes.remove(pos);
         this.storageBoxStatus.remove(pos);
         this.boxFullConfirmCount.remove(pos);
         if (this.storageShulkerPositions.remove(pos)) {
            ChatUtils.sendMsg("该潜影盒已在储存列表中，标记为工具盒后已从储存列表移除");
         }
      }
   }

   private boolean isToolShulker(net.minecraft.core.BlockPos pos) {
      return pos != null && pos.equals(this.toolShulkerPos);
   }

   private void refreshStorageShulkersFromCoordinates() {
      if (this.storageMarkMode.get() == AutoMiner.MarkMode.COORDINATE) {
         for (String s : this.storageShulkerCoords.get()) {
            net.minecraft.core.BlockPos pos = this.parsePos(s);
            if (pos != null) {
               if (this.isToolShulker(pos)) {
                  this.warning("坐标 " + pos.toShortString() + " 是工具潜影盒，已跳过，不算储存盒", new Object[0]);
               } else {
                  this.knownStorageBoxes.add(pos);
                  this.storageShulkerPositions.add(pos);
               }
            }
         }
      }
   }

   private net.minecraft.core.BlockPos findNearestMarkedStorageShulker() {
      return this.findNearestMarkedStorageShulker(null);
   }

   private net.minecraft.core.BlockPos findNearestMarkedStorageShulker(net.minecraft.core.BlockPos exclude) {
      if (this.mc.level != null && this.mc.player != null) {
         net.minecraft.core.BlockPos playerPos = this.mc.player.blockPosition();
         net.minecraft.core.BlockPos best = null;
         double bestDistance = Double.MAX_VALUE;

         for (net.minecraft.core.BlockPos pos : new ArrayList<>(this.storageShulkerPositions)) {
            if (!pos.equals(this.toolShulkerPos) && !pos.equals(exclude) && !this.isFullStorageBoxFromWorld(pos) && this.isShulkerBlock(pos)) {
               double distance = playerPos.distSqr(pos);
               if (distance < bestDistance) {
                  bestDistance = distance;
                  best = pos;
               }
            }
         }

         return best;
      } else {
         return null;
      }
   }

   private List<net.minecraft.core.BlockPos> markedNeighborStorageBoxes(net.minecraft.core.BlockPos center) {
      List<net.minecraft.core.BlockPos> neighbors = new ArrayList<>();
      if (center != null && this.mc.level != null) {
         for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
               if (dx != 0 || dz != 0) {
                  net.minecraft.core.BlockPos pos = center.offset(dx, 0, dz);
                  if (!pos.equals(this.toolShulkerPos) && this.knownStorageBoxes.contains(pos) && this.isShulkerBlock(pos)) {
                     neighbors.add(pos);
                  }
               }
            }
         }

         return neighbors;
      } else {
         return neighbors;
      }
   }

   private net.minecraft.core.BlockPos findNeighborStorageBox(net.minecraft.core.BlockPos center) {
      for (net.minecraft.core.BlockPos pos : this.markedNeighborStorageBoxes(center)) {
         if (!this.fullStorageBoxes.contains(pos)) {
            return pos;
         }
      }

      return null;
   }

   private AutoMiner.BoxStatus queryBoxStateFromWorld(net.minecraft.core.BlockPos pos) {
      if (pos != null && this.mc.level != null) {
         if (this.fullCheckMode.get() == AutoMiner.FullCheckMode.CACHE) {
            return this.storageBoxStatus.get(pos);
         } else if (this.mc.level.getBlockState(pos).getBlock() instanceof net.minecraft.world.level.block.ShulkerBoxBlock) {
            return this.storageBoxStatus.get(pos);
         } else if (this.fullStorageBoxes.contains(pos)) {
            this.noteDestroyedFullBox(pos);
            this.knownStorageBoxes.add(pos);
            this.storageShulkerPositions.remove(pos);
            return null;
         } else {
            boolean removed = this.knownStorageBoxes.remove(pos) | this.storageShulkerPositions.remove(pos);
            this.fullStorageBoxes.remove(pos);
            this.storageBoxStatus.remove(pos);
            this.boxFullConfirmCount.remove(pos);
            this.boxRecheckTime.remove(pos);
            if (removed) {
               ChatUtils.sendMsg("储存盒 " + pos.toShortString() + " 已经不在世界上了，已从储存盒列表移除");
            }

            return null;
         }
      } else {
         return null;
      }
   }

   private boolean isFullStorageBoxFromWorld(net.minecraft.core.BlockPos pos) {
      this.queryBoxStateFromWorld(pos);
      return this.fullStorageBoxes.contains(pos);
   }

   private net.minecraft.core.BlockPos findRecheckTarget() {
      AutoMiner.FullCheckMode mode = (AutoMiner.FullCheckMode)this.fullCheckMode.get();
      if (mode != AutoMiner.FullCheckMode.WORLD && mode != AutoMiner.FullCheckMode.CACHE) {
         if (this.mc.level != null && this.mc.player != null) {
            long cooldown = mode == AutoMiner.FullCheckMode.ALL ? 5000L : 30000L;
            long now = System.currentTimeMillis();
            net.minecraft.core.BlockPos playerPos = this.mc.player.blockPosition();
            net.minecraft.core.BlockPos best = null;
            double bestDistance = Double.MAX_VALUE;

            for (net.minecraft.core.BlockPos pos : this.fullStorageBoxes) {
               if (!pos.equals(this.toolShulkerPos) && this.isShulkerBlock(pos) && !this.allFullRechecked.contains(pos)) {
                  Long last = this.boxRecheckTime.get(pos);
                  if (last == null || now - last >= cooldown) {
                     double distance = playerPos.distSqr(pos);
                     if (distance < bestDistance) {
                        bestDistance = distance;
                        best = pos;
                     }
                  }
               }
            }

            if (best != null) {
               this.boxRecheckTime.put(best, now);
            }

            return best;
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   private boolean allKnownStorageBoxesFull() {
      if (this.knownStorageBoxes.isEmpty()) {
         return false;
      } else {
         int existing = 0;

         for (net.minecraft.core.BlockPos pos : this.knownStorageBoxes) {
            if (!pos.equals(this.toolShulkerPos) && this.isShulkerBlock(pos)) {
               existing++;
               if (!this.fullStorageBoxes.contains(pos)) {
                  return false;
               }
            }
         }

         return existing > 0;
      }
   }

   private void disableBecauseAllBoxesFull() {
      this.error("所有储存盒都是满的，没地方存放物品，已自动关闭模块。清空一个盒子（或重新标记）后再开启。", new Object[0]);
      ChatUtils.sendMsg("检查完所有存储盒：全部是满的，自动挖掘已停止");
      if (this.isShulkerBoxOpen()) {
         this.closeShulkerBox();
      }

      if (this.isActive()) {
         this.toggle();
      }
   }

   private void watchDestroyedFullBoxes() {
      if (!this.fullStorageBoxes.isEmpty() && this.mc.player != null && this.mc.level != null) {
         net.minecraft.core.BlockPos playerPos = this.mc.player.blockPosition();

         for (net.minecraft.core.BlockPos pos : this.fullStorageBoxes) {
            if (pos.closerThan(playerPos, 32.0) && this.mc.level.hasChunkAt(pos) && !this.isShulkerBlock(pos)) {
               this.noteDestroyedFullBox(pos);
            }
         }
      }
   }

   private void noteDestroyedFullBox(net.minecraft.core.BlockPos pos) {
      if (pos != null) {
         if (this.destroyedFullBoxes.putIfAbsent(pos, System.currentTimeMillis()) == null) {
            ChatUtils.sendMsg("已满的储存盒 " + pos.toShortString() + " 被挖掉了，3 秒后重置这个坐标的状态");
         }
      }
   }

   private void tickDestroyedFullBoxResets() {
      if (!this.destroyedFullBoxes.isEmpty()) {
         long now = System.currentTimeMillis();
         Iterator<Entry<net.minecraft.core.BlockPos, Long>> it = this.destroyedFullBoxes.entrySet().iterator();

         while (it.hasNext()) {
            Entry<net.minecraft.core.BlockPos, Long> entry = it.next();
            Long since = entry.getValue();
            if (since != null && now - since >= 3000L) {
               net.minecraft.core.BlockPos pos = entry.getKey();
               it.remove();
               this.resetDestroyedBoxState(pos);
            }
         }
      }
   }

   private void resetDestroyedBoxState(net.minecraft.core.BlockPos pos) {
      if (pos != null && !pos.equals(this.toolShulkerPos)) {
         this.fullStorageBoxes.remove(pos);
         this.storageBoxStatus.remove(pos);
         this.boxFullConfirmCount.remove(pos);
         this.boxRecheckTime.remove(pos);
         this.allFullRechecked.remove(pos);
         this.knownStorageBoxes.add(pos);
         if (this.isShulkerBlock(pos)) {
            this.storageShulkerPositions.add(pos);
            ChatUtils.sendMsg("储存盒 " + pos.toShortString() + " 的坐标状态已重置，会重新检查里面的内容");
         } else {
            ChatUtils.sendMsg("储存盒 " + pos.toShortString() + " 的坐标状态已重置，放回潜影盒后会自动重新识别");
         }
      }
   }

   private void restoreKnownStorageBoxes() {
      if (!this.knownStorageBoxes.isEmpty() && this.mc.player != null && this.mc.level != null) {
         net.minecraft.core.BlockPos playerPos = this.mc.player.blockPosition();

         for (net.minecraft.core.BlockPos pos : this.knownStorageBoxes) {
            if (!pos.equals(this.toolShulkerPos)
               && !this.fullStorageBoxes.contains(pos)
               && !this.storageShulkerPositions.contains(pos)
               && pos.closerThan(playerPos, 32.0)
               && this.mc.level.hasChunkAt(pos)
               && this.isShulkerBlock(pos)) {
               this.storageShulkerPositions.add(pos);
               ChatUtils.sendMsg("储存盒 " + pos.toShortString() + " 又出现了，已重新纳入储存盒列表");
            }
         }
      }
   }

   @EventHandler
   private void onTick(Pre event) {
      if (this.mc.player != null && this.mc.level != null) {
         this.watchDestroyedFullBoxes();
         this.tickDestroyedFullBoxResets();
         this.restoreKnownStorageBoxes();
         this.inspectOpenShulkerBox();
         if (this.shulkerReopenTimer > 0) {
            this.shulkerReopenTimer--;
         }

         if (this.pendingShulkerOpen != null) {
            if (this.shulkerReopenTimer <= 0) {
               net.minecraft.core.BlockPos target = this.pendingShulkerOpen;
               this.pendingShulkerOpen = null;
               if (this.isShulkerBlock(target)) {
                  this.openShulkerBox(target);
                  this.waitingForShulkerOpen = true;
                  this.shulkerInteractionTimer = 0;
                  this.info("冷却结束，打开下一个潜影盒: " + target.toShortString(), new Object[0]);
               }

               this.tickTimer = this.currentState == AutoMiner.MinerState.STORING_ITEMS ? this.fillMoveDelay() : (Integer)this.delay.get();
            }
         } else if (this.pendingStorageMark != null) {
            this.pendingStorageMarkTimer++;
            if (this.pendingStorageMarkTimer > 60) {
               net.minecraft.core.BlockPos stuck = this.pendingStorageMark;
               this.pendingStorageMarkTimer = 0;
               this.waitingForShulkerOpen = false;
               this.shulkerInteractionTimer = 0;
               if (!this.handleShulkerTaskTimeout("盒子 " + stuck.toShortString() + " 的界面识别")) {
                  this.pendingStorageMark = null;
               } else {
                  this.requestShulkerOpen(stuck);
               }
            }
         } else if (this.currentState != AutoMiner.MinerState.WAITING_TOOL_SELECTION) {
            if (this.tickTimer > 0) {
               this.tickTimer--;
            } else {
               switch (this.currentState) {
                  case MINING:
                     this.handleMining();
                  case WAITING_FOR_TARGET_BOX:
                  default:
                     break;
                  case INVENTORY_FULL:
                     this.handleInventoryFull();
                     break;
                  case TOOL_BROKEN:
                     this.handleToolBroken();
                     break;
                  case GOING_TO_STORAGE:
                     this.handleGoingToStorage();
                     break;
                  case GOING_TO_TOOLS:
                     this.handleGoingToTools();
                     break;
                  case STORING_ITEMS:
                     this.handleStoringItems();
                     break;
                  case GETTING_TOOLS:
                     this.handleGettingTools();
                     break;
                  case RETURNING:
                     this.handleReturning();
               }

               this.tickTimer = this.currentState == AutoMiner.MinerState.STORING_ITEMS ? this.fillMoveDelay() : (Integer)this.delay.get();
            }
         }
      }
   }

   private int fillMoveDelay() {
      return this.withJitter((Integer)this.fillDelay.get());
   }

   private int withJitter(int base) {
      int pct = (Integer)this.fillDelayJitter.get();
      if (pct <= 0) {
         return base;
      } else {
         int maxExtra = Math.round(base * (pct / 100.0F));
         return maxExtra > 0 ? base + this.random.nextInt(maxExtra + 1) : base;
      }
   }

   private void inspectOpenShulkerBox() {
      if (this.mc.player != null && this.mc.level != null) {
         if (this.mc.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen) {
            if (this.currentState != AutoMiner.MinerState.GETTING_TOOLS) {
               net.minecraft.world.inventory.AbstractContainerMenu handler = this.mc.player.containerMenu;
               if (handler != null) {
                  net.minecraft.core.BlockPos pos = this.resolveOpenShulkerPos();
                  if (pos != null) {
                     AutoMiner.BoxInspection result = this.inspect(handler);
                     this.updateBoxStatus(pos, result);
                     if (this.pendingStorageMark != null && this.pendingStorageMark.equals(pos)) {
                        this.finishStorageMarkInspection(pos, result);
                     }
                  }
               }
            }
         }
      }
   }

   private net.minecraft.core.BlockPos resolveOpenShulkerPos() {
      if (this.mc.player == null) {
         return null;
      } else {
         net.minecraft.core.BlockPos playerPos = this.mc.player.blockPosition();
         if (this.pendingStorageMark != null && this.isShulkerBlock(this.pendingStorageMark) && playerPos.closerThan(this.pendingStorageMark, 8.0)) {
            return this.pendingStorageMark;
         } else if (this.currentStorageShulker != null
            && this.isShulkerBlock(this.currentStorageShulker)
            && playerPos.closerThan(this.currentStorageShulker, 8.0)) {
            return this.currentStorageShulker;
         } else {
            net.minecraft.core.BlockPos best = null;
            double bestDistance = Double.MAX_VALUE;

            for (net.minecraft.core.BlockPos pos : this.storageShulkerPositions) {
               if (!pos.equals(this.toolShulkerPos) && this.isShulkerBlock(pos)) {
                  double distance = playerPos.distSqr(pos);
                  if (distance < bestDistance) {
                     bestDistance = distance;
                     best = pos;
                  }
               }
            }

            for (net.minecraft.core.BlockPos posx : this.storageBoxStatus.keySet()) {
               if (!posx.equals(this.toolShulkerPos) && this.isShulkerBlock(posx)) {
                  double distance = playerPos.distSqr(posx);
                  if (distance < bestDistance) {
                     bestDistance = distance;
                     best = posx;
                  }
               }
            }

            return bestDistance <= 16.0 ? best : null;
         }
      }
   }

   private AutoMiner.BoxInspection inspect(net.minecraft.world.inventory.AbstractContainerMenu handler) {
      if (handler == null) {
         return null;
      } else {
         int containerSlots = handler.slots.size() - 36;
         if (containerSlots <= 0) {
            return null;
         } else {
            boolean allEmpty = true;
            boolean hasEmpty = false;
            boolean hasOther = false;
            boolean canAcceptMore = false;

            for (int i = 0; i < containerSlots; i++) {
               net.minecraft.world.item.ItemStack stack = handler.getSlot(i).getItem();
               if (stack.isEmpty()) {
                  hasEmpty = true;
               } else {
                  allEmpty = false;
                  if (this.isTargetItem(stack)) {
                     if (stack.getCount() < stack.getMaxStackSize()) {
                        canAcceptMore = true;
                     }
                  } else {
                     hasOther = true;
                  }
               }
            }

            boolean noRoom = !hasEmpty && !canAcceptMore;
            AutoMiner.BoxStatus status;
            if (allEmpty) {
               status = AutoMiner.BoxStatus.EMPTY;
            } else if (noRoom) {
               status = AutoMiner.BoxStatus.FULL;
            } else {
               status = AutoMiner.BoxStatus.PARTIAL;
            }

            return new AutoMiner.BoxInspection(status, status == AutoMiner.BoxStatus.FULL, hasOther);
         }
      }
   }

   private void updateBoxStatus(net.minecraft.core.BlockPos pos, net.minecraft.world.inventory.AbstractContainerMenu handler) {
      this.updateBoxStatus(pos, this.inspect(handler));
   }

   private void updateBoxStatus(net.minecraft.core.BlockPos pos, AutoMiner.BoxInspection result) {
      if (pos != null && result != null) {
         AutoMiner.BoxStatus status = result.status();
         AutoMiner.BoxStatus old = this.storageBoxStatus.put(pos, status);
         if (this.knownStorageBoxes.contains(pos)) {
            if (status == AutoMiner.BoxStatus.FULL) {
               if (this.fullCheckMode.get() == AutoMiner.FullCheckMode.ALL) {
                  int times = this.boxFullConfirmCount.merge(pos, 1, Integer::sum);
                  if (times < 3) {
                     ChatUtils.sendMsg("储存盒 " + pos.toShortString() + " 像是已经满了，第 " + times + "/3 次确认（缓存已刷新），还会再开门复核");
                     return;
                  }
               }

               boolean wasNotFull = this.fullStorageBoxes.add(pos);
               boolean wasInList = this.storageShulkerPositions.remove(pos);
               if (wasNotFull || wasInList) {
                  ChatUtils.sendMsg("储存盒 " + pos.toShortString() + " 已经满了，已从储存盒列表移除，之后不再去访问它");
               }
            } else {
               this.fullStorageBoxes.remove(pos);
               this.boxFullConfirmCount.remove(pos);
               this.allFullRechecked.remove(pos);
               boolean wasOut = !this.storageShulkerPositions.contains(pos);
               if (this.storageShulkerPositions.add(pos) && wasOut) {
                  ChatUtils.sendMsg("储存盒 " + pos.toShortString() + (result.hasOther() ? " 里面有其他物品，已保留在储存盒列表（存沙子前会先取出）" : " 还有空位，已重新加入储存盒列表"));
               }
            }
         }

         if (old != status) {
            if (status == AutoMiner.BoxStatus.FULL) {
               ChatUtils.sendMsg("储存盒 " + pos.toShortString() + " 已满，已标红");
            } else if (status == AutoMiner.BoxStatus.EMPTY) {
               ChatUtils.sendMsg("储存盒 " + pos.toShortString() + " 是空盒，已标绿");
            } else {
               ChatUtils.sendMsg("储存盒 " + pos.toShortString() + " 没满/有其他物品，已标黄");
            }
         }
      }
   }

   private boolean isShulkerBlock(net.minecraft.core.BlockPos pos) {
      return pos != null && this.mc.level != null && this.mc.level.getBlockState(pos).getBlock() instanceof net.minecraft.world.level.block.ShulkerBoxBlock;
   }

   @EventHandler
   private void onRender(Render3DEvent event) {
      if ((Boolean)this.renderStorageBox.get()) {
         if (this.mc.level != null && this.mc.player != null) {
            if (!this.storageBoxStatus.isEmpty() || this.toolShulkerPos != null) {
               double maxDistance = ((Integer)this.renderDistance.get()).intValue();
               double maxDistanceSq = maxDistance * maxDistance;
               net.minecraft.core.BlockPos playerPos = this.mc.player.blockPosition();

               for (Entry<net.minecraft.core.BlockPos, AutoMiner.BoxStatus> entry : this.storageBoxStatus.entrySet()) {
                  AutoMiner.BoxStatus status = entry.getValue();
                  net.minecraft.core.BlockPos pos = entry.getKey();
                  if (!pos.equals(this.toolShulkerPos) && this.isShulkerBlock(pos) && !(playerPos.distSqr(pos) > maxDistanceSq)) {
                     SettingColor color = switch (status) {
                        case EMPTY -> (SettingColor)this.emptyColor.get();
                        case PARTIAL -> (SettingColor)this.otherItemColor.get();
                        case FULL -> (SettingColor)this.fullColor.get();
                     };
                     event.renderer.box(pos, this.sideOf(color), color, (ShapeMode)this.renderShapeMode.get(), 0);
                  }
               }

               if (this.toolShulkerPos != null && this.isShulkerBlock(this.toolShulkerPos) && playerPos.distSqr(this.toolShulkerPos) <= maxDistanceSq) {
                  SettingColor color = (SettingColor)this.toolBoxColor.get();
                  event.renderer.box(this.toolShulkerPos, this.sideOf(color), color, (ShapeMode)this.renderShapeMode.get(), 0);
               }
            }
         }
      }
   }

   private SettingColor sideOf(SettingColor color) {
      return new SettingColor(color.r, color.g, color.b, 40);
   }

   private void handleMining() {
      if (this.isInventoryFull()) {
         this.currentState = AutoMiner.MinerState.INVENTORY_FULL;
      } else if (this.needNewTool()) {
         this.currentState = AutoMiner.MinerState.TOOL_BROKEN;
      } else {
         net.minecraft.core.BlockPos targetPos = this.findNearestTargetBlock();
         if (targetPos != null) {
            if (this.currentTarget == null || !this.currentTarget.equals(targetPos)) {
               if ((Boolean)this.packetMine.get() && !this.positionCache.isInCache(targetPos)) {
                  net.minecraft.core.BlockPos playerPos = this.mc.player.blockPosition();
                  if (targetPos.getY() >= playerPos.getY() && playerPos.distSqr(targetPos) < 17.64) {
                     this.positionCache.addToCache(targetPos);
                     BlockUtils.breakBlock(targetPos, true);
                  }
               }

               this.currentTarget = targetPos;
               this.lastMiningPos = this.mc.player.blockPosition();
               BaritoneAPI.getProvider().getPrimaryBaritone().getMineProcess().mine(this.targetBlocks.get().toArray(new net.minecraft.world.level.block.Block[0]));
            }
         } else {
            long now = System.currentTimeMillis();
            if (now - this.lastNoTargetWarn > 5000L) {
               this.lastNoTargetWarn = now;
               ChatUtils.sendMsg("附近没有找到目标方块");
            }
         }
      }
   }

   private void handleInventoryFull() {
      ChatUtils.sendMsg("背包已满，搜索存储潜影盒");
      net.minecraft.core.BlockPos storageShulker = this.findSandStorageShulkerBox();
      if (storageShulker != null && this.fullStorageBoxes.contains(storageShulker)) {
         storageShulker = null;
      }

      if (storageShulker == null && this.allKnownStorageBoxesFull()) {
         net.minecraft.core.BlockPos recheck = this.findRecheckTarget();
         if (recheck == null) {
            this.disableBecauseAllBoxesFull();
            return;
         }

         storageShulker = recheck;
         this.allFullRechecked.add(recheck);
         ChatUtils.sendMsg("已知储存盒都满了，最后复核 " + recheck.toShortString());
      }

      if (storageShulker != null) {
         this.currentStorageShulker = storageShulker;
         this.currentState = AutoMiner.MinerState.GOING_TO_STORAGE;
         this.shulkerTaskAttempts = 0;
         this.cancelBaritone();
         this.setGoalNearShulker(storageShulker);
         ChatUtils.sendMsg("找到存储潜影盒: " + storageShulker.toShortString());
      } else {
         long now = System.currentTimeMillis();
         if (now - this.lastNoStorageWarn > 10000L) {
            this.lastNoStorageWarn = now;
            this.error("没有可用的存储潜影盒！请先标记至少一个储存盒，或清空一个满盒。", new Object[0]);
         }

         this.currentState = AutoMiner.MinerState.MINING;
      }
   }

   private void handleToolBroken() {
      AutoMiner.ToolType selectedToolType = (AutoMiner.ToolType)this.toolType.get();
      ChatUtils.sendMsg("工具耐久度过低，前往工具潜影盒获取" + selectedToolType.getDisplayName());
      if (this.toolShulkerPos != null) {
         this.currentState = AutoMiner.MinerState.GOING_TO_TOOLS;
         this.shulkerTaskAttempts = 0;
         this.setGoalNearShulker(this.toolShulkerPos);
         ChatUtils.sendMsg("前往工具存储潜影盒: " + this.toolShulkerPos.toShortString());
      } else {
         this.error("工具潜影盒位置未设置！请重新启动模块并标记工具潜影盒。", new Object[0]);
         this.currentState = AutoMiner.MinerState.WAITING_TOOL_SELECTION;
         ChatUtils.sendMsg("请用按键（或坐标）标记工具潜影盒！");
      }
   }

   private void handleGoingToStorage() {
      if (this.currentStorageShulker != null && this.fullStorageBoxes.contains(this.currentStorageShulker)) {
         net.minecraft.core.BlockPos neighbor = this.findNeighborStorageBox(this.currentStorageShulker);
         if (neighbor != null) {
            ChatUtils.sendMsg("储存盒 " + this.currentStorageShulker.toShortString() + " 已满，改去紧挨着的储存盒 " + neighbor.toShortString());
            this.currentStorageShulker = neighbor;
            this.cancelBaritone();
            this.setGoalNearShulker(neighbor);
         }
      }

      if (this.currentStorageShulker != null
         && this.isShulkerBlock(this.currentStorageShulker)
         && this.mc.player.blockPosition().closerThan(this.currentStorageShulker, 4.0)) {
         this.currentState = AutoMiner.MinerState.STORING_ITEMS;
         this.cancelBaritone();
      } else {
         net.minecraft.core.BlockPos nearbyShulker = this.findNearbyShulkerBox();
         if (nearbyShulker != null && !this.isToolShulker(nearbyShulker) && this.mc.player.blockPosition().closerThan(nearbyShulker, 4.0)) {
            this.currentStorageShulker = nearbyShulker;
            this.currentState = AutoMiner.MinerState.STORING_ITEMS;
            this.cancelBaritone();
         }
      }
   }

   private void handleGoingToTools() {
      if (this.toolShulkerPos != null && this.mc.player.blockPosition().closerThan(this.toolShulkerPos, 4.0)) {
         this.currentState = AutoMiner.MinerState.GETTING_TOOLS;
         this.cancelBaritone();
      }
   }

   private void handleStoringItems() {
      if (this.isShulkerBoxOpen()) {
         this.shulkerTaskAttempts = 0;
         this.storeSandToShulkerBox();
      } else {
         if (this.waitingForShulkerOpen) {
            this.shulkerInteractionTimer++;
            if (this.shulkerInteractionTimer <= 40) {
               return;
            }

            this.waitingForShulkerOpen = false;
            this.shulkerInteractionTimer = 0;
            if (!this.handleShulkerTaskTimeout("储存潜影盒打开")) {
               return;
            }
         }

         if (this.isShulkerBoxOpen()) {
            this.shulkerTaskAttempts = 0;
            this.storeSandToShulkerBox();
         } else {
            net.minecraft.core.BlockPos target = this.currentStorageShulker != null
                  && this.isShulkerBlock(this.currentStorageShulker)
                  && this.mc.player.blockPosition().closerThan(this.currentStorageShulker, 4.0)
               ? this.currentStorageShulker
               : this.findNearbyShulkerBox();
            if (target != null && !this.isToolShulker(target)) {
               this.currentStorageShulker = target;
               this.requestShulkerOpen(target);
            } else {
               this.error("附近没有可用的储存潜影盒（工具盒不能存沙子）！", new Object[0]);
               this.currentState = AutoMiner.MinerState.MINING;
            }
         }
      }
   }

   private void handleGettingTools() {
      if (this.isShulkerBoxOpen()) {
         this.shulkerTaskAttempts = 0;
         this.getToolFromShulkerBox();
      } else {
         if (this.waitingForShulkerOpen) {
            this.shulkerInteractionTimer++;
            if (this.shulkerInteractionTimer <= 40) {
               return;
            }

            this.waitingForShulkerOpen = false;
            this.shulkerInteractionTimer = 0;
            if (!this.handleShulkerTaskTimeout("工具潜影盒打开")) {
               return;
            }
         }

         if (this.isShulkerBoxOpen()) {
            this.shulkerTaskAttempts = 0;
            this.getToolFromShulkerBox();
         } else if (this.toolShulkerPos != null) {
            this.requestShulkerOpen(this.toolShulkerPos);
         } else {
            this.error("工具潜影盒位置未设置！", new Object[0]);
            this.currentState = AutoMiner.MinerState.WAITING_TOOL_SELECTION;
         }
      }
   }

   private void handleReturning() {
      if (this.lastMiningPos != null && this.mc.player.blockPosition().closerThan(this.lastMiningPos, 3.0)) {
         this.currentState = AutoMiner.MinerState.MINING;
         this.cancelBaritone();
         this.info("已返回挖掘位置", new Object[0]);
      }
   }

   private boolean isInventoryFull() {
      int emptySlots = 0;

      for (int i = 0; i < 36; i++) {
         if (this.mc.player.getInventory().getItem(i).isEmpty()) {
            if (++emptySlots > 2) {
               return false;
            }
         }
      }

      return true;
   }

   private boolean needNewTool() {
      AutoMiner.ToolType selectedToolType = (AutoMiner.ToolType)this.toolType.get();
      List<net.minecraft.world.item.Item> toolPriority = this.getToolPriorityList(selectedToolType);
      FindItemResult tool = InvUtils.find(itemStackx -> toolPriority.contains(itemStackx.getItem()));
      if (!tool.found()) {
         return true;
      } else {
         net.minecraft.world.item.ItemStack itemStack = this.mc.player.getInventory().getItem(tool.slot());
         if (!itemStack.isDamageableItem()) {
            return false;
         } else {
            int maxDamage = itemStack.getMaxDamage();
            int currentDamage = itemStack.getDamageValue();
            int remainingDurability = maxDamage - currentDamage;
            return remainingDurability <= (Integer)this.minDurability.get();
         }
      }
   }

   private net.minecraft.core.BlockPos findNearestTargetBlock() {
      net.minecraft.core.BlockPos playerPos = this.mc.player.blockPosition();
      int range = (Integer)this.miningRange.get();
      this.targetBlockLookup.clear();
      this.targetBlockLookup.addAll((Collection<? extends net.minecraft.world.level.block.Block>)this.targetBlocks.get());
      if (this.targetBlockLookup.isEmpty()) {
         return null;
      } else {
         this.scanBest = null;
         this.scanBestDistSq = Double.MAX_VALUE;
         this.scanPlayerPos = playerPos;
         int px = playerPos.getX();
         int py = playerPos.getY();
         int pz = playerPos.getZ();

         for (int r = 0; r <= range && (this.scanBest == null || !(this.scanBestDistSq <= (double)r * r)); r++) {
            for (int dx = -r; dx <= r; dx++) {
               int adx = Math.abs(dx);

               for (int dy = -r; dy <= r; dy++) {
                  int ady = Math.abs(dy);
                  if (Math.max(adx, ady) < r) {
                     this.scanTargetAt(px + dx, py + dy, pz - r);
                     this.scanTargetAt(px + dx, py + dy, pz + r);
                  } else {
                     for (int dz = -r; dz <= r; dz++) {
                        this.scanTargetAt(px + dx, py + dy, pz + dz);
                     }
                  }
               }
            }
         }

         this.scanPlayerPos = null;
         return this.scanBest;
      }
   }

   private void scanTargetAt(int x, int y, int z) {
      this.scanMutable.set(x, y, z);
      if (this.targetBlockLookup.contains(this.mc.level.getBlockState(this.scanMutable).getBlock())) {
         double distance = this.scanPlayerPos.distSqr(this.scanMutable);
         if (distance < this.scanBestDistSq) {
            this.scanBestDistSq = distance;
            this.scanBest = this.scanMutable.immutable();
         }
      }
   }

   private net.minecraft.core.BlockPos findSandStorageShulkerBox() {
      net.minecraft.core.BlockPos marked = this.findNearestMarkedStorageShulker();
      if (marked != null) {
         return marked;
      } else {
         net.minecraft.core.BlockPos playerPos = this.mc.player.blockPosition();
         int radius = (Integer)this.shulkerSearchRadius.get();
         int px = playerPos.getX();
         int py = playerPos.getY();
         int pz = playerPos.getZ();
         MutableBlockPos p = new MutableBlockPos();

         for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
               for (int z = -radius; z <= radius; z++) {
                  p.set(px + x, py + y, pz + z);
                  net.minecraft.world.level.block.Block block = this.mc.level.getBlockState(p).getBlock();
                  if (block instanceof net.minecraft.world.level.block.ShulkerBoxBlock && !p.equals(this.toolShulkerPos)) {
                     net.minecraft.core.BlockPos found = p.immutable();
                     if (!this.isFullStorageBoxFromWorld(found)) {
                        return found;
                     }
                  }
               }
            }
         }

         return null;
      }
   }

   private net.minecraft.core.BlockPos findNearbyShulkerBox() {
      net.minecraft.core.BlockPos playerPos = this.mc.player.blockPosition();

      for (net.minecraft.core.BlockPos marked : new ArrayList<>(this.storageShulkerPositions)) {
         if (!marked.equals(this.toolShulkerPos)
            && !this.isFullStorageBoxFromWorld(marked)
            && playerPos.closerThan(marked, 3.0)
            && this.mc.level.getBlockState(marked).getBlock() instanceof net.minecraft.world.level.block.ShulkerBoxBlock) {
            return marked;
         }
      }

      int px = playerPos.getX();
      int py = playerPos.getY();
      int pz = playerPos.getZ();
      MutableBlockPos p = new MutableBlockPos();

      for (int x = -3; x <= 3; x++) {
         for (int y = -3; y <= 3; y++) {
            for (int z = -3; z <= 3; z++) {
               p.set(px + x, py + y, pz + z);
               net.minecraft.world.level.block.Block block = this.mc.level.getBlockState(p).getBlock();
               if (block instanceof net.minecraft.world.level.block.ShulkerBoxBlock && !p.equals(this.toolShulkerPos)) {
                  net.minecraft.core.BlockPos found = p.immutable();
                  if (!this.isFullStorageBoxFromWorld(found)) {
                     return found;
                  }
               }
            }
         }
      }

      return null;
   }

   private net.minecraft.core.BlockPos findSafeStandPosNear(net.minecraft.core.BlockPos box) {
      if (box != null && this.mc.level != null && this.mc.player != null) {
         net.minecraft.core.BlockPos playerPos = this.mc.player.blockPosition();
         net.minecraft.core.BlockPos best = null;
         double bestKey = Double.MAX_VALUE;
         double bestPlayerDist = Double.MAX_VALUE;

         for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
               for (int dz = -2; dz <= 2; dz++) {
                  if (dx != 0 || dy != 0 || dz != 0) {
                     net.minecraft.core.BlockPos pos = box.offset(dx, dy, dz);
                     if (this.isSafeStandPos(pos)) {
                        double boxDist = box.distSqr(pos);
                        if (!(boxDist > 9.0)) {
                           double playerDist = playerPos.distSqr(pos);
                           double key = boxDist + (dy == 0 ? 0.0 : 10.0);
                           boolean closerToBox = key < bestKey - 0.01;
                           boolean sameDistance = Math.abs(key - bestKey) <= 0.01 && playerDist < bestPlayerDist;
                           if (closerToBox || sameDistance) {
                              bestKey = key;
                              bestPlayerDist = playerDist;
                              best = pos;
                           }
                        }
                     }
                  }
               }
            }
         }

         return best;
      } else {
         return null;
      }
   }

   private boolean isSafeStandPos(net.minecraft.core.BlockPos pos) {
      if (pos == null || this.mc.level == null) {
         return false;
      } else if (!this.isPassableAt(pos) || !this.isPassableAt(pos.above())) {
         return false;
      } else if (!this.isDangerousAt(pos) && !this.isDangerousAt(pos.above())) {
         net.minecraft.core.BlockPos below = pos.below();
         net.minecraft.world.level.block.state.BlockState belowState = this.mc.level.getBlockState(below);
         return belowState.getCollisionShape(this.mc.level, below).isEmpty() ? false : !this.isDangerousAt(below);
      } else {
         return false;
      }
   }

   private boolean isPassableAt(net.minecraft.core.BlockPos pos) {
      return this.mc.level.getBlockState(pos).getCollisionShape(this.mc.level, pos).isEmpty();
   }

   private boolean isDangerousAt(net.minecraft.core.BlockPos pos) {
      net.minecraft.world.level.block.Block block = this.mc.level.getBlockState(pos).getBlock();
      return block == net.minecraft.world.level.block.Blocks.LAVA
         || block == net.minecraft.world.level.block.Blocks.FIRE
         || block == net.minecraft.world.level.block.Blocks.SOUL_FIRE
         || block == net.minecraft.world.level.block.Blocks.CAMPFIRE
         || block == net.minecraft.world.level.block.Blocks.SOUL_CAMPFIRE
         || block == net.minecraft.world.level.block.Blocks.MAGMA_BLOCK
         || block == net.minecraft.world.level.block.Blocks.CACTUS
         || block == net.minecraft.world.level.block.Blocks.SWEET_BERRY_BUSH
         || block == net.minecraft.world.level.block.Blocks.COBWEB
         || block == net.minecraft.world.level.block.Blocks.POWDER_SNOW;
   }

   private void setGoalNearShulker(net.minecraft.core.BlockPos shulker) {
      if (shulker != null) {
         net.minecraft.core.BlockPos stand = this.findSafeStandPosNear(shulker);
         if (stand != null) {
            ChatUtils.sendMsg("前往盒子 " + shulker.toShortString() + " 旁的安全位置 " + stand.toShortString());
         } else {
            ChatUtils.sendMsg("盒子 " + shulker.toShortString() + " 周围没有能站的地方，直接朝盒子走");
            stand = shulker;
         }

         BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(new GoalBlock(stand));
      }
   }

   private void scanAndProtectShulkerBoxes() {
      this.protectedShulkerBoxes.clear();
      net.minecraft.core.BlockPos playerPos = this.mc.player.blockPosition();
      int radius = (Integer)this.shulkerSearchRadius.get();
      int px = playerPos.getX();
      int py = playerPos.getY();
      int pz = playerPos.getZ();
      MutableBlockPos p = new MutableBlockPos();

      for (int x = -radius; x <= radius; x++) {
         for (int y = -radius; y <= radius; y++) {
            for (int z = -radius; z <= radius; z++) {
               p.set(px + x, py + y, pz + z);
               net.minecraft.world.level.block.Block block = this.mc.level.getBlockState(p).getBlock();
               if (block instanceof net.minecraft.world.level.block.ShulkerBoxBlock) {
                  this.protectedShulkerBoxes.add(p.immutable());
               }
            }
         }
      }

      ChatUtils.sendMsg("已扫描到 " + this.protectedShulkerBoxes.size() + " 个潜影盒，将被保护");
   }

   private void updateProtectedBlocks() {
      if (BaritoneUtils.IS_AVAILABLE) {
         try {
            Settings settings = BaritoneAPI.getSettings();
            List<net.minecraft.world.level.block.Block> blocksToAvoid = new ArrayList<>();
            blocksToAvoid.add(net.minecraft.world.level.block.Blocks.SHULKER_BOX);
            blocksToAvoid.addAll(net.minecraft.world.level.block.Blocks.DYED_SHULKER_BOX.asList());
            settings.blocksToDisallowBreaking.value = blocksToAvoid;
            this.info("已设置 Baritone 保护所有潜影盒", new Object[0]);
         } catch (Exception var3) {
            this.warning("设置 Baritone 保护失败: " + var3.getMessage(), new Object[0]);
         }
      }
   }

   private void clearProtectedBlocks() {
      if (BaritoneUtils.IS_AVAILABLE) {
         try {
            Settings settings = BaritoneAPI.getSettings();
            settings.blocksToDisallowBreaking.value = new ArrayList();
         } catch (Exception var2) {
            this.warning("清理 Baritone 保护设置失败: " + var2.getMessage(), new Object[0]);
         }
      }
   }

   private void openShulkerBox(net.minecraft.core.BlockPos pos) {
      if (this.mc.gameMode != null && this.mc.player != null) {
         try {
            if (!this.mc.player.blockPosition().closerThan(pos, 5.0)) {
               this.warning("距离潜影盒太远，无法打开", new Object[0]);
               return;
            }

            net.minecraft.world.level.block.Block block = this.mc.level.getBlockState(pos).getBlock();
            if (!(block instanceof net.minecraft.world.level.block.ShulkerBoxBlock)) {
               this.warning("目标位置不是潜影盒: " + pos.toShortString(), new Object[0]);
               return;
            }

            net.minecraft.world.phys.Vec3 hitVec = net.minecraft.world.phys.Vec3.atCenterOf(pos);
            net.minecraft.core.Direction side = net.minecraft.core.Direction.UP;
            net.minecraft.world.phys.BlockHitResult hitResult = new net.minecraft.world.phys.BlockHitResult(hitVec, side, pos, false);
            this.mc.gameMode.useItemOn(this.mc.player, net.minecraft.world.InteractionHand.MAIN_HAND, hitResult);
            this.info("正在打开潜影盒: " + pos.toShortString(), new Object[0]);
         } catch (Exception var6) {
            this.error("打开潜影盒时发生错误: " + var6.getMessage(), new Object[0]);
         }
      }
   }

   private void requestShulkerOpen(net.minecraft.core.BlockPos pos) {
      if (pos != null) {
         if (this.shulkerReopenTimer > 0) {
            this.pendingShulkerOpen = pos;
            this.waitingForShulkerOpen = false;
            this.shulkerInteractionTimer = 0;
         } else {
            this.openShulkerBox(pos);
            this.waitingForShulkerOpen = true;
            this.shulkerInteractionTimer = 0;
         }
      }
   }

   private boolean handleShulkerTaskTimeout(String what) {
      this.shulkerTaskAttempts++;
      if (this.shulkerTaskAttempts >= 3) {
         this.error(what + "连续 3 次超时，任务失败，已自动关闭模块", new Object[0]);
         ChatUtils.sendMsg("任务失败：潜影盒操作连续 3 次超时，自动挖掘已停止");
         this.shulkerTaskAttempts = 0;
         this.pendingShulkerOpen = null;
         this.shulkerReopenTimer = 0;
         if (this.isActive()) {
            this.toggle();
         }

         return false;
      } else {
         this.warning(what + "超时，第 " + this.shulkerTaskAttempts + " 次重试…", new Object[0]);
         return true;
      }
   }

   private void storeSandToShulkerBox() {
      if (this.mc.gameMode != null) {
         if (this.isToolShulker(this.currentStorageShulker)) {
            this.warning("当前打开的是工具潜影盒，跳过存放", new Object[0]);
            this.closeShulkerBox();
            this.currentStorageShulker = null;
            if ((Boolean)this.autoReturn.get() && this.lastMiningPos != null) {
               this.currentState = AutoMiner.MinerState.RETURNING;
               BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(new GoalBlock(this.lastMiningPos));
            } else {
               this.currentState = AutoMiner.MinerState.MINING;
            }
         } else {
            int itemsMoved = 0;
            int maxMovesPerTick = 1;
            boolean hasMovedItems = false;
            net.minecraft.world.inventory.AbstractContainerMenu handler = this.mc.player.containerMenu;
            int total = handler.slots.size();
            int playerFirst = total - 36;
            long now = System.currentTimeMillis();
            if ((Boolean)this.extractOtherItems.get() && now >= this.extractStallUntil && this.hasEmptyPlayerSlot(handler)) {
               int otherSlot = this.findOtherItemSlot(handler);
               if (otherSlot >= 0) {
                  net.minecraft.world.item.ItemStack other = handler.getSlot(otherSlot).getItem();
                  String otherName = other.getItem().getName(other).getString();
                  int otherCount = other.getCount();
                  int otherBefore = this.countOtherItems(handler);
                  this.mc.gameMode.handleContainerInput(handler.containerId, otherSlot, 0, ContainerInput.QUICK_MOVE, this.mc.player);
                  if (this.countOtherItems(handler) >= otherBefore) {
                     this.extractStallUntil = now + 5000L;
                     this.warning("取不出盒子里的其他物品，先停 5 秒再试", new Object[0]);
                  } else {
                     this.info("取出" + otherName + "放回背包，数量: " + otherCount, new Object[0]);
                  }

                  this.updateBoxStatus(this.currentStorageShulker, handler);
                  return;
               }
            }

            for (int i = playerFirst; i < total; i++) {
               net.minecraft.world.item.ItemStack stack = handler.getSlot(i).getItem();
               if (!stack.isEmpty() && this.isTargetItem(stack)) {
                  if (!this.containerHasRoomFor(handler, stack)) {
                     this.updateBoxStatus(this.currentStorageShulker, handler);
                     net.minecraft.core.BlockPos nextShulker = this.findNextEmptyShulkerBox();
                     if (nextShulker != null) {
                        this.closeShulkerBox();
                        this.currentStorageShulker = nextShulker;
                        this.requestShulkerOpen(nextShulker);
                        return;
                     }

                     this.closeShulkerBox();
                     this.finishStoring();
                     return;
                  }

                  this.mc.gameMode.handleContainerInput(handler.containerId, i, 0, ContainerInput.QUICK_MOVE, this.mc.player);
                  itemsMoved++;
                  hasMovedItems = true;
                  this.info("放入" + stack.getItem().getName(stack).getString() + "到潜影盒，数量: " + stack.getCount(), new Object[0]);
                  if (itemsMoved >= maxMovesPerTick) {
                     break;
                  }
               }
            }

            this.updateBoxStatus(this.currentStorageShulker, handler);
            if (!this.hasMoreTargetBlocksToStore()) {
               this.closeShulkerBox();
               this.finishStoring();
            }
         }
      }
   }

   private boolean isTargetItem(net.minecraft.world.item.ItemStack stack) {
      for (net.minecraft.world.level.block.Block targetBlock : this.targetBlocks.get()) {
         if (stack.getItem() == targetBlock.asItem()) {
            return true;
         }
      }

      return false;
   }

   private int findOtherItemSlot(net.minecraft.world.inventory.AbstractContainerMenu handler) {
      int containerSlots = handler.slots.size() - 36;

      for (int i = 0; i < containerSlots; i++) {
         net.minecraft.world.item.ItemStack stack = handler.getSlot(i).getItem();
         if (!stack.isEmpty() && !this.isTargetItem(stack)) {
            return i;
         }
      }

      return -1;
   }

   private int countOtherItems(net.minecraft.world.inventory.AbstractContainerMenu handler) {
      int containerSlots = handler.slots.size() - 36;
      int count = 0;

      for (int i = 0; i < containerSlots; i++) {
         net.minecraft.world.item.ItemStack stack = handler.getSlot(i).getItem();
         if (!stack.isEmpty() && !this.isTargetItem(stack)) {
            count++;
         }
      }

      return count;
   }

   private boolean hasEmptyPlayerSlot(net.minecraft.world.inventory.AbstractContainerMenu handler) {
      int playerFirst = handler.slots.size() - 36;

      for (int i = playerFirst; i < handler.slots.size(); i++) {
         if (handler.getSlot(i).getItem().isEmpty()) {
            return true;
         }
      }

      return false;
   }

   private void finishStoring() {
      if ((Boolean)this.autoReturn.get() && this.lastMiningPos != null) {
         this.currentState = AutoMiner.MinerState.RETURNING;
         BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(new GoalBlock(this.lastMiningPos));
      } else {
         this.currentState = AutoMiner.MinerState.MINING;
      }

      this.info("物品存储完成", new Object[0]);
   }

   private void getToolFromShulkerBox() {
      if (this.mc.gameMode != null) {
         int toolSlot = this.findToolInShulker();
         if (toolSlot != -1) {
            int targetSlot = this.findPlayerSlotForTool();
            if (targetSlot != -1) {
               this.moveItemFromShulker(toolSlot, targetSlot);
               this.info("获取新工具成功", new Object[0]);
               this.closeShulkerBox();
               if ((Boolean)this.autoReturn.get() && this.lastMiningPos != null) {
                  this.currentState = AutoMiner.MinerState.RETURNING;
                  BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(new GoalBlock(this.lastMiningPos));
               } else {
                  this.currentState = AutoMiner.MinerState.MINING;
               }
            } else {
               this.warning("背包已满，无法获取新工具！", new Object[0]);
               this.closeShulkerBox();
               this.currentState = AutoMiner.MinerState.MINING;
            }
         } else {
            AutoMiner.ToolType selectedToolType = (AutoMiner.ToolType)this.toolType.get();
            this.warning("潜影盒中没有找到可用的" + selectedToolType.getDisplayName() + "！", new Object[0]);
            this.closeShulkerBox();
            this.currentState = AutoMiner.MinerState.MINING;
         }
      }
   }

   private net.minecraft.core.BlockPos findNextEmptyShulkerBox() {
      net.minecraft.core.BlockPos neighbor = this.findNeighborStorageBox(this.currentStorageShulker);
      if (neighbor != null) {
         ChatUtils.sendMsg("当前储存盒已满，改用紧挨着的储存盒: " + neighbor.toShortString());
         return neighbor;
      } else {
         net.minecraft.core.BlockPos marked = this.findNearestMarkedStorageShulker(this.currentStorageShulker);
         if (marked != null) {
            return marked;
         } else {
            net.minecraft.core.BlockPos playerPos = this.mc.player.blockPosition();
            int radius = (Integer)this.shulkerSearchRadius.get();
            int px = playerPos.getX();
            int py = playerPos.getY();
            int pz = playerPos.getZ();
            MutableBlockPos p = new MutableBlockPos();

            for (int x = -radius; x <= radius; x++) {
               for (int y = -radius; y <= radius; y++) {
                  for (int z = -radius; z <= radius; z++) {
                     p.set(px + x, py + y, pz + z);
                     net.minecraft.world.level.block.Block block = this.mc.level.getBlockState(p).getBlock();
                     if (block instanceof net.minecraft.world.level.block.ShulkerBoxBlock && !p.equals(this.toolShulkerPos) && !p.equals(this.currentStorageShulker)) {
                        net.minecraft.core.BlockPos found = p.immutable();
                        if (!this.isFullStorageBoxFromWorld(found)) {
                           return found;
                        }
                     }
                  }
               }
            }

            return null;
         }
      }
   }

   private boolean containerHasRoomFor(net.minecraft.world.inventory.AbstractContainerMenu handler, net.minecraft.world.item.ItemStack stack) {
      int containerSlots = handler.slots.size() - 36;

      for (int i = 0; i < containerSlots; i++) {
         net.minecraft.world.item.ItemStack slotStack = handler.getSlot(i).getItem();
         if (slotStack.isEmpty()) {
            return true;
         }

         if (stack.getMaxStackSize() > 1 && net.minecraft.world.item.ItemStack.isSameItemSameComponents(slotStack, stack) && slotStack.getCount() < slotStack.getMaxStackSize()) {
            return true;
         }
      }

      return false;
   }

   private int findToolInShulker() {
      if (!(this.mc.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen)) {
         return -1;
      } else {
         net.minecraft.world.inventory.AbstractContainerMenu handler = this.mc.player.containerMenu;
         AutoMiner.ToolType selectedToolType = (AutoMiner.ToolType)this.toolType.get();

         for (net.minecraft.world.item.Item preferredTool : this.getToolPriorityList(selectedToolType)) {
            for (int i = 0; i < 27 && i < handler.slots.size(); i++) {
               net.minecraft.world.item.ItemStack slotStack = handler.getSlot(i).getItem();
               if (!slotStack.isEmpty()
                  && slotStack.getItem() == preferredTool
                  && (!slotStack.isDamageableItem() || slotStack.getMaxDamage() - slotStack.getDamageValue() > (Integer)this.minDurability.get())) {
                  return i;
               }
            }
         }

         return -1;
      }
   }

   private int findPlayerSlotForTool() {
      AutoMiner.ToolType selectedToolType = (AutoMiner.ToolType)this.toolType.get();
      List<net.minecraft.world.item.Item> toolPriority = this.getToolPriorityList(selectedToolType);

      for (int i = 0; i < this.mc.player.getInventory().getContainerSize(); i++) {
         net.minecraft.world.item.ItemStack stack = this.mc.player.getInventory().getItem(i);
         if (!stack.isEmpty()
            && toolPriority.contains(stack.getItem())
            && stack.isDamageableItem()
            && stack.getMaxDamage() - stack.getDamageValue() <= (Integer)this.minDurability.get()) {
            return i + 27;
         }
      }

      for (int ix = 0; ix < this.mc.player.getInventory().getContainerSize(); ix++) {
         if (this.mc.player.getInventory().getItem(ix).isEmpty()) {
            return ix + 27;
         }
      }

      return -1;
   }

   private void moveItemFromShulker(int shulkerSlot, int playerSlot) {
      if (this.mc.gameMode != null) {
         this.mc.gameMode.handleContainerInput(this.mc.player.containerMenu.containerId, shulkerSlot, 0, ContainerInput.QUICK_MOVE, this.mc.player);
      }
   }

   private boolean hasMoreTargetBlocksToStore() {
      for (int i = 0; i < 36; i++) {
         net.minecraft.world.item.ItemStack stack = this.mc.player.getInventory().getItem(i);
         if (!stack.isEmpty() && this.isTargetItem(stack)) {
            return true;
         }
      }

      return false;
   }

   private void cancelBaritone() {
      BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().cancelEverything();
   }

   private void closeShulkerBox() {
      if (this.mc.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen) {
         this.mc.player.closeContainer();
         ChatUtils.sendMsg("关闭潜影盒");
      }

      this.shulkerReopenTimer = 15;
   }

   private boolean isShulkerBoxOpen() {
      return this.mc.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
   }

   private List<net.minecraft.world.item.Item> getToolPriorityList(AutoMiner.ToolType toolType) {
      return switch (toolType) {
         case SHOVEL -> SHOVEL_PRIORITY;
         case PICKAXE -> PICKAXE_PRIORITY;
         case AXE -> AXE_PRIORITY;
         case HOE -> HOE_PRIORITY;
      };
   }

   private record BoxInspection(AutoMiner.BoxStatus status, boolean full, boolean hasOther) {
   }

   public static enum BoxStatus {
      EMPTY,
      PARTIAL,
      FULL;
   }

   public static enum FullCheckMode {
      AUTO("询问世界后再开启"),
      WORLD("仅询问世界"),
      CACHE("仅询问世界缓存"),
      ALL("我全都要");

      private final String displayName;

      private FullCheckMode(String displayName) {
         this.displayName = displayName;
      }

      public String getDisplayName() {
         return this.displayName;
      }

      @Override
      public String toString() {
         return this.displayName;
      }
   }

   public static enum MarkMode {
      KEY("按键标记"),
      COORDINATE("坐标标记");

      private final String displayName;

      private MarkMode(String displayName) {
         this.displayName = displayName;
      }

      public String getDisplayName() {
         return this.displayName;
      }

      @Override
      public String toString() {
         return this.displayName;
      }
   }

   private static enum MinerState {
      WAITING_TOOL_SELECTION,
      MINING,
      WAITING_FOR_TARGET_BOX,
      INVENTORY_FULL,
      TOOL_BROKEN,
      GOING_TO_STORAGE,
      GOING_TO_TOOLS,
      STORING_ITEMS,
      GETTING_TOOLS,
      RETURNING;
   }

   public static enum ToolType {
      SHOVEL("铲子"),
      PICKAXE("镐子"),
      AXE("斧子"),
      HOE("锄头");

      private final String displayName;

      private ToolType(String displayName) {
         this.displayName = displayName;
      }

      public String getDisplayName() {
         return this.displayName;
      }

      @Override
      public String toString() {
         return this.displayName;
      }
   }
}
