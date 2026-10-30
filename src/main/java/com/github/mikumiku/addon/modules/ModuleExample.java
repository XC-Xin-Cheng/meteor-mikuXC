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
import com.github.mikumiku.addon.ok.MikuMikuAddon;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

public class ModuleExample extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgRender = this.settings.createGroup("Render");

    /**
     * Example setting.
     * The {@code name} parameter should be in kebab-case.
     * If you want to access the setting from another class, simply make the setting {@code public}, and use
     * {@link meteordevelopment.meteorclient.systems.modules.Modules#get(Class)} to access the {@link Module} object.
     */
    private final Setting<Double> scale = sgGeneral.add(new DoubleSetting.Builder()
        .name("scale")
        .description("The size of the marker.")
        .defaultValue(2.0d)
        .range(0.5d, 10.0d)
        .build()
    );

    private final Setting<SettingColor> color = sgRender.add(new ColorSetting.Builder()
        .name("color")
        .description("The color of the marker.")
        .defaultValue(Color.MAGENTA)
        .build()
    );

    /**
     * The {@code name} parameter should be in kebab-case.
     */
    public ModuleExample() {
        super(BaseModule.CATEGORY, "world-origin", "An example module that highlights the center of the world.");
    }

    /**
     * Example event handling method.
     * Requires {@link MikuMikuAddon#getPackage()} to be setup correctly, otherwise the game will crash whenever the module is enabled.
     */
    @EventHandler
    private void onRender3d(Render3DEvent event) {
        // Create & stretch the marker object
        AABB marker = new AABB(BlockPos.ZERO);
        marker = marker.expandTowards(
            scale.get() * marker.getXsize(),
            scale.get() * marker.getYsize(),
            scale.get() * marker.getZsize()
        );

        // Render the marker based on the color setting
        event.renderer.box(marker, color.get(), color.get(), ShapeMode.Both, 0);
    }
}
