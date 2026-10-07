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
package com.github.mikumiku.addon.gui;

import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.widgets.pressable.WPressable;
import meteordevelopment.meteorclient.utils.render.color.Color;

/**
 * 可点击的小色块，用来显示并修改某个矿石的边框颜色。
 * 它直接引用样式里的 {@link Color} 对象，取色器改色后色块会立刻跟着变。
 */
public class WOreColorSwatch extends WPressable {
    private final Color color;

    public WOreColorSwatch(Color color) {
        this.color = color;
        this.tooltip = "点击选择边框颜色";
    }

    @Override
    protected void onCalculateSize() {
        width = theme.scale(16);
        height = theme.scale(16);
    }

    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        // 先铺一圈描边，白色等浅色在浅色主题下也看得见。
        renderer.quad(x, y, width, height, mouseOver ? theme.textColor() : theme.textSecondaryColor());
        renderer.quad(x + 1, y + 1, width - 2, height - 2, color);
    }
}
