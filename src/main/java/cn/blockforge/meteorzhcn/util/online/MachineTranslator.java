package cn.blockforge.meteorzhcn.util.online;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 机翻：按所选服务商构造请求、解析响应。
 *
 * <p>免 Key 的 MyMemory / Google 走内置地址模板，用户也可以自填；Quark 与自定义必须自填。
 * 百度、有道按各自的官方签名规则拼参数；DeepL、微软、Yandex、LibreTranslate 用 JSON 或表单
 * 批量提交；Papago 一次只能翻一条，按条请求。</p>
 *
 * <p>模板里的 {@code {text}} 会被替换成 URL 编码后的原文，{@code {lang}} 会被替换成
 * 当前目标语言在这次服务商下的代码，{@code {source}} 会被替换成自动识别出的源语言代码。</p>
 *
 * <p>源语言：目标是中文以外的语言时，收集到的原文里既有英文（Meteor 原始模块名），
 * 也可能有中文（上一轮已经汉化过的界面文字）。所以除模板外的服务商一律让接口自动识别源语言，
 * 百度、有道用 {@code from=auto}；Papago 不支持 auto，则按文本内容判断是中文还是英文。</p>
 */
public class MachineTranslator implements Translator {
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private static final String BAIDU_URL = "https://fanyi-api.baidu.com/api/trans/vip/translate";
    private static final String YOUDAO_URL = "https://openapi.youdao.com/api";
    private static final String DEEPL_URL = "https://api.deepl.com/v2/translate";
    private static final String DEEPL_FREE_URL = "https://api-free.deepl.com/v2/translate";
    private static final String MICROSOFT_URL = "https://api.cognitive.microsofttranslator.com/translate";
    private static final String YANDEX_URL = "https://translate.api.cloud.yandex.net/translate/v2/translate";
    private static final String PAPAGO_URL = "https://openapi.naver.com/v1/papago/n2mt";
    private static final String LIBRE_URL = "https://libretranslate.com/translate";

    private static final String[] TEXT_FIELDS = {"translatedText", "translation", "dst", "result", "text"};
    private static final String[] WRAPPER_FIELDS = {"responseData", "data", "result", "transResult"};

    /** 百度标准版一次最多 6000 字节，这里留足余量；多条用 \n 拼成一次请求。 */
    private static final int BAIDU_BATCH = 10;
    private static final int BAIDU_MAX_CHARS = 600;

    /** 支持批量提交的服务商一次带多少条、多少字符。 */
    private static final int BATCH = 20;
    private static final int BATCH_MAX_CHARS = 8000;

    private final MachineProvider provider;
    private final TargetLang target;
    private final String template;
    private final String appId;
    private final String appKey;
    private final int timeoutSeconds;

    public MachineTranslator(MachineProvider provider, TargetLang target, String template, String appId, String appKey, int timeoutSeconds) {
        this.provider = provider == null ? MachineProvider.MYMEMORY : provider;
        this.target = target == null ? TargetLang.ZH_CN : target;
        this.template = template == null ? "" : template.trim();
        this.appId = appId == null ? "" : appId.trim();
        this.appKey = appKey == null ? "" : appKey.trim();
        this.timeoutSeconds = timeoutSeconds;
    }

    @Override
    public List<String> translate(List<String> texts) throws Exception {
        return switch (this.provider) {
            case BAIDU -> this.translateBaidu(texts);
            case YOUDAO -> this.translateYoudao(texts);
            case DEEPL -> this.translateDeepl(texts);
            case MICROSOFT -> this.translateMicrosoft(texts);
            case YANDEX -> this.translateYandex(texts);
            case PAPAGO -> this.translatePapago(texts);
            case LIBRETRANSLATE -> this.translateLibre(texts);
            default -> this.translateTemplate(texts);
        };
    }

    @Override
    public int batchSize() {
        return switch (this.provider) {
            // LibreTranslate 只有部分实例接受数组形式的 q：公共实例常见的实现一次只回一条
            // translatedText，若按批提交就会静默丢掉其余译文，所以这里老老实实逐条请求。
            case DEEPL, MICROSOFT, YANDEX -> BATCH;
            case LIBRETRANSLATE -> 1;
            case BAIDU -> BAIDU_BATCH;
            default -> 1;
        };
    }

