package cn.blockforge.meteorzhcn.util;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map.Entry;

import cn.blockforge.meteorzhcn.mixin.SettingGroupAccessor;
import cn.blockforge.meteorzhcn.modules.Translation;
import cn.blockforge.meteorzhcn.util.trans_engine.AbstractTransEngine;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.player.ChatUtils;

/**
 * 开发用：把当前所有模块/设置的键导出成一份 JSON 骨架，方便补译文。
 * 只有展开 Dev 分组时才会在界面上看到入口。
 */
public class JsonDump {
    private static final JsonDump INSTANCE = new JsonDump();
    private LinkedHashMap<String, String> entMap = new LinkedHashMap<>();
    private BufferedWriter dumpBW;

    public static JsonDump getINSTANCE() {
        return INSTANCE;
    }

    private Translation getTran() {
        return Modules.get().get(Translation.class);
    }

    public void write(AbstractTransEngine engine, AbstractTransEngine engine2) {
        this.dump2Set(engine, engine2);

        try {
            File path = new File(this.getTran().sSetDumpPath.get());
            if (!path.exists() && !path.createNewFile()) {
                ChatUtils.warning("DumpError Can't Create Dump File");
                this.entMap.clear();
                return;
            }

            this.dumpBW = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(path, false), StandardCharsets.UTF_8));

            for (Entry<String, String> entry : this.entMap.entrySet()) {
                this.dumpBW.write("\"" + entry.getKey() + "\":\"" + TransUtil.formatValue(entry.getValue()) + "\",");
                this.dumpBW.newLine();
            }

            this.dumpBW.flush();
            this.dumpBW.close();
        } catch (IOException e) {
            ChatUtils.error(e.getMessage());
            this.entMap.clear();
            return;
        } finally {
            try {
                if (this.dumpBW != null) {
                    this.dumpBW.close();
                }
            } catch (IOException ignored) {
            }
        }

        this.entMap.clear();
    }

    private void dump2Set(AbstractTransEngine engine, AbstractTransEngine engine2) {
        boolean dumpText = this.getTran().bSetDumpText.get();

        for (Module module : Modules.get().getAll()) {
            String addonName = TransUtil.getAddonName(module);
            if (this.getTran().translationModules.get().contains(addonName)) {
                String nameKey = engine.getModuleNameKey(module);
                this.addEntry(nameKey, dumpText ? engine2.transModuleName(module) : module.name);
                String desKey = engine.getModuleDescriptionKey(module);
                this.addEntry(desKey, dumpText ? engine2.transModuleDescription(module) : module.description);

                for (SettingGroup group : module.settings.groups) {
                    for (Setting<?> setting : ((SettingGroupAccessor) group).getSettings()) {
                        String settingNameKey = engine.getSettingNameKey(module, group, setting);
                        this.addEntry(settingNameKey, dumpText ? engine2.transSettingName(module, group, setting) : setting.name);
                        String settDescKey = engine.getSettingDesKey(module, group, setting);
                        this.addEntry(settDescKey, dumpText ? engine2.transSettingDes(module, group, setting) : setting.description);
                    }
                }
            }
        }
    }

    private void addEntry(String key, String value) {
        this.entMap.putIfAbsent(key, value);
    }
}
