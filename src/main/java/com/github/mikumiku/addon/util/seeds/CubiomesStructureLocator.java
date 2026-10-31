package com.github.mikumiku.addon.util.seeds;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;

/**
 * 1.21.11 分支的降级桩：cubiomes 的 FFM 绑定按更高 Java 版本编译，无法在 Java 21 下加载。
 * 保留原有 API，但定位器永远创建失败，调用方会自动退回本版原生 / 种子库算法。
 */
public final class CubiomesStructureLocator {
    public record Found(String display, int blockX, int blockZ, int y) {
    }

    private CubiomesStructureLocator() {
    }

    public static boolean isNativeLoaded() {
        return false;
    }

    public static String loadMessage() {
        return "1.21.11 构建未内置 cubiomes 原生库，已退回本版原生 / 种子库算法";
    }

    public static CubiomesStructureLocator tryCreate(SeedVersion version, long seed) {
        return null;
    }

    public String versionLabel() {
        return "unavailable";
    }

    public Set<String> supportedDisplays() {
        return Set.of();
    }

    public List<Found> scan(int centerChunkX, int centerChunkZ, int radiusChunks,
                            Set<String> wanted, List<String> skipped, BooleanSupplier running) {
        return new ArrayList<>();
    }
}
