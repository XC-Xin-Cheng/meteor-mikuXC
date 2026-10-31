package com.github.mikumiku.addon.util.seeds;

import java.util.List;

/**
 * 1.21.1 分支的降级桩：26.x 原生战利品模拟依赖本版注册表与结构生成 API，
 * 1.21.1 上不可用。保留原有 API，{@code create} 永远返回 null，
 * 调用方会退回种子库（seedfinding）路径。
 */
public final class NativeStructureLoot {
    public record Chest(int x, int y, int z, boolean enchantedGoldenApple) {
    }

    private NativeStructureLoot() {
    }

    public static NativeStructureLoot create(net.minecraft.core.RegistryAccess registries,
                                             net.minecraft.server.packs.resources.ResourceManager resources,
                                             long seed) {
        return null;
    }

    public List<Chest> predict(String structureId, net.minecraft.world.level.ChunkPos chunkPos,
                               NativeStructureLocator.Dim dimension) {
        return null;
    }
}
