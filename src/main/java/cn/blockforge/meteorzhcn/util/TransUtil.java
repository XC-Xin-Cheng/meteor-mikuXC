package cn.blockforge.meteorzhcn.util;

import java.util.Set;
import java.util.stream.Collectors;

import meteordevelopment.meteorclient.addons.AddonManager;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.network.chat.Component;

/**
 * 汉化用到的字符串工具：键名归一化、读取当前语言下的译文。
 */
public final class TransUtil {
    private TransUtil() {
    }

    private static String trans(String s) {
        return Component.translatable(s).getString();
    }

    /** 取键对应的译文；当前语言里没有这个键时返回给定的兜底文本。 */
    public static String trans(String key, String alternative) {
        String trans = trans(key);
        return trans.equals(key) ? alternative : trans;
    }

    /**
     * 当前语言里是否存在这个键：缺失时原版会把键名原样返回，以此判断。
     * 自动引擎靠它来决定到底该用哪一套键写法。
     */
    public static boolean hasKey(String key) {
        return !trans(key).equals(key);
    }

    /** 列出当前已加载的全部 Meteor 附加模组名（归一化之后）。 */
    public static Set<String> getAddonNames() {
        return AddonManager.ADDONS.stream().map(addon -> addon.name).map(TransUtil::baseFormat).collect(Collectors.toSet());
    }

    public static String getAddonName(Module m) {
        return baseFormat(m.addon == null ? "unknow_addon" : m.addon.name);
    }

    /** 与 Meteor 生成键时使用的规则保持一致：小写，空格/短横线/点/引号一律换成下划线。 */
    public static String baseFormat(String s) {
        // 保持 toLowerCase() 的原始语义（它比逐字符 Character.toLowerCase 覆盖得更全），
        // 但把后面的四次 replace 合成一次遍历：没有需要替换的字符时直接返回，
        // 有的话也只产生一个中间字符串。汉化时每个模块/设置都要过一遍这里。
        String lower = s.toLowerCase();
        int length = lower.length();

        for (int i = 0; i < length; i++) {
            char c = lower.charAt(i);
            if (c == ' ' || c == '-' || c == '.' || c == '"') {
                StringBuilder sb = new StringBuilder(length);

                for (int j = 0; j < length; j++) {
                    char current = lower.charAt(j);
                    sb.append(current == ' ' || current == '-' || current == '.' || current == '"' ? '_' : current);
                }

                return sb.toString();
            }
        }

        return lower;
    }

    public static String formatValue(String s) {
        return s.replace("\"", "\\\"");
    }
}
