package cn.blockforge.meteorzhcn.util.online;

import java.util.ArrayList;
import java.util.List;

/**
 * 联网翻译的目标语言。主要覆盖汉语（简繁）、日语、韩语、俄语、德语，
 * 顺带带上法语、西语、葡语、意语、阿语、泰语、越南语、印尼语、马来语、
 * 土耳其语、波兰语、荷兰语、乌克兰语、印地语，共二十种。
 *
 * <p>各服务商的语言代码写法并不统一：MyMemory 用 BCP-47（{@code zh-CN}），
 * Google 用简写（{@code zh-CN} / {@code ja}），百度用自家代码（{@code zh} / {@code jp} / {@code kor}），
 * 有道又是另一套（{@code zh-CHS} / {@code ja}）。新增的 DeepL、微软、Yandex、Papago、
 * LibreTranslate 也各有一列，翻译时按当前服务商取对应代码。
 * 某家不支持某个目标语言时该列为 {@code null}，翻译前会给出明确提示，而不是发一个必然报错的请求。</p>
 *
 * <p>{@code toString()} 会被 Meteor 的 EnumSetting 直接显示，所以这里返回中文。</p>
 */
public enum TargetLang {
    ZH_CN("简体中文", "zh-CN", "zh-CN", "zh", "zh-CHS", "ZH-HANS", "zh-Hans", "zh", "zh-CN", "zh",
            "简体中文（Simplified Chinese）"),
    ZH_TW("繁体中文", "zh-TW", "zh-TW", "cht", "zh-CHT", "ZH-HANT", "zh-Hant", "zh", "zh-TW", "zt",
            "繁体中文（Traditional Chinese）"),
    JA("日语（日本語）", "ja-JP", "ja", "jp", "ja", "JA", "ja", "ja", "ja", "ja",
            "日语（Japanese）"),
    KO("韩语（한국어）", "ko-KR", "ko", "kor", "ko", "KO", "ko", "ko", "ko", "ko",
            "韩语（Korean）"),
    RU("俄语（Русский）", "ru-RU", "ru", "ru", "ru", "RU", "ru", "ru", "ru", "ru",
            "俄语（Russian）"),
    DE("德语（Deutsch）", "de-DE", "de", "de", "de", "DE", "de", "de", "de", "de",
            "德语（German）"),
    FR("法语（Français）", "fr-FR", "fr", "fra", "fr", "FR", "fr", "fr", "fr", "fr",
            "法语（French）"),
    ES("西班牙语（Español）", "es-ES", "es", "spa", "es", "ES", "es", "es", "es", "es",
            "西班牙语（Spanish）"),
    PT("葡萄牙语（Português）", "pt-PT", "pt", "pt", "pt", "PT-BR", "pt", "pt", null, "pt",
            "葡萄牙语（Portuguese）"),
    IT("意大利语（Italiano）", "it-IT", "it", "it", "it", "IT", "it", "it", "it", "it",
            "意大利语（Italian）"),
    AR("阿拉伯语（العربية）", "ar-SA", "ar", "ara", "ar", null, "ar", "ar", null, "ar",
            "阿拉伯语（Arabic）"),
    TH("泰语（ไทย）", "th-TH", "th", "th", "th", null, "th", "th", "th", "th",
            "泰语（Thai）"),
    VI("越南语（Tiếng Việt）", "vi-VN", "vi", "vie", "vi", null, "vi", "vi", "vi", "vi",
            "越南语（Vietnamese）"),
    ID("印尼语（Bahasa Indonesia）", "id-ID", "id", "ind", "id", "ID", "id", "id", "id", "id",
            "印尼语（Indonesian）"),
    MS("马来语（Bahasa Melayu）", "ms-MY", "ms", "may", "ms", null, "ms", "ms", null, "ms",
            "马来语（Malay）"),
    TR("土耳其语（Türkçe）", "tr-TR", "tr", "tr", "tr", "TR", "tr", "tr", null, "tr",
            "土耳其语（Turkish）"),
    PL("波兰语（Polski）", "pl-PL", "pl", "pl", "pl", "PL", "pl", "pl", null, "pl",
            "波兰语（Polish）"),
    NL("荷兰语（Nederlands）", "nl-NL", "nl", "nl", "nl", "NL", "nl", "nl", null, "nl",
            "荷兰语（Dutch）"),
    UK("乌克兰语（Українська）", "uk-UA", "uk", "ukr", "uk", "UK", "uk", "uk", null, "uk",
            "乌克兰语（Ukrainian）"),
    HI("印地语（हिन्दी）", "hi-IN", "hi", "hi", "hi", null, "hi", "hi", null, "hi",
            "印地语（Hindi）");

