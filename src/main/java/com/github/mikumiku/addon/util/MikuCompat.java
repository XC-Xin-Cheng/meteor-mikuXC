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
package com.github.mikumiku.addon.util;

import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * 26.1 / 26.2 之间的少量 API 兼容层。
 *
 * <p>26.2 把几个长期存在的成员挪了位置，编译期（本工程按 26.1 编译）全都存在，
 * 一到 26.2 运行时就会抛 {@code NoSuchFieldError} / {@code NoSuchMethodError}：</p>
 * <ul>
 *   <li>当前界面：26.1 是 {@code Minecraft.screen} / {@code Minecraft.setScreen(Screen)}；
 *       26.2 挪到了 {@code Minecraft.gui.screen()} / {@code Gui.setScreen(Screen)},
 *       打开界面还要走 {@code Minecraft.setScreenAndShow(Screen)}。</li>
 *   <li>聊天栏：26.1 是 {@code Minecraft.gui.getChat()}；26.2 挪到 {@code Minecraft.gui.hud.getChat()}。</li>
 *   <li>相机：26.1 是 {@code GameRenderer.getMainCamera()}；26.2 改名 {@code GameRenderer.mainCamera()}。</li>
 *   <li>区块重绘：26.1 是 {@code LevelRenderer.allChanged()}；26.2 改名 {@code LevelRenderer.resetLevelRenderData()}。</li>
 *   <li>文本渲染：26.1 是 {@code TextRenderer.begin(double[, boolean, boolean])}；26.2 起第一个参数
 *       变成当前帧的 GUI 上下文 {@code GuiGraphicsExtractor}（{@code Render2DEvent.graphics}），
 *       旧的单参数重载被删除，照旧调用会抛 {@code NoSuchMethodError} 并直接把游戏打崩。</li>
 * </ul>
 *
 * <p>统一在这里先用反射探测新版本成员，探不到再走编译期的那条老路。全部包在
 * {@code catch (Throwable)} 里，任何一边缺失都只是降级，不会把游戏打崩。</p>
 */
public final class MikuCompat {
    private MikuCompat() {}

    // ---------------- 当前界面 ----------------

    private static boolean screenInit;
    private static Field mcScreenField;          // 26.1: Minecraft.screen
    private static Field mcGuiField;             // 两边都有: Minecraft.gui
    private static Method guiScreenMethod;       // 26.2: Gui.screen()
    private static Method guiSetScreenMethod;    // 26.2: Gui.setScreen(Screen)
    private static Method mcSetScreenAndShow;    // 26.2: Minecraft.setScreenAndShow(Screen)
    private static Method mcSetScreenMethod;     // 26.1: Minecraft.setScreen(Screen)

    private static void initScreen() {
        if (screenInit) return;
        screenInit = true;
        try { mcScreenField = Minecraft.class.getField("screen"); } catch (Throwable ignored) {}
        try { mcGuiField = Minecraft.class.getField("gui"); } catch (Throwable ignored) {}
        if (mcGuiField != null) {
            Class<?> guiType = mcGuiField.getType();
            try { guiScreenMethod = guiType.getMethod("screen"); } catch (Throwable ignored) {}
            try { guiSetScreenMethod = guiType.getMethod("setScreen", Screen.class); } catch (Throwable ignored) {}
        }
        try { mcSetScreenAndShow = Minecraft.class.getMethod("setScreenAndShow", Screen.class); } catch (Throwable ignored) {}
        try { mcSetScreenMethod = Minecraft.class.getMethod("setScreen", Screen.class); } catch (Throwable ignored) {}
    }

    /** 当前打开的界面，没有则返回 null。等价于 26.1 的 {@code mc.screen}。 */
    public static Screen screen() {
        initScreen();
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return null;
        if (guiScreenMethod != null && mcGuiField != null) {
            try {
                Object gui = mcGuiField.get(mc);
                Object value = guiScreenMethod.invoke(gui);
                return value instanceof Screen screen ? screen : null;
            } catch (Throwable ignored) {
                // 落到 26.1 的字段
            }
        }
        if (mcScreenField != null) {
            try {
                Object value = mcScreenField.get(mc);
                return value instanceof Screen screen ? screen : null;
            } catch (Throwable ignored) {
                // 两个版本都取不到，按“没有界面”处理
            }
        }
        return null;
    }

