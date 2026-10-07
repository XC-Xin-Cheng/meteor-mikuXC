package cn.blockforge.meteorzhcn.util.trans_engine;

import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;

/**
 * 键生成规则。同一个界面（模块名/描述/设置名/设置描述）在两代 Meteor 上键的写法不同，
 * 所以把“怎么拼键”单独抽出来，翻译动作本身共用。
 */
public interface IKeyGenerate {
    String getModuleNameKey(Module module);

    String getModuleDescriptionKey(Module module);

    String getSettingNameKey(Module module, SettingGroup group, Setting<?> setting);

    String getSettingDesKey(Module module, SettingGroup group, Setting<?> setting);
}
