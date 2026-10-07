package cn.blockforge.meteorzhcn.util.online;

/**
 * 机翻服务商。免费免 Key 的：MyMemory、Google；LibreTranslate 用公共实例或自建实例；
 * DeepL、微软、Yandex 只需要一个 API Key；Papago 需要 Client ID + Client Secret；
 * 百度、有道走官方开放接口，需要 AppID 与密钥；夸克与自定义则是「自己填接口地址模板」。
 *
 * <p>新增 DeepL / 微软 / Yandex / Papago 是为了补上日语、韩语、俄语、德语等目标语言的翻译质量，
 * 这几家在各自擅长的语种上比 MyMemory 更靠得住；LibreTranslate 则给不想申请 Key、
 * 或想用自建实例的用户一个免费选项。</p>
 *
 * <p>{@code toString()} 会被 Meteor 的 EnumSetting 直接显示，所以这里返回中文。</p>
 */
public enum MachineProvider {
    MYMEMORY("MyMemory（免费·免 Key）"),
    GOOGLE("Google（免费·免 Key）"),
    LIBRETRANSLATE("LibreTranslate（免费/自建）"),
    DEEPL("DeepL（需 API Key）"),
    MICROSOFT("微软翻译（需 Key）"),
    YANDEX("Yandex（需 Key）"),
    PAPAGO("Papago 韩语（需 Client ID+Secret）"),
    BAIDU("百度翻译（需 AppID+密钥）"),
    YOUDAO("有道智云（需 AppID+密钥）"),
    QUARK("夸克（需自填地址）"),
    CUSTOM("自定义地址模板");

    private final String label;

    MachineProvider(String label) {
        this.label = label;
    }

    /** 按用户填写的 URL 模板发请求的服务商。 */
    public boolean usesTemplate() {
        return this == MYMEMORY || this == GOOGLE || this == QUARK || this == CUSTOM;
    }

    /** 有内置地址、但也允许用模板覆盖的服务商（LibreTranslate 可换成自建实例）。 */
    public boolean usesServiceUrl() {
        return this == LIBRETRANSLATE;
    }

    /** 需要用户填写 AppID / Client ID 的服务商（漏填会直接提示，不发请求）。 */
    public boolean requiresAppId() {
        return this == BAIDU || this == YOUDAO || this == PAPAGO;
    }

    /** 需要用户填写密钥的服务商（Papago 的密钥即 Client Secret）。 */
    public boolean requiresKey() {
        return this == BAIDU || this == YOUDAO || this == PAPAGO
                || this == DEEPL || this == MICROSOFT || this == YANDEX;
    }

    /**
     * 界面上是否显示 machine-appid 输入框。微软翻译把该框当作可选的区域(region)、
     * Yandex 把它当作可选的 folderId，留空时由服务端自行推断，所以它们不算「必须填写」。
     */
    public boolean usesAppId() {
        return this.requiresAppId() || this == MICROSOFT || this == YANDEX;
    }

    /** 界面上是否显示 machine-key 输入框；LibreTranslate 与自定义模板允许留空。 */
    public boolean acceptsKey() {
        return this.requiresKey() || this == LIBRETRANSLATE || this == CUSTOM;
    }

    /** 没有内置地址、必须由用户自己填模板的服务商。 */
    public boolean needsCustomUrl() {
        return this == QUARK || this == CUSTOM;
    }

    /** LibreTranslate 的默认公共实例地址；其余服务商返回空串。 */
    public String defaultUrl() {
        return this == LIBRETRANSLATE ? "https://libretranslate.com/translate" : "";
    }

    /** 去掉括号里的说明，用于把它放进一句提示语里（「Papago 不支持…」）。 */
    public String shortName() {
        int bracket = this.label.indexOf('（');
        return bracket < 0 ? this.label : this.label.substring(0, bracket);
    }

    @Override
    public String toString() {
        return this.label;
    }
}
