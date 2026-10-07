package cn.blockforge.meteorzhcn.settings;

import java.util.Collection;
import java.util.Map;

import cn.blockforge.meteorzhcn.util.ClientScreens;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.utils.SettingsWidgetFactory;
import meteordevelopment.meteorclient.gui.widgets.WLabel;
import meteordevelopment.meteorclient.gui.widgets.containers.WContainer;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.Settings;

/**
 * 把“多选字符串”设置画成 选择 + 重置 两个按钮，并在旁边显示已选数量。
 * 通过 {@link SettingsWidgetFactory#registerCustomFactory} 注册，不用去改 Meteor 的设置界面工厂。
 */
public class StringSelectWidgetFactory implements SettingsWidgetFactory.Factory {
    private final GuiTheme theme;

    public StringSelectWidgetFactory(GuiTheme theme) {
        this.theme = theme;
    }

    @Override
    public void create(WTable table, Setting<?> setting) {
        StringSelectSetting select = (StringSelectSetting) setting;
        boolean addCount = SelectedCountLabel.getSize(select) != -1;
        WContainer container = table;

        if (addCount) {
            container = table.add(this.theme.horizontalList()).expandCellX().widget();
            ((WHorizontalList) container).spacing *= 2.0;
        }

        WButton button = container.add(this.theme.button("Select")).expandCellX().widget();
        button.action = () -> ClientScreens.open(new StringSelectScreen(this.theme, select));

        if (addCount) {
            container.add(new SelectedCountLabel(select).color(this.theme.textSecondaryColor()));
        }

        WButton reset = table.add(this.theme.button(GuiRenderer.RESET)).widget();
        reset.action = select::reset;
    }

    /** 旁边的“(N selected)”计数标签，数量变化时才重算文本。 */
    private static class SelectedCountLabel extends WLabel {
        private final Setting<?> setting;
        private int lastSize = -1;

        SelectedCountLabel(Setting<?> setting) {
            super("", false);
            this.setting = setting;
        }

        @Override
        protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
            int size = getSize(this.setting);
            if (size != this.lastSize) {
                if (this.setting.get() instanceof Settings) {
                    this.set("(" + size + " Group)");
                } else {
                    this.set("(" + size + " selected)");
                }

                this.lastSize = size;
            }

            if (!this.text.isEmpty()) {
                renderer.text(this.text, this.x, this.y, this.color != null ? this.color : this.theme.textSecondaryColor(), false);
            }
        }

        static int getSize(Setting<?> setting) {
            if (setting.get() instanceof Collection<?> collection) {
                return collection.size();
            } else if (setting.get() instanceof Map<?, ?> map) {
                return map.size();
            } else {
                return setting.get() instanceof Settings ? ((Settings) setting.get()).groups.size() : -1;
            }
        }
    }
}
