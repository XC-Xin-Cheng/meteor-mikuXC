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

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Util;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * meteor-miku 的本地文件底座：统一决定文件放哪、怎么读怎么写、旧方案集怎么分隔。
 *
 * <p>所有文本文件都放在<b>游戏根目录</b>（和 {@code mods}、{@code config} 同级）里的
 * {@code meteor-miku} 文件夹中：</p>
 *
 * <ul>
 *   <li>{@link MikuChatStore}：{@code Miku-Chat.txt} / {@code Miku-Chat-old.txt}；</li>
 *   <li>{@link OreSelectStore}：{@code Select-Ore.txt} / {@code Select-Ore-old.txt}。</li>
 * </ul>
 *
 * <p>文件夹不存在会自动建出来；万一建不出来（目录被占用等）就退回游戏根目录写，
 * 保证用户的数据不会因为一个文件夹建不出来而丢掉。</p>
 *
 * <p>旧方案集（备份）文件里，每一份以一行
 * {@code # ===== 方案集 #3 · 2026-02-14 12:34:56 =====} 开头，后面的内容块由各存储类
 * 自己决定；以 {@code #} 开头的行在读取时当注释忽略，所以手写注释不会出错。</p>
 */
public final class MikuStoreFiles {

    /** 存放所有 meteor-miku 文本文件的子文件夹名。 */
    public static final String FOLDER_NAME = "meteor-miku";

    /** 旧方案集分隔行的开头，形如 {@code # ===== 方案集 #3 · 时间 =====}。 */
    public static final String SNAPSHOT_PREFIX = "# ===== 方案集 #";
    /** 分隔行里序号与时间之间的分隔符。 */
    public static final String SNAPSHOT_MIDDLE = " · ";
    /** 分隔行的结尾。 */
    public static final String SNAPSHOT_SUFFIX = " =====";
    /** 分隔行里的时间格式。 */
    public static final DateTimeFormatter SNAPSHOT_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private MikuStoreFiles() {
    }

    /** 游戏根目录（.minecraft 或整合包根目录）。 */
    public static File gameDir() {
        return FabricLoader.getInstance().getGameDir().toFile();
    }

    /**
     * 存放方案文件的 {@code meteor-miku} 文件夹，不存在就建出来。
     * 建不出来时退回游戏根目录，避免写文件直接失败。
     */
    public static File storeDir() {
        File dir = new File(gameDir(), FOLDER_NAME);
        if (!dir.exists() && !dir.mkdirs() && !dir.isDirectory()) return gameDir();
        return dir;
    }

    /** 把文本按 UTF-8 覆盖写到指定文件；父目录不存在会先建出来。 */
    public static boolean writeText(File file, String text) {
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) return false;
            Files.write(file.toPath(), text.getBytes(StandardCharsets.UTF_8));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** 读一个文本文件的所有行；文件不存在、读不动时返回空列表。 */
    public static List<String> readAllLines(File file) {
        if (file == null || !file.isFile() || !file.canRead()) return new ArrayList<>();
        try {
            return Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /** 读文件并按分隔行切成一块块原始文本，保留用户手写的注释与空行。 */
    public static List<String> readBlocks(File file) {
        List<String> blocks = new ArrayList<>();
        StringBuilder current = null;

        for (String line : readAllLines(file)) {
            if (line.trim().startsWith(SNAPSHOT_PREFIX)) {
                if (current != null) blocks.add(current.toString());
                current = new StringBuilder();
            } else if (current == null) {
                // 分隔行之前的内容：早期版本直接写下的记录，单独成块
                if (line.isBlank()) continue;
                current = new StringBuilder();
            }
            current.append(line).append('\n');
        }
        if (current != null) blocks.add(current.toString());
        return blocks;
    }

    /** 取一块文本的第一行，用来读分隔行里的序号。 */
    public static String firstLine(String block) {
        if (block == null) return null;
        int end = block.indexOf('\n');
        return end < 0 ? block : block.substring(0, end);
    }

    /** 生成一行方案集分隔行。 */
    public static String snapshotHeader(int index, LocalDateTime time) {
        return SNAPSHOT_PREFIX + index + SNAPSHOT_MIDDLE + SNAPSHOT_TIME_FORMAT.format(time) + SNAPSHOT_SUFFIX;
    }

    /** 从分隔行里读出序号；读不出返回 0。 */
    public static int parseIndex(String header) {
        if (header == null || !header.startsWith(SNAPSHOT_PREFIX)) return 0;

        String body = header.substring(SNAPSHOT_PREFIX.length());
        int end = body.indexOf(SNAPSHOT_SUFFIX);
        if (end >= 0) body = body.substring(0, end);
        int sep = body.indexOf(SNAPSHOT_MIDDLE);
        String indexText = (sep >= 0 ? body.substring(0, sep) : body).trim();

        try {
            return Integer.parseInt(indexText);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** 从分隔行里读出时间文本；读不出返回 {@code null}（界面显示“时间未知”）。 */
    public static String parseTimeText(String header) {
        if (header == null) return null;

        String body = header;
        int end = body.indexOf(SNAPSHOT_SUFFIX);
        if (end >= 0) body = body.substring(0, end);
        int sep = body.lastIndexOf(SNAPSHOT_MIDDLE);
        if (sep < 0) return null;

        String time = body.substring(sep + SNAPSHOT_MIDDLE.length()).trim();
        return time.isEmpty() ? null : time;
    }

    /** 用资源管理器/访达打开 {@code meteor-miku} 文件夹。 */
    public static boolean openFolder() {
        try {
            Util.getPlatform().openFile(storeDir());
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
