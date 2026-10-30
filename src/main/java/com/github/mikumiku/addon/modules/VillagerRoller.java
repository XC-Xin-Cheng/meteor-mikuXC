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
import com.github.mikumiku.addon.gui.EnchantmentSelectScreen;
import com.github.mikumiku.addon.util.*;
import com.github.mikumiku.addon.util.MikuCompat;
import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.objects.ObjectIntImmutablePair;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.entity.player.InteractEntityEvent;
import meteordevelopment.meteorclient.events.entity.player.StartBreakingBlockEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WSection;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.input.WDropdown;
import meteordevelopment.meteorclient.gui.widgets.input.WIntEdit;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.gui.widgets.pressable.WCheckbox;
import meteordevelopment.meteorclient.gui.widgets.pressable.WMinus;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.ISerializable;
import meteordevelopment.meteorclient.utils.misc.Names;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ClientboundMerchantOffersPacket;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.Holder;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.ChatFormatting;
import net.minecraft.world.InteractionHand;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * "authors":
 * "FlexCoral",
 * "seasnail8169",
 * "Cloudburst"
 * <p>
 * "repo": "https://github.com/maxsupermanhd/meteor-villager-roller"
 */
public class VillagerRoller extends BaseModule {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgSound = settings.createGroup("声音");
    private final SettingGroup sgChatFeedback = settings.createGroup("聊天反馈", false);

