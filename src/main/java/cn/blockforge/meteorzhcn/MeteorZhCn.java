package cn.blockforge.meteorzhcn;

import com.mojang.logging.LogUtils;

import cn.blockforge.meteorzhcn.modules.Translation;
import cn.blockforge.meteorzhcn.settings.StringSelectSetting;
import cn.blockforge.meteorzhcn.settings.StringSelectWidgetFactory;
import cn.blockforge.meteorzhcn.util.ChatPrefix;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.gui.utils.SettingsWidgetFactory;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.slf4j.Logger;

/**
 * Meteor 客户端简体中文补全的附加模组入口。
 * 唯一前置是 Meteor 本体，不再需要额外的翻译插件。
 */
public class MeteorZhCn extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();
    public static final Category CATEGORY = new Category("XC Meteor翻译");

    @Override
    public void onInitialize() {
        // 自定义设置类型必须在任何设置界面被创建之前登记，否则多半选项设置会画不出控件。
        SettingsWidgetFactory.registerCustomFactory(StringSelectSetting.class, StringSelectWidgetFactory::new);

        // 把本模组消息的 [Meteor] 前缀换成 [XC I18n]；Meteor 未就绪时会自动留到联网翻译启动时再装。
        ChatPrefix.install();

        Modules.get().add(new Translation());
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
    }

    @Override
    public String getPackage() {
        return "cn.blockforge.meteorzhcn";
    }
}
