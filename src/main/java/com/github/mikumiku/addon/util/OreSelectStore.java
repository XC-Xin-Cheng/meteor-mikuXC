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
package com.github.mikumiku.addon.util;

import java.io.File;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 「种子矿透」矿石选择的本地文本存储。
 *
 * <p>和 {@link MikuChatStore} 同一套做法：文件放在<b>游戏根目录</b>的
 * {@code meteor-miku} 文件夹里，主文件 {@code Select-Ore.txt}，一行一种矿石：</p>
 *
 * <pre>{@code 深层 绿宝石 on on}</pre>
 *
 * <p>四个字段依次是：</p>
 *
 * <ul>
 *   <li>层：{@code 浅层} / {@code 深层} / {@code 下界}（也认 {@code shallow/deep/nether}）；</li>
 *   <li>矿石：中文名（如 {@code 绿宝石}），也认 baritone 名（{@code emerald_ore}）与枚举名；</li>
 *   <li>连接：{@code on/off}，边框是否与相邻的同类矿石连成一体；</li>
 *   <li>显示：{@code on/off}，这种矿石画不画、挖不挖。</li>
 * </ul>
 *
 * <p>点矿石选择界面里的「重置」时，会先把当前选择追加成一份“旧选择方案集”到
 * {@code Select-Ore-old.txt}，再恢复默认选择；每份之间用一行
 * {@code # ===== 方案集 #N · 时间 =====} 隔开，读取用 {@link #loadHistory()}。
 * 两个文件不存在时会自动创建。</p>
 *
 * <p>颜色不属于这个文件（它存在 Meteor 自己的配置里），所以读写文件只覆盖
 * 「连接」和「显示」两项，用户调好的颜色不会被冲掉。</p>
 */
public final class OreSelectStore {

    /** 选择主文件，放在 {@code meteor-miku} 文件夹里。 */
    public static final String FILE_NAME = "Select-Ore.txt";
    /** 点重置时追加旧选择方案集的文件，同样在 {@code meteor-miku} 文件夹里。 */
    public static final String OLD_FILE_NAME = "Select-Ore-old.txt";

    /** {@code Select-Ore-old.txt} 里默认保留多少份旧选择方案集。 */
    public static final int DEFAULT_MAX_SNAPSHOTS = 20;
    /** 上限的最大值，防止手滑填太大把文件撑爆。 */
    public static final int HARD_MAX_SNAPSHOTS = 500;

    private OreSelectStore() {
    }

    public static File storeDir() {
        return MikuStoreFiles.storeDir();
    }

    public static File mainFile() {
        return new File(storeDir(), FILE_NAME);
    }

    public static File oldFile() {
        return new File(storeDir(), OLD_FILE_NAME);
    }

    /**
     * 按「层 → 矿石」的顺序列出所有合法的“矿石 + 层”，和选择界面里的分段顺序一致。
     * 写文件时按这个顺序逐行写出，读文件时也用它补全没写到的行（按 off 处理）。
     */
    public static List<Ore.OreKey> allKeys() {
        List<Ore.OreKey> keys = new ArrayList<>();
        for (Ore.OreLayer layer : Ore.OreLayer.values()) {
            for (Ore.OreType type : Ore.OreType.inLayer(layer)) {
                keys.add(Ore.OreKey.of(type, layer));
            }
        }
        return keys;
    }

    /** 拼出文件里的一行：{@code 深层 绿宝石 on on}。 */
    public static String toLine(Ore.OreKey key, boolean connected, boolean display) {
        return key.layer.shortName + " " + key.type.displayName + " "
            + (connected ? "on" : "off") + " " + (display ? "on" : "off");
    }

    /**
     * 把当前选择写进指定文件（UTF-8，覆盖写）：每种合法矿石一行，
     * 连接/显示按当前状态写 on 或 off。第一行是格式说明注释，读取时忽略。
     *
     * @return 写成功返回 true
     */
    public static boolean save(File file, OreListSetting setting) {
        if (file == null || setting == null) return false;

        Set<Ore.OreKey> selected = setting.get();
        StringBuilder sb = new StringBuilder();
        sb.append("# 格式：层 矿石 连接 显示（on=开，off=关），例如：深层 绿宝石 on on\n");
        for (Ore.OreKey key : allKeys()) {
            Ore.OreStyle style = setting.style(key);
            sb.append(toLine(key, style.connected, selected.contains(key))).append('\n');
        }
        return MikuStoreFiles.writeText(file, sb.toString());
    }

    /**
     * 从文件读回选择：文件里出现 {@code on} 的行才勾上，没写到的按关处理。
     * 只覆盖「连接」与「显示」，用户调好的颜色保持不动。
     *
     * @return 读到至少一行可识别的内容并写回设置时返回 true；文件没有可识别内容时返回 false，
     *         调用方据此保留当前选择，避免读失败把用户的选择清空
     */
    public static boolean load(File file, OreListSetting setting) {
        if (setting == null) return false;

        Set<Ore.OreKey> selected = new LinkedHashSet<>();
        Set<Ore.OreKey> connected = new HashSet<>();
        int parsed = 0;

        for (String line : MikuStoreFiles.readAllLines(file)) {
            Parsed parsedLine = parseLine(line);
            if (parsedLine == null) continue;
            parsed++;
            if (parsedLine.display) selected.add(parsedLine.key);
            if (parsedLine.connected) connected.add(parsedLine.key);
        }

        if (parsed == 0) return false;

        for (Ore.OreKey key : allKeys()) {
            setting.style(key).connected = connected.contains(key);
        }

        Set<Ore.OreKey> current = setting.get();
        current.clear();
        current.addAll(selected);
        setting.onChanged();
        return true;
    }

    /**
     * 确保主文件 {@link #FILE_NAME} 存在；不存在就按当前选择生成一份。
     *
     * @return 主文件（可能尚未成功创建，调用方按需自行判断）
     */
    public static File ensureMainFile(OreListSetting setting) {
        File main = mainFile();
        if (main.isFile()) return main;
        save(main, setting);
        return main;
    }

    /**
     * 确保备份文件 {@link #OLD_FILE_NAME} 存在；不存在就按当前选择建出第一份旧方案集。
     * 文件已存在时原样保留，绝不覆盖用户攒下来的历史记录。
     *
     * @return 备份文件（可能尚未成功创建，调用方按需自行判断）
     */
    public static File ensureOldFile(OreListSetting setting) {
        File old = oldFile();
        if (old.isFile()) return old;
        appendSnapshot(setting, DEFAULT_MAX_SNAPSHOTS);
        return old;
    }

    /**
     * 启用模块时用：补齐两个文件，并把主文件里的选择读回设置。
     *
     * <p>主文件不存在时先按当前选择建出来（首次安装就是把默认选择写进去），
     * 因此第一次打开就能在游戏根目录的 {@code meteor-miku} 文件夹里看到这两个文件。</p>
     *
     * @return 真的从文件读到了选择返回 true
     */
    public static boolean ensureAndLoad(OreListSetting setting) {
        File main = ensureMainFile(setting);
        boolean loaded = main.isFile() && load(main, setting);
        ensureOldFile(setting);
        return loaded;
    }

    /**
     * 把当前选择作为一个新的「旧选择方案集」<b>追加</b>到 {@link #OLD_FILE_NAME}，
     * 不覆盖之前的记录；超过 {@code maxSnapshots} 份时从最旧的一份开始丢。
     *
     * <p>这就是「重置」按钮背后的写入动作：重置前先把当前选择追加进去，再恢复默认。
     * 快照里只写勾了「显示」或「连接」的矿石，读的时候没写到的按 off 处理。</p>
     *
     * @return 写成功返回 true
     */
    public static boolean appendSnapshot(OreListSetting setting, int maxSnapshots) {
        File file = oldFile();
        List<String> blocks = MikuStoreFiles.readBlocks(file);

        int nextIndex = blocks.size() + 1;
        for (String block : blocks) {
            int index = MikuStoreFiles.parseIndex(MikuStoreFiles.firstLine(block));
            if (index >= nextIndex) nextIndex = index + 1;
        }

        StringBuilder block = new StringBuilder();
        block.append(MikuStoreFiles.snapshotHeader(nextIndex, LocalDateTime.now())).append('\n');
        if (setting != null) {
            Set<Ore.OreKey> selected = setting.get();
            for (Ore.OreKey key : allKeys()) {
                Ore.OreStyle style = setting.style(key);
                boolean display = selected.contains(key);
                if (!display && !style.connected) continue;
                block.append(toLine(key, style.connected, display)).append('\n');
            }
        }
        blocks.add(block.toString());

        int limit = Math.max(1, Math.min(HARD_MAX_SNAPSHOTS, maxSnapshots));
        while (blocks.size() > limit) {
            blocks.remove(0);
        }

        StringBuilder out = new StringBuilder();
        for (String part : blocks) {
            out.append(part);
        }
        return MikuStoreFiles.writeText(file, out.toString());
    }

    /**
     * 读出 {@link #OLD_FILE_NAME} 里的所有旧选择方案集，按文件顺序返回（最旧的在前，
     * 最新追加的在最后）。界面里展示时倒过来放即可。
     */
    public static List<OreSelectSnapshot> loadHistory() {
        List<OreSelectSnapshot> list = new ArrayList<>();
        List<String> blocks = MikuStoreFiles.readBlocks(oldFile());

        int order = 0;
        for (String block : blocks) {
            String header = null;
            Set<Ore.OreKey> selected = new LinkedHashSet<>();
            Set<Ore.OreKey> connected = new HashSet<>();

            for (String line : block.split("\n")) {
                String trimmed = line.trim();
                if (header == null && trimmed.startsWith(MikuStoreFiles.SNAPSHOT_PREFIX)) {
                    header = trimmed;
                    continue;
                }
                Parsed parsed = parseLine(trimmed);
                if (parsed == null) continue;
                if (parsed.display) selected.add(parsed.key);
                if (parsed.connected) connected.add(parsed.key);
            }

            order++;
            int index = MikuStoreFiles.parseIndex(header);
            if (index <= 0) index = order;
            list.add(new OreSelectSnapshot(index, MikuStoreFiles.parseTimeText(header), selected, connected));
        }
        return list;
    }

    /**
     * 用系统默认程序（记事本等）打开选择文件 {@link #FILE_NAME}，方便直接编辑。
     * 两个文件不存在时会先建出来再打开。
     *
     * @return 成功交给系统打开返回 true
     */
    public static boolean openConfigFile(OreListSetting setting) {
        File file = ensureMainFile(setting);
        ensureOldFile(setting);

        try {
            net.minecraft.util.Util.getPlatform().openFile(file);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 解析一行：空行、注释、字段不足、层或矿石认不出时返回 {@code null}。
     * 矿石名允许带空格，所以从第 1 个字段到「倒数第 2 个字段」之间都算矿石名，
     * 最后两个字段固定是「连接」和「显示」。
     */
    private static Parsed parseLine(String line) {
        if (line == null) return null;
        String trimmed = line.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("#")) return null;

        String[] fields = trimmed.split("\\s+");
        if (fields.length < 4) return null;

        Ore.OreLayer layer = Ore.OreLayer.byToken(fields[0]);
        if (layer == null) return null;

        StringBuilder oreName = new StringBuilder();
        for (int i = 1; i < fields.length - 2; i++) {
            if (oreName.length() > 0) oreName.append(' ');
            oreName.append(fields[i]);
        }

        Ore.OreType type = Ore.OreType.byId(oreName.toString());
        if (type == null || !type.hasLayer(layer)) return null;

        boolean connected = isOn(fields[fields.length - 2]);
        boolean display = isOn(fields[fields.length - 1]);
        return new Parsed(Ore.OreKey.of(type, layer), connected, display);
    }

    /** {@code on/true/1/yes} 视为开，其余视为关。 */
    private static boolean isOn(String token) {
        if (token == null) return false;
        String value = token.trim().toLowerCase(Locale.ROOT);
        return value.startsWith("on") || value.equals("true") || value.equals("1") || value.equals("yes");
    }

    /** 一行解析结果。 */
    private record Parsed(Ore.OreKey key, boolean connected, boolean display) {
    }
}