    @Override
    public int maxCharsPerBatch() {
        return switch (this.provider) {
            case DEEPL, MICROSOFT, YANDEX -> BATCH_MAX_CHARS;
            case LIBRETRANSLATE -> Integer.MAX_VALUE;
            case BAIDU -> BAIDU_MAX_CHARS;
            default -> Integer.MAX_VALUE;
        };
    }

    // ---------------------------------------------------------------- 模板类服务商

    private List<String> translateTemplate(List<String> texts) throws Exception {
        List<String> out = new ArrayList<>(texts.size());

        for (String text : texts) {
            out.add(this.translateViaTemplate(text));
        }

        return out;
    }

    private String translateViaTemplate(String text) throws Exception {
        String tmpl = this.resolveTemplate(text);
        String encoded = URLEncoder.encode(text, StandardCharsets.UTF_8);
        String url = tmpl.contains("{text}") ? tmpl.replace("{text}", encoded) : tmpl + encoded;

        String body = this.get(url).body();
        String result = pickTranslation(body);

        if (result == null || result.isBlank()) {
            throw new IOException("响应里没有译文：" + truncate(body));
        }
        if (result.contains("MYMEMORY WARNING")) {
            throw new IOException("免费额度已用完（MyMemory）");
        }

        return result;
    }

    private String resolveTemplate(String text) throws IOException {
        String tmpl = this.template.isBlank() ? this.builtinTemplate() : this.template;
        String targetCode = requireCode(this.target.codeFor(this.provider), this.provider.toString());

        // {lang} 与 {source} 让自填模板也能跟着目标语言、源语言走；没写占位符就原样发出。
        tmpl = tmpl.replace("{lang}", targetCode);
        return tmpl.contains("{source}") ? tmpl.replace("{source}", detectSource(text)) : tmpl;
    }

    private String builtinTemplate() throws IOException {
        return switch (this.provider) {
            case GOOGLE -> "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl={lang}&dt=t&q={text}";
            case MYMEMORY -> "https://api.mymemory.translated.net/get?q={text}&langpair={source}%7C{lang}";
            default -> throw new IOException(this.provider + " 没有内置接口地址，请在 machine-api-url 里填写自己的地址模板");
        };
    }

    // ---------------------------------------------------------------- 百度

    private List<String> translateBaidu(List<String> texts) throws Exception {
        if (this.appId.isBlank() || this.appKey.isBlank()) {
            throw new IOException("百度翻译需要先在 machine-appid / machine-key 里填写 AppID 与密钥");
        }

        // 每条压成单行后用 \n 拼接，返回的 trans_result 就按行一一对应。
        List<String> flattened = new ArrayList<>(texts.size());
        StringBuilder query = new StringBuilder();

        for (String text : texts) {
            String one = text.replace('\r', ' ').replace('\n', ' ');
            flattened.add(one);

            if (query.length() > 0) {
                query.append('\n');
            }
            query.append(one);
        }

        String q = query.toString();
        String salt = Long.toString(ThreadLocalRandom.current().nextLong(100000L, 999999L));
        String sign = digest("MD5", this.appId + q + salt + this.appKey);

        String url = BAIDU_URL
                + "?q=" + URLEncoder.encode(q, StandardCharsets.UTF_8)
                // from=auto 让百度自己识别源语言：原文可能是英文模块名，也可能是已汉化的中文。
                + "&from=auto&to=" + requireCode(this.target.baiduCode(), "百度翻译")
                + "&appid=" + URLEncoder.encode(this.appId, StandardCharsets.UTF_8)
                + "&salt=" + salt
                + "&sign=" + sign;

        String body = this.post(url, null, null, null).body();
        Object root = parseJson(body);

        Object errorCode = MiniJson.at(root, "error_code");
        if (isErrorCode(errorCode)) {
            throw new IOException("百度翻译报错 " + errorCode + "：" + MiniJson.at(root, "error_msg"));
        }

        Object results = MiniJson.at(root, "trans_result");
        if (results instanceof List<?> list) {
            List<String> out = new ArrayList<>(list.size());

            for (Object item : list) {
                Object dst = MiniJson.at(item, "dst");
                out.add(dst instanceof String s ? s : null);
            }

            // 行数与输入对不上时退回逐条请求，免得整批作废。
            if (out.size() == texts.size()) {
                return out;
            }

            List<String> oneByOne = new ArrayList<>(texts.size());
            for (String text : texts) {
                oneByOne.add(this.translateBaiduOne(text));
            }
            return oneByOne;
        }

        throw new IOException("百度翻译响应里没有 trans_result：" + truncate(body));
    }

