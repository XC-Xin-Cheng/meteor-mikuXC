package cn.blockforge.meteorzhcn.util.trans_engine;

import cn.blockforge.meteorzhcn.util.KeyBuilder;
import cn.blockforge.meteorzhcn.util.TransUtil;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;

/**
 * 旧式键：模块名不带 {@code .name} 后缀，设置名与组名保持原样（不做下划线归一化）。
 * 形如 {@code meteor.<附加模组>.<分类>.<模块>.setting.<组>.<设置>}。
 */
public class TransEngineOld extends AbstractTransEngine {
    KeyBuilder builder = new KeyBuilder();

    @Override
    public String getModuleNameKey(Module module) {
        String moduleName = module.name;
        this.builder.reset();
        return this.builder.append(TransUtil.getAddonName(module)).append(TransUtil.baseFormat(module.category.name)).end(TransUtil.baseFormat(moduleName));
    }

    @Override
    public String getModuleDescriptionKey(Module module) {
        this.builder.reset();
        return this.builder.module(module).end("description");
    }

    @Override
    public String getSettingNameKey(Module module, SettingGroup group, Setting setting) {
        String settingName = setting.name;
        this.builder.reset();
        return this.builder.module(module).append("setting").append(group.name).end(settingName);
    }

    @Override
    public String getSettingDesKey(Module module, SettingGroup group, Setting setting) {
        String settingName = setting.name;
        this.builder.reset();
        return this.builder.module(module).append("setting").append(group.name).append(settingName).end("description");
    }
}