    private final String label;
    private final String myMemoryCode;
    private final String googleCode;
    private final String baiduCode;
    private final String youdaoCode;
    private final String deeplCode;
    private final String bingCode;
    private final String yandexCode;
    private final String papagoCode;
    private final String libreCode;
    private final String promptName;

    TargetLang(String label, String myMemoryCode, String googleCode, String baiduCode, String youdaoCode,
               String deeplCode, String bingCode, String yandexCode, String papagoCode, String libreCode,
               String promptName) {
        this.label = label;
        this.myMemoryCode = myMemoryCode;
        this.googleCode = googleCode;
        this.baiduCode = baiduCode;
        this.youdaoCode = youdaoCode;
        this.deeplCode = deeplCode;
        this.bingCode = bingCode;
        this.yandexCode = yandexCode;
        this.papagoCode = papagoCode;
        this.libreCode = libreCode;
        this.promptName = promptName;
    }

    /** MyMemory 的 langpair 目标代码，形如 {@code zh-CN}。 */
    public String myMemoryCode() {
        return this.myMemoryCode;
    }

    /** Google 的 tl 参数，形如 {@code zh-CN} 或 {@code ja}。 */
    public String googleCode() {
        return this.googleCode;
    }

    /** 百度的 to 参数，形如 {@code zh} / {@code jp} / {@code kor}。 */
    public String baiduCode() {
        return this.baiduCode;
    }

    /** 有道的 to 参数，形如 {@code zh-CHS} / {@code ja}。 */
    public String youdaoCode() {
        return this.youdaoCode;
    }

    /** DeepL 的 target_lang，形如 {@code ZH-HANS} / {@code JA}；不支持时返回 {@code null}。 */
    public String deeplCode() {
        return this.deeplCode;
    }

    /** 微软翻译的 to 参数，形如 {@code zh-Hans} / {@code ja}。 */
    public String bingCode() {
        return this.bingCode;
    }

    /** Yandex 的 lang 参数，形如 {@code zh} / {@code ja}。 */
    public String yandexCode() {
        return this.yandexCode;
    }

    /** Papago 的 target 参数，形如 {@code zh-CN} / {@code ja}；不支持时返回 {@code null}。 */
    public String papagoCode() {
        return this.papagoCode;
    }

    /** LibreTranslate 的 target 参数，形如 {@code zh} / {@code ja} / {@code zt}。 */
    public String libreCode() {
        return this.libreCode;
    }

    /** 写给大模型看的语言名，例如「简体中文（Simplified Chinese）」。 */
    public String promptName() {
        return this.promptName;
    }

    /**
     * 目标不是中文时，中文原文也需要送去翻译（例如把中文说明翻成日语）。
     * 目标本身就是中文时，中文原文直接跳过，省一次网络请求。
     */
    public boolean needsTranslationFromChinese() {
        return this != ZH_CN && this != ZH_TW;
    }