    private String translateBaiduOne(String text) throws Exception {
        String q = text.replace('\r', ' ').replace('\n', ' ');
        String salt = Long.toString(ThreadLocalRandom.current().nextLong(100000L, 999999L));
        String sign = digest("MD5", this.appId + q + salt + this.appKey);

        String url = BAIDU_URL
                + "?q=" + URLEncoder.encode(q, StandardCharsets.UTF_8)
                + "&from=auto&to=" + requireCode(this.target.baiduCode(), "百度翻译")
                + "&appid=" + URLEncoder.encode(this.appId, StandardCharsets.UTF_8)
                + "&salt=" + salt
                + "&sign=" + sign;

        String body = this.post(url, null, null, null).body();
        Object root = parseJson(body);

        Object errorCode = MiniJson.at(root, "error_code");
        if (isErrorCode(errorCode)) {
            throw new IOException("百度翻译报错 " + errorCode + "：" + MiniJson.at(root, "error_msg"));
        }

        Object dst = MiniJson.at(root, "trans_result", 0, "dst");
        if (dst instanceof String s && !s.isBlank()) {
            return s;
        }

        throw new IOException("百度翻译响应里没有译文：" + truncate(body));
    }

    // ---------------------------------------------------------------- 有道

    private List<String> translateYoudao(List<String> texts) throws Exception {
        if (this.appId.isBlank() || this.appKey.isBlank()) {
            throw new IOException("有道翻译需要先在 machine-appid / machine-key 里填写应用ID与应用密钥");
        }

        List<String> out = new ArrayList<>(texts.size());

        for (String text : texts) {
            out.add(this.translateYoudaoOne(text));
        }

        return out;
    }

    private String translateYoudaoOne(String text) throws Exception {
        String salt = UUID.randomUUID().toString();
        String curtime = Long.toString(System.currentTimeMillis() / 1000L);
        // 有道 v3 签名：sha256(appKey + truncate(q) + salt + curtime + appSecret)
        String sign = digest("SHA-256", this.appId + truncateQuery(text) + salt + curtime + this.appKey);

        String form = "q=" + URLEncoder.encode(text, StandardCharsets.UTF_8)
                // from=auto 让有道自己识别源语言。
                + "&from=auto&to=" + requireCode(this.target.youdaoCode(), "有道智云")
                + "&appKey=" + URLEncoder.encode(this.appId, StandardCharsets.UTF_8)
                + "&salt=" + URLEncoder.encode(salt, StandardCharsets.UTF_8)
                + "&sign=" + sign
                + "&signType=v3"
                + "&curtime=" + curtime;

        String body = this.post(YOUDAO_URL, "application/x-www-form-urlencoded", form, null).body();
        Object root = parseJson(body);

        Object errorCode = MiniJson.at(root, "errorCode");
        if (isErrorCode(errorCode)) {
            throw new IOException("有道翻译报错 " + errorCode + "：" + MiniJson.at(root, "error"));
        }

        Object translation = MiniJson.at(root, "translation", 0);
        if (translation instanceof String s && !s.isBlank()) {
            return s;
        }

        throw new IOException("有道翻译响应里没有译文：" + truncate(body));
    }

    /** 有道签名要求：长度超过 20 时取「前 10 + 长度 + 后 10」。 */
    private static String truncateQuery(String q) {
        int length = q.length();
        return length <= 20 ? q : q.substring(0, 10) + length + q.substring(length - 10);
    }

    // ---------------------------------------------------------------- DeepL

