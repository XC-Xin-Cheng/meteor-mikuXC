package com.github.mikumiku.addon.util.seeds;

/**
 * 26.3 分支的降级桩：古城定位依赖的 {@code random_spread} / 生物群系取样接口在本版已重构，
 * {@code create} 返回 {@code null}，调用方跳过古城并给出提示。
 */
public final class AncientCityLocator {
    public static final int SPACING = 24;
    public static final int SEPARATION = 8;
    public static final int SALT = 20083232;
    public static final int FLOOR_Y = -51;

    private AncientCityLocator() {
    }

    public static AncientCityLocator create(net.minecraft.core.RegistryAccess registryAccess, long seed) {
        return null;
    }

    public net.minecraft.world.level.ChunkPos potentialChunk(long seed, int regionX, int regionZ) {
        return null;
    }

    public boolean isDeepDarkAt(int chunkX, int chunkZ) {
        return false;
    }
}