    /**
     * 取当前服务商认识的语言代码。夸克与自定义地址模板没有固定格式，
     * 统一给通用的 BCP-47 写法（{@code zh-CN}），用户可以在模板里用 {@code {lang}} 引用。
     * DeepL 与 Papago 不支持某些目标语言时返回 {@code null}，由翻译器给出明确提示。
     */
    public String codeFor(MachineProvider provider) {
        return switch (provider) {
            case MYMEMORY -> this.myMemoryCode;
            case GOOGLE -> this.googleCode;
            case BAIDU -> this.baiduCode;
            case YOUDAO -> this.youdaoCode;
            case DEEPL -> this.deeplCode;
            case MICROSOFT -> this.bingCode;
            case YANDEX -> this.yandexCode;
            case PAPAGO -> this.papagoCode;
            case LIBRETRANSLATE -> this.libreCode;
            default -> this.myMemoryCode;
        };
    }

    /**
     * 当前服务商能否翻这个目标语言。模板类服务商（MyMemory、Google、夸克、自定义）
     * 的语言代码可以回退成通用写法，或由用户自己在地址模板里决定，所以一律认为可以。
     * 其余服务商以语种表里那一列是否为 {@code null} 为准。
     */
    public boolean supportedBy(MachineProvider provider) {
        return provider.usesTemplate() || this.codeFor(provider) != null;
    }

    /**
     * 支持这个目标语言的服务商名单，用于在用户选到不支持的组合时给一句明确的替代建议。
     * 夸克与自定义地址模板需要用户自己搞定接口，不列入推荐。
     */
    public String providerSuggestions() {
        List<String> names = new ArrayList<>();

        for (MachineProvider provider : MachineProvider.values()) {
            if (provider == MachineProvider.QUARK || provider == MachineProvider.CUSTOM) {
                continue;
            }

            if (this.supportedBy(provider)) {
                names.add(provider.shortName());
            }
        }

        return String.join("、", names);
    }

    @Override
    public String toString() {
        return this.label;
    }

    /**
     * 这段文字是不是已经写成了目标语言。收集待翻译文本时用它跳过「已经译好」的条目，
     * 例如目标为简体中文时，已经是中文的模块名就不必再发一次请求；同理目标为日语时，
     * 已经是中文的条目仍会被送去翻成日语。
     *
     * <p>只认各语言最有代表性的字符块。拉丁字母系（德、法、西、葡、意）与英语同形，
     * 无法靠字符区分，这些目标一律返回 false，交由网络请求去判断。</p>
     */
    public boolean alreadyInTargetScript(String text) {
        if (text == null) {
            return false;
        }

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            boolean hit = switch (this) {
                case ZH_CN, ZH_TW -> isHan(c);
                case JA -> isKana(c);
                case KO -> isHangul(c);
                case RU, UK -> isCyrillic(c);
                case AR -> isArabic(c);
                case TH -> isThai(c);
                case HI -> isDevanagari(c);
                default -> false;
            };

            if (hit) {
                return true;
            }
        }

        return false;
    }

    private static boolean isHan(char c) {
        return (c >= 0x4E00 && c <= 0x9FFF) || (c >= 0x3400 && c <= 0x4DBF) || (c >= 0xF900 && c <= 0xFAFF)
                || (c >= 0x3000 && c <= 0x303F) || (c >= 0xFF00 && c <= 0xFFEF);
    }

    private static boolean isKana(char c) {
        return (c >= 0x3040 && c <= 0x309F) || (c >= 0x30A0 && c <= 0x30FF);
    }

    private static boolean isHangul(char c) {
        return (c >= 0xAC00 && c <= 0xD7A3) || (c >= 0x1100 && c <= 0x11FF) || (c >= 0x3130 && c <= 0x318F);
    }

    private static boolean isCyrillic(char c) {
        return (c >= 0x0400 && c <= 0x04FF) || (c >= 0x0500 && c <= 0x052F);
    }

    private static boolean isArabic(char c) {
        return (c >= 0x0600 && c <= 0x06FF) || (c >= 0x0750 && c <= 0x077F) || (c >= 0xFB50 && c <= 0xFDFF);
    }

    private static boolean isThai(char c) {
        return c >= 0x0E00 && c <= 0x0E7F;
    }

    private static boolean isDevanagari(char c) {
        return (c >= 0x0900 && c <= 0x097F) || (c >= 0xA8E0 && c <= 0xA8FF);
    }
}
