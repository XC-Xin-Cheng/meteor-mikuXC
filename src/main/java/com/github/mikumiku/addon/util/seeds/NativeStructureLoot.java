package com.github.mikumiku.addon.util.seeds;

import java.util.List;

/**
 * 26.3 分支的降级桩：原版结构战利品模拟依赖本版注册表与结构生成 API，
 * 在 26.3 上不可用。保留原有 API，{@code create} 返回 {@code null}，
 * 调用方退回种子库路线。
 */
public final class NativeStructureLoot {
    public record Chest(int x, int y, int z, String lootTable, boolean enchantedGoldenApple) {
    }

    private NativeStructureLoot() {
    }

    public static NativeStructureLoot create(net.minecraft.core.RegistryAccess registries,
                                             net.minecraft.server.packs.resources.ResourceManager resources,
                                             long seed) {
        return null;
    }

    public boolean supports(NativeStructureLocator.Dim dim) {
        return false;
    }

    public List<Chest> predict(String structureId, net.minecraft.world.level.ChunkPos startChunk,
                               NativeStructureLocator.Dim dim) {
        return null;
    }
}