    private List<String> translateDeepl(List<String> texts) throws Exception {
        if (this.appKey.isBlank()) {
            throw new IOException("DeepL 需要先在 machine-key 里填写 API Key");
        }

        String targetCode = requireCode(this.target.deeplCode(), "DeepL");
        // 免费版 Key 以 :fx 结尾，终结点与付费版不同。
        String endpoint = this.appKey.endsWith(":fx") ? DEEPL_FREE_URL : DEEPL_URL;

        StringBuilder form = new StringBuilder();

        for (String text : texts) {
            if (form.length() > 0) {
                form.append('&');
            }
            form.append("text=").append(URLEncoder.encode(text, StandardCharsets.UTF_8));
        }

        form.append("&target_lang=").append(URLEncoder.encode(targetCode, StandardCharsets.UTF_8));
        // 不填 source_lang，交给 DeepL 自动识别。

        String body = this.post(endpoint, "application/x-www-form-urlencoded", form.toString(),
                Map.of("Authorization", "DeepL-Auth-Key " + this.appKey)).body();
        Object root = parseJson(body);
        List<?> translations = asList(MiniJson.at(root, "translations"));

        if (translations == null) {
            throw new IOException("DeepL 响应里没有 translations：" + truncate(body));
        }

        List<String> out = new ArrayList<>(translations.size());

        for (Object item : translations) {
            Object text = MiniJson.at(item, "text");
            out.add(text instanceof String s ? s : null);
        }

        return out;
    }

    // ---------------------------------------------------------------- 微软翻译

    private List<String> translateMicrosoft(List<String> texts) throws Exception {
        if (this.appKey.isBlank()) {
            throw new IOException("微软翻译需要先在 machine-key 里填写订阅密钥");
        }

        String targetCode = requireCode(this.target.bingCode(), "微软翻译");
        StringBuilder json = new StringBuilder("[");

        for (int i = 0; i < texts.size(); i++) {
            if (i > 0) {
                json.append(',');
            }
            json.append("{\"Text\":").append(MiniJson.quote(texts.get(i))).append('}');
        }

        json.append(']');

        String url = MICROSOFT_URL + "?api-version=3.0&to=" + URLEncoder.encode(targetCode, StandardCharsets.UTF_8);

        // machine-appid 在这里当作可选的区域(region)；留空走全局终结点。
        Map<String, String> headers = this.appId.isBlank()
                ? Map.of("Ocp-Apim-Subscription-Key", this.appKey)
                : Map.of(
                        "Ocp-Apim-Subscription-Key", this.appKey,
                        "Ocp-Apim-Subscription-Region", this.appId
                );

        String body = this.post(url, "application/json", json.toString(), headers).body();
        Object root = parseJson(body);

        if (!(root instanceof List<?> list) || list.size() != texts.size()) {
            throw new IOException("微软翻译响应格式不符：" + truncate(body));
        }

        List<String> out = new ArrayList<>(texts.size());

        for (Object item : list) {
            Object text = MiniJson.at(item, "translations", 0, "text");
            out.add(text instanceof String s ? s : null);
        }

        return out;
    }

    // ---------------------------------------------------------------- Yandex

    private List<String> translateYandex(List<String> texts) throws Exception {
        if (this.appKey.isBlank()) {
            throw new IOException("Yandex 需要先在 machine-key 里填写 API Key");
        }

        String targetCode = requireCode(this.target.yandexCode(), "Yandex");

        StringBuilder json = new StringBuilder("{\"targetLanguageCode\":").append(MiniJson.quote(targetCode));

        // machine-appid 当作 Yandex Cloud 的 folderId，可留空由服务端推断。
        if (!this.appId.isBlank()) {
            json.append(",\"folderId\":").append(MiniJson.quote(this.appId));
        }

        json.append(",\"texts\":[");

        for (int i = 0; i < texts.size(); i++) {
            if (i > 0) {
                json.append(',');
            }
            json.append(MiniJson.quote(texts.get(i)));
        }

        json.append("]}");

        String body = this.post(YANDEX_URL, "application/json", json.toString(),
                Map.of("Authorization", "Api-Key " + this.appKey)).body();
        Object root = parseJson(body);
        List<?> translations = asList(MiniJson.at(root, "translations"));

        if (translations == null) {
            throw new IOException("Yandex 响应里没有 translations：" + truncate(body));
        }

        List<String> out = new ArrayList<>(translations.size());

        for (Object item : translations) {
            Object text = MiniJson.at(item, "text");
            out.add(text instanceof String s ? s : null);
        }

        return out;
    }

