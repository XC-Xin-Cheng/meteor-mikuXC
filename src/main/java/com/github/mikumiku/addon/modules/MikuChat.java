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
import com.github.mikumiku.addon.util.ChatScheme;
import com.github.mikumiku.addon.util.ChatSchemeListSetting;
import com.github.mikumiku.addon.util.MikuChatStore;
import com.github.mikumiku.addon.util.MikuCompat;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.entity.player.Player;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 「Miku 聊天」——可自定义多套聊天方案。
 *
 * <p>每套方案独立设置发送间隔、消息列表、顺序/随机。多套方案可以同时启用，
 * 互不干扰，例如“打招呼”和“夸夸机”两套一起跑。</p>
 *
 * <p>消息里的 {@code %s} 默认不去搜索玩家：直接发送，{@code %s} 占位符会被去掉。
 * 勾上「范围内搜索玩家」后才按老行为在「寻找范围」内找最近的玩家来替换，附近一个玩家
 * 都没有时这一轮先跳过，不会把 {@code %s} 原样发到聊天栏。</p>
 *
 * <p>每套方案的间隔后面都有一个单位下拉框，各选各的：毫秒（最长 10 分钟）、
 * 秒（最长 5 小时）、分（最长 10 小时）；不管选哪一档，方案文件里存的都是毫秒。</p>
 *
 * <p>方案在配置界面里展开成一张可编辑的表，直接加一行、填消息即可，
 * 不需要改代码。默认方案见 {@link #defaultSchemes()}：游戏根目录 meteor-miku 文件夹里还没有
 * {@code Miku-Chat.txt} / {@code Miku-Chat-old.txt} 时，就按这套默认值创建。</p>
 */
public class MikuChat extends BaseModule {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<List<ChatScheme>> schemes = sgGeneral.add(new ChatSchemeListSetting.Builder()
        .name("聊天方案")
        .description("可添加多套方案：勾选启用、改名字、填间隔（每套方案的间隔后面都能切毫秒/秒/分）、写多条消息（用 | 分隔）、切顺序/随机；多套可同时生效")
        .defaultValue(defaultSchemes())
        // 走方法引用而不是直接读 maxHistory 字段：该字段在下面才声明，
        // 字段初始化器里直接引用会触发 illegal forward reference
        .historyLimit(this::maxHistoryValue)
        .build()
    );

    /**
     * 内置默认方案：35 套，间隔 50000 ms，随机且不重复，默认全部关闭。
     *
     * <p>这套默认值同时用于三处：首次安装时的设置初值、点「重置」后恢复的
     * 内容、以及游戏根目录 meteor-miku 文件夹里那两个方案文件不存在时自动创建的内容。</p>
     */
    public static List<ChatScheme> defaultSchemes() {
        String[][] def = {
            {"方案1", "爹再狠一点干我"},
            {"方案2", "再深点！爹再深一点！"},
            {"方案3", "好爽！爹的太大了！"},
            {"方案4", "我爱你的大屌 %s！"},
            {"方案5", "爹在我射出来前别停！"},
            {"方案6", "你为了我硬到不行"},
            {"方案7", "想把我菊花撑大吗 %s？"},
            {"方案8", "我爱你爹"},
            {"方案9", "操到我菊花开花"},
            {"方案10", "%s 超爱我的菊花"},
            {"方案11", "我用紧致的菊花让 %s 爽到不行"},
            {"方案12", "爹的大屌又粗又多汁！"},
            {"方案13", "求你用尽全力操我"},
            {"方案14", "我是 %s 的专属娘受精壶！"},
            {"方案15", "求你把滚烫的汁射进我最深处爹！"},
            {"方案16", "我爱 %s 在我体内的感觉！"},
            {"方案17", "%s 一看到我屁股就硬到不行！"},
            {"方案18", "%s 特别爱狠狠干我的菊花！"},
            {"方案19", "你就不能把最后一句说出来吗"},
            {"方案20", "乖乖听话，让爹爽"},
            {"方案21", "老子最爱干你屁眼 %s！"},
            {"方案22", "把你菊花献给爹！"},
            {"方案23", "最爱你被我操到流前水的样子 %s"},
            {"方案24", "像个乖狗一样给爹舔到底"},
            {"方案25", "过来骑上爹的家伙 %s"},
            {"方案26", "最爱你含着我看着我的样子 %s"},
            {"方案27", "%s 被我干的时候可爱到爆"},
            {"方案28", "%s 的菊花紧到爆！"},
            {"方案29", "%s 挨操的时候真像个乖狗"},
            {"方案30", "最爱你骑在我上面扭屁股"},
            {"方案31", "%s 挨干的时候娇喘得贼奶"},
            {"方案32", "%s 是天下第一精壶！"},
            {"方案33", "%s 永远饥渴着等爹的家伙"},
            {"方案34", "每次看到 %s 我都硬得像石头"},
            {"方案35", "你就不能把最后一句说出来吗"}
        };

        List<ChatScheme> list = new ArrayList<>(def.length);
        for (String[] entry : def) {
            ChatScheme scheme = new ChatScheme(entry[0], false, 50000, true, entry[1]);
            scheme.avoidRepeat = true;
            list.add(scheme);
        }
        return list;
    }

    /**
     * 是否在「寻找范围」内搜索玩家来替换 {@code %s}。
     *
     * <p>勾上 = 老行为：消息里带 {@code %s} 时，找范围内最近的玩家并替换成他的名字，
     * 附近一个玩家都没有就跳过这一轮。不勾 = 不做搜索，消息直接发出去，{@code %s}
     * 占位符会被去掉，不会原样显示在聊天栏。</p>
     */
    private final Setting<Boolean> searchPlayer = sgGeneral.add(new BoolSetting.Builder()
        .name("范围内搜索玩家")
        .description("开启后消息里的 %s 会替换成「寻找范围」内最近的玩家名，找不到玩家就跳过这一轮；关闭则不做搜索，消息直接发送（%s 会被去掉）")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("寻找范围")
        .description("把 %s 替换成玩家名时，最多在多少格内找最近的玩家")
        .defaultValue(16.0)
        .min(1.0)
        .sliderRange(1.0, 64.0)
        .visible(searchPlayer::get)
        .build()
    );

    private final Setting<Boolean> ignoreFriends = sgGeneral.add(new BoolSetting.Builder()
        .name("忽略好友")
        .description("找 %s 的替换对象时不把好友算进去")
        .defaultValue(false)
        .visible(searchPlayer::get)
        .build()
    );

    private final Setting<Boolean> pauseInGui = sgGeneral.add(new BoolSetting.Builder()
        .name("界面中暂停")
        .description("打开背包/聊天/设置界面时先不发消息，避免关界面的瞬间补发一串")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> notify = sgGeneral.add(new BoolSetting.Builder()
        .name("启用提示")
        .description("启用时在聊天栏提示当前有几套方案在发消息")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> autoSave = sgGeneral.add(new BoolSetting.Builder()
        .name("自动保存")
        .description("按下面的间隔，自动把所有聊天方案写入游戏根目录 meteor-miku 文件夹的 " + MikuChatStore.FILE_NAME)
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> autoSaveInterval = sgGeneral.add(new IntSetting.Builder()
        .name("自动保存间隔（秒）")
        .description("自动保存的间隔，单位秒；默认 1200 秒 = 20 分钟")
        .defaultValue(1200)
        .min(10)
        .sliderRange(10, 3600)
        .visible(autoSave::get)
        .build()
    );

    /**
     * {@code Miku-Chat-old.txt} 里最多保留多少份旧方案集。
     *
     * <p>每点一次「重置」就会追加一份，超过这个数就从最旧的一份开始删。填再大也会被
     * {@link MikuChatStore#HARD_MAX_SNAPSHOTS} 夹住，避免文件无限膨胀。</p>
     */
    private final Setting<Integer> maxHistory = sgGeneral.add(new IntSetting.Builder()
        .name("旧方案集上限")
        .description("Miku-Chat-old.txt 里最多保留多少份「重置」前的方案集；超出时从最旧的一份开始丢")
        .defaultValue(MikuChatStore.DEFAULT_MAX_SNAPSHOTS)
        .min(1)
        .sliderRange(1, 100)
        .build()
    );

    /** 给 {@link ChatSchemeListSetting} 用的旧方案集上限；设置项还没就绪时回落到默认值。 */
    private int maxHistoryValue() {
        Integer value = maxHistory.get();
        return value == null ? MikuChatStore.DEFAULT_MAX_SNAPSHOTS : value;
    }

    /** 下一次自动保存的时间戳（毫秒）。 */
    private long nextAutoSaveAt = 0L;

    public MikuChat() {
        super(BaseModule.CATEGORY, "Miku 聊天",
            "可自定义多套聊天方案：每套独立设置间隔、消息、顺序/随机；消息里的 %s 可选择是否替换成附近玩家名（默认直接发送）");
    }

    @Override
    public void onActivate() {
        super.onActivate();

        // 方案文本以游戏根目录的 Miku-Chat.txt 为准：有文件就读进来，没文件就先按当前方案建一份
        loadSchemesFromFile();

        long now = System.currentTimeMillis();
        int enabled = 0;
        for (ChatScheme scheme : schemes.get()) {
            scheme.resetRuntime(now);
            if (scheme.enabled) enabled++;
        }
        nextAutoSaveAt = now + autoSaveIntervalMs();

        if (notify.get()) {
            info("Miku 聊天已启用，当前 %d 套方案在发消息（方案文件：meteor-miku/%s）", enabled, MikuChatStore.FILE_NAME);
        }
    }

    /**
     * 从 {@code Miku-Chat.txt} 载入方案，并把该有的文件补齐。
     *
     * <p>文件存在且至少有 1 行能解析时，用它替换当前方案；文件存在但解析不出方案时保持现状，
     * 不覆盖用户文件；文件不存在时按当前方案（首次即 {@link #defaultSchemes()}）写一份出去，
     * 方便用户直接编辑。备份文件 {@code Miku-Chat-old.txt} 不存在时同样建一份。旧版
     * {@code Miku-Chet.txt} 会在这一步自动迁到新名字，用户已有的方案不会丢。</p>
     */
    private void loadSchemesFromFile() {
        File main = MikuChatStore.ensureMainFile(schemes.get());
        List<ChatScheme> loaded = MikuChatStore.load(main);
        if (!loaded.isEmpty()) {
            List<ChatScheme> list = schemes.get();
            list.clear();
            list.addAll(loaded);
        }

        // 备份文件也补一份，用户在游戏根目录能直接看到这两个文件
        MikuChatStore.ensureOldFile(schemes.get());
    }

    /** 自动保存间隔换算成毫秒，至少 1 秒。 */
    private long autoSaveIntervalMs() {
        return Math.max(1, autoSaveInterval.get()) * 1000L;
    }

    /** 到点就把当前所有方案写回 Miku-Chat.txt；关掉「自动保存」时什么都不做。 */
    private void autoSaveTick() {
        if (!autoSave.get()) return;

        long now = System.currentTimeMillis();
        if (now < nextAutoSaveAt) return;

        MikuChatStore.save(MikuChatStore.mainFile(), schemes.get());
        nextAutoSaveAt = now + autoSaveIntervalMs();
    }

    @Override
    public void onDeactivate() {
        for (ChatScheme scheme : schemes.get()) {
            scheme.nextSendAt = 0L;
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        autoSaveTick();

        if (mc.player == null || mc.level == null) return;
        if (pauseInGui.get() && MikuCompat.screen() != null) return;

        long now = System.currentTimeMillis();
        List<ChatScheme> list = schemes.get();

        for (ChatScheme scheme : list) {
            // 在界面上刚勾上/取消：重新计时，避免一点开就立刻发
            if (scheme.enabled != scheme.wasEnabled) {
                scheme.wasEnabled = scheme.enabled;
                scheme.nextSendAt = now + scheme.clampedIntervalMs();
            }

            if (!scheme.enabled || !scheme.hasMessages()) continue;
            if (now < scheme.nextSendAt) continue;

            String message = scheme.pickMessage();
            if (message == null) continue;

            if (message.contains("%s")) {
                if (searchPlayer.get()) {
                    Player target = nearestPlayer();
                    if (target == null) {
                        // 消息里必须要名字但附近没有玩家，这一轮先跳过，不把 %s 原样发出去
                        scheme.nextSendAt = now + scheme.clampedIntervalMs();
                        continue;
                    }
                    message = message.replace("%s", target.getName().getString());
                } else {
                    // 关闭「范围内搜索玩家」：不找玩家，消息直接发；%s 占位符去掉，避免聊天栏出现 “%s”
                    message = stripPlayerPlaceholder(message);
                    if (message.isEmpty()) {
                        scheme.nextSendAt = now + scheme.clampedIntervalMs();
                        continue;
                    }
                }
            }

            ChatUtils.sendPlayerMsg(message);
            scheme.nextSendAt = now + scheme.clampedIntervalMs();
        }
    }

    /** 在设定范围内找最近的、不是自己的玩家；没有则返回 null。 */
    private Player nearestPlayer() {
        Player closest = null;
        double closestSq = range.get() * range.get();

        for (Player player : mc.level.players()) {
            if (player == mc.player) continue;
            if (ignoreFriends.get() && Friends.get().isFriend(player)) continue;

            double distSq = mc.player.distanceToSqr(player);
            if (distSq <= closestSq) {
                closestSq = distSq;
                closest = player;
            }
        }

        return closest;
    }

    /**
     * 关闭「范围内搜索玩家」时，把消息里的 {@code %s} 去掉再发。
     *
     * <p>顺手把去掉占位符后留下的多余空格、以及标点前多出来的空格收拾干净，
     * 例如「大屌 %s！」→「大屌！」。整条消息只剩占位符时返回空串，调用方会跳过这一轮。</p>
     */
    private static String stripPlayerPlaceholder(String message) {
        String cleaned = message.replace("%s", "");
        cleaned = cleaned.replaceAll("\\s+([，。！？、；：,.!?;:])", "$1");
        return cleaned.replaceAll("\\s{2,}", " ").trim();
    }
}
