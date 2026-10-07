package cn.blockforge.meteorzhcn.util;

import cn.blockforge.meteorzhcn.MeteorZhCn;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * 把本模组发出的聊天消息前缀从 Meteor 默认的 {@code [Meteor]} 换成 {@code [XC I18n]}。
 *
 * <p>Meteor 的 {@link ChatUtils} 在取前缀时会遍历调用栈，找出第一个非 ChatUtils 的调用类，
 * 再在自定义前缀表里按「类名 startsWith 注册键」匹配，命中就用注册的组件替换默认前缀。
 * 所以这里注册整个 {@code cn.blockforge.meteorzhcn} 包，模组里所有聊天消息（尤其是联网翻译
 * 的开始、进度、完成提示）都会显示成 {@code [XC I18n]}，而 Meteor 本体与其他附加模组的消息
 * 不受影响。</p>
 *
 * <p>注册必须等 Meteor 把默认前缀初始化出来之后再做：自定义前缀表一旦非空，Meteor 取前缀就会
 * 走上面的匹配逻辑，默认前缀为 null 时其他类的消息会报错。因此 {@link #install()} 会先检查
 * {@link ChatUtils#getMeteorPrefix()}，没就绪就留到下次再装。</p>
 */
public final class ChatPrefix {
    /** 展示用的前缀文字，不含方括号。 */
    public static final String NAME = "XC I18n";

    /** 注册键：命中所有以它开头的调用类。 */
    private static final String PACKAGE = "cn.blockforge.meteorzhcn";

    private static final Component COMPONENT = Component.empty()
            .append(Component.literal("[").withStyle(ChatFormatting.DARK_GRAY))
            .append(Component.literal(NAME).withStyle(ChatFormatting.AQUA))
            .append(Component.literal("]").withStyle(ChatFormatting.DARK_GRAY));

    private static boolean installed;

    private ChatPrefix() {
    }

    /** 装上自定义前缀；重复调用安全，Meteor 未就绪时会留到下一次。 */
    public static synchronized void install() {
        if (installed) {
            return;
        }

        if (ChatUtils.getMeteorPrefix() == null) {
            // Meteor 还没初始化好默认前缀，此时注册会让其他类的消息取到 null。
            return;
        }

        try {
            ChatUtils.registerCustomPrefix(PACKAGE, () -> COMPONENT);
            installed = true;
        } catch (Throwable t) {
            MeteorZhCn.LOG.warn("注册聊天前缀失败，继续沿用 Meteor 默认前缀", t);
        }
    }
}
