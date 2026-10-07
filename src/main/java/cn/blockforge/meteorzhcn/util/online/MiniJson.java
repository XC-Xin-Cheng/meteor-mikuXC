package cn.blockforge.meteorzhcn.util.online;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 极简 JSON 解析/转义工具。联网翻译只需要读出服务端响应里的几个字段，
 * 为此引入一个完整的 JSON 库不划算，这里手写一个够用的递归下降解析器。
 * 支持对象、数组、字符串、数字、布尔、null，解析结果用 Java 原生类型表示。
 */
public final class MiniJson {
    private final String text;
    private int pos;

    private MiniJson(String text) {
        this.text = text;
    }

    /** 解析整段文本；失败抛 {@link IllegalArgumentException}。 */
    public static Object parse(String text) {
        MiniJson parser = new MiniJson(text);
        parser.skipWhitespace();
        Object value = parser.parseValue();
        parser.skipWhitespace();
        if (parser.pos < parser.text.length()) {
            throw new IllegalArgumentException("JSON 末尾有多余内容");
        }
        return value;
    }

    /**
     * 按路径取值，路径元素为 {@link String}（对象字段）或 {@link Integer}（数组下标）。
     * 中途类型不符或越界一律返回 null，调用方只需判空。
     */
    public static Object at(Object node, Object... path) {
        Object current = node;

        for (Object key : path) {
            if (current == null) {
                return null;
            }

            if (key instanceof Integer index && current instanceof List<?> list) {
                current = index >= 0 && index < list.size() ? list.get(index) : null;
            } else if (key instanceof String name && current instanceof Map<?, ?> map) {
                current = map.get(name);
            } else {
                return null;
            }
        }

        return current;
    }

    /** 把字符串转义成合法的 JSON 字符串字面量（含两侧引号）。 */
    public static String quote(String value) {
        StringBuilder sb = new StringBuilder(value.length() + 2);
        sb.append('"');

        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);

            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }

        sb.append('"');
        return sb.toString();
    }

    private Object parseValue() {
        char c = this.peek();

        return switch (c) {
            case '{' -> this.parseObject();
            case '[' -> this.parseArray();
            case '"' -> this.parseString();
            case 't' -> this.parseLiteral("true", Boolean.TRUE);
            case 'f' -> this.parseLiteral("false", Boolean.FALSE);
            case 'n' -> this.parseLiteral("null", null);
            default -> this.parseNumber();
        };
    }

    private Map<String, Object> parseObject() {
        this.expect('{');
        Map<String, Object> map = new LinkedHashMap<>();
        this.skipWhitespace();

        if (this.peek() == '}') {
            this.pos++;
            return map;
        }

        while (true) {
            this.skipWhitespace();
            String key = this.parseString();
            this.skipWhitespace();
            this.expect(':');
            this.skipWhitespace();
            map.put(key, this.parseValue());
            this.skipWhitespace();

            char c = this.peek();
            if (c == ',') {
                this.pos++;
                continue;
            }
            if (c == '}') {
                this.pos++;
                return map;
            }
            throw new IllegalArgumentException("JSON 对象里出现意外字符");
        }
    }

    private List<Object> parseArray() {
        this.expect('[');
        List<Object> list = new ArrayList<>();
        this.skipWhitespace();

        if (this.peek() == ']') {
            this.pos++;
            return list;
        }

        while (true) {
            this.skipWhitespace();
            list.add(this.parseValue());
            this.skipWhitespace();

            char c = this.peek();
            if (c == ',') {
                this.pos++;
                continue;
            }
            if (c == ']') {
                this.pos++;
                return list;
            }
            throw new IllegalArgumentException("JSON 数组里出现意外字符");
        }
    }

    private String parseString() {
        this.expect('"');
        StringBuilder sb = new StringBuilder();

        while (true) {
            if (this.pos >= this.text.length()) {
                throw new IllegalArgumentException("JSON 字符串没有闭合");
            }

            char c = this.text.charAt(this.pos++);
            if (c == '"') {
                return sb.toString();
            }
            if (c != '\\') {
                sb.append(c);
                continue;
            }

            if (this.pos >= this.text.length()) {
                throw new IllegalArgumentException("JSON 转义不完整");
            }

            char esc = this.text.charAt(this.pos++);
            switch (esc) {
                case '"' -> sb.append('"');
                case '\\' -> sb.append('\\');
                case '/' -> sb.append('/');
                case 'b' -> sb.append('\b');
                case 'f' -> sb.append('\f');
                case 'n' -> sb.append('\n');
                case 'r' -> sb.append('\r');
                case 't' -> sb.append('\t');
                case 'u' -> {
                    if (this.pos + 4 > this.text.length()) {
                        throw new IllegalArgumentException("JSON 的 \\u 转义不完整");
                    }
                    sb.append((char) Integer.parseInt(this.text.substring(this.pos, this.pos + 4), 16));
                    this.pos += 4;
                }
                default -> throw new IllegalArgumentException("不认识的 JSON 转义 " + esc);
            }
        }
    }

    private Object parseNumber() {
        int start = this.pos;

        while (this.pos < this.text.length()) {
            char c = this.text.charAt(this.pos);
            if ((c >= '0' && c <= '9') || c == '-' || c == '+' || c == '.' || c == 'e' || c == 'E') {
                this.pos++;
            } else {
                break;
            }
        }

        if (start == this.pos) {
            throw new IllegalArgumentException("JSON 里出现无法识别的字符");
        }

        return Double.parseDouble(this.text.substring(start, this.pos));
    }

    private Object parseLiteral(String literal, Object value) {
        if (!this.text.startsWith(literal, this.pos)) {
            throw new IllegalArgumentException("JSON 里出现无法识别的字符");
        }

        this.pos += literal.length();
        return value;
    }

    private void skipWhitespace() {
        while (this.pos < this.text.length() && Character.isWhitespace(this.text.charAt(this.pos))) {
            this.pos++;
        }
    }

    private char peek() {
        if (this.pos >= this.text.length()) {
            throw new IllegalArgumentException("JSON 意外结束");
        }

        return this.text.charAt(this.pos);
    }

    private void expect(char expected) {
        if (this.peek() != expected) {
            throw new IllegalArgumentException("JSON 里期望字符 " + expected);
        }

        this.pos++;
    }
}
