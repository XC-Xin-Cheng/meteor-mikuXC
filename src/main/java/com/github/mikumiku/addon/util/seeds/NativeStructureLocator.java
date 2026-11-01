package com.github.mikumiku.addon.util.seeds;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;

/**
 * 26.3 分支的降级桩。
 *
 * <p>Minecraft 26.3 重构了世界生成 API：{@code ConfiguredFeature} 被移除，特征配置
 * 直接挂在 Feature 实例上；原来的结构集 / 生物群系取样接口也整体换过，
 * 本模组原先的「本版原生」定位实现无法编译。这里保留原有 API，
 * {@code create} 返回 {@code null}，调用方自动退回种子库（seedfinding）路线。</p>
 */
public final class NativeStructureLocator {
    public static final int SURFACE_Y = 63;
    public static final int WAYPOINT_Y = 100;
    public static final int NETHER_FORTRESS_Y = 64;
    public static final int NETHER_BASTION_Y = 33;
    public static final int NETHER_FOSSIL_Y = 32;
    public static final int END_Y = 64;
    public static final int STRONGHOLD_Y = 32;

    public enum Dim {
        OVERWORLD, NETHER, END
    }

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

    private NativeStructureLocator() {
    }

    public static NativeStructureLocator create(net.minecraft.core.RegistryAccess registryAccess, long seed) {
        return null;
    }

    public Set<String> supportedDisplays() {
        return Set.of();
    }

    public List<Found> scan(int centerChunkX, int centerChunkZ, int radiusChunks,
                            Set<String> wanted, BooleanSupplier running) {
        return new ArrayList<>();
    }
}
