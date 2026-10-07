package cn.blockforge.meteorzhcn.util.online;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import cn.blockforge.meteorzhcn.MeteorZhCn;

/**
 * 联网译文缓存。键是「模式 + 原文」，同名原文只会真正请求一次，之后点 Translate 直接命中。
 * 用 {@link Properties} 落盘：JDK 自带、转义规则可靠，中文字符会原样写成 UTF-8。
 */
public class TransCache {
    private static final String SEPARATOR = "\t";

    private final File file;
    private final Properties entries = new Properties();
    private boolean dirty;

    public TransCache(File file) {
        this.file = file;
        this.load();
    }

    public synchronized String get(String mode, String source) {
        return this.entries.getProperty(key(mode, source));
    }

    public synchronized void put(String mode, String source, String translation) {
        if (translation == null || translation.isBlank() || translation.equals(source)) {
            return;
        }

        this.entries.setProperty(key(mode, source), translation);
        this.dirty = true;
    }

    public synchronized int size() {
        return this.entries.size();
    }

    public synchronized void clear() {
        this.entries.clear();
        this.dirty = true;
    }

    public synchronized void save() {
        if (!this.dirty) {
            return;
        }

        File parent = this.file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            MeteorZhCn.LOG.warn("无法创建联网翻译缓存目录 {}", parent);
            return;
        }

        try (Writer writer = new OutputStreamWriter(new FileOutputStream(this.file), StandardCharsets.UTF_8)) {
            this.entries.store(writer, "Meteor-I18n-XC online translation cache");
            this.dirty = false;
        } catch (IOException e) {
            MeteorZhCn.LOG.warn("写入联网翻译缓存失败", e);
        }
    }

    private void load() {
        if (!this.file.exists()) {
            return;
        }

        try (Reader reader = new InputStreamReader(new FileInputStream(this.file), StandardCharsets.UTF_8)) {
            this.entries.load(reader);
        } catch (IOException e) {
            MeteorZhCn.LOG.warn("读取联网翻译缓存失败", e);
        }
    }

    private static String key(String mode, String source) {
        return mode + SEPARATOR + source;
    }
}
