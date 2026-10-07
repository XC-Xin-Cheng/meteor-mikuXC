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
package com.github.mikumiku.addon;

import com.github.mikumiku.addon.commands.CommandMiku;
import com.github.mikumiku.addon.hud.ForemanScanHud;
import com.github.mikumiku.addon.hud.HudMiku;
import com.github.mikumiku.addon.hud.PotCountHud;
import com.github.mikumiku.addon.modules.*;
import com.github.mikumiku.addon.modules.sorter.ItemSorterModule;
import com.github.mikumiku.addon.nerv_printer.modules.MapNamer;
import com.github.mikumiku.addon.util.DebugModule;
import com.github.mikumiku.addon.util.timer.TickTimerManager;
import meteordevelopment.meteorclient.commands.Commands;
import meteordevelopment.meteorclient.renderer.Fonts;
import meteordevelopment.meteorclient.renderer.text.FontFace;
import meteordevelopment.meteorclient.renderer.text.FontFamily;
import meteordevelopment.meteorclient.renderer.text.FontInfo;
import meteordevelopment.meteorclient.systems.config.Config;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.player.ChatUtils;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class MikuApp {
    // 上游用 lombok @Slf4j，此处等价展开为显式字段，避免引入注解处理器
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MikuApp.class);
    private static boolean authenticated = false;
    private static boolean shown = false;

    /** 当前版本初始化失败、被安全跳过的模块名，供启动提示使用。 */
    private static final List<String> skippedModules = new ArrayList<>();

    /**
     * 安全注册一个模块。
     *
     * <p>本工程按 26.1 编译、也要能在 26.2 加载。如果某个模块在构造阶段读到了 26.2 已经移除的
     * 静态常量（历史上出现过 {@code net.minecraft.util.Tuple}、{@code EntityType.EXPERIENCE_ORB}、
     * {@code Items.WHITE_SHULKER_BOX} 等），异常会一路冒到 {@code MikuApp.init}，把整个游戏一起打崩。
     * 这里按模块隔离：谁失败就只跳过谁，其余模块照常加载，并在日志与聊天栏里点名。</p>
     */
    private static void add(Modules modules, Class<? extends Module> type) {
        try {
            modules.add(type.getDeclaredConstructor().newInstance());
        } catch (Throwable t) {
            recordSkip(type.getSimpleName(), t);
        }
    }

    /** 带构造参数的模块（HeadlessModule 之类）走这个重载。 */
    private static void add(Modules modules, String label, Supplier<Module> factory) {
        try {
            modules.add(factory.get());
        } catch (Throwable t) {
            recordSkip(label, t);
        }
    }

    private static void recordSkip(String label, Throwable t) {
        Throwable cause = t instanceof InvocationTargetException ite && ite.getCause() != null ? ite.getCause() : t;
        if (!skippedModules.contains(label)) skippedModules.add(label);
        log.error("[miku] 模块「{}」在当前 Minecraft 版本初始化失败，已自动跳过（不影响其它模块）。", label, cause);
    }

    public static void onRegisterCategories() {
        Modules.registerCategory(BaseModule.CATEGORY);
        Modules.registerCategory(BaseModule.CATEGORY_MIKU_COMBAT);
        Modules.registerCategory(BaseModule.CATEGORY_MIKU_PRO);
        Modules.registerCategory(BaseModule.CATEGORY_MIKU_BUILD);
        Modules.registerCategory(BaseModule.CATEGORY_MIKU_LEGIT);
    }

    public static void init() {
        log.info("Initializing Meteor Addon Miku");

        TickTimerManager.INSTANCE.getTickTime();
        // Modules
        Modules modules = Modules.get();
//        modules.add(new ModuleExample());
        add(modules, PlayerAlert.class);
        add(modules, AutoTrashModule.class);
        add(modules, LiquidFiller.class);
        add(modules, AutoMiner.class);
        add(modules, AutoUseItems.class);
        add(modules, TreeAura.class);
        add(modules, SeedMine.class);
        add(modules, OnekeyFireWork.class);
        add(modules, StructureFinder.class);
        add(modules, EnchantedAppleFinder.class);
        add(modules, ElytraFinder.class);
        add(modules, ShulkerBoxItemFetcher.class);
        add(modules, VillagerRoller.class);
        add(modules, LitematicaMover.class);
        add(modules, AutoWalk.class);
        add(modules, HandsomeSpin.class);
        add(modules, AutoCrystalBlock.class);
        add(modules, AutoLog.class);
        add(modules, ElytraUnbreak.class);
        add(modules, GhostMine.class);
        add(modules, AutoWither.class);
        add(modules, AnchorAuraPlus.class);
        add(modules, AutoEz.class);
        add(modules, RoadBuilder.class);
        add(modules, SelfTrapPlusPlus.class);
        add(modules, AutoLoginPlus.class);
        add(modules, NetherSearchArea.class);
        add(modules, NoFall.class);
        add(modules, EntityList.class);
        add(modules, AutoTouchFire.class);
        add(modules, FarmHelper.class);
        add(modules, AutoXP.class);
        add(modules, ChestAura.class);
        add(modules, FastFall.class);
//        modules.add(new TridentFly());
        add(modules, ElytraFlyPlus.class);
        add(modules, Hover.class);
        add(modules, HighwayBlocker.class);
        add(modules, HighwayClearer.class);
        add(modules, Criticals.class);
        add(modules, MaceCombo.class);
//        modules.add(new AutoFollowPlayer());
        add(modules, OneKeyPearl.class);
        add(modules, NoJumpDelay.class);
        add(modules, Scaffold.class);
        add(modules, BedrockFinder.class);
//        modules.add(new ChestplateFly());
//        Modules.get().add(new ElytraFlyPlusPlus());

        add(modules, UserGuide.class);
        add(modules, AutoSlab.class);
        add(modules, FishingRodFace.class);
        add(modules, KillAuraMiku.class);
        add(modules, PearlMark.class);
        add(modules, UniversalSupply.class);
//        modules.add(new CometTunnel());
//        modules.add(new UniversalSupplySystem());
        add(modules, ExtendedFirework.class);
        add(modules, Phase.class);
        add(modules, AutoCrystal.class);
        add(modules, AutoSpray.class);

//        modules.add(new HeadlessModule(CATEGORY_MIKU_PRO, "投影打印机", "内测中"));
        add(modules, BestPrinter.class);
        add(modules, AutoElytraHarvester.class);
        add(modules, BaritoneHelperModule.class);

        // 地图画 Modules
//        Modules.get().add(new CarpetPrinter());
//        Modules.get().add(new FullBlockPrinter());
        add(modules, MapNamer.class);

        add(modules, Surround.class);
        add(modules, SurroundPlus.class);
        add(modules, SurroundPlus2.class);
        add(modules, AutoDigFeet.class);
        add(modules, AutoHoleFill.class);
        add(modules, HoleSnap.class);
        add(modules, AutoBlockCenter.class);

        add(modules, FakeCoordinates.class);
        add(modules, LookUpModule.class);
        add(modules, MikuClicker.class);
        add(modules, HumanizedInput.class);
        add(modules, ItemSorterModule.class);

        // 「Miku 合法」；「Miku 聊天」「Miku 消息设置」挂在主分类「Miku」下面
        add(modules, LegitKillAura.class);
        add(modules, LegitMineAura.class);
        add(modules, MikuChat.class);
        add(modules, MikuMessageSettings.class);

        add(modules, Velocity.class);
        add(modules, AntiKnockback.class);
        add(modules, PearlBot.class);
        add(modules, DebugModule.class);
        add(modules, VillagerTrader.class);
        add(modules, ElytraArmorSwitch.class);
        add(modules, AutoChorusFruit.class);
        add(modules, DeathAutoCommand.class);
        add(modules, MassExtractor.class);
        add(modules, "药水显示", () -> new HeadlessModule(BaseModule.CATEGORY_MIKU_PRO, "药水显示", "在顶部点击Hud-Edit, 然后右键添加， 添加出来后右键元素可以设置"));

//        modules.add(new HeadlessModule(CATEGORY_MIKU_PRO, "赶路助手", "一键"));
//        modules.add(new HeadlessModule(CATEGORY_MIKU_MAP, "鱼竿糊脸", "一键"));
//        modules.add(new HeadlessModule(CATEGORY_MIKU_PRO, "核爆挖掘", "一键"));

//        modules.add(new HeadlessModule(BaseModule.CATEGORY_MIKU_PRO, "全自动KIT", "一键"));
//        modules.add(new HeadlessModule(BaseModule.CATEGORY_MIKU_PRO, "全自动合成", "一键"));
//        modules.add(new HeadlessModule(BaseModule.CATEGORY_MIKU_PRO, "自动袭击", "一键"));
//        modules.add(new HeadlessModule(BaseModule.CATEGORY_MIKU_PRO, "自动喷神龟", "一键"));
//        modules.add(new HeadlessModule(BaseModule.CATEGORY_MIKU_PRO, "自动口服神龟", "一键"));
//        modules.add(new HeadlessModule(BaseModule.CATEGORY_MIKU_PRO, "自动配补给包", "一键"));
//        modules.add(new HeadlessModule(BaseModule.CATEGORY_MIKU_PRO, "自动附魔", "一键"));
//        modules.add(new HeadlessModule(BaseModule.CATEGORY_MIKU_PRO, "末地自动拿鞘翅", "一键"));
//        modules.add(new HeadlessModule(BaseModule.CATEGORY_MIKU_PRO, "自动卡服机", "一键"));

//        modules.add(new HeadlessModule(BaseModule.CATEGORY_MIKU_PRO, "全自动地图画", "地图画"));
//        modules.add(new HeadlessModule(BaseModule.CATEGORY_MIKU_PRO, "地图画基座", "地图画"));


//        Fucker fucker = new Fucker();

//        modules.add(new MikuModule(CATEGORY, "miku", "miku"));
//        MikuModule mikuModule = new MikuModule(CATEGORY, "miku插件", "miku");
        ChatUtils.warning("Miku插件群：1013297171");
        // Commands
        try {
            Commands.add(new CommandMiku());
        } catch (Throwable t) {
            recordSkip("指令 CommandMiku", t);
        }

        // HUD（同样逐个隔离，单个元素初始化失败不影响其余）
        try {
            Hud.get().register(HudMiku.INFO);
        } catch (Throwable t) {
            recordSkip("HUD HudMiku", t);
        }
        try {
            Hud.get().register(PotCountHud.INFO);
        } catch (Throwable t) {
            recordSkip("HUD PotCount", t);
        }
        try {
            Hud.get().register(ForemanScanHud.INFO);
        } catch (Throwable t) {
            recordSkip("HUD ForemanScan", t);
        }

        if (!skippedModules.isEmpty()) {
            ChatUtils.warning("以下模块在当前 Minecraft 版本不可用，已自动跳过：" + String.join("、", skippedModules));
        }

        try {
            Config config = Config.get();
            config.customFont.set(true);
            // 查找并设置Dengxian字体
            FontFamily dengxianFamily = Fonts.getFamily("Dengxian");

            if (dengxianFamily != null) {
                FontFace dengxianFont = dengxianFamily.get(FontInfo.Type.Regular);
                if (dengxianFont != null) {
                    config.font.set(dengxianFont);
                }
            }
        } catch (Exception e) {

        }


    }

}