    // ---------------------------------------------------------------- Papago

    private List<String> translatePapago(List<String> texts) throws Exception {
        if (this.appId.isBlank() || this.appKey.isBlank()) {
            throw new IOException("Papago 需要先在 machine-appid / machine-key 里填写 Client ID 与 Client Secret");
        }

        String targetCode = requireCode(this.target.papagoCode(), "Papago");
        List<String> out = new ArrayList<>(texts.size());

        for (String text : texts) {
            // Papago 不支持自动识别源语言，按文本里有没有汉字自己判断。
            String form = "source=" + URLEncoder.encode(detectSource(text), StandardCharsets.UTF_8)
                    + "&target=" + URLEncoder.encode(targetCode, StandardCharsets.UTF_8)
                    + "&text=" + URLEncoder.encode(text.replace('\r', ' ').replace('\n', ' '), StandardCharsets.UTF_8);

            Map<String, String> headers = Map.of(
                    "X-Naver-Client-Id", this.appId,
                    "X-Naver-Client-Secret", this.appKey
            );

            String body = this.post(PAPAGO_URL, "application/x-www-form-urlencoded", form, headers).body();
            Object root = parseJson(body);

            Object error = MiniJson.at(root, "errorCode");
            if (error != null) {
                throw new IOException("Papago 报错 " + error + "：" + MiniJson.at(root, "errorMessage"));
            }

            Object translated = MiniJson.at(root, "message", "result", "translatedText");
            if (translated instanceof String s && !s.isBlank()) {
                out.add(s);
            } else {
                throw new IOException("Papago 响应里没有译文：" + truncate(body));
            }
        }

        return out;
    }

    // ---------------------------------------------------------------- LibreTranslate

    private List<String> translateLibre(List<String> texts) throws Exception {
        String targetCode = requireCode(this.target.libreCode(), "LibreTranslate");

        StringBuilder json = new StringBuilder("{\"q\":[");

        for (int i = 0; i < texts.size(); i++) {
            if (i > 0) {
                json.append(',');
            }
            json.append(MiniJson.quote(texts.get(i)));
        }

        // source=auto 让实例自动识别；公共实例现在多要 api_key，自建实例留空即可。
        json.append("],\"source\":\"auto\",\"target\":").append(MiniJson.quote(targetCode)).append(",\"format\":\"text\"");

        if (!this.appKey.isBlank()) {
            json.append(",\"api_key\":").append(MiniJson.quote(this.appKey));
        }

        json.append('}');

        String url = this.template.isBlank() ? LIBRE_URL : this.template;
        String body = this.post(url, "application/json", json.toString(), null).body();
        Object root = parseJson(body);
        Object translated = MiniJson.at(root, "translatedText");

        if (translated instanceof String s && !s.isBlank()) {
            return List.of(s);
        }

        if (translated instanceof List<?> list) {
            List<String> out = new ArrayList<>(list.size());

            for (Object item : list) {
                out.add(item instanceof String s ? s : null);
            }

            return out;
        }

        throw new IOException("LibreTranslate 响应里没有译文：" + truncate(body));
    }

    // ---------------------------------------------------------------- 网络

