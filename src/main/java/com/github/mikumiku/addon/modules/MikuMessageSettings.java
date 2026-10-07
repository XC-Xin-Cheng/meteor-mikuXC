/*
 * 本文件属于 meteor-miku 项目：https://github.com/mikumiku7/meteor-miku
 * meteor-miku 是 meteor-miku 在 Minecraft 26.1 / Fabric 上的移植版本。
 *
 * Copyright (C) 2024-2026 XC_XinCheng
 *
 * SPDX-License-Identifier: GPL-3.0-only
 *
 * 本程序是自由软件：你可以依据 GNU 通用公共许可证（GNU GPL）第 3 版的条款
 * 重新发布和/或修改它。
 *
 * 本程序基于“有用”的期望分发，但不提供任何担保，甚至不提供适销性或特定
 * 用途适用性的默示担保。详见 GNU 通用公共许可证。
 *
 * 你应当已随本程序收到一份 GNU 通用公共许可证副本；如果没有，请见
 * <https://www.gnu.org/licenses/>。
 */
package com.github.mikumiku.addon.modules;

import com.github.mikumiku.addon.BaseModule;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

/**
 * 「Miku 消息设置」——把聊天栏里那条固定的紫色 {@code [Meteor]} 换成你自己写的文字与颜色。
 *
 * <p>Meteor 的聊天工具每发一条消息都会先拼一个前缀 {@code [Meteor]}（用 addon 自己的
 * 颜色），后面再接模块名那段 {@code [模块名]}。本模块不改 Meteor 源码，而是让
 * {@code mixin/MeteorChatPrefixMixin} 在 Meteor 生成前缀时换成这里设置的内容；本模组、
 * 其它 Meteor addon 与 Meteor 自己发出的消息都会跟着变。</p>
 *
 * <p>设置项：</p>
 * <ul>
 *   <li>「前缀文字」：方括号里的内容，默认 {@code Meteor}；留空表示不改，仍用原版前缀。</li>
 *   <li>「颜色模式」：单色（取色器选一个颜色）或渐变（起始色 → 结束色，逐字过渡）。</li>
 *   <li>「起始颜色 / 结束颜色」：单色模式只用起始颜色。</li>
 *   <li>「保留方括号」：关掉后连方括号一起去掉，只留文字。</li>
 *   <li>「启用预览」：勾一下立刻在聊天栏发一条样例消息，确认文字与颜色。</li>
 * </ul>
 */
public class MikuMessageSettings extends BaseModule {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<String> prefixText = sgGeneral.add(new StringSetting.Builder()
        .name("前缀文字")
        .description("聊天栏前缀方括号里的内容，默认 Meteor；留空表示不改，继续用 Meteor 原来的前缀")
        .defaultValue("Meteor")
        .build()
    );

    private final Setting<ColorMode> colorMode = sgGeneral.add(new EnumSetting.Builder<ColorMode>()
        .name("颜色模式")
        .description("单色 = 整个前缀一个颜色；渐变 = 从起始颜色逐字过渡到结束颜色")
        .defaultValue(ColorMode.SINGLE)
        .build()
    );

    private final Setting<SettingColor> startColor = sgGeneral.add(new ColorSetting.Builder()
        .name("起始颜色")
        .description("单色模式用这一个颜色；渐变模式用它作为第一个字的颜色")
        .defaultValue(new SettingColor(200, 60, 255))
        .build()
    );

    private final Setting<SettingColor> endColor = sgGeneral.add(new ColorSetting.Builder()
        .name("结束颜色")
        .description("渐变模式里最后一个字的颜色")
        .defaultValue(new SettingColor(80, 200, 255))
        .visible(() -> colorMode.get() == ColorMode.GRADIENT)
        .build()
    );