    private final Setting<Boolean> disableIfFound = sgGeneral.add(new BoolSetting.Builder()
        .name("找到后禁用")
        .description("找到附魔后从列表中禁用该附魔")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> disconnectIfFound = sgGeneral.add(new BoolSetting.Builder()
        .name("找到后断开连接")
        .description("找到列表中的附魔后断开与服务器的连接")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> saveListToConfig = sgGeneral.add(new BoolSetting.Builder()
        .name("保存列表到配置")
        .description("启用将刷取列表保存和加载到配置文件和剪贴板缓冲区")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> enablePlaySound = sgGeneral.add(new BoolSetting.Builder()
        .name("启用声音")
        .description("找到想要的交易时播放声音")
        .defaultValue(true)
        .build()
    );

    private final Setting<List<SoundEvent>> sound = sgSound.add(new SoundEventListSetting.Builder()
        .name("播放声音")
        .description("启用时找到想要的交易时播放的声音")
        .defaultValue(Collections.singletonList(SoundEvents.AMETHYST_CLUSTER_BREAK))
        .build()
    );

    private final Setting<Double> soundPitch = sgSound.add(new DoubleSetting.Builder()
        .name("声音音调")
        .description("播放声音的音调")
        .defaultValue(1.0)
        .min(0)
        .sliderRange(0, 8)
        .build()
    );

    private final Setting<Double> soundVolume = sgSound.add(new DoubleSetting.Builder()
        .name("声音音量")
        .description("播放声音的音量")
        .defaultValue(1.0)
        .min(0)
        .sliderRange(0, 1)
        .build()
    );

    private final Setting<Boolean> pauseOnScreen = sgGeneral.add(new BoolSetting.Builder()
        .name("界面暂停")
        .description("打开任何界面时暂停刷取")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> headRotateOnPlace = sgGeneral.add(new BoolSetting.Builder()
        .name("放置时转头")
        .description("放置方块时是否看向方块？")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> failedToPlaceDelay = sgGeneral.add(new IntSetting.Builder()
        .name("放置失败延迟")
        .description("方块放置失败后的延迟（毫秒）")
        .defaultValue(1500)
        .min(0)
        .sliderRange(0, 10000)
        .build()
    );

    private final Setting<Boolean> failedToPlaceDisable = sgGeneral.add(new BoolSetting.Builder()
        .name("放置失败禁用")
        .description("方块放置失败时禁用刷取器")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> maxProfessionWaitTime = sgGeneral.add(new IntSetting.Builder()
        .name("最大职业等待时间")
        .description("村民不接受职业时的等待时间（毫秒）。零 = 无限制。")
        .defaultValue(0)
        .min(0)
        .sliderRange(0, 10000)
        .build()
    );

    private final Setting<Boolean> onlyTradeable = sgGeneral.add(new BoolSetting.Builder()
        .name("仅可交易")
        .description("隐藏未标记为可交易的附魔")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> sortEnchantments = sgGeneral.add(new BoolSetting.Builder()
        .name("附魔排序")
        .description("按名称排序显示附魔")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> instantRebreak = sgGeneral.add(new BoolSetting.Builder()
        .name("瞬间破坏")
        .description("使用瞬间破坏来立即挖掘讲台。最好站在讲台位置上方。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> interactRetry = sgGeneral.add(new IntSetting.Builder()
        .name("交互重试")
        .description("如果服务器未确认村民交互数据包，在此刻数后发送另一个。0 = 不重试。")
        .defaultValue(0)
        .min(0)
        .sliderRange(0, 200)
        .build()
    );

    private final Setting<Boolean> cfSetup = sgChatFeedback.add(new BoolSetting.Builder()
        .name("设置提示")
        .description("开始时的操作提示（否则在模块列表状态中显示）")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> cfPausedOnScreen = sgChatFeedback.add(new BoolSetting.Builder()
        .name("界面暂停提示")
        .description("刷取已暂停，与村民交互以继续")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> cfLowerLevel = sgChatFeedback.add(new BoolSetting.Builder()
        .name("低等级提示")
        .description("找到附魔 %s 但不是最高等级：%d（最高）> %d（找到）")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> cfTooExpensive = sgChatFeedback.add(new BoolSetting.Builder()
        .name("价格过高提示")
        .description("找到附魔 %s 但价格太高：%s（最高价格）< %d（成本）")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> cfIgnored = sgChatFeedback.add(new BoolSetting.Builder()
        .name("不在列表提示")
        .description("找到附魔 %s 但不在列表中。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> cfProfessionTimeout = sgChatFeedback.add(new BoolSetting.Builder()
        .name("职业超时提示")
        .description("村民未在指定时间内接受职业")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> cfPlaceFailed = sgChatFeedback.add(new BoolSetting.Builder()
        .name("放置失败提示")
        .description("放置失败，无法放置或无法将讲台放入快捷栏（仍会触发放置失败设置）")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> cfDiscrepancy = sgChatFeedback.add(new BoolSetting.Builder()
        .name("状态异常提示")
        .description("刷取器进入了意外状态（可能是反作弊干扰）")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> cfSentRetryInteract = sgChatFeedback.add(new BoolSetting.Builder()
        .name("重试交互提示")
        .description("让你知道服务器丢弃了初始交互数据包并发送了额外的数据包。")
        .defaultValue(true)
        .build()
    );

    private enum State {
        DISABLED("已禁用"),
        WAITING_FOR_TARGET_BLOCK("等待目标方块"),
        WAITING_FOR_TARGET_VILLAGER("等待目标村民"),
        ROLLING_BREAKING_BLOCK("刷取中-破坏方块"),
        ROLLING_WAITING_FOR_VILLAGER_PROFESSION_CLEAR("刷取中-等待村民职业清除"),
        ROLLING_PLACING_BLOCK("刷取中-放置方块"),
        ROLLING_WAITING_FOR_VILLAGER_PROFESSION_NEW("刷取中-等待村民新职业"),
        ROLLING_WAITING_FOR_VILLAGER_TRADES("刷取中-等待村民交易");

        private final String displayName;

        State(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    private static final Path CONFIG_PATH = MeteorClient.FOLDER.toPath().resolve("VillagerRoller");
    private State currentState = State.DISABLED;
    private Villager rollingVillager;
    private BlockPos rollingBlockPos;
    private Block rollingBlock;
    private final List<RollingEnchantment> searchingEnchants = new ArrayList<>();
    private long failedToPlacePrevMsg = System.currentTimeMillis();
    private long currentProfessionWaitTime;

    public VillagerRoller() {
        super(CATEGORY_MIKU_BUILD,  "刷附魔书", "自动破坏和放置讲台来刷村民交易的附魔书。 建议价格21。 基于meteor-villager-roller");
    }

    @Override
    public void onActivate() {
        super.onActivate();
        if (toggleOnBindRelease) {
            toggleOnBindRelease = false;
            if (cfSetup.get()) {
                warning("你的’按键释放时切换‘设置为true，我已经帮你关闭了，省得你排查问题");
            }
        }
        currentState = State.WAITING_FOR_TARGET_BLOCK;
        if (cfSetup.get()) {
            info("攻击你想要刷的讲台方块");
        }
    }

    @Override
    public void onDeactivate() {
        currentState = State.DISABLED;
    }

    @Override
    public String getInfoString() {
        return currentState.toString();
    }

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = super.toTag();
        if (saveListToConfig.get()) {
            ListTag l = new ListTag();
            for (RollingEnchantment e : searchingEnchants) {
                l.add(e.toTag());
            }
            tag.put("rolling", l);
        }
        return tag;
    }

    @Override
    public Module fromTag(CompoundTag tag) {
        super.fromTag(tag);
        if (saveListToConfig.get()) {
//            ListTag l = tag.getListOrEmpty("rolling");

            ListTag l = Via.getNbtList(tag, "rolling");
            searchingEnchants.clear();
            for (Tag e : l) {
                if (e.getId() != Tag.TAG_COMPOUND) {
                    info("无效的列表元素");
                    continue;
                }
                searchingEnchants.add(new RollingEnchantment().fromTag((CompoundTag) e));
            }
        }
        return this;
    }


    @Override
    public WWidget getWidget(GuiTheme theme) {
        WVerticalList list = theme.verticalList();
        fillWidget(theme, list);
        return list;
    }

    private void fillWidget(GuiTheme theme, WVerticalList list) {
        WSection loadDataSection = list.add(theme.section("配置保存")).expandX().widget();

        WTable control = loadDataSection.add(theme.table()).expandX().widget();

        WTextBox savedConfigName = control.add(theme.textBox("default")).expandWidgetX().expandCellX().expandX().widget();
        WButton save = control.add(theme.button("保存")).expandX().widget();
        save.action = () -> {
            if (VillagerRollerFileUtils.saveSearchingToFile(
                new File(new File(MeteorClient.FOLDER, "VillagerRoller"), savedConfigName.get() + ".nbt"),
                searchingEnchants,
                this::error)) {
                info("保存成功");
            } else {
                error("保存失败");
            }
            list.clear();
            fillWidget(theme, list);
        };
        control.row();

        ArrayList<String> configs = new ArrayList<>();
        if (Files.notExists(CONFIG_PATH)) {
            if (!CONFIG_PATH.toFile().mkdirs()) error("创建目录失败 [{}]", CONFIG_PATH);
        } else {
            try (DirectoryStream<Path> configDir = Files.newDirectoryStream(CONFIG_PATH)) {
                for (Path config : configDir) {
                    configs.add(FilenameUtils.removeExtension(config.getFileName().toString()));
                }
            } catch (IOException e) {
                error("列出目录失败", e);
            }
        }
        if (!configs.isEmpty()) {
            WDropdown<String> loadedConfigName = control.add(theme.dropdown(configs.toArray(new String[0]), "default")).expandWidgetX().expandCellX().expandX().widget();
            WButton load = control.add(theme.button("加载")).expandX().widget();
            load.action = () -> {
                if (VillagerRollerFileUtils.loadSearchingFromFile(
                    new File(new File(MeteorClient.FOLDER, "VillagerRoller"), loadedConfigName.get() + ".nbt"),
                    searchingEnchants,
                    this::error)) {
                    list.clear();
                    fillWidget(theme, list);
                    info("加载成功");
                } else {
                    error("加载文件失败。");
                }
            };
        }

        WSection enchantments = list.add(theme.section("附魔")).expandX().widget();

        WTable table = enchantments.add(theme.table()).expandX().widget();
        table.add(theme.item(Items.BOOK.getDefaultInstance()));
        table.add(theme.label("附魔"));
        table.add(theme.label("等级"));
        table.add(theme.label("价格"));
        table.add(theme.label("启用"));
        table.add(theme.label("移除"));
        table.row();
        if (sortEnchantments.get()) {
            searchingEnchants.removeIf(ench -> ench.enchantment == null);
            searchingEnchants.sort(Comparator.comparing(o -> o.enchantment));
        }

        Optional<Registry<Enchantment>> reg;
        if (mc.level != null) {
            reg = Optional.of(mc.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT));
        } else {
            reg = Optional.empty();
        }

        for (int i = 0; i < searchingEnchants.size(); i++) {
            RollingEnchantment e = searchingEnchants.get(i);
            Optional<Holder.Reference<Enchantment>> en;
            if (reg.isPresent()) {
                en = reg.get().get(e.enchantment);
            } else {
                en = Optional.empty();
            }
            final int si = i;
            ItemStack book = Items.ENCHANTED_BOOK.getDefaultInstance();
            int maxlevel = 255;
            if (en.isPresent()) {
                book = Via.getEnchantedBookWith(en);
                maxlevel = en.get().value().getMaxLevel();
            }
            table.add(theme.item(book));

            WHorizontalList label = theme.horizontalList();
            WButton c = label.add(theme.button("更改")).widget();
            c.action = () -> MikuCompat.setScreen(new EnchantmentSelectScreen(theme, onlyTradeable.get(), sel -> {
                searchingEnchants.set(si, sel);
                list.clear();
                fillWidget(theme, list);
            }));
            if (en.isPresent()) {
                label.add(theme.label(Names.get(en.get())));
            } else {
                label.add(theme.label(e.enchantment.toString()));
            }
            table.add(label);

            WIntEdit lev = table.add(theme.intEdit(e.minLevel, 0, maxlevel, true)).minWidth(40).expandX().widget();
            lev.action = () -> e.minLevel = lev.get();
            lev.tooltip = "最低附魔等级，0表示仅最高等级（对于自定义附魔，0相当于1）";

            WHorizontalList costbox = table.add(theme.horizontalList()).minWidth(50).expandX().widget();
            WIntEdit cost = costbox.add(theme.intEdit(e.maxCost, 0, 64, false)).minWidth(40).expandX().widget();
            cost.action = () -> e.maxCost = cost.get();
            cost.tooltip = "最高绿宝石价格，0表示无限制";

            WButton setOptimal = costbox.add(theme.button("最优")).widget();
            setOptimal.tooltip = "设置为最优价格（2 + 最高等级*3）（珍贵附魔翻倍）（如果已知）";
            setOptimal.action = () -> {
                list.clear();
                en.ifPresent(enchantmentReference -> e.maxCost = getMinimumPrice(enchantmentReference));
                fillWidget(theme, list);
            };

            WCheckbox enabled = table.add(theme.checkbox(e.enabled)).widget();
            enabled.action = () -> e.enabled = enabled.checked;
            enabled.tooltip = "是否启用？";

            WMinus del = table.add(theme.minus()).widget();
            del.action = () -> {
                list.clear();
                searchingEnchants.remove(e);
                fillWidget(theme, list);
            };
            table.row();
        }

        WTable controls = list.add(theme.table()).expandX().widget();

        WButton removeAll = controls.add(theme.button("移除全部")).expandX().widget();
        removeAll.action = () -> {
            list.clear();
            searchingEnchants.clear();
            fillWidget(theme, list);
        };

        WButton add = controls.add(theme.button("添加")).expandX().widget();
        add.action = () -> MikuCompat.setScreen(new EnchantmentSelectScreen(theme, onlyTradeable.get(), e -> {
            e.minLevel = 1;
            e.maxCost = 64;
            e.enabled = true;
            searchingEnchants.add(e);
            list.clear();
            fillWidget(theme, list);
        }));

        WButton addAll = controls.add(theme.button("添加全部")).expandX().widget();
        addAll.action = () -> {
            list.clear();
            searchingEnchants.clear();
            if (reg.isPresent()) {
                for (Holder<Enchantment> e : getEnchants(onlyTradeable.get())) {
                    searchingEnchants.add(new RollingEnchantment(reg.get().getKey(e.value()), e.value().getMaxLevel(), getMinimumPrice(e), true));
                }
            }
            fillWidget(theme, list);
        };
        controls.row();

        WButton setOptimalForAll = controls.add(theme.button("全部设为最优")).expandX().widget();
        setOptimalForAll.action = () -> {
            list.clear();
            if (reg.isPresent()) {
                for (RollingEnchantment e : searchingEnchants) {
                    reg.get().get(e.enchantment).ifPresent(enchantmentReference -> e.maxCost = getMinimumPrice(enchantmentReference));
                }
            }
            fillWidget(theme, list);
        };

        WButton priceBumpUp = controls.add(theme.button("全部价格+1")).expandX().widget();
        priceBumpUp.action = () -> {
            list.clear();
            for (RollingEnchantment e : searchingEnchants) {
                if (e.maxCost < 64) e.maxCost++;
            }
            fillWidget(theme, list);
        };

        WButton priceBumpDown = controls.add(theme.button("全部价格-1")).expandX().widget();
        priceBumpDown.action = () -> {
            list.clear();
            for (RollingEnchantment e : searchingEnchants) {
                if (e.maxCost > 0) e.maxCost--;
            }
            fillWidget(theme, list);
        };
        controls.row();

        WButton setZeroForAll = controls.add(theme.button("全部价格归零")).expandX().widget();
        setZeroForAll.action = () -> {
            list.clear();
            for (RollingEnchantment e : searchingEnchants) {
                e.maxCost = 0;
            }
            fillWidget(theme, list);
        };

        WButton enableAll = controls.add(theme.button("启用全部")).expandX().widget();
        enableAll.action = () -> {
            list.clear();
            for (RollingEnchantment e : searchingEnchants) {
                e.enabled = true;
            }
            fillWidget(theme, list);
        };

        WButton disableAll = controls.add(theme.button("禁用全部")).expandX().widget();
        disableAll.action = () -> {
            list.clear();
            for (RollingEnchantment e : searchingEnchants) {
                e.enabled = false;
            }
            fillWidget(theme, list);
        };
        controls.row();

    }


    public List<Holder<Enchantment>> getEnchants(boolean onlyTradeable) {
        if (mc.level == null) {
            return Collections.emptyList();
        }
        Registry<Enchantment> reg = mc.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        List<Holder<Enchantment>> available = new ArrayList<>();
        if (onlyTradeable) {
            var i = reg.getTagOrEmpty(EnchantmentTags.TRADEABLE);
            i.iterator().forEachRemaining(available::add);
            return available;
        } else {
            for (var a : reg.asHolderIdMap()) {
                available.add(a);
            }
            return available;
        }
    }

    public static int getMinimumPrice(Holder<Enchantment> e) {
        if (e == null) return 0;
        return e.is(EnchantmentTags.DOUBLE_TRADE_PRICE) ? (2 + 3 * e.value().getMaxLevel()) * 2 : 2 + 3 * e.value().getMaxLevel();
    }

    /**
     * 获取所有附魔ID及其最大等级
     *
     * @return Map<String, Integer> 附魔ID字符串到最大等级的映射
     */
    public Map<String, Integer> getAllEnchantmentsWithMaxLevel() {
        Map<String, Integer> enchantments = new HashMap<>();

        if (mc.level == null) {
            return enchantments;
        }

        Optional<Registry<Enchantment>> reg = Optional.of(mc.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT));
        if (reg.isEmpty()) {
            return enchantments;
        }

        for (Map.Entry<ResourceKey<Enchantment>, Enchantment> entry : reg.get().entrySet()) {
            String enchantmentId = entry.getKey().identifier().toString();
            int maxLevel = entry.getValue().getMaxLevel();
            enchantments.put(enchantmentId, maxLevel);
        }

        return enchantments;
    }

    private long waitingForTradesTicks = 0;

    public void triggerInteract() {
        if (pauseOnScreen.get() && MikuCompat.screen() != null) {
            if (cfPausedOnScreen.get()) {
                info("刷取已暂停，与村民交互以继续");
            }
        } else {
            Vec3 playerPos = mc.player.getEyePosition();
            Vec3 villagerPos = rollingVillager.getEyePosition();
            EntityHitResult entityHitResult = ProjectileUtil.getEntityHitResult(mc.player, playerPos, villagerPos, rollingVillager.getBoundingBox(), Entity::isPickable, playerPos.distanceToSqr(villagerPos));
            if (entityHitResult == null) {
                // Raycast didn't find villager entity?
                mc.gameMode.interact(mc.player, rollingVillager, new EntityHitResult(rollingVillager), InteractionHand.MAIN_HAND);
                waitingForTradesTicks = 0;
            } else {
                InteractionResult actionResult = mc.gameMode.interact(mc.player, rollingVillager, entityHitResult, InteractionHand.MAIN_HAND);
                if (!actionResult.consumesAction()) {
                    mc.gameMode.interact(mc.player, rollingVillager, new EntityHitResult(rollingVillager), InteractionHand.MAIN_HAND);
                    waitingForTradesTicks = 0;
                }
            }
        }
    }

    public List<Pair<Holder<Enchantment>, Integer>> getEnchants(ItemStack stack) {
        List<Pair<Holder<Enchantment>, Integer>> ret = new ArrayList<>();
        for (var e : EnchantmentHelper.getEnchantmentsForCrafting(stack).entrySet()) {
            ret.add(ObjectIntImmutablePair.of(e.getKey(), e.getIntValue()));
        }
        return ret;
    }

    @EventHandler
    private void onReceivePacket(PacketEvent.Receive event) {
        if (currentState != State.ROLLING_WAITING_FOR_VILLAGER_TRADES) return;
        if (!(event.packet instanceof ClientboundMerchantOffersPacket p)) return;
        mc.executeIfPossible(() -> triggerTradeCheck(p.getOffers()));
    }

    public void triggerTradeCheck(MerchantOffers l) {
        for (MerchantOffer offer : l) {
            ItemStack sellItem = offer.getResult();
            if (!sellItem.is(Items.ENCHANTED_BOOK) || sellItem.get(DataComponents.STORED_ENCHANTMENTS) == null)
                continue;

            for (Pair<Holder<Enchantment>, Integer> enchant : getEnchants(sellItem)) {
                int enchantLevel = enchant.right();
                var reg = Via.getEnchantmentRegistry();

                String enchantIdString = reg.getKey(enchant.key().value()).toString();
                String enchantName = Names.get(enchant.key());

                boolean found = false;
                for (RollingEnchantment e : searchingEnchants) {
                    if (!e.enabled || !e.enchantment.toString().equals(enchantIdString)) continue;
                    found = true;
                    if (e.minLevel <= 0) {
                        int ml = enchant.key().value().getMaxLevel();
                        if (enchantLevel < ml) {
                            if (cfLowerLevel.get()) {
                                info(String.format("找到附魔 %s 但不是最高等级：%d（找到）< %d（最高）",
                                    enchantName, enchantLevel, ml));
                            }
                            continue;
                        }
                    } else if (e.minLevel > enchantLevel) {
                        if (cfLowerLevel.get()) {
                            info(String.format("找到附魔 %s 但等级太低：%d（刷到等级）< %d（要求等级）",
                                enchantName, enchantLevel, e.minLevel));
                        }
                        continue;
                    }
                    if (e.maxCost > 0 && offer.getBaseCostA().getCount() > e.maxCost) {
                        if (cfTooExpensive.get()) {
                            info(String.format("找到附魔 %s 但价格太高：%s（价格）> %d（最优价格）",
                                enchantName, offer.getBaseCostA().getCount(), e.maxCost));
                        }
                        continue;
                    }
                    if (disableIfFound.get()) e.enabled = false;
                    toggle();
                    if (enablePlaySound.get() && !sound.get().isEmpty()) {
                        mc.getSoundManager().play(SimpleSoundInstance.forUI(sound.get().get(0),
                            soundPitch.get().floatValue(), soundVolume.get().floatValue()));
                    }
                    String levelText = (enchantLevel > 1 || enchant.key().value().getMaxLevel() > 1) ? " " + enchantLevel : "";

                    String message = String.format(
                        "%s[%s%s%s] 找到附魔 %s%s%s%s，价格 %s%d%s 绿宝石。",
                        ChatFormatting.GRAY,
                        ChatFormatting.GREEN,
                        title,
                        ChatFormatting.GRAY,
                        ChatFormatting.WHITE,
                        enchantName,
                        levelText,
                        ChatFormatting.GRAY,
                        ChatFormatting.WHITE,
                        offer.getBaseCostA().getCount(),
                        ChatFormatting.GRAY
                    );

                    ChatUtils.sendMsg(message);

                    if (disconnectIfFound.get()) {

                        mc.getConnection().getConnection().disconnect(Component.literal(message));
                    }
                    break;
                }
                if (!found && cfIgnored.get()) {
                    info(String.format("找到附魔 %s 但不在列表中。", enchantName));
                }
            }
        }

        mc.player.closeContainer();
        currentState = State.ROLLING_BREAKING_BLOCK;
    }

    @EventHandler
    private void onInteractEntity(InteractEntityEvent event) {
        if (currentState != State.WAITING_FOR_TARGET_VILLAGER) return;
        if (!(event.entity instanceof Villager villager)) return;

        rollingVillager = villager;
        currentState = State.ROLLING_BREAKING_BLOCK;
        if (cfSetup.get()) {
            ChatUtils.sendMsg("OK 开刷");
        }
        event.cancel();
    }

    @EventHandler(priority = EventPriority.HIGH)
    private void onStartBreakingBlockEvent(StartBreakingBlockEvent event) {
        if (currentState != State.WAITING_FOR_TARGET_BLOCK) return;

        rollingBlockPos = event.blockPos;
        rollingBlock = mc.level.getBlockState(rollingBlockPos).getBlock();
        currentState = State.WAITING_FOR_TARGET_VILLAGER;
        if (instantRebreak.get()) {
            mc.getConnection().send(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, rollingBlockPos, Direction.UP));
        }
        if (cfSetup.get()) {
            ChatUtils.sendMsg("已选择讲台，现在右键你想要刷的村民");
        }
    }

    private void placeFailed(String msg) {
        if (failedToPlacePrevMsg + failedToPlaceDelay.get() <= System.currentTimeMillis()) {
            if (cfPlaceFailed.get()) {
                info(msg);
            }
            failedToPlacePrevMsg = System.currentTimeMillis();
        }
        if (failedToPlaceDisable.get()) toggle();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        switch (currentState) {
            case ROLLING_BREAKING_BLOCK -> {
                if (instantRebreak.get()) {
                    mc.getConnection().send(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, rollingBlockPos, Direction.DOWN));
                }
                if (mc.level.getBlockState(rollingBlockPos) == Blocks.AIR.defaultBlockState()) {
                    // info("方块已破坏，等待村民清除职业...");
                    currentState = State.ROLLING_WAITING_FOR_VILLAGER_PROFESSION_CLEAR;
                } else if (!instantRebreak.get() && !BlockUtils.breakBlock(rollingBlockPos, true)) {
                    error("无法破坏指定方块");
                    toggle();
                }
            }
            case ROLLING_WAITING_FOR_VILLAGER_PROFESSION_CLEAR -> {
                if (mc.level.getBlockState(rollingBlockPos).is(Blocks.LECTERN)) {
                    if (cfDiscrepancy.get()) {
                        info("刷取方块挖掘被撤销？");
                    }
                    currentState = State.ROLLING_BREAKING_BLOCK;
                    return;
                }
                if (Via.isNoneProfession(rollingVillager.getVillagerData())) {
                    // info("Profession cleared");
                    currentState = State.ROLLING_PLACING_BLOCK;
                }
//                rollingVillager.getVillagerData().profession().getKey().ifPresent(profession -> {
//                    if (profession == VillagerProfession.NONE) {
//                        // info("职业已清除");
//                        currentState = State.ROLLING_PLACING_BLOCK;
//                    }
//                });
            }
            case ROLLING_PLACING_BLOCK -> {

                int slot = BagUtil.findItemInventorySlot(rollingBlock.asItem());
                if (slot == -1) {
                    placeFailed("背包中未找到讲台");
                    return;
                }

                if (!BaritoneUtil.canPlace(rollingBlockPos, true)) {
                    placeFailed("该位置无法放置讲台");
                    return;
                }

                if (!BaritoneUtil.placeBlock(rollingBlockPos, rollingBlock.asItem())) {
                    placeFailed("放置讲台失败");
                    return;
                }
                currentState = State.ROLLING_WAITING_FOR_VILLAGER_PROFESSION_NEW;
                if (maxProfessionWaitTime.get() > 0) {
                    currentProfessionWaitTime = System.currentTimeMillis();
                }
            }
            case ROLLING_WAITING_FOR_VILLAGER_PROFESSION_NEW -> {
                if (maxProfessionWaitTime.get() > 0 && (currentProfessionWaitTime + maxProfessionWaitTime.get() <= System.currentTimeMillis())) {
                    if (cfProfessionTimeout.get()) {
                        info("村民未在指定时间内接受职业");
                    }
                    currentState = State.ROLLING_BREAKING_BLOCK;
                    return;
                }
                if (mc.level.getBlockState(rollingBlockPos) == Blocks.AIR.defaultBlockState()) {
                    if (cfDiscrepancy.get()) {
                        info("讲台放置被服务器撤销（反作弊？）");
                    }
                    currentState = State.ROLLING_PLACING_BLOCK;
                    return;
                }
                if (!mc.level.getBlockState(rollingBlockPos).is(Blocks.LECTERN)) {
                    if (cfDiscrepancy.get()) {
                        info("放置了错误的方块？！");
                    }
                    currentState = State.ROLLING_BREAKING_BLOCK;
                    return;
                }
                if (!Via.isNoneProfession(rollingVillager.getVillagerData())) {
                    currentState = State.ROLLING_WAITING_FOR_VILLAGER_TRADES;
                    triggerInteract();
                }

//                rollingVillager.getVillagerData().profession().getKey().ifPresent(profession -> {
//                    if (profession != VillagerProfession.NONE) {
//                        currentState = State.ROLLING_WAITING_FOR_VILLAGER_TRADES;
//                        triggerInteract();
//                    }
//                });
            }
            case ROLLING_WAITING_FOR_VILLAGER_TRADES -> {
                var retryTicks = interactRetry.get();
                if (retryTicks > 0) {
                    if (waitingForTradesTicks >= retryTicks) {
                        if (cfSentRetryInteract.get()) {
                            info("发送另一个交互数据包");
                        }
                        triggerInteract();
                    } else {
                        waitingForTradesTicks++;
                    }
                }
            }
            default -> {
                // 等待其他状态
            }
        }
    }

    public static class RollingEnchantment implements ISerializable<RollingEnchantment> {
        public Identifier enchantment;
        public int minLevel;
        public int maxCost;
        public boolean enabled;

        public RollingEnchantment(Identifier enchantment, int minLevel, int maxCost, boolean enabled) {
            this.enchantment = enchantment;
            this.minLevel = minLevel;
            this.maxCost = maxCost;
            this.enabled = enabled;
        }

        public RollingEnchantment() {
            enchantment = Identifier.fromNamespaceAndPath("minecraft", "protection");
            minLevel = 0;
            maxCost = 0;
            enabled = false;
        }

        @Override
        public CompoundTag toTag() {
            CompoundTag tag = new CompoundTag();
            tag.putString("enchantment", enchantment.toString());
            tag.putInt("minLevel", minLevel);
            tag.putInt("maxCost", maxCost);
            tag.putBoolean("enabled", enabled);
            return tag;
        }

        @Override
        public RollingEnchantment fromTag(CompoundTag tag) {
            Via.tagRollingEnchantment(tag, this);
            return this;
        }
    }
}
