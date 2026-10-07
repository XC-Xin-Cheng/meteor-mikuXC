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
import com.github.mikumiku.addon.BaseModule;
import com.github.mikumiku.addon.util.StringMapSetting;
import com.github.mikumiku.addon.util.BaritoneUtil;
import com.github.mikumiku.addon.util.ChatUtils;
import com.github.mikumiku.addon.util.MikuUtil;
import meteordevelopment.meteorclient.events.entity.player.InteractBlockEvent;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PearlBot extends BaseModule {

    public PearlBot() {
        super(CATEGORY_MIKU_PRO, "珍珠点大管家", "自动管理上百人的珍珠点都可以，随时私聊拉");
    }

    // 设置组
    private final SettingGroup sgGeneral = settings.getDefaultGroup();


    private final Setting<String> keyword = sgGeneral.add(new StringSetting.Builder()
        .name("关键字")
        .description("包含该关键字的私聊就拉")
        .defaultValue("拉我")
        .build()
    );

    private final Setting<Map<String, String>> pearls = sgGeneral.add(new StringMapSetting.Builder()
        .name("珍珠点")
        .description("左边用户名，右边是活板门坐标。")
        .build()
    );

    private final Setting<Boolean> setup = sgGeneral.add(new BoolSetting.Builder()
        .name("添加珍珠点模式")
        .description("该模式可以添加珍珠点")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> searchSigns = sgGeneral.add(new BoolSetting.Builder()
        .name("智能识别告示牌")
        .description("若未找到珍珠记录，则尝试从上方告示牌获取珍珠主人的名字")
        .defaultValue(true)
        .build()
    );


    private final Setting<Integer> searchRadius = sgGeneral.add(new IntSetting.Builder()
        .name("搜索告示牌半径")
        .description("搜索告示牌的范围半径。")
        .defaultValue(64)
        .min(8)
        .sliderRange(8, 128)
        .visible(() -> searchSigns.get())
        .build()
    );

    private final Setting<Integer> timeout = sgGeneral.add(new IntSetting.Builder()
        .name("赶路超时时间")
        .description("允许拉珍珠的最大时间tick。")
        .defaultValue(200)
        .min(20)
        .sliderRange(20, 20000)
        .build()
    );
    private final Setting<Boolean> distanceCheck = sgGeneral.add(new BoolSetting.Builder()
        .name("太远的不拉")
        .description("当与珍珠距离过远时自动拒绝请求。")
        .defaultValue(true)
        .build()
    );
    private final Setting<Integer> maxDistance = sgGeneral.add(new IntSetting.Builder()
        .name("最大距离")
        .description("允许拉珍珠的最大距离。")
        .defaultValue(120)
        .min(10)
        .sliderRange(10, 300)
        .visible(() -> distanceCheck.get())
        .build()
    );

    private final Setting<Boolean> returnToStartPos = sgGeneral.add(new BoolSetting.Builder()
        .name("使用后返回出发点")
        .description("拉珍珠后是否返回起始位置。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> msgregrex = sgGeneral.add(new BoolSetting.Builder()
        .name("自定义私聊正则")
        .description("服务器的私聊正则，每个服务器可能不一样。")
        .defaultValue(false)
        .build()
    );
    private final Setting<String> regrex = sgGeneral.add(new StringSetting.Builder()
        .name("私聊正则")
        .description("私聊正则,默认是3C的格式。")
        .defaultValue("^📨\\s+([^➡]+?)\\s+➡\\s+(.+)$")
        .visible(() -> msgregrex.get())
        .build()
    );
    private BlockPos startPos = null;
    private boolean loading = false;

    private BlockPos taskPos = null;

    private String taskName = "";

    // 当前已加载的 tick 计数
    private int loadingTicks = 0;
    // 最大超时 tick（200 tick = 约10秒）
    private static final int MAX_LOADING_TICKS = 200;

    // 缓存系统 - 存储珍珠ID和对应的主人名字
    public final Map<UUID, String> pearlOwnerCache = new HashMap<>();

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (!Utils.isLoading() && isActive()) {

        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {

        // 如果正在 loading，就计时
        if (loading) {
            loadingTicks++;
            if (loadingTicks > timeout.get()) {
                info("拉珍珠加载超时，自动重置");
                loading = false;
                loadingTicks = 0;
                BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().cancelEverything();
                return;
            }
        }

        if (taskPos != null) {
            if (MikuUtil.pathTo(taskPos, 4)) {
                pull(taskPos, taskName);

                if (returnToStartPos.get() && startPos != null) {
                    MikuUtil.pathTo(startPos, 0);
                }
            }
        }

    }

    @Override
    public void onActivate() {
        super.onActivate();

        if (setup.get()) {
            ChatUtils.sendMsg("请精确右键点击珍珠点的活板门方块来添加，不用担心触发。");
        }

    }

    @EventHandler
    private void onInteractBlock(InteractBlockEvent event) {
        // 只在引导阶段处理交互
        if (!setup.get()) return;

        BlockPos pos = event.result.getBlockPos();
        Block block = mc.level.getBlockState(pos).getBlock();

        // 检查是否是活板门
        if (block instanceof TrapDoorBlock) {
            addPearl("用户名" + pearls.get().size(), pos);
            event.cancel();
        }
    }

    @EventHandler
    private void onChat(ReceiveMessageEvent event) {
        if (mc.player == null || mc.level == null) return;

        String msg = event.getMessage().getString();
        ParsedMessage parsed = parsePrivateMessage(msg);
        if (parsed == null) return;

        String username = parsed.username;
        String content = parsed.content;
        if (!content.contains(keyword.get())) return;

        info("收到来自 " + username + " 的拉珍珠请求。");

        // Try to find pearl by username
        BlockPos pearl = stringToBlockPos(pearls.get().get(username));
        if (pearl == null && searchSigns.get()) {
            pearl = findPearlBySign(username);
            if (pearl != null) {
                pearls.get().put(username, blockPosToString(pearl));
                info("已通过告示牌记录珍珠: " + blockPosToString(pearl));
            }
        }

        if (pearl == null) {
            whisper(username, "没找到你的珍珠啊 " + username);
            return;
        }

        // Distance check
        BlockPos playerPos = mc.player.blockPosition();
        double distance = playerPos.distSqr(pearl);

        if (distanceCheck.get() && distance > maxDistance.get() * maxDistance.get()) {
            whisper(username, "我现在离你的珍珠太远，距离 " + distance);
            return;
        }

        // Simulate "load pearl"
        info("开始执行珍珠拉任务: " + username);
        loadPearl(pearl, username, distance);
    }


    // Search signs in world for a player name
    private BlockPos findPearlBySign(String username) {
        int px = mc.player.blockPosition().getX();
        int py = mc.player.blockPosition().getY();
        int pz = mc.player.blockPosition().getZ();

        int r = searchRadius.get();
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -4; dy <= 4; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    BlockPos pos = new BlockPos(px + dx, py + dy, pz + dz);
                    var blockEntity = mc.level.getBlockEntity(pos);
                    if (blockEntity instanceof net.minecraft.world.level.block.entity.SignBlockEntity sign) {
                        for (var line : sign.getFrontText().getMessages(false)) {
                            if (line.getString().toLowerCase().contains(username.toLowerCase())) {
                                info("找到包含名字的告示牌: " + pos.toShortString());
                                return pos.below(); // 珍珠位置通常在告示牌下方
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    // Send whisper message (client-side)
    private void whisper(String username, String msg) {
        if (mc.player == null) return;
        mc.player.connection.sendChat("/msg " + username + " " + msg);
    }

    // ======================
    // Helpers
    // ======================
    public ParsedMessage parsePrivateMessage(String msg) {
        String regstr = "^📨\\s+([^➡]+?)\\s+➡\\s+(.+)$";

        if (msgregrex.get()) {
            regstr = regrex.get();
        }

        Pattern p = Pattern.compile(regstr);
        Matcher m = p.matcher(msg);
        if (m.matches()) {
            return new ParsedMessage(m.group(1).trim(), m.group(2).trim());
        }
        return null;
    }

    public ParsedMessage parsePrivateMessageOrg(String msg) {
        // 匹配格式：username whispers: message
        String regex = "^([A-Za-z0-9_]+)\\s+whispers:\\s+(.+)$";
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(regex);
        java.util.regex.Matcher matcher = pattern.matcher(msg);

        if (matcher.matches()) {
            String username = matcher.group(1).trim();
            String content = matcher.group(2).trim();
            return new ParsedMessage(username, content);
        }

        return null; // 不符合私信格式
    }

    public void addPearl(String id, BlockPos pos) {
        pearls.get().put(id, blockPosToString(pos));
        ChatUtils.sendMsg("已添加珍珠 §b" + id + "§f -> " + pos.toShortString());
        ChatUtils.sendMsg("请自行修改§b" + id + "§f 为用户昵称");

    }

    public void loadPearl(BlockPos pearl, String username, double distance) {
        if (loading) {
            warning("当前已有珍珠在拉中。");
            return;
        }
        BlockPos pos = pearl;
        if (pos == null) {
            error("未找到 §c" + username + "§f 的珍珠。");
            return;
        }
        if (mc.player == null || mc.level == null) {
            error("玩家未在线，无法执行。");
            return;
        }

        startPos = mc.player.blockPosition();
        loading = true;
        info("§a正在拉珍珠: " + username);
        whisper(username, "正在路上，距离：" + distance);


        pull(pos, username);


    }

    private void pull(BlockPos pos, String id) {
        double distance = mc.player.distanceToSqr(pos.getX(), pos.getY(), pos.getZ());
        if (distance < 25) {
            BaritoneUtil.clickBlock(pos, Direction.UP, true, InteractionHand.MAIN_HAND, BaritoneUtil.SwingSide.All);
            info("§a珍珠已成功拉！ID: §b" + id);
            loading = false;
            taskPos = null;
            return;
        }

        taskPos = pos;
        taskName = id;


        // 模拟拉完成回调
        mc.execute(() -> {
            info("§a珍珠已成功拉！ID: §b" + id);
            if (returnToStartPos.get() && startPos != null) {
                mc.execute(() -> {
                    mc.player.teleportTo(
                        startPos.getX() + 0.5,
                        startPos.getY(),
                        startPos.getZ() + 0.5
                    );
                    info("§7已返回起始位置。");
                });
            }
            loading = false;
        });
    }


    // BlockPos → String
    public static String blockPosToString(BlockPos pos) {
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

    // String → BlockPos
    public static BlockPos stringToBlockPos(String str) {
        if (str == null) {
            return null;
        }

        try {
            String[] parts = str.trim().split("\\s+");
            if (parts.length != 3) throw new IllegalArgumentException("Invalid BlockPos format");
            int x = Integer.parseInt(parts[0]);
            int y = Integer.parseInt(parts[1]);
            int z = Integer.parseInt(parts[2]);
            return new BlockPos(x, y, z);
        } catch (Exception e) {
            ChatUtils.sendMsg("无法解析珍珠位置字符: " + str);
        }
        return null;
    }

    public static String getCurrentServerAddress() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getCurrentServer() != null) {
            return mc.getCurrentServer().ip;
        }
        return "unknown";
    }


    // Data record
    public static class ParsedMessage {
        public final String username;
        public final String content;

        public ParsedMessage(String username, String content) {
            this.username = username;
            this.content = content;
        }
    }
}