    private final Setting<Boolean> brackets = sgGeneral.add(new BoolSetting.Builder()
        .name("保留方括号")
        .description("关掉后前缀只留「前缀文字」，不再带 [ ]")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> preview = sgGeneral.add(new BoolSetting.Builder()
        .name("启用预览")
        .description("勾一下就在聊天栏发一条样例消息，立刻看到当前前缀文字与颜色；看完会自动取消勾选")
        .defaultValue(false)
        .onChanged(this::onPreviewChanged)
        .build()
    );

    public MikuMessageSettings() {
        super(BaseModule.CATEGORY, "Miku 消息设置",
            "[Miku 消息设置] 把聊天栏里固定的紫色 [Meteor] 换成你自己写的文字与颜色："
                + "「前缀文字」填方括号里的内容，「颜色模式」选单色或渐变，再用取色器挑颜色。"
                + "勾一下「启用预览」可以立刻在聊天栏看到效果。");
    }

    @Override
    public void onActivate() {
        super.onActivate();
        info("Miku 消息设置已启用：聊天栏前缀现在显示为「%s」", prefixLabel());
    }

    @Override
    public void onDeactivate() {
        info("Miku 消息设置已禁用，聊天栏前缀还原为 Meteor 原前缀");
    }

    private void onPreviewChanged(boolean value) {
        if (!value) return;

        if (prefixText.get() == null || prefixText.get().isEmpty()) {
            info("前缀文字为空，聊天栏仍用 Meteor 原前缀");
        } else {
            info("样例消息：前缀现在是「%s」，后面的 [模块名] 不受影响", prefixLabel());
        }
        preview.set(false);
    }

    private String prefixLabel() {
        String text = prefixText.get();
        if (text == null) text = "";
        return brackets.get() ? "[" + text + "]" : text;
    }

    /**
     * 给 mixin 用：按当前设置拼出前缀组件；返回 {@code null} 表示不改，继续用 Meteor 原前缀。
     *
     * <p>模块没注册、没启用、或「前缀文字」留空时都返回 {@code null}，保证关掉开关就完全
     * 恢复原样。颜色用 {@link TextColor#fromRgb(int)} 写死 RGB，避免把 alpha 也带进去
     * 导致前缀变透明。</p>
     */
    public static Component buildPrefix() {
        MikuMessageSettings self = Modules.get().get(MikuMessageSettings.class);
        if (self == null || !self.isActive()) return null;

        String text = self.prefixText.get();
        if (text == null || text.isEmpty()) return null;

        boolean withBrackets = self.brackets.get();
        boolean gradient = self.colorMode.get() == ColorMode.GRADIENT;
        int start = 0xFF000000 | (self.startColor.get().getPacked() & 0xFFFFFF);
        int end = 0xFF000000 | (self.endColor.get().getPacked() & 0xFFFFFF);

        MutableComponent prefix = Component.empty().setStyle(Style.EMPTY.applyFormat(ChatFormatting.GRAY));
        if (withBrackets) prefix.append("[");

        if (gradient && text.length() > 1) {
            int length = text.length();
            for (int i = 0; i < length; i++) {
                double ratio = (double) i / (length - 1);
                prefix.append(Component.literal(String.valueOf(text.charAt(i)))
                    .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(lerpColor(start, end, ratio)))));
            }
        } else {
            prefix.append(Component.literal(text)
                .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(start & 0xFFFFFF))));
        }

        if (withBrackets) prefix.append("]");
        return prefix;
    }

    /** 两个 packed RGB 颜色之间线性插值，返回 packed RGB。 */
    private static int lerpColor(int from, int to, double ratio) {
        double t = Math.max(0.0, Math.min(1.0, ratio));
        int r = (int) (((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t);
        int g = (int) (((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t);
        int b = (int) ((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
        return ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
    }

    public enum ColorMode {
        SINGLE("单色"),
        GRADIENT("渐变");

        private final String title;

        ColorMode(String title) {
            this.title = title;
        }

        @Override
        public String toString() {
            return title;
        }
    }
}
