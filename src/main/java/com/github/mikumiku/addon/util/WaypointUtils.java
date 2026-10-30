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

import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.minimap.BuiltInHudModules;
import xaero.hud.minimap.module.MinimapSession;
import xaero.hud.minimap.waypoint.WaypointColor;
import xaero.hud.minimap.waypoint.set.WaypointSet;
import xaero.hud.minimap.world.MinimapWorld;
import xaero.map.mods.SupportMods;

public class WaypointUtils {

    /**
     * Xaero 路径点颜色索引，对应 {@link WaypointColor} 枚举的序号：
     * {@code GREEN}=10、{@code PURPLE}=13、{@code YELLOW}=14。
     * 这里用编译期常量，避免在没装 Xaero 时因加载枚举而拖垮模块类。
     */
    public static final int COLOR_GREEN = 10;
    public static final int COLOR_YELLOW = 14;

    public static void addToWaypoints(int x, int y, int z, String name) {
        addToWaypoints(x, y, z, name, "翅", COLOR_GREEN);
    }

    public static void addToWaypoints(int x, int y, int z, String name, String shortName) {
        addToWaypoints(x, y, z, name, shortName, COLOR_GREEN);
    }

    /**
     * 按指定颜色添加一个 Xaero 路径点。
     *
     * @return 真正写入返回 {@code true}；Xaero 不可用或该 X/Z 已有路径点时返回 {@code false}
     */
    public static boolean addToWaypoints(int x, int y, int z, String name, String shortName, int color) {
        WaypointSet waypointSet = getWaypointSet();
        if (waypointSet == null) return false;

        // dont add waypoint that already exists
        if (getWaypointByCoordinate(x, z) != null) return false;

        Waypoint waypoint = new Waypoint(
            x,
            y,
            z,
            name,
            shortName,
            color,
            0,
            false);

        waypointSet.add(waypoint);

        SupportMods.xaeroMinimap.requestWaypointsRefresh();

        return true;
    }

    public static Waypoint getWaypointByCoordinate(int x, int z) {
        WaypointSet waypointSet = getWaypointSet();
        if (waypointSet == null) return null;
        for (Waypoint waypoint : waypointSet.getWaypoints()) {
            if (waypoint.getX() == x && waypoint.getZ() == z) {
                return waypoint;
            }
        }
        return null;
    }


    public static WaypointSet getWaypointSet() {
        MinimapSession minimapSession = BuiltInHudModules.MINIMAP.getCurrentSession();
        if (minimapSession == null) return null;
        MinimapWorld currentWorld = minimapSession.getWorldManager().getCurrentWorld();
        if (currentWorld == null) return null;
        return currentWorld.getCurrentWaypointSet();
    }


}
