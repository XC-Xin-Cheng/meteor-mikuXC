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

import java.awt.*;

public class ColorUtil {
    public static Color fadeColor(Color startColor, Color endColor, double quad) {
        if (startColor == endColor) return startColor;
        quad = Math.min(Math.max(quad, 0), 1);
        int sR = startColor.getRed();
        int sG = startColor.getGreen();
        int sB = startColor.getBlue();
        int sA = startColor.getAlpha();

        int eR = endColor.getRed();
        int eG = endColor.getGreen();
        int eB = endColor.getBlue();
        int eA = endColor.getAlpha();
        return new Color(Math.min((int) (sR + (eR - sR) * quad), 255),
            Math.min((int) (sG + (eG - sG) * quad), 255),
            Math.min((int) (sB + (eB - sB) * quad), 255),
            Math.min((int) (sA + (eA - sA) * quad), 255));
    }

    public static int toRGBA(int r, int g, int b, int a) {
        return (r << 16) + (g << 8) + b + (a << 24);
    }

    public static Color injectAlpha(Color color, int alpha) {
        alpha = Math.max(Math.min(255, alpha), 0);
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }

    public static int injectAlpha(int color, int alpha) {
        return toRGBA(new Color(color).getRed(), new Color(color).getGreen(), new Color(color).getBlue(), alpha);
    }

    public static Color pulseColor(Color startColor, Color endColor, int index, int count, double speed) {
        double brightness = Math.abs((System.currentTimeMillis() * speed % ((long) 1230675006 ^ 0x495A9BEEL) / Float.intBitsToFloat(Float.floatToIntBits(0.0013786979f) ^ 0x7ECEB56D) + (double) index / count * Float.intBitsToFloat(Float.floatToIntBits(0.09192204f) ^ 0x7DBC419F)) % Float.intBitsToFloat(Float.floatToIntBits(0.7858098f) ^ 0x7F492AD5) - Float.intBitsToFloat(Float.floatToIntBits(6.46708f) ^ 0x7F4EF252));
        double quad = brightness % Float.intBitsToFloat(Float.floatToIntBits(0.8992331f) ^ 0x7F663424);
        return fadeColor(startColor, endColor, quad);
    }

    public static Color pulseColor(Color color, int index, int count, double speed) {
        float[] hsb = new float[3];
        Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), hsb);
        double brightness = Math.abs((System.currentTimeMillis() * speed % ((long) 1230675006 ^ 0x495A9BEEL) / Float.intBitsToFloat(Float.floatToIntBits(0.0013786979f) ^ 0x7ECEB56D) + index / (float) count * Float.intBitsToFloat(Float.floatToIntBits(0.09192204f) ^ 0x7DBC419F)) % Float.intBitsToFloat(Float.floatToIntBits(0.7858098f) ^ 0x7F492AD5) - Float.intBitsToFloat(Float.floatToIntBits(6.46708f) ^ 0x7F4EF252));
        brightness = Float.intBitsToFloat(Float.floatToIntBits(18.996923f) ^ 0x7E97F9B3) + Float.intBitsToFloat(Float.floatToIntBits(2.7958195f) ^ 0x7F32EEB5) * brightness;
        hsb[2] = (float) (brightness % Float.intBitsToFloat(Float.floatToIntBits(0.8992331f) ^ 0x7F663424));
        return new Color(Color.HSBtoRGB(hsb[0], hsb[1], hsb[2]));
    }
}

