package cn.blockforge.meteorzhcn.settings;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.utils.Cell;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WSection;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.gui.widgets.pressable.WCheckbox;
import meteordevelopment.meteorclient.utils.Utils;

/**
 * 勾选界面：列出全部候选字符串，支持按关键词过滤，整块勾选/取消。
 */
public class StringSelectScreen extends WindowScreen {
    private final StringSelectSetting setting;
    private final WTextBox filter;
    private WVerticalList list;
    private String filterText = "";
    private WSection strings;
    private WTable stringsT;
    int strsSize;

    public StringSelectScreen(GuiTheme theme, StringSelectSetting setting) {
        super(theme, setting.title);
        this.setting = setting;
        this.filter = super.add(theme.textBox("")).minWidth(400.0).expandX().widget();
        this.filter.setFocused(true);
        this.filter.action = () -> {
            this.filterText = this.filter.get().trim();
            this.list.clear();
            this.initWidgets();
        };
        this.list = super.add(theme.verticalList()).expandX().widget();
    }

    @Override
    public <W extends WWidget> Cell<W> add(W widget) {
        return this.list.add(widget);
    }

    public void initWidgets() {
        this.strsSize = 0;

        for (String s : this.setting.get()) {
            if (this.setting.filter == null || this.setting.filter.test(s)) {
                this.strsSize++;
            }
        }

        List<String> stringE = new ArrayList<>();
        WCheckbox stringC = this.theme.checkbox(this.strsSize > 0);
        this.strings = this.theme.section("Strings", this.strings != null && this.strings.isExpanded(), stringC);
        stringC.action = () -> this.tableChecked(stringE, stringC.checked);
        Cell<WSection> stringsCell = this.add(this.strings).expandX();
        this.stringsT = this.strings.add(this.theme.table()).expandX().widget();
        Consumer<String> stringForeach = str -> {
            stringE.add(str);
            this.addString(this.stringsT, stringC, str);
        };
        this.strings.setExpanded(true);
        if (this.filterText.isEmpty()) {
            this.setting.validValues.forEach(stringForeach);
        } else {
            List<Match> entities = new ArrayList<>();
            this.setting.validValues.forEach(str -> {
                int words = Utils.searchInWords(str, this.filterText);
                int diff = Utils.searchLevenshteinDefault(str, this.filterText, false);
                if (words > 0 || diff < str.length() / 2) {
                    entities.add(new Match(str, diff));
                }
            });
            entities.sort(Comparator.comparingInt(Match::diff));

            for (Match match : entities) {
                stringForeach.accept(match.value());
            }
        }

        if (this.stringsT.cells.isEmpty()) {
            this.list.cells.remove(stringsCell);
        }
    }

    private void tableChecked(List<String> strings, boolean checked) {
        boolean changed = false;

        for (String string : strings) {
            if (checked) {
                this.setting.get().add(string);
                changed = true;
            } else if (this.setting.get().remove(string)) {
                changed = true;
            }
        }

        if (changed) {
            this.list.clear();
            this.initWidgets();
            this.setting.onChanged();
        }
    }

    private void addString(WTable table, WCheckbox tableCheckbox, String str) {
        table.add(this.theme.label(str));
        WCheckbox a = table.add(this.theme.checkbox(this.setting.get().contains(str))).expandCellX().right().widget();
        a.action = () -> {
            if (a.checked) {
                this.setting.get().add(str);
                if (this.strsSize == 0) {
                    tableCheckbox.checked = true;
                }

                this.strsSize++;
            } else if (this.setting.get().remove(str)) {
                this.strsSize--;
                if (this.strsSize == 0) {
                    tableCheckbox.checked = false;
                }
            }
        };
        table.row();
    }

    /** 过滤命中项：字符串与编辑距离，距离越小越靠前。原先借用了原版的 Tuple，但 26.2 已移除该类。 */
    private record Match(String value, int diff) {
    }
}
