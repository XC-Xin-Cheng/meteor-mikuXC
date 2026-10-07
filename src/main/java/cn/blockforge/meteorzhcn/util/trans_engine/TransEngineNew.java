package cn.blockforge.meteorzhcn.util.trans_engine;

import cn.blockforge.meteorzhcn.util.KeyBuilder;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;

/**
 * 新式键：模块名以 {@code .name} 结尾，设置名与组名做下划线归一化。
 * 形如 {@code meteor.<附加模组>.<分类>.<模块>.setting.<组>.<设置>.name}。
 */
public class TransEngineNew extends AbstractTransEngine {
    KeyBuilder builder = new KeyBuilder();

    @Override
    public String getModuleNameKey(Module module) {
        this.builder.reset();
        return this.builder.module(module).end("name");
    }

    @Override
    public String getModuleDescriptionKey(Module module) {
        this.builder.reset();
        return this.builder.module(module).end("description");
    }

    @Override
    public String getSettingNameKey(Module module, SettingGroup group, Setting<?> s) {
        String settingName = s.name;
        this.builder.reset();
        return this.builder.module(module).appendWithFormat("setting").appendWithFormat(group.name).appendWithFormat(settingName).end("name");
    }

    @Override
    public String getSettingDesKey(Module module, SettingGroup group, Setting<?> s) {
        String settingName = s.name;
        this.builder.reset();
        return this.builder.module(module).appendWithFormat("setting").appendWithFormat(group.name).appendWithFormat(settingName).end("description");
    }
}
