package cn.blockforge.meteorzhcn.util;

import meteordevelopment.meteorclient.systems.modules.Module;

/**
 * 逐段拼接汉化键。段与段之间用点连接，拼完整之后整串就是翻译键。
 */
public class KeyBuilder {
    StringBuilder sb = new StringBuilder();

    public KeyBuilder(Module m) {
        this.append("meteor").append(TransUtil.getAddonName(m)).append(TransUtil.baseFormat(m.category.name)).append(TransUtil.baseFormat(m.name));
    }

    public KeyBuilder() {
        this.append("meteor");
    }

    public KeyBuilder reset() {
        // 复用同一个 StringBuilder：汉化一遍要给成千上万个设置拼键，
        // 原实现每次 reset 都换一块新缓冲，纯属给 GC 找活干。
        this.sb.setLength(0);
        this.append("meteor");
        return this;
    }

    public KeyBuilder module(Module m) {
        this.append(TransUtil.getAddonName(m)).append(TransUtil.baseFormat(m.category.name)).append(TransUtil.baseFormat(m.name));
        return this;
    }

    public KeyBuilder append(String s) {
        this.sb.append(s).append(".");
        return this;
    }

    public KeyBuilder appendWithFormat(String s) {
        this.sb.append(TransUtil.baseFormat(s)).append(".");
        return this;
    }

    public String end(String s) {
        return this.sb.append(s).toString();
    }

    public String endWithFormat(String s) {
        return this.sb.append(TransUtil.baseFormat(s)).toString();
    }
}
