package cn.blockforge.meteorzhcn.util.trans_engine;

import cn.blockforge.meteorzhcn.util.KeyBuilder;
import cn.blockforge.meteorzhcn.util.TransUtil;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;

/**
 * 早期键：整条键里没有附加模组段，设置段也保持原样不做下划线归一化。
 * 形如 {@code meteor.<分类>.<模块>} 与 {@code meteor.<分类>.<模块>.setting.<组>.<设置>}。
 * 1.21.1 一路到 26.2 的词库里都保留着这套写法，自动引擎拿它兜底。
 */
public class TransEngineLegacy extends AbstractTransEngine {
    KeyBuilder builder = new KeyBuilder();

    @Override
    public String getModuleNameKey(Module module) {
        this.builder.reset();
        this.builder.append(TransUtil.baseFormat(module.category.name));
        return this.builder.end(TransUtil.baseFormat(module.name));
    }

    @Override
    public String getModuleDescriptionKey(Module module) {
        this.builder.reset();
        this.builder.append(TransUtil.baseFormat(module.category.name)).append(TransUtil.baseFormat(module.name));
        return this.builder.end("description");
    }

    @Override
    public String getSettingNameKey(Module module, SettingGroup group, Setting<?> setting) {
        this.builder.reset();
        this.builder.append(TransUtil.baseFormat(module.category.name)).append(TransUtil.baseFormat(module.name));
        return this.builder.append("setting").append(group.name).end(setting.name);
    }

    @Override
    public String getSettingDesKey(Module module, SettingGroup group, Setting<?> setting) {
        this.builder.reset();
        this.builder.append(TransUtil.baseFormat(module.category.name)).append(TransUtil.baseFormat(module.name));
        return this.builder.append("setting").append(group.name).append(setting.name).end("description");
    }
}
