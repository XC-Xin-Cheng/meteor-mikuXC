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

public interface MEnum {


    /**
     * 过滤模式枚举
     */
    enum ListMode {
        Whitelist("白名单"),
        Blacklist("黑名单");

        private final String displayName;

        ListMode(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }


    enum WalkMode {
        Simple("简单"),
        Smart("智能");

        private final String displayName;

        WalkMode(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }


    public enum WalkDirection {
        Forwards("前进"),
        Backwards("后退"),
        Left("左"),
        Right("右");

        private final String displayName;

        WalkDirection(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    enum LandingMode {
        Chorus("恰紫颂果"),
        Light("温柔降落"),
        HEAD("头朝下暴力降落");


        private final String displayName;

        LandingMode(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }
}
