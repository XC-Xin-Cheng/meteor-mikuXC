package cn.blockforge.meteorzhcn.util.online;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 口语化翻译：调用 OpenAI 兼容的对话接口，把一批词条以 JSON 数组送去，
 * 要求模型也回一个等长 JSON 数组。批量请求是为了别把上万条文本拆成上万次调用。
 */
public class ColloquialTranslator implements Translator {
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private static final int MAX_CHARS_PER_BATCH = 4000;
    private static final String SYSTEM_PROMPT = """
            你是 Minecraft 模组界面的本地化译者。用户会给出一个 JSON 字符串数组，请逐条翻译成 {{lang}}。
            要求：
            1. 口语化、自然，像 {{lang}}玩家日常说话，不要生硬的书面语；
            2. 界面文字尽量简短，不要比原文长太多；
            3. 保留 %s、%d、%f 等占位符，保留 § 颜色代码，游戏术语和英文缩写按当地玩家习惯处理（如 Kill Aura、Fly 之类的模组术语要译得短而顺口）；
            4. 不要添加解释、注释或前后缀。
            只输出一个 JSON 字符串数组，元素个数与顺序必须和输入完全一致。""";

    private final String apiUrl;
    private final String apiKey;
    private final String model;
    private final TargetLang target;
    private final int timeoutSeconds;
    private final int batchSize;

    public ColloquialTranslator(String apiUrl, String apiKey, String model, TargetLang target, int timeoutSeconds, int batchSize) {
        this.apiUrl = apiUrl == null || apiUrl.isBlank() ? "https://api.openai.com/v1/chat/completions" : apiUrl.trim();
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model == null || model.isBlank() ? "gpt-4o-mini" : model.trim();
        this.target = target == null ? TargetLang.ZH_CN : target;
        this.timeoutSeconds = timeoutSeconds;
        this.batchSize = Math.max(1, batchSize);
    }

    @Override
    public List<String> translate(List<String> texts) throws Exception {
        String requestBody = this.buildRequest(texts);

        HttpRequest request = HttpRequest.newBuilder(URI.create(this.apiUrl))
                .timeout(Duration.ofSeconds(this.timeoutSeconds))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + this.apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("HTTP " + response.statusCode() + "：" + truncate(response.body()));
        }

        return parseResponse(response.body(), texts.size());
    }

    @Override
    public int batchSize() {
        return this.batchSize;
    }

    @Override
    public int maxCharsPerBatch() {
        return MAX_CHARS_PER_BATCH;
    }

    private String systemPrompt() {
        return SYSTEM_PROMPT.replace("{{lang}}", this.target.promptName());
    }

    private String buildRequest(List<String> texts) {
        StringBuilder array = new StringBuilder("[");
        for (int i = 0; i < texts.size(); i++) {
            if (i > 0) {
                array.append(',');
            }
            array.append(MiniJson.quote(texts.get(i)));
        }
        array.append(']');

        return "{\"model\":" + MiniJson.quote(this.model)
                + ",\"temperature\":0.3,\"messages\":["
                + "{\"role\":\"system\",\"content\":" + MiniJson.quote(this.systemPrompt()) + "},"
                + "{\"role\":\"user\",\"content\":" + MiniJson.quote(array.toString()) + "}]}";
    }

    private static List<String> parseResponse(String body, int expected) throws IOException {
        Object root;

        try {
            root = MiniJson.parse(body);
        } catch (RuntimeException e) {
            throw new IOException("响应不是合法 JSON");
        }

        Object content = MiniJson.at(root, "choices", 0, "message", "content");
        if (!(content instanceof String text)) {
            throw new IOException("响应里没有 choices[0].message.content");
        }

        Object parsed;

        try {
            parsed = MiniJson.parse(stripCodeFence(text.trim()));
        } catch (RuntimeException e) {
            throw new IOException("模型没有返回 JSON 数组");
        }

        // 有的模型会把数组包在 {"translations":[...]} 里，这里顺手兼容一下。
        if (parsed instanceof java.util.Map<?, ?> map) {
            Object nested = map.get("translations");

            if (nested == null) {
                nested = map.get("result");
            }
            if (nested == null) {
                nested = map.get("data");
            }

            parsed = nested;
        }

        if (!(parsed instanceof List<?> list) || list.size() != expected) {
            throw new IOException("译文数量与输入不一致");
        }

        List<String> out = new ArrayList<>(expected);
        for (Object item : list) {
            out.add(item instanceof String s ? s : null);
        }

        return out;
    }

    /** 模型有时会把 JSON 包在 ```json ... ``` 里，去掉这层围栏。 */
    private static String stripCodeFence(String text) {
        if (!text.startsWith("```")) {
            return text;
        }

        int firstNewline = text.indexOf('\n');
        if (firstNewline < 0) {
            return text.replace("`", "");
        }

        String inner = text.substring(firstNewline + 1);
        int closing = inner.lastIndexOf("```");
        return closing >= 0 ? inner.substring(0, closing).trim() : inner.trim();
    }

    private static String truncate(String text) {
        if (text == null) {
            return "";
        }

        return text.length() <= 200 ? text : text.substring(0, 200) + "…";
    }
}
