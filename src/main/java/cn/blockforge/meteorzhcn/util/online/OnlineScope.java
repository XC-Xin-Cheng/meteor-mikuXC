package cn.blockforge.meteorzhcn.util.online;

/**
 * 联网翻译的覆盖范围。设置描述数量极大（上万个），全部走联网既慢又费额度，
 * 所以默认只翻模块名与模块描述，其余由用户按需打开。
 */
public enum OnlineScope {
    MODULES("模块名与描述"),
    NAMES("模块名与设置名"),
    ALL("全部（含设置描述）");

    private final String label;

    OnlineScope(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return this.label;
    }
}
