package cn.blockforge.meteorzhcn.util.online;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import cn.blockforge.meteorzhcn.MeteorZhCn;
import cn.blockforge.meteorzhcn.mixin.ModuleAccessor;
import cn.blockforge.meteorzhcn.mixin.SettingAccessor;
import cn.blockforge.meteorzhcn.mixin.SettingGroupAccessor;
import cn.blockforge.meteorzhcn.modules.Translation;
import cn.blockforge.meteorzhcn.util.TransUtil;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.Settings;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.client.Minecraft;

/**
 * 联网翻译调度器：在客户端线程收集文本、命中缓存，然后把请求丢到后台线程池，
 * 完成后回到客户端线程写回标题/描述。
 *
 * <p>同一批文本会被合并去重，译文按「模式 + 原文」落盘缓存，所以第二次点 Translate
 * 基本不再发请求。</p>
 */
public final class OnlineTranslator {
    private static final OnlineTranslator INSTANCE = new OnlineTranslator();

    private final TransCache cache = new TransCache(cacheFile());
    private final AtomicBoolean running = new AtomicBoolean(false);
    /** 第一次被联网翻译前记下原始描述；字段一旦被改写就再也取不回来，只能提前留底。 */
    private final Map<Module, String> moduleDescriptions = Collections.synchronizedMap(new IdentityHashMap<>());
    private final Map<Setting<?>, String> settingDescriptions = Collections.synchronizedMap(new IdentityHashMap<>());

    private OnlineTranslator() {
    }

    public static OnlineTranslator get() {
        return INSTANCE;
    }

    public int cacheSize() {
        return this.cache.size();
    }

    private static File cacheFile() {
        File folder = MeteorClient.FOLDER != null ? MeteorClient.FOLDER : new File(".");
        return new File(folder, "meteor-zh-cn-cache.properties");
    }

    /** 开始一轮联网翻译；必须在客户端线程调用。 */
    public void start(Translation module, TransMode mode) {
        if (!mode.isOnline()) {
            return;
        }

        // 附加模组初始化可能早于 Meteor 的前缀初始化，这里再补装一次，保证进度提示显示 [XC I18n]。
        cn.blockforge.meteorzhcn.util.ChatPrefix.install();

        if (!this.running.compareAndSet(false, true)) {
            ChatUtils.warning("联网翻译正在进行中，请稍候。");
            return;
        }

        Translator provider;
        String namespace;
        TargetLang target = module.targetLanguage.get();

        try {
            if (mode == TransMode.COLLOQUIAL) {
                if (module.sSetApiKey.get().isBlank()) {
                    this.running.set(false);
                    ChatUtils.warning("口语化翻译需要先在 api-key 里填写密钥。");
                    return;
                }

                provider = new ColloquialTranslator(
                        module.sSetApiUrl.get(), module.sSetApiKey.get(), module.sSetModel.get(),
                        target, module.iSetTimeout.get(), module.iSetBatchSize.get()
                );
                namespace = mode.name() + ":" + target.name();
            } else {
                MachineProvider machineProvider = module.machineProvider.get();

                // 选到服务商不支持的语种时先在聊天栏说清楚、并推荐几家能用的，
                // 而不是把每一批都发出去、再拿回一串必然失败的请求。
                if (!target.supportedBy(machineProvider)) {
                    this.running.set(false);
                    ChatUtils.warning("%s 不支持%s，可以改用：%s；或把 translation-mode 换成联网口语化翻译。",
                            machineProvider.shortName(), target, target.providerSuggestions());
                    return;
                }

                if (machineProvider.requiresAppId() && module.sSetMachineAppId.get().isBlank()) {
                    this.running.set(false);
                    ChatUtils.warning("%s 需要先在 machine-appid 里填写应用标识。", machineProvider);
                    return;
                }

                if (machineProvider.requiresKey() && module.sSetMachineKey.get().isBlank()) {
                    this.running.set(false);
                    ChatUtils.warning("%s 需要先在 machine-key 里填写密钥。", machineProvider);
                    return;
                }

                if (machineProvider.needsCustomUrl() && module.sSetMachineUrl.get().isBlank()) {
                    this.running.set(false);
                    ChatUtils.warning("%s 没有内置接口地址，请先在 machine-api-url 里填写地址模板。", machineProvider);
                    return;
                }

                provider = new MachineTranslator(
                        machineProvider, target, module.sSetMachineUrl.get(),
                        module.sSetMachineAppId.get(), module.sSetMachineKey.get(), module.iSetTimeout.get()
                );
                // 不同服务商、不同目标语言的译文都不一样，缓存里必须分开存。
                namespace = mode.name() + ":" + machineProvider.name() + ":" + target.name();
            }
        } catch (Throwable t) {
            this.running.set(false);
            MeteorZhCn.LOG.error("创建联网翻译器失败", t);
            ChatUtils.error("联网翻译启动失败：" + t);
            return;
        }

        Map<String, List<Consumer<String>>> entries;

        try {
            entries = this.collect(module);
        } catch (Throwable t) {
            this.running.set(false);
            MeteorZhCn.LOG.error("收集待翻译文本失败", t);
            ChatUtils.error("联网翻译启动失败：" + t);
            return;
        }

        List<String> pending = new ArrayList<>();
        int cached = 0;

        for (Map.Entry<String, List<Consumer<String>>> entry : entries.entrySet()) {
            String translation = this.cache.get(namespace, entry.getKey());

            if (translation != null) {
                for (Consumer<String> apply : entry.getValue()) {
                    apply.accept(translation);
                }
                cached++;
            } else {
                pending.add(entry.getKey());
            }
        }

        if (pending.isEmpty()) {
            this.running.set(false);
            ChatUtils.info("联网翻译完成：全部命中缓存（%d 条）。", cached);
            return;
        }

        int threads = Math.max(1, Math.min(8, module.iSetThreads.get()));
        ChatUtils.info("联网翻译开始：待翻译 %d 条，命中缓存 %d 条，模式 %s，目标语言 %s。", pending.size(), cached, mode, target);

        Thread worker = new Thread(() -> {
            try {
                this.run(provider, namespace, entries, pending, threads);
            } catch (Throwable t) {
                MeteorZhCn.LOG.error("联网翻译失败", t);
                this.finish(namespace, entries, 0, pending.size(), t, null);
            }
        }, "meteor-zh-cn-online");
        worker.setDaemon(true);
        worker.start();
    }

