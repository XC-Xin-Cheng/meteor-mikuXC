package cn.blockforge.meteorzhcn.util.online;

/**
 * 翻译模式。本地词库沿用内置汉化文件，其余两种走联网。
 * {@code toString()} 会被 Meteor 的 EnumSetting 直接显示，所以这里返回中文。
 */
public enum TransMode {
    LOCAL("本地词库"),
    MACHINE("联网机翻（可选服务商）"),
    COLLOQUIAL("联网口语化（需 API Key）");

    private final String label;

    TransMode(String label) {
        this.label = label;
    }

    public boolean isOnline() {
        return this != LOCAL;
    }

    @Override
    public String toString() {
        return this.label;
    }
}