    /** 打开/关闭界面。等价于 26.1 的 {@code mc.setScreen(screen)}。 */
    public static void setScreen(Screen screen) {
        initScreen();
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        if (mcSetScreenAndShow != null) {
            try { mcSetScreenAndShow.invoke(mc, screen); return; } catch (Throwable ignored) {}
        }
        if (guiSetScreenMethod != null && mcGuiField != null) {
            try { guiSetScreenMethod.invoke(mcGuiField.get(mc), screen); return; } catch (Throwable ignored) {}
        }
        if (mcSetScreenMethod != null) {
            try { mcSetScreenMethod.invoke(mc, screen); } catch (Throwable ignored) {}
        }
    }

    // ---------------- 聊天栏 ----------------

    private static boolean chatInit;
    private static Method mcGuiGetChat;   // 26.1: Gui.getChat()
    private static Field guiHudField;     // 26.2: Gui.hud
    private static Method hudGetChat;     // 26.2: Hud.getChat()

    private static void initChat() {
        if (chatInit) return;
        chatInit = true;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.gui == null) return;
        Class<?> guiType = mc.gui.getClass();
        try { mcGuiGetChat = guiType.getMethod("getChat"); } catch (Throwable ignored) {}
        try {
            Field hudField = guiType.getField("hud");
            guiHudField = hudField;
            hudGetChat = hudField.getType().getMethod("getChat");
        } catch (Throwable ignored) {}
    }

    /** 聊天栏组件。等价于 26.1 的 {@code mc.gui.getChat()}。 */
    public static ChatComponent chat() {
        initChat();
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.gui == null) return null;
        if (guiHudField != null && hudGetChat != null) {
            try {
                Object hud = guiHudField.get(mc.gui);
                Object value = hudGetChat.invoke(hud);
                return value instanceof ChatComponent component ? component : null;
            } catch (Throwable ignored) {
                // 落到 26.1 的 Gui.getChat()
            }
        }
        if (mcGuiGetChat != null) {
            try {
                Object value = mcGuiGetChat.invoke(mc.gui);
                return value instanceof ChatComponent component ? component : null;
            } catch (Throwable ignored) {
                // 取不到就返回 null
            }
        }
        return null;
    }

    // ---------------- 相机 ----------------

    private static boolean cameraInit;
    private static Method gameRendererMainCamera;    // 26.2: GameRenderer.mainCamera()
    private static Method gameRendererGetMainCamera; // 26.1: GameRenderer.getMainCamera()

    private static void initCamera() {
        if (cameraInit) return;
        cameraInit = true;
        try { gameRendererMainCamera = GameRenderer.class.getMethod("mainCamera"); } catch (Throwable ignored) {}
        try { gameRendererGetMainCamera = GameRenderer.class.getMethod("getMainCamera"); } catch (Throwable ignored) {}
    }

    /** 主相机，取不到时返回 null。 */
    public static Camera mainCamera() {
        initCamera();
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.gameRenderer == null) return null;
        if (gameRendererMainCamera != null) {
            try {
                Object value = gameRendererMainCamera.invoke(mc.gameRenderer);
                return value instanceof Camera camera ? camera : null;
            } catch (Throwable ignored) {
                // 落到 26.1 的老方法
            }
        }
        if (gameRendererGetMainCamera != null) {
            try {
                Object value = gameRendererGetMainCamera.invoke(mc.gameRenderer);
                return value instanceof Camera camera ? camera : null;
            } catch (Throwable ignored) {
                // 两边都取不到
            }
        }
        return null;
    }

    // ---------------- 区块重绘 ----------------

    private static boolean levelInit;
    private static Method levelRendererReset;    // 26.2: LevelRenderer.resetLevelRenderData()
    private static Method levelRendererChanged;  // 26.1: LevelRenderer.allChanged()

    private static void initLevelRenderer() {
        if (levelInit) return;
        levelInit = true;
        try { levelRendererReset = LevelRenderer.class.getMethod("resetLevelRenderData"); } catch (Throwable ignored) {}
        try { levelRendererChanged = LevelRenderer.class.getMethod("allChanged"); } catch (Throwable ignored) {}
    }

    /** 强制重建区块渲染数据。等价于 26.1 的 {@code mc.levelRenderer.allChanged()}。 */
    public static void reloadRenderer() {
        initLevelRenderer();
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.levelRenderer == null) return;
        if (levelRendererReset != null) {
            try { levelRendererReset.invoke(mc.levelRenderer); return; } catch (Throwable ignored) {}
        }
        if (levelRendererChanged != null) {
            try { levelRendererChanged.invoke(mc.levelRenderer); } catch (Throwable ignored) {}
        }
    }

    // ---------------- 文本渲染 ----------------

    private static boolean textInit;
    private static Method textBeginWithGraphics;      // 26.2: begin(GuiGraphicsExtractor, double, boolean, boolean)
    private static Method textBeginWithGraphicsScale; // 26.2: begin(GuiGraphicsExtractor, double)
    private static Method textBeginScaleOnly;         // 26.1: begin(double, boolean, boolean)
    private static Method textBeginScale;             // 26.1: begin(double)

    private static void initText() {
        if (textInit) return;
        textInit = true;
        try {
            for (Method m : TextRenderer.class.getMethods()) {
                if (!m.getName().equals("begin")) continue;
                Class<?>[] p = m.getParameterTypes();
                if (p.length == 4 && p[1] == double.class && p[2] == boolean.class && p[3] == boolean.class
                    && !p[0].isPrimitive()) {
                    textBeginWithGraphics = m;
                } else if (p.length == 2 && p[1] == double.class && !p[0].isPrimitive()) {
                    textBeginWithGraphicsScale = m;
                } else if (p.length == 3 && p[0] == double.class && p[1] == boolean.class && p[2] == boolean.class) {
                    textBeginScaleOnly = m;
                } else if (p.length == 1 && p[0] == double.class) {
                    textBeginScale = m;
                }
            }
        } catch (Throwable ignored) {
            // 探测失败就退化成“不画文字”，不会把游戏打崩
        }
    }

    /**
     * 兼容两代签名的 {@link TextRenderer#begin}。
     *
     * <p>26.2 把 GUI 上下文提到了第一个参数，26.1 没有这个参数。这里按运行时探测到的
     * 真实方法分派：{@code graphics} 一般传 {@code Render2DEvent.graphics}，26.1 下会被忽略。</p>
     *
     * @param renderer 文本渲染器，通常是 {@code TextRenderer.get()}
     * @param graphics 当前帧的 GUI 上下文，可为 null
     * @param scale    缩放倍率
     * @param big      是否使用大号字体（对应 26.1 的 {@code begin(scale, false, true)}）
     */
    public static void beginText(TextRenderer renderer, Object graphics, double scale, boolean big) {
        if (renderer == null) return;
        initText();
        if (graphics != null) {
            if (textBeginWithGraphics != null) {
                try {
                    textBeginWithGraphics.invoke(renderer, graphics, scale, false, big);
                    return;
                } catch (Throwable ignored) {
                    // 继续尝试其它签名
                }
            }
            if (!big && textBeginWithGraphicsScale != null) {
                try {
                    textBeginWithGraphicsScale.invoke(renderer, graphics, scale);
                    return;
                } catch (Throwable ignored) {
                    // 继续尝试其它签名
                }
            }
        }
        if (textBeginScaleOnly != null) {
            try {
                textBeginScaleOnly.invoke(renderer, scale, false, big);
                return;
            } catch (Throwable ignored) {
                // 继续尝试其它签名
            }
        }
        if (textBeginScale != null) {
            try { textBeginScale.invoke(renderer, scale); } catch (Throwable ignored) {}
        }
    }
}
