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

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class NetworkUtils {

    /**
     * 请求 ping 接口并判断是否联通且返回值正确（期望 "pong" 文本）。
     * 重试最多 3 次（总尝试次数 = 3）。
     *
     * @param pingUrl 完整的 ping 接口 URL，例如 "https://example.xx.dev/ping"
     * @return true 如果网络可达且返回体为 "pong"，否则 false
     */
    public static Boolean isPingReachable(String pingUrl) {
        final int maxAttempts = 3;
        final Duration requestTimeout = Duration.ofSeconds(5); // 单次请求超时
        HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(pingUrl))
            .timeout(requestTimeout)
            .GET()
            .build();

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                // 判断 HTTP 状态码和响应体
                if (response.statusCode() == 200) {
                    String body = response.body();
                    if (body != null && body.trim().equals("pong")) {
                        return Boolean.TRUE;
                    } else {
                        // 收到非预期 body，视为失败并重试
                        // 可以选择记录日志：System.out.println(...)
                    }
                } else {
                    // 非 200 状态码，视为失败并重试
                }
            } catch (IOException | InterruptedException e) {
                // 网络或中断异常，视为失败并重试
                // 恢复中断状态（如果是 InterruptedException）
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
            }

            // 如果不是最后一次尝试，短暂等待后重试（简单退避）
            if (attempt < maxAttempts) {
                try {
                    Thread.sleep(500L * attempt); // 等待 0.5s, 1.0s, ...（简单退避）
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        // 所有尝试失败
        return Boolean.FALSE;
    }


}
