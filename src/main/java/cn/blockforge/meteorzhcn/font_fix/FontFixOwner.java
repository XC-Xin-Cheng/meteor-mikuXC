package cn.blockforge.meteorzhcn.font_fix;

/**
 * 由 {@code Font} 的 mixin 实现的接口：让渲染器在销毁字体时能通知动态字形图集收尾
 * （stb 的打包上下文是原生内存，只靠 GC 收不走）。
 */
public interface FontFixOwner {
    void meteorzhcn$release();
}