    /** 清空缓存并把界面文字还原成原文。 */
    public void clearCache() {
        this.cache.clear();
        this.cache.save();

        this.onClient(() -> {
            this.reset();
            ChatUtils.info("已清空联网翻译缓存，并把界面文字还原成原文。");
        });
    }

    private void run(Translator provider, String namespace, Map<String, List<Consumer<String>>> entries, List<String> pending, int threads) {
        List<List<String>> batches = partition(pending, provider.batchSize(), provider.maxCharsPerBatch());
        ExecutorService pool = Executors.newFixedThreadPool(threads, task -> {
            Thread thread = new Thread(task, "meteor-zh-cn-online");
            thread.setDaemon(true);
            return thread;
        });

        try {
            List<Future<List<String>>> futures = new ArrayList<>(batches.size());

            for (List<String> batch : batches) {
                futures.add(pool.submit(() -> provider.translate(batch)));
            }

            int ok = 0;
            int fail = 0;
            int done = 0;
            int reported = 0;
            /** 第一处失败的原因，最后随结果一起报给用户，免得只看到「失败 N 条」不知为何。 */
            String firstError = null;

            for (int i = 0; i < batches.size(); i++) {
                List<String> batch = batches.get(i);

                try {
                    List<String> raw = futures.get(i).get();
                    List<String> result = raw == null ? List.of() : raw;

                    // 接口回得比请求短、甚至回空时，差额就是没拿到的译文，计为失败而不是当作成功跳过。
                    if (result.size() < batch.size()) {
                        if (firstError == null) {
                            firstError = "接口只返回 " + result.size() + "/" + batch.size() + " 条译文";
                        }
                        fail += batch.size() - result.size();
                    }

                    for (int j = 0; j < batch.size(); j++) {
                        String source = batch.get(j);
                        String translation = j < result.size() ? result.get(j) : null;

                        if (translation != null && !translation.isBlank() && !translation.equals(source)) {
                            this.cache.put(namespace, source, translation);
                            ok++;
                        }
                    }
                } catch (Exception e) {
                    fail += batch.size();

                    if (firstError == null) {
                        Throwable cause = e.getCause() != null ? e.getCause() : e;
                        firstError = cause.getMessage() == null ? cause.toString() : cause.getMessage();
                    }
                }

                done += batch.size();
                int percent = done * 100 / pending.size();

                if (percent - reported >= 10) {
                    reported = percent;
                    this.onClient(() -> ChatUtils.info("联网翻译进度：%d%%", percent));
                }
            }

            this.cache.save();
            this.finish(namespace, entries, ok, fail, null, firstError);
        } finally {
            pool.shutdown();
        }
    }

    private void finish(String namespace, Map<String, List<Consumer<String>>> entries, int ok, int fail, Throwable error, String failReason) {
        this.onClient(() -> {
            this.apply(entries, namespace);
            this.running.set(false);

            String message;

            if (error != null) {
                message = "联网翻译中断：" + error.getMessage();
            } else {
                message = "联网翻译完成：新增 " + ok + " 条，失败 " + fail + " 条。";

                if (fail > 0 && failReason != null) {
                    // 把第一家报错的内容带出来：换服务商、补 Key、改语种都有据可依。
                    message += "首个失败原因：" + failReason;
                }
            }

            ChatUtils.sendMsg(error == null ? net.minecraft.ChatFormatting.GRAY : net.minecraft.ChatFormatting.RED, message);
        });
    }

