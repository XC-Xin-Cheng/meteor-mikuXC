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

import java.awt.FileDialog;
import java.awt.Frame;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 「Miku 聊天」的本地文本存储。
 *
 * <p>所有方案放在<b>游戏根目录</b>（和 {@code mods}、{@code config} 同级）里的
 * {@code meteor-miku} 文件夹中，主文件 {@code Miku-Chat.txt}，一行一套方案，
 * 方便直接用记事本改。格式为：</p>
 *
 * <pre>{@code on1 方案名 4000 要发送的消息 on2 on3}</pre>
 *
 * <ul>
 *   <li>{@code on1/off1}：这套方案是否启用</li>
 *   <li>方案名：不能带空格（写入时会把空格换成下划线）</li>
 *   <li>间隔：毫秒</li>
 *   <li>消息：可写空格；多条消息之间用 {@code |} 分隔</li>
 *   <li>{@code on2/off2}：是否随机挑一条</li>
 *   <li>{@code on3/off3}：随机时是否避免和上一条重复</li>
 *   <li>单位（可选）：行尾的 {@code s} 或 {@code min}，表示这套方案在界面上选的填写单位；
 *       毫秒档不写，所以老格式的行照旧能读</li>
 * </ul>
 *
 * <p>点「重置」时<b>不会清空</b>备份文件，而是把当前整套方案<b>追加</b>成一份新的
 * “旧方案集”到同文件夹的 {@code Miku-Chat-old.txt}，再用一行分隔行和之前的记录隔开；
 * 文件里最多保留多少份由用户自己定（超出时从最旧的一份开始丢）。这两个文件如果
 * 不存在，会在启用模块或点「打开配置文件」时按默认方案自动创建。</p>
 *
 * <p>老的安装把这两个文件直接放在游戏根目录：首次启用时会自动搬进
 * {@code meteor-miku} 文件夹，用户的方案不会丢。</p>
 *
 * <p>备份文件的格式：每份旧方案集以一行
 * {@code # ===== 方案集 #3 · 2026-02-14 12:34:56 =====} 开头，后面跟着当时所有方案行；
 * 这种以 {@code #} 开头的行会被 {@link ChatScheme#fromLine(String)} 当作注释忽略，
 * 所以旧版本读这个文件也不会出错。读取用 {@link #loadHistory()}。</p>
 */
public final class MikuChatStore {

    /** 存放方案文件的 {@code meteor-miku} 文件夹名。 */
    public static final String FOLDER_NAME = MikuStoreFiles.FOLDER_NAME;

    /** 方案主文件，放在 {@code meteor-miku} 文件夹里。 */
    public static final String FILE_NAME = "Miku-Chat.txt";
    /** 点重置时追加旧方案集的文件，同样在 {@code meteor-miku} 文件夹里。 */
    public static final String OLD_FILE_NAME = "Miku-Chat-old.txt";
    /** 旧版（r63 及以前）的写法，读到就自动迁到 {@link #FILE_NAME}，避免用户丢方案。 */
    public static final String LEGACY_FILE_NAME = "Miku-Chet.txt";

    /** 旧方案集分隔行的开头，形如 {@code # ===== 方案集 #3 · 时间 =====}。 */
    public static final String SNAPSHOT_PREFIX = MikuStoreFiles.SNAPSHOT_PREFIX;
    /** 分隔行里序号与时间之间的分隔符。 */
    public static final String SNAPSHOT_MIDDLE = MikuStoreFiles.SNAPSHOT_MIDDLE;
    /** 分隔行的结尾。 */
    public static final String SNAPSHOT_SUFFIX = MikuStoreFiles.SNAPSHOT_SUFFIX;

    /** 用户没自定义时，{@code Miku-Chat-old.txt} 里默认保留多少份旧方案集。 */
    public static final int DEFAULT_MAX_SNAPSHOTS = 20;
    /** 上限的最大值，防止手滑填太大把文件撑爆。 */
    public static final int HARD_MAX_SNAPSHOTS = 500;

    private MikuChatStore() {
    }

    /** 游戏根目录（.minecraft 或整合包根目录）。 */
    public static File gameDir() {
        return MikuStoreFiles.gameDir();
    }

    /** 存放方案文件的 {@code meteor-miku} 文件夹。 */
    public static File storeDir() {
        return MikuStoreFiles.storeDir();
    }

    public static File mainFile() {
        return new File(storeDir(), FILE_NAME);
    }

    public static File oldFile() {
        return new File(storeDir(), OLD_FILE_NAME);
    }

    /** 旧版文件名对应的文件（游戏根目录），仅用于自动迁移。 */
    public static File legacyFile() {
        return new File(gameDir(), LEGACY_FILE_NAME);
    }

    /**
     * 确保主文件 {@link #FILE_NAME} 存在，并把旧位置的方案自动迁过来。
     *
     * <ul>
     *   <li>主文件已在：原样返回；</li>
     *   <li>主文件不在、游戏根目录有老版 {@code Miku-Chat.txt}（或更老的
     *       {@code Miku-Chet.txt}）：优先直接改名搬进 {@code meteor-miku} 文件夹，
     *       改名失败（被占用等）就改成读旧写新，旧文件保留不删；</li>
     *   <li>都不在：按传进来的方案生成一份主文件。</li>
     * </ul>
     *
     * @param schemes 文件都不存在时用来生成初值；可为 {@code null}
     * @return 主文件（可能尚未成功创建，调用方按需自行判断）
     */
    public static File ensureMainFile(List<ChatScheme> schemes) {
        File main = mainFile();
        if (main.isFile()) return main;

        for (File legacy : new File[]{ new File(gameDir(), FILE_NAME), legacyFile() }) {
            if (!legacy.isFile()) continue;
            try {
                File parent = main.getParentFile();
                if (parent != null && !parent.exists()) parent.mkdirs();
                java.nio.file.Files.move(legacy.toPath(), main.toPath());
                return main;
            } catch (Exception ignored) {
                // 改名失败就退化成读旧写新，旧文件留着，不删用户数据
                List<ChatScheme> loaded = load(legacy);
                save(main, loaded.isEmpty() ? schemes : loaded);
                return main;
            }
        }

        save(main, schemes);
        return main;
    }

    /**
     * 确保备份文件 {@link #OLD_FILE_NAME} 存在；不存在就把游戏根目录的老备份搬过来，
     * 都没有时按传进来的方案建出第一份旧方案集。文件已存在时<b>原样保留</b>，
     * 绝不覆盖用户攒下来的历史记录。
     *
     * @param schemes 用来生成初值；可为 {@code null}
     * @return 备份文件（可能尚未成功创建，调用方按需自行判断）
     */
    public static File ensureOldFile(List<ChatScheme> schemes) {
        File old = oldFile();
        if (old.isFile()) return old;

        File legacy = new File(gameDir(), OLD_FILE_NAME);
        if (legacy.isFile()) {
            // 老备份直接搬到新文件夹；搬不动就复制内容，老文件保留
            try {
                File parent = old.getParentFile();
                if (parent != null && !parent.exists()) parent.mkdirs();
                java.nio.file.Files.move(legacy.toPath(), old.toPath());
                return old;
            } catch (Exception ignored) {
                List<String> lines = MikuStoreFiles.readAllLines(legacy);
                if (!lines.isEmpty() && MikuStoreFiles.writeText(old, String.join("\n", lines) + "\n")) {
                    return old;
                }
            }
        }

        appendSnapshot(schemes == null ? new ArrayList<>() : schemes, 1);
        return old;
    }

    /**
     * 把所有方案按一行一套写进指定文件（UTF-8，覆盖写）。
     *
     * <p>只会用于方案主文件 {@link #FILE_NAME}；备份文件 {@link #OLD_FILE_NAME}
     * 请改用 {@link #appendSnapshot(List, int)} 追加，不要整个覆盖。</p>
     *
     * @return 写成功返回 true
     */
    public static boolean save(File file, List<ChatScheme> schemes) {
        if (file == null || schemes == null) return false;

        StringBuilder sb = new StringBuilder();
        for (ChatScheme scheme : schemes) {
            sb.append(scheme.toLine()).append('\n');
        }
        return MikuStoreFiles.writeText(file, sb.toString());
    }

    /**
     * 把当前整套方案作为一个新的「旧方案集」<b>追加</b>到 {@link #OLD_FILE_NAME}，
     * 不覆盖之前的记录；超过 {@code maxSnapshots} 份时从最旧的一份开始丢。
     *
     * <p>这就是「重置」按钮背后的写入动作：重置前先把当前方案追加进去，再恢复默认方案。
     * 每份之间会用一行 {@code # ===== 方案集 #N · 时间 =====} 隔开。</p>
     *
     * @param schemes      要保存的当前方案
     * @param maxSnapshots 最多保留多少份，1 ~ {@link #HARD_MAX_SNAPSHOTS}
     * @return 写成功返回 true
     */
    public static boolean appendSnapshot(List<ChatScheme> schemes, int maxSnapshots) {
        if (schemes == null) return false;

        File file = oldFile();
        List<String> blocks = MikuStoreFiles.readBlocks(file);

        // 有编号的按最大编号 +1；早期没有分隔行、读不出编号的块用“块数 +1”兜底，
        // 保证新一份的编号不会和已有的撞车
        int nextIndex = blocks.size() + 1;
        for (String block : blocks) {
            int index = MikuStoreFiles.parseIndex(MikuStoreFiles.firstLine(block));
            if (index >= nextIndex) nextIndex = index + 1;
        }

        StringBuilder block = new StringBuilder();
        block.append(MikuStoreFiles.snapshotHeader(nextIndex, java.time.LocalDateTime.now())).append('\n');
        for (ChatScheme scheme : schemes) {
            block.append(scheme.toLine()).append('\n');
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
     * 读出 {@link #OLD_FILE_NAME} 里的所有旧方案集，按文件顺序返回（最旧的在前，
     * 最新追加的在最后）。界面里展示时倒过来放即可。
     *
     * <p>文件内容按 {@code # ===== 方案集 #…} 分隔行切块；分隔行之前可能还有早期版本
     * 直接写入、没有分隔行的方案，会当成一份“早期记录”一起返回。文件不存在、读不动或
     * 完全为空时返回空列表。</p>
     */
    public static List<MikuChatSnapshot> loadHistory() {
        List<MikuChatSnapshot> list = new ArrayList<>();
        List<String> blocks = MikuStoreFiles.readBlocks(oldFile());

        int order = 0;
        for (String block : blocks) {
            String header = null;
            List<ChatScheme> schemes = new ArrayList<>();
            for (String line : block.split("\n")) {
                String trimmed = line.trim();
                if (header == null && trimmed.startsWith(SNAPSHOT_PREFIX)) {
                    header = trimmed;
                    continue;
                }
                ChatScheme scheme = ChatScheme.fromLine(trimmed);
                if (scheme != null) schemes.add(scheme);
            }

            order++;
            int index = MikuStoreFiles.parseIndex(header);
            if (index <= 0) index = order;
            list.add(new MikuChatSnapshot(index, MikuStoreFiles.parseTimeText(header), schemes));
        }
        return list;
    }

    /**
     * 从文件读回所有方案。文件不存在、读不动、或整行都不合法时返回空列表，
     * 调用方据此决定是否覆盖现有方案（避免读失败把用户数据清空）。
     */
    public static List<ChatScheme> load(File file) {
        List<ChatScheme> list = new ArrayList<>();
        for (String line : MikuStoreFiles.readAllLines(file)) {
            ChatScheme scheme = ChatScheme.fromLine(line);
            if (scheme != null) list.add(scheme);
        }
        return list;
    }

    /** 用资源管理器/访达打开 {@code meteor-miku} 文件夹，方便直接改 Miku-Chat.txt。 */
    public static boolean openFolder() {
        return MikuStoreFiles.openFolder();
    }

    /**
     * 用系统默认程序（记事本等）打开配置文件 {@link #FILE_NAME}，方便直接编辑。
     *
     * <p>文件还不存在时，先按传进来的方案生成一份再打开；备份文件 {@link #OLD_FILE_NAME}
     * 不存在时也一并补一份。生成失败则返回 false，调用方可以退回去打开文件夹。</p>
     *
     * @param schemes 文件不存在时用来生成初值；可为 {@code null}
     * @return 成功交给系统打开返回 true
     */
    public static boolean openConfigFile(List<ChatScheme> schemes) {
        List<ChatScheme> defaults = schemes == null ? new ArrayList<>() : schemes;
        File file = ensureMainFile(defaults);
        ensureOldFile(defaults);

        try {
            net.minecraft.util.Util.getPlatform().openFile(file);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 弹出系统文件选择框，让用户从本地任意位置挑一个方案文本文件。
     *
     * <p>文件框放在后台守护线程里弹出，免得卡住游戏主线程；用户选完或取消后回调。
     * 当前环境弹不出文件框（比如没图形界面）时，回调 {@code onUnavailable}，
     * 调用方可以退回去读游戏根目录 {@code meteor-miku} 文件夹里的 {@link #FILE_NAME}。</p>
     *
     * @param onChosen      用户选好文件或取消时回调；参数为 {@code null} 表示取消
     * @param onUnavailable 打不开文件选择框时回调
     */
    public static void chooseFileAsync(Consumer<File> onChosen, Runnable onUnavailable) {
        Thread thread = new Thread(() -> {
            File chosen = null;
            try {
                FileDialog dialog = new FileDialog((Frame) null, "选择聊天方案文件（" + FILE_NAME + " 格式）");
                dialog.setMode(FileDialog.LOAD);
                dialog.setFile("*.txt");
                File dir = storeDir();
                if (dir.isDirectory()) dialog.setDirectory(dir.getAbsolutePath());
                dialog.setVisible(true);

                String name = dialog.getFile();
                if (name != null && !name.isBlank()) {
                    String directory = dialog.getDirectory();
                    chosen = directory == null ? new File(name) : new File(directory, name);
                }
                dialog.dispose();
            } catch (Throwable t) {
                onUnavailable.run();
                return;
            }
            onChosen.accept(chosen);
        }, "miku-chat-file-picker");
        thread.setDaemon(true);
        thread.start();
    }
}
