package cn.blockforge.meteorzhcn.util;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * 跨版本地打开一个界面。
 *
 * <p>26.1 里是 {@code Minecraft.setScreen(Screen)}，26.2 把界面状态挪进了 {@code Gui}，
 * 变成 {@code Minecraft.gui.setScreen(Screen)}（{@code Minecraft.setScreen} 已不存在）。
 * 两边共同拥有的只有 {@code Minecraft.setScreenAndShow}，但那会立刻再渲染一帧，
 * 从按钮回调里调用会递归进渲染。因此这里按版本在运行时挑出正确的方法，
 * 编译期不引用任何一边特有的成员。</p>
 */
public final class ClientScreens {
    private static Method setScreen;
    private static Object setScreenTarget;
    private static boolean resolved;

    private ClientScreens() {
    }

    public static void open(Screen screen) {
        Minecraft minecraft = Minecraft.getInstance();

        if (!resolved) {
            resolve(minecraft);
        }

        try {
            setScreen.invoke(setScreenTarget, screen);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("无法打开自定义界面", e);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            throw cause instanceof RuntimeException runtime ? runtime : new RuntimeException("无法打开自定义界面", cause);
        }
    }

    private static void resolve(Minecraft minecraft) {
        resolved = true;

        // 26.1 及更早：Minecraft.setScreen(Screen)
        try {
            setScreen = Minecraft.class.getMethod("setScreen", Screen.class);
            setScreenTarget = minecraft;
            return;
        } catch (NoSuchMethodException ignored) {
        }

        // 26.2：界面状态在 Minecraft.gui 上
        try {
            Object gui = Minecraft.class.getField("gui").get(minecraft);
            setScreen = gui.getClass().getMethod("setScreen", Screen.class);
            setScreenTarget = gui;
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("当前版本的 Minecraft 找不到可用的 setScreen 方法", e);
        }
    }
}
