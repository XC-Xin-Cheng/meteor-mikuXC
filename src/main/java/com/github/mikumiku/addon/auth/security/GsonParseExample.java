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
package com.github.mikumiku.addon.auth.security;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

public class GsonParseExample {
    public static void main(String[] args) {
        String json = "{\n" +
            "  \"success\": true,\n" +
            "  \"message\": \"ok\",\n" +
            "  \"data\": {\n" +
            "    \"token\": \"token\",\n" +
            "    \"config\": {\n" +
            "      \"api_version\": \"1.0\",\n" +
            "      \"server_time\": 1735555555555,\n" +
            "      \"features_enabled\": [\"feature1\", \"feature2\"],\n" +
            "      \"max_connections\": 10\n" +
            "    }\n" +
            "  }\n" +
            "}";

        Gson gson = new Gson();
        Type type = new TypeToken<Map<String, Object>>() {
        }.getType();
        Map<String, Object> root = gson.fromJson(json, type);

        // 顶层字段
        boolean success = (Boolean) root.get("success");
        String message = (String) root.get("message");
        System.out.println("success=" + success + ", message=" + message);

        // data 层
        Map<String, Object> data = (Map<String, Object>) root.get("data");
        String token = (String) data.get("token");
        System.out.println("token=" + token);

        // config 层
        Map<String, Object> config = (Map<String, Object>) data.get("config");
        String apiVersion = (String) config.get("api_version");
        Double serverTime = ((Number) config.get("server_time")).doubleValue();
        List<String> features = (List<String>) config.get("features_enabled");
        int maxConnections = ((Number) config.get("max_connections")).intValue();

        System.out.println("api_version=" + apiVersion);
        System.out.println("server_time=" + serverTime.longValue());
        System.out.println("features_enabled=" + features);
        System.out.println("max_connections=" + maxConnections);
    }
}
