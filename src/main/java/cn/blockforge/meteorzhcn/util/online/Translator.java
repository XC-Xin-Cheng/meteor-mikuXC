package cn.blockforge.meteorzhcn.util.online;

import java.util.List;

/**
 * 一个联网翻译服务的最小抽象：吃一批英文文本，吐顺序一致的译文。
 */
public interface Translator {
    /** 翻译一批文本；返回的列表长度必须与输入一致，失败时抛异常。 */
    List<String> translate(List<String> texts) throws Exception;

    /** 单个请求最多带多少条文本。机翻按条请求，大模型可以批量。 */
    int batchSize();

    /** 单个请求最多带多少字符，避免长描述把请求撑爆。 */
    int maxCharsPerBatch();
}
