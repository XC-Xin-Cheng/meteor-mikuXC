package com.github.mikumiku.addon.util.seeds;

import java.util.ArrayList;
import java.util.List;

/**
 * 1.21.11 分支的降级桩：26.x 原生世界生成定位依赖本版注册表结构，
 * 1.21.11 上不可用。保留原有 API，{@code create} 永远返回 null，
 * 调用方会退回种子库（seedfinding）路径。
 */
public final class NativeStructureLocator {
    public static final int SURFACE_Y = 63;
    public static final int WAYPOINT_Y = 100;
    public static final int NETHER_FORTRESS_Y = 64;
    public static final int NETHER_BASTION_Y = 33;
    public static final int NETHER_FOSSIL_Y = 32;
    public static final int END_Y = 64;
    public static final int STRONGHOLD_Y = 32;

    public static final class Found {
        public final String display;
        public final String id;
        public final Dim dim;
        public final int chunkX;
        public final int chunkZ;
        public final int blockX;
        public final int blockZ;
        public final int y;

        public Found(String display, String id, int chunkX, int chunkZ, int blockX, int blockZ, int y, Dim dim) {
            this.display = display;
            this.id = id;
            this.dim = dim;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.blockX = blockX;
            this.blockZ = blockZ;
            this.y = y;
        }
    }

    public enum Dim {
        OVERWORLD, NETHER, END
    }

    private NativeStructureLocator() {
    }

    public static NativeStructureLocator create(net.minecraft.core.RegistryAccess registryAccess, long seed) {
        return null;
    }

    public java.util.Set<String> supportedDisplays() {
        return java.util.Set.of();
    }

    public List<Found> scan(int centerChunkX, int centerChunkZ, int radiusChunks,
                            java.util.Set<String> wanted, java.util.function.BooleanSupplier running) {
        return new ArrayList<>();
    }
}