    private HttpResponse<String> get(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(this.timeoutSeconds))
                .header("User-Agent", "Mozilla/5.0 (Meteor-I18n-XC)")
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        checkStatus(response);
        return response;
    }

    private HttpResponse<String> post(String url, String contentType, String body, Map<String, String> headers) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(this.timeoutSeconds))
                .header("User-Agent", "Mozilla/5.0 (Meteor-I18n-XC)")
                .header("Accept", "application/json");

        if (contentType != null) {
            builder.header("Content-Type", contentType);
        }

        if (headers != null) {
            for (Map.Entry<String, String> header : headers.entrySet()) {
                builder.header(header.getKey(), header.getValue());
            }
        }

        HttpRequest request = body == null
                ? builder.POST(HttpRequest.BodyPublishers.noBody()).build()
                : builder.POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build();

        HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        checkStatus(response);
        return response;
    }

    private static void checkStatus(HttpResponse<String> response) throws IOException {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("HTTP " + response.statusCode() + "：" + truncate(response.body()));
        }
    }

    // ---------------------------------------------------------------- 解析

    private static Object parseJson(String body) throws IOException {
        try {
            return MiniJson.parse(body);
        } catch (RuntimeException e) {
            throw new IOException("响应不是合法 JSON：" + truncate(body));
        }
    }

    private static List<?> asList(Object node) {
        return node instanceof List<?> list ? list : null;
    }

    /** 某个服务商不支持所选目标语言时，给一句能看懂的提示，而不是发一个必然报错的请求。 */
    private static String requireCode(String code, String providerName) throws IOException {
        if (code == null || code.isBlank()) {
            throw new IOException(providerName + " 不支持所选目标语言，请换一家服务商，或改用联网口语化翻译");
        }

        return code;
    }

    /**
     * 粗略判断源语言：含汉字、且几乎没有拉丁字母的当作简体中文，其余当作英文。
     * 只用于 Papago 这类必须显式指定源语言的接口。
     */
    private static String detectSource(String text) {
        int han = 0;
        int latin = 0;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            if (c >= 0x4E00 && c <= 0x9FFF) {
                han++;
            } else if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) {
                latin++;
            }
        }

        return han > 0 && latin == 0 ? "zh-CN" : "en";
    }

    /** 0 / "0" / "0.0" 视为成功，其余视为错误码。 */
    private static boolean isErrorCode(Object code) {
        if (code == null) {
            return false;
        }
        if (code instanceof Number number) {
            return number.doubleValue() != 0.0;
        }

        String text = String.valueOf(code).trim();
        return !text.isEmpty() && !"0".equals(text) && !"0.0".equals(text);
    }

    private static String pickTranslation(String body) {
        Object root;

        try {
            root = MiniJson.parse(body);
        } catch (RuntimeException e) {
            return null;
        }

        return pickTranslation(root);
    }

    private static String pickTranslation(Object node) {
        if (node instanceof Map<?, ?> map) {
            for (String wrapper : WRAPPER_FIELDS) {
                Object nested = map.get(wrapper);

                if (nested instanceof Map<?, ?> || nested instanceof List<?>) {
                    String found = pickTranslation(nested);
                    if (found != null) {
                        return found;
                    }
                }
            }

            for (String field : TEXT_FIELDS) {
                Object value = map.get(field);
                if (value instanceof String s && !s.isBlank()) {
                    return s;
                }
            }

            return null;
        }

        if (node instanceof List<?> list && !list.isEmpty()) {
            Object first = list.get(0);

            if (first instanceof String s && !s.isBlank()) {
                return s;
            }

            // Google gtx：[[["译文","原文",...], ...], ...]，把各段拼起来。
            if (first instanceof List<?> segments) {
                StringBuilder sb = new StringBuilder();
                boolean any = false;

                for (Object segment : segments) {
                    if (segment instanceof List<?> parts && !parts.isEmpty() && parts.get(0) instanceof String piece) {
                        sb.append(piece);
                        any = true;
                    } else if (segment instanceof String piece) {
                        sb.append(piece);
                        any = true;
                    }
                }

                if (any) {
                    return sb.toString();
                }
            }
        }

        return null;
    }

    // ---------------------------------------------------------------- 杂项

    private static String digest(String algorithm, String text) throws Exception {
        MessageDigest md = MessageDigest.getInstance(algorithm);
        byte[] bytes = md.digest(text.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder(bytes.length * 2);

        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }

        return sb.toString();
    }

    private static String truncate(String text) {
        if (text == null) {
            return "";
        }

        return text.length() <= 200 ? text : text.substring(0, 200) + "…";
    }
}