    /** 把已缓存的结果写回模块与设置；必须在客户端线程调用。 */
    private void apply(Map<String, List<Consumer<String>>> entries, String namespace) {
        for (Map.Entry<String, List<Consumer<String>>> entry : entries.entrySet()) {
            String translation = this.cache.get(namespace, entry.getKey());

            if (translation == null) {
                continue;
            }

            for (Consumer<String> apply : entry.getValue()) {
                apply.accept(translation);
            }
        }
    }

    /** 在客户端线程收集这一轮要处理的文本，同一原文只登记一次。 */
    private Map<String, List<Consumer<String>>> collect(Translation module) {
        OnlineScope scope = module.onlineScope.get();
        TargetLang target = module.targetLanguage.get();
        Set<String> selected = module.translationModules.get();
        Map<String, List<Consumer<String>>> entries = new LinkedHashMap<>();

        for (Module moduleI : Modules.get().getAll()) {
            if (!selected.contains(TransUtil.getAddonName(moduleI))) {
                continue;
            }

            this.add(entries, target, moduleI.name, value -> ((ModuleAccessor) moduleI).setTitle(value));

            if (scope != OnlineScope.NAMES) {
                this.moduleDescriptions.putIfAbsent(moduleI, moduleI.description);
                this.add(entries, target, moduleI.description, value -> ((ModuleAccessor) moduleI).setDescription(value));
            }

            if (scope == OnlineScope.MODULES) {
                continue;
            }

            Settings settings = moduleI.settings;

            for (SettingGroup group : settings.groups) {
                for (Setting<?> setting : ((SettingGroupAccessor) group).getSettings()) {
                    this.add(entries, target, setting.name, value -> ((SettingAccessor) setting).setTitle(value));

                    if (scope == OnlineScope.ALL) {
                        this.settingDescriptions.putIfAbsent(setting, setting.description);
                        this.add(entries, target, setting.description, value -> ((SettingAccessor) setting).setDescription(value));
                    }
                }
            }
        }

        return entries;
    }

    private void add(Map<String, List<Consumer<String>>> entries, TargetLang target, String source, Consumer<String> apply) {
        if (!isTranslatable(source, target)) {
            return;
        }

        entries.computeIfAbsent(source, key -> new ArrayList<>()).add(apply);
    }

    private void reset() {
        for (Module module : Modules.get().getAll()) {
            ((ModuleAccessor) module).setTitle(Utils.nameToTitle(module.name));
            ((ModuleAccessor) module).setDescription(this.moduleDescriptions.getOrDefault(module, module.description));

            for (SettingGroup group : module.settings.groups) {
                for (Setting<?> setting : ((SettingGroupAccessor) group).getSettings()) {
                    ((SettingAccessor) setting).setTitle(Utils.nameToTitle(setting.name));
                    ((SettingAccessor) setting).setDescription(this.settingDescriptions.getOrDefault(setting, setting.description));
                }
            }
        }
    }

    private void onClient(Runnable task) {
        Minecraft client = Minecraft.getInstance();

        if (client == null) {
            task.run();
        } else {
            client.execute(task);
        }
    }

    private static List<List<String>> partition(List<String> sources, int maxCount, int maxChars) {
        List<List<String>> batches = new ArrayList<>();
        List<String> current = new ArrayList<>();
        int chars = 0;

        for (String source : sources) {
            if (!current.isEmpty() && (current.size() >= maxCount || (long) chars + source.length() > maxChars)) {
                batches.add(current);
                current = new ArrayList<>();
                chars = 0;
            }

            current.add(source);
            chars += source.length();
        }

        if (!current.isEmpty()) {
            batches.add(current);
        }

        return batches;
    }

    /** 只翻含拉丁字母、且尚未写成目标语言的文本；已经是目标语言写法的直接跳过。 */
    private static boolean isTranslatable(String source, TargetLang target) {
        if (source == null) {
            return false;
        }

        String text = source.trim();
        if (text.isEmpty() || target.alreadyInTargetScript(text)) {
            return false;
        }

        boolean hasLatin = false;
        boolean hasHan = false;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) {
                hasLatin = true;
                break;
            }

            if (c >= 0x4E00 && c <= 0x9FFF) {
                hasHan = true;
            }
        }

        // 目标是中文时中文原文直接跳过；翻成日、韩、俄等语言时，中文原文同样要送去翻译。
        return hasLatin || (hasHan && target.needsTranslationFromChinese());
    }
}
