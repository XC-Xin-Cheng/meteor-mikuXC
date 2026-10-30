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

import baritone.api.BaritoneAPI;
import baritone.api.utils.BetterBlockPos;
import com.github.mikumiku.addon.BaseModule;
import com.github.mikumiku.addon.mixinface.MagicMix;
import com.github.mikumiku.addon.util.MeteorXrayBridge;
import com.github.mikumiku.addon.util.Ore;
import com.github.mikumiku.addon.util.OreListSetting;
import com.github.mikumiku.addon.util.OreSelectStore;
import com.github.mikumiku.addon.util.seeds.Seed;
import com.github.mikumiku.addon.util.seeds.SeedVersion;
import com.github.mikumiku.addon.util.seeds.Seeds;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.BlockUpdateEvent;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.pathing.BaritoneUtils;
import meteordevelopment.meteorclient.pathing.NopPathManager;
import meteordevelopment.meteorclient.pathing.PathManagers;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.world.Dimension;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class SeedMine extends BaseModule {

    private final Map<Long, Map<Ore, Set<Vec3>>> chunkRenderers = new ConcurrentHashMap<>();
    private Seed worldSeed = null;
    private Map<ResourceKey<Biome>, List<Ore>> oreConfig;
    public List<BlockPos> oreGoals = new ArrayList<>();
    private int tickCounter = 0;
    /** 与世界同步的计时器（tick）。 */
    private int syncCounter = 0;

    /**
     * “边框连接”模式用的本帧矿位索引：下标是 {@link Ore.OreKey#index()}，里面存打包后的方块坐标。
     * 渲染前按可见区块重建一次，之后查相邻方块都是无分配的长整型集合查找。
     */
    private final LongOpenHashSet[] connectedScratch = new LongOpenHashSet[Ore.OreKey.COUNT];

    /** 借鉴上游 rejects 的 OreSim#AirCheck：控制算出的矿位是否要额外做空气判定。 */
    public enum AirCheck {
        /** 只在区块加载时判定一次，之后即使被挖开也不移除。 */
        ON_LOAD,
        /** 加载时判定，并在方块更新时重新判定（上游默认值）。 */
        RECHECK,
        /** 完全不判定，直接把算出来的位置都画出来。 */
        OFF
    }

    private final SettingGroup sgSeed = settings.createGroup("种子设置");
    private final Setting<String> seedInput = sgSeed.add(new StringSetting.Builder()
        .name("种子")
        .description("输入世界种子。（默认值为3C3U种子）")
        .defaultValue("-7346913998703726680")
        .build()
    );

    private final Setting<String> mcVersionInput = sgSeed.add(new StringSetting.Builder()
        .name("MC版本")
        .description("手动输入 Minecraft 版本，支持 1.12.2 ~ 26.2（例如 26.2、1.21.4、1.20.1、1.12.2）。低于 1.12.2、高于 26.2 或认不出来的写法按空白处理：点“应用种子”不会写入种子库，聊天栏也不会输出内容。矿位计算直接调用本版游戏原生的世界生成算法（与上游 meteor-rejects 的 OreSim 相同，26.x 补丁号之间算法一致），该版本仅作种子元数据记录。")
        .defaultValue(SeedVersion.MAX_INPUT_VERSION)
        .build()
    );

    private final Setting<Boolean> applySeed = sgSeed.add(new BoolSetting.Builder()
        .name("应用种子")
        .description("点击应用上面设置的种子和版本")
        .defaultValue(false)
        .onChanged(this::onApplySeedChanged)
        .build()
    );

    private final Setting<Boolean> worldSync = sgSeed.add(new BoolSetting.Builder()
        .name("与世界同步")
        .description("自动读取当前世界的真实种子并与世界保持一致：切换世界或维度、种子变化时自动重算矿位。单人存档直接取存档种子，服务器上取按世界名保存的种子。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> syncInterval = sgSeed.add(new IntSetting.Builder()
        .name("同步时间")
        .description("每隔多少秒与当前世界同步一次（检查种子/维度并补算新加载的区块）。")
        .defaultValue(3)
        .min(1)
        .max(600)
        .sliderMax(30)
        .visible(worldSync::get)
        .build()
    );

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final SettingGroup sgOres = settings.createGroup("矿物");

    /**
     * 勾选了哪些矿石就显示/挖掘哪些矿石。浅层 = 石头变种（Y≥0），
     * 深层 = 深板岩变种（Y&lt;0），下界矿石单独一段。
     * 每种矿石还能单独设置边框是否连接、用什么颜色（见选择界面）。
     */
    private final OreListSetting oreSelection = sgOres.add(new OreListSetting.Builder()
        .name("矿石")
        .description("点击 Select 选择要显示的矿石：浅层（Y≥0 石头）与深层（Y<0 深板岩）分开勾选，每段标题右边的勾选框可一次全选整层；每种矿石可单独改边框颜色、是否与相邻同类矿石连成一体。选择会实时保存到游戏根目录 meteor-miku 文件夹的 Select-Ore.txt（格式：层 矿石 连接 显示，例如「深层 绿宝石 on on」）。")
        .build()
    );

    /** 当前计算矿位时使用的维度，决定 y 坐标算浅层还是深层。 */
    private Dimension oreDimension = Dimension.Overworld;

    private final SettingGroup sgBridge = settings.createGroup("Meteor 透视联动");

    /**
     * 把种子算出的矿位同步给 Meteor 本体的透视功能。
     *
     * <p>Meteor 本体是直接问服务器“那个坐标有什么”，问不到就画不出来。服务器开了反矿透
     * （发包时把矿石换成石头）时，客户端世界里没有矿石，本体的 Xray / block-esp 什么都
     * 找不到；这里让种子矿透代替服务器告诉它那个地方是什么，由本体的渲染器去画。</p>
     */
    private final Setting<Boolean> meteorBridge = sgBridge.add(new BoolSetting.Builder()
        .name("同步Meteor")
        .description("把种子算出的矿位告诉 Meteor 本体：本体自己问不到坐标上有什么时，由种子矿透代替服务器告诉它那里是什么。方块透视（block-esp）与 Xray 白名单同理。")
        .defaultValue(false)
        .onChanged(this::onBridgeChanged)
        .build()
    );

    private final Setting<Boolean> bridgeBlockEsp = sgBridge.add(new BoolSetting.Builder()
        .name("同步Meteor：方块透视")
        .description("把矿位注入 Meteor 本体的 block-esp（同时把矿石加进它的方块列表），由本体按每种方块的颜色/形状/连线设置渲染。")
        .defaultValue(true)
        .visible(meteorBridge::get)
        .build()
    );

    private final Setting<Boolean> bridgeXray = sgBridge.add(new BoolSetting.Builder()
        .name("同步Meteor：Xray 白名单")
        .description("把选中的矿石方块加进 Meteor 本体 Xray 的白名单：本体 Xray 打开时不会把这些矿石一起透明掉。")
        .defaultValue(true)
        .visible(meteorBridge::get)
        .build()
    );

    private final Setting<Integer> bridgeInterval = sgBridge.add(new IntSetting.Builder()
        .name("刷新间隔")
        .description("每隔多少秒把矿位重新同步一次（新算出的区块、矿石选择的变化会在这之后生效）。同时重新检查每处矿位：客户端里已经变成空气的（矿石被挖走了）会从这个间隔起不再画边框，也只在这个间隔生效。")
        .defaultValue(2)
        .min(1)
        .max(60)
        .sliderMax(10)
        .visible(meteorBridge::get)
        .build()
    );

    private final Setting<Boolean> bridgeHideSelf = sgBridge.add(new BoolSetting.Builder()
        .name("隐藏种子自绘")
        .description("打开后不再用种子矿透自己的线条画矿，只交给 Meteor 本体的方块透视画，免得两套框叠在一起。")
        .defaultValue(false)
        .visible(() -> meteorBridge.get() && bridgeBlockEsp.get())
        .build()
    );

    /** 透视联动的刷新计时器（tick）。 */
    private int bridgeCounter = 0;

    /**
     * 曾经观察到「实心」的矿位。空气判定关掉时，算出来的矿位里本来就可能包含洞穴里的空气格，
     * 这些不能被当成「被挖走」。只有先见过它实心、之后又变成空气的，才算矿石被挖走。
     */
    private final LongOpenHashSet solidSeen = new LongOpenHashSet();


    private final Setting<Integer> horizontalRadius = sgGeneral.add(new IntSetting.Builder()
        .name("区块范围")
        .description("显示区块的距离上限。")
        .defaultValue(5)
        .min(1)
        .sliderMax(10)
        .build()
    );

    private final Setting<AirCheck> airCheck = sgGeneral.add(new EnumSetting.Builder<AirCheck>()
        .name("空气判定")
        .description("是否对算出的矿位做空气检测。默认“重新判定”会在方块被挖开后移除该矿位。")
        .defaultValue(AirCheck.RECHECK)
        .build()
    );


    private final Setting<Boolean> baritone = sgGeneral.add(new BoolSetting.Builder()
        .name("同步设置baritone" + (BaritoneUtils.IS_AVAILABLE ? "（OK）" : "（没找到）"))
        .description("将baritone矿物位置设置为种子算出的实际位置。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> directMine = sgGeneral.add(new BoolSetting.Builder()
        .name("直接开挖")
        .description("不建议使用，除非实在不会命令")
        .defaultValue(false)
        .build()
    );
    private final Setting<Boolean> low = sgGeneral.add(new BoolSetting.Builder()
        .name("仅挖残骸密集区")
        .description("远古残骸在 Y=8~24 层最集中，挖掘效率最高，我们就挖这些。")
        .defaultValue(false)
        .build()
    );
    private final Setting<Boolean> diamond = sgGeneral.add(new BoolSetting.Builder()
        .name("仅挖钻石密集区")
        .description("钻石残骸在 Y=-58~-10 层最集中，挖掘效率最高，我们就挖这些。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> updateInterval = sgGeneral.add(new IntSetting.Builder()
        .name("更新间隔")
        .description("设置目标的间隔时间（秒）")
        .defaultValue(1)
        .min(1)
        .max(10)
        .sliderMax(10)
        .build()
    );


    public SeedMine() {
        super(CATEGORY_MIKU_BUILD, "种子矿透", "种子透视增强版。输入种子，算出矿物实际位置。注意必须使用基于彗星版的男中音。基于meteor_rejects");
    }

    /** 这个矿位对应的层：下界全是下界岩变种，主世界按 y 分浅层/深层。 */
    private Ore.OreLayer layerOf(double y) {
        if (oreDimension == Dimension.Nether) return Ore.OreLayer.NETHER;
        return y < 0 ? Ore.OreLayer.DEEP : Ore.OreLayer.SHALLOW;
    }

    /** 该矿位是否被“矿石”选择器选中。查找用的是预算好的 key，不产生新对象。 */
    private boolean isOreSelected(Ore ore, Vec3 pos) {
        return oreSelection.get().contains(Ore.OreKey.of(ore.type, layerOf(pos.y)));
    }

    public boolean baritone() {
        return isActive() && baritone.get() && isBaritonePresent();
    }

    public static boolean isBaritonePresent() {
        try {
            Class.forName("baritone.api.BaritoneAPI", false, ClassLoader.getSystemClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        } catch (Throwable t) {
            // 某些情况下 BaritoneAPI 类存在但初始化失败（例如循环依赖），也要视为不可用
            return false;
        }
    }


    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null || oreConfig == null) {
            return;
        }
        if (Seeds.get().getSeed() == null) return;

        // 同步给本体 block-esp 且用户选择隐藏自绘时，就完全不画自己的线框，避免两层框重叠。
        if (meteorBridge.get() && bridgeBlockEsp.get() && bridgeHideSelf.get()) return;

        int chunkX = mc.player.chunkPosition().x();
        int chunkZ = mc.player.chunkPosition().z();
        int rangeVal = horizontalRadius.get();

        // 连接边框要知道相邻方块是不是同一类矿石（可能跨区块），
        // 先按同一批可见区块把矿位收进无分配的长整型集合，渲染时直接查。
        if (hasConnectedSelection()) buildConnectedScratch(chunkX, chunkZ, rangeVal);

        for (int range = 0; range <= rangeVal; range++) {
            for (int x = -range + chunkX; x <= range + chunkX; x++) {
                renderChunk(x, chunkZ + range - rangeVal, event);
            }
            for (int x = (-range) + 1 + chunkX; x < range + chunkX; x++) {
                renderChunk(x, chunkZ - range + rangeVal + 1, event);
            }
        }
    }

    /** 选择里有没有开启“边框连接”的矿石。 */
    private boolean hasConnectedSelection() {
        for (Ore.OreKey key : oreSelection.get()) {
            if (oreSelection.style(key).connected) return true;
        }
        return false;
    }

    /** 把可见区块里所有“已选中且开了连接”的矿位按 key 收进 scratch，每帧重建一次。 */
    private void buildConnectedScratch(int chunkX, int chunkZ, int rangeVal) {
        Set<Ore.OreKey> selected = oreSelection.get();

        for (Ore.OreKey key : selected) {
            if (!oreSelection.style(key).connected) continue;
            LongOpenHashSet set = connectedScratch[key.index()];
            if (set == null) {
                set = new LongOpenHashSet();
                connectedScratch[key.index()] = set;
            } else {
                set.clear();
            }
        }

        for (int range = 0; range <= rangeVal; range++) {
            for (int x = -range + chunkX; x <= range + chunkX; x++) {
                fillConnectedScratch(x, chunkZ + range - rangeVal, selected);
            }
            for (int x = (-range) + 1 + chunkX; x < range + chunkX; x++) {
                fillConnectedScratch(x, chunkZ - range + rangeVal + 1, selected);
            }
        }
    }

    private void fillConnectedScratch(int x, int z, Set<Ore.OreKey> selected) {
        Map<Ore, Set<Vec3>> chunk = chunkRenderers.get(ChunkPos.pack(x, z));
        if (chunk == null) return;

        for (Map.Entry<Ore, Set<Vec3>> oreRenders : chunk.entrySet()) {
            Ore.OreType type = oreRenders.getKey().type;
            for (Vec3 pos : oreRenders.getValue()) {
                Ore.OreKey key = Ore.OreKey.of(type, layerOf(pos.y));
                if (!selected.contains(key) || !oreSelection.style(key).connected) continue;
                LongOpenHashSet set = connectedScratch[key.index()];
                if (set != null) set.add(BlockPos.asLong((int) pos.x, (int) pos.y, (int) pos.z));
            }
        }
    }

    private void renderChunk(int x, int z, Render3DEvent event) {
        Map<Ore, Set<Vec3>> chunk = chunkRenderers.get(ChunkPos.pack(x, z));
        if (chunk == null) return;

        Set<Ore.OreKey> selected = oreSelection.get();

        for (Map.Entry<Ore, Set<Vec3>> oreRenders : chunk.entrySet()) {
            Ore.OreType type = oreRenders.getKey().type;
            for (Vec3 pos : oreRenders.getValue()) {
                Ore.OreKey key = Ore.OreKey.of(type, layerOf(pos.y));
                if (!selected.contains(key)) continue;

                Ore.OreStyle style = oreSelection.style(key);
                if (style.connected) drawConnectedBox(event, key, pos, style.color);
                else event.renderer.boxLines(pos.x, pos.y, pos.z, pos.x + 1, pos.y + 1, pos.z + 1, style.color, 0);
            }
        }
    }

    /**
     * 连接模式：把相邻的同类矿石看成一个整体，只画整体的外轮廓。
     * 一条棱只有在它是外轮廓的拐角时才画；两方块相邻造成的“平墙面接缝”和内部的棱都不画，
     * 所以 2x2x2 的一团矿只会得到包围盒那 12 条棱，而不是每个小方块的网格。
     */
    private void drawConnectedBox(Render3DEvent event, Ore.OreKey key, Vec3 pos, Color color) {
        int x = (int) pos.x;
        int y = (int) pos.y;
        int z = (int) pos.z;
        LongOpenHashSet set = connectedScratch[key.index()];
        if (set == null) {
            event.renderer.boxLines(pos.x, pos.y, pos.z, pos.x + 1, pos.y + 1, pos.z + 1, color, 0);
            return;
        }

        double x0 = x, y0 = y, z0 = z, x1 = x + 1, y1 = y + 1, z1 = z + 1;

        // 平行于 X 的 4 条棱（参数：对角方块，两个相邻方块）
        if (edgeVisible(set, x, y - 1, z - 1, x, y - 1, z, x, y, z - 1))
            event.renderer.line(x0, y0, z0, x1, y0, z0, color);
        if (edgeVisible(set, x, y - 1, z + 1, x, y - 1, z, x, y, z + 1))
            event.renderer.line(x0, y0, z1, x1, y0, z1, color);
        if (edgeVisible(set, x, y + 1, z - 1, x, y, z - 1, x, y + 1, z))
            event.renderer.line(x0, y1, z0, x1, y1, z0, color);
        if (edgeVisible(set, x, y + 1, z + 1, x, y, z + 1, x, y + 1, z))
            event.renderer.line(x0, y1, z1, x1, y1, z1, color);

        // 平行于 Y 的 4 条棱
        if (edgeVisible(set, x - 1, y, z - 1, x - 1, y, z, x, y, z - 1))
            event.renderer.line(x0, y0, z0, x0, y1, z0, color);
        if (edgeVisible(set, x - 1, y, z + 1, x - 1, y, z, x, y, z + 1))
            event.renderer.line(x0, y0, z1, x0, y1, z1, color);
        if (edgeVisible(set, x + 1, y, z - 1, x + 1, y, z, x, y, z - 1))
            event.renderer.line(x1, y0, z0, x1, y1, z0, color);
        if (edgeVisible(set, x + 1, y, z + 1, x + 1, y, z, x, y, z + 1))
            event.renderer.line(x1, y0, z1, x1, y1, z1, color);

        // 平行于 Z 的 4 条棱
        if (edgeVisible(set, x - 1, y - 1, z, x - 1, y, z, x, y - 1, z))
            event.renderer.line(x0, y0, z0, x0, y0, z1, color);
        if (edgeVisible(set, x - 1, y + 1, z, x - 1, y, z, x, y + 1, z))
            event.renderer.line(x0, y1, z0, x0, y1, z1, color);
        if (edgeVisible(set, x + 1, y - 1, z, x + 1, y, z, x, y - 1, z))
            event.renderer.line(x1, y0, z0, x1, y0, z1, color);
        if (edgeVisible(set, x + 1, y + 1, z, x + 1, y, z, x, y + 1, z))
            event.renderer.line(x1, y1, z0, x1, y1, z1, color);
    }

    /**
     * 环绕一条棱的 4 个方块里，本方块一定是实心的；再看另外 3 个：
     * 1 个对角方块 + 2 个相邻方块。只有本方块（凸角）、3 块实心（凹角）
     * 或“本方块 + 对角方块”这 2 块（对角接触）时，这条棱才属于外轮廓；
     * 4 块全实心是内部棱，相邻 2 块实心是同一面平墙上的接缝，都不画。
     */
    private static boolean edgeVisible(LongOpenHashSet set,
                                       int dx, int dy, int dz,
                                       int ax, int ay, int az,
                                       int bx, int by, int bz) {
        boolean diagonal = set.contains(BlockPos.asLong(dx, dy, dz));
        int count = (diagonal ? 1 : 0)
            + (set.contains(BlockPos.asLong(ax, ay, az)) ? 1 : 0)
            + (set.contains(BlockPos.asLong(bx, by, bz)) ? 1 : 0);

        if (count >= 3) return false;   // 4 块全实心 → 内部棱
        if (count == 2) return true;    // 共 3 块实心 → 凹角
        if (count == 0) return true;    // 只有本方块 → 凸角
        return diagonal;                // 只有 1 个相邻方块实心 → 平墙接缝，不画
    }

    @EventHandler
    private void onBlockUpdate(BlockUpdateEvent event) {
        if (airCheck.get() != AirCheck.RECHECK || event.newState.canOcclude()) return;

        long chunkKey = ChunkPos.pack(event.pos);
        if (chunkRenderers.containsKey(chunkKey)) {
            Vec3 pos = Vec3.atLowerCornerOf(event.pos);
            for (var ore : chunkRenderers.get(chunkKey).values()) {
                ore.remove(pos);
            }
            solidSeen.remove(event.pos.asLong());
        }
    }

    /**
     * 按「刷新间隔」把已经被挖走的矿石从矿位表里清掉，下一轮 {@link #syncBridge()} 重建叠加层时，
     * 本体 block-esp 里注入的框也会一起消失。
     *
     * <p>为什么不靠方块的更新事件就够了：本体 block-esp 是拿「旧方块 → 新方块」是否在它的方块
     * 列表里来判断增删的。服务器开了反矿透把矿石发成石头时，玩家挖走矿石在客户端只是
     * 「石头 → 空气」，本体认不出这里有矿石，旧的框就会一直留着。这里改成定期拿客户端世界
     * 的真实方块状态复核一遍，挖走了（不再实心）就删。
     *
     * <p>区块没加载时读方块状态会得到空气，所以只复核已加载的区块，免得把远处还没加载的矿位误删。
     */
    private void pruneMinedOres() {
        if (mc.level == null) return;

        // 开了空气判定时，矿位在算出时就确认过是实心的，现在变空气就是被挖走了。
        // 关掉空气判定时矿位里本来就可能含空气格，只有先见着实心、之后再变空气的才算。
        boolean airMeansMined = airCheck.get() != AirCheck.OFF;
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();

        for (Map.Entry<Long, Map<Ore, Set<Vec3>>> chunkEntry : chunkRenderers.entrySet()) {
            long chunkKey = chunkEntry.getKey();
            if (!mc.level.hasChunk(ChunkPos.getX(chunkKey), ChunkPos.getZ(chunkKey))) continue;

            for (Set<Vec3> positions : chunkEntry.getValue().values()) {
                Iterator<Vec3> it = positions.iterator();
                while (it.hasNext()) {
                    Vec3 pos = it.next();
                    int x = (int) pos.x;
                    int y = (int) pos.y;
                    int z = (int) pos.z;

                    mutable.set(x, y, z);
                    if (mc.level.getBlockState(mutable).canOcclude()) {
                        solidSeen.add(BlockPos.asLong(x, y, z));
                        continue;
                    }

                    long packed = BlockPos.asLong(x, y, z);
                    if (airMeansMined || solidSeen.contains(packed)) {
                        it.remove();
                        solidSeen.remove(packed);
                    }
                }
            }
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.level == null || oreConfig == null) return;

        // 与世界同步：按“同步时间”定期检查种子/维度，并补算新加载的区块。
        if (worldSync.get()) {
            syncCounter++;
            if (syncCounter >= syncInterval.get() * 20) {
                syncCounter = 0;
                syncWithWorld();
            }
        }

        // 把矿位同步给 Meteor 本体：定期重推一次，让新算出的区块和选择变化跟上。
        // 同一个间隔里先清掉已经被挖走的矿位，再同步，这样本体那边注入的框会立刻跟着消失。
        if (meteorBridge.get()) {
            bridgeCounter++;
            if (bridgeCounter >= bridgeInterval.get() * 20) {
                bridgeCounter = 0;
                pruneMinedOres();
                syncBridge();
            }
        }

        // 直接开挖功能
        if (directMine.get() && BaritoneUtils.IS_AVAILABLE) {
            tickCounter++;
            int intervalTicks = updateInterval.get() * 20; // 转换为tick数

            if (tickCounter >= intervalTicks) {
                tickCounter = 0;
                setNearestMiningTarget();
            }
        }

        List<BlockPos> tempGoals = new ArrayList<>();
        int rangeVal = 4;
        var chunkPos = mc.player.chunkPosition();

        for (int range = 0; range <= rangeVal; ++range) {
            for (int x = -range + chunkPos.x(); x <= range + chunkPos.x(); ++x) {
                tempGoals.addAll(addToBaritone(x, chunkPos.z() + range - rangeVal));
            }
            for (int x = -range + 1 + chunkPos.x(); x < range + chunkPos.x(); ++x) {
                tempGoals.addAll(addToBaritone(x, chunkPos.z() - range + rangeVal + 1));
            }
        }

        if (low.get()) {
            tempGoals.removeIf(p -> p.getY() < 7 || p.getY() > 30);
        }

        if (diamond.get()) {
            tempGoals.removeIf(p -> p.getY() < -58 || p.getY() > -10);
        }

        // 曼哈顿距离排序
        tempGoals.sort(Comparator.comparingInt(p ->
            Math.abs(p.getX() - mc.player.getBlockX()) + Math.abs(p.getZ() - mc.player.getBlockZ())
        ));

        MagicMix.oreGoals.addAll(tempGoals);
    }

    private ArrayList<BlockPos> addToBaritone(int chunkX, int chunkZ) {
        ArrayList<BlockPos> baritoneGoals = new ArrayList<>();
        long chunkKey = ChunkPos.pack(chunkX, chunkZ);
        if (this.chunkRenderers.containsKey(chunkKey)) {
            this.chunkRenderers.get(chunkKey).entrySet().stream()
                .flatMap(entry -> entry.getValue().stream()
                    .filter(pos -> isOreSelected(entry.getKey(), pos)))
                .map(BlockPos::containing)
                .forEach(baritoneGoals::add);
        }
        return baritoneGoals;
    }

    private void setNearestMiningTarget() {
        if (mc.player == null || !BaritoneUtils.IS_AVAILABLE) return;

        // 收集所有可用的矿石目标
        List<BlockPos> allTargets = new ArrayList<>();
        int rangeVal = 8; // 扩大搜索范围
        var chunkPos = mc.player.chunkPosition();

        for (int range = 0; range <= rangeVal; ++range) {
            for (int x = -range + chunkPos.x(); x <= range + chunkPos.x(); ++x) {
                allTargets.addAll(addToBaritone(x, chunkPos.z() + range - rangeVal));
            }
            for (int x = -range + 1 + chunkPos.x(); x < range + chunkPos.x(); ++x) {
                allTargets.addAll(addToBaritone(x, chunkPos.z() - range + rangeVal + 1));
            }
        }

        if (allTargets.isEmpty()) return;

        // 按曼哈顿距离排序
        allTargets.sort(Comparator.comparingInt(p ->
            Math.abs(p.getX() - mc.player.getBlockX()) +
                Math.abs(p.getY() - mc.player.getBlockY()) +
                Math.abs(p.getZ() - mc.player.getBlockZ())
        ));

        // 获取最近的目标
        BlockPos nearestTarget = allTargets.get(0);

        BetterBlockPos betterBlockPos = BetterBlockPos.from(nearestTarget);

        Set<String> ores = new LinkedHashSet<>();
        for (Ore.OreKey key : oreSelection.get()) {
            ores.add(key.type.mineName);
        }

        // 设置Baritone挖掘目标
        try {
            if (!BaritoneAPI.getProvider().getPrimaryBaritone().getMineProcess().isActive()) {
                info("设置挖掘目标: " + nearestTarget.getX() + ", " + nearestTarget.getY() + ", " + nearestTarget.getZ());

                BaritoneAPI.getProvider().getPrimaryBaritone().getMineProcess().mineByName(ores.toArray(new String[]{}));
            }
        } catch (Exception e) {
            info("设置挖掘目标失败" + e);
        }

//        BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(betterBlockPos);

    }


    private void onApplySeedChanged(boolean value) {
        if (value) {
            SeedVersion version = SeedVersion.parseInput(mcVersionInput.get());
            if (version == null) {
                // 版本输入为空或超出 1.12.2 ~ 26.2：按空白处理，不写种子库也不输出
                applySeed.set(false);
                return;
            }
            String seed = seedInput.get();
            if (!seed.isEmpty()) {
                Seeds.get().setSeed(seed, version);
                info("已设置种子: " + seed + " 版本: " + version.name);
                reload();
            } else {
                error("请先输入种子");
            }
            applySeed.set(false);
        }
    }

    @Override
    public void onActivate() {
        super.onActivate();

        // 矿石选择以游戏根目录 meteor-miku 文件夹里的 Select-Ore.txt 为准：
        // 两个文件不存在就先按当前选择建出来，文件里有内容就覆盖当前选择。
        OreSelectStore.ensureAndLoad(oreSelection);

        if (Seeds.get().getSeed() == null) {
            error("未找到种子。请在种子设置中输入种子并点击应用");
            this.toggle();
            return;
        }

        if (PathManagers.get() instanceof NopPathManager) {
            info("需要 Baritone");
            toggle();
            return;
        }

        Seed currentSeed = Seeds.get().getSeed();
        String versionName = currentSeed.version == null ? "" : currentSeed.version.name;
        info("注意种子是否正确。当前种子: " + currentSeed.seed + " 版本: " + versionName);

        reload();
    }

    @Override
    public void onDeactivate() {
        this.chunkRenderers.clear();
        this.oreConfig = null;
        solidSeen.clear();
        bridgeCounter = 0;
        // 关掉时把当前矿石选择写回 Select-Ore.txt，下次启用读回来时不会丢。
        OreSelectStore.save(OreSelectStore.mainFile(), oreSelection);
        // 把注入到 Meteor 本体里的矿位、加进本体设置里的矿石都收回来。
        MeteorXrayBridge.disable();
    }


//    @EventHandler
//    private void onPlayerRespawn(PlayerRespawnEvent event) {
//        reload();
//    }

    private void loadVisibleChunks() {
        if (mc.player == null) {
            return;
        }

        for (ChunkAccess chunk : Utils.chunks(false)) {
            doMathOnChunk(chunk);
        }
    }

    private void reload() {
        Seed seed = Seeds.get().getSeed();
        if (seed == null) return;
        worldSeed = seed;
        oreDimension = PlayerUtils.getDimension();
        oreConfig = Ore.getRegistry(oreDimension);
        MagicMix.oreGoals.clear();
        chunkRenderers.clear();
        solidSeen.clear();
        if (mc.level != null && worldSeed != null) {
            loadVisibleChunks();
        }
        syncBridge();
    }

    /**
     * 把当前算好的矿位同步给 Meteor 本体（叠加层 + block-esp + Xray 白名单）。
     * 没开联动、或者还没进世界时不动任何东西。
     */
    private void syncBridge() {
        if (!meteorBridge.get() || !isActive()) return;
        if (mc.player == null || mc.level == null || oreConfig == null) return;

        MeteorXrayBridge.sync(oreSelection.get(), oreDimension, chunkRenderers,
            bridgeBlockEsp.get(), bridgeXray.get());
    }

    private void onBridgeChanged(boolean value) {
        if (value) syncBridge();
        else MeteorXrayBridge.disable();
    }

    /**
     * 与当前世界同步一次：种子或维度变了就整片重算，否则只补算新加载的区块。
     * 单人存档读的是存档真实种子，服务器上读的是按世界名保存的种子。
     */
    private void syncWithWorld() {
        Seed current = Seeds.get().getSeed();
        if (current == null) return;

        Dimension dimension = PlayerUtils.getDimension();
        boolean seedChanged = worldSeed == null
            || !Objects.equals(current.seed, worldSeed.seed)
            || current.version != worldSeed.version;
        boolean dimensionChanged = dimension != oreDimension;

        if (seedChanged || dimensionChanged) {
            worldSeed = current;
            oreDimension = dimension;
            oreConfig = Ore.getRegistry(oreDimension);
            MagicMix.oreGoals.clear();
            chunkRenderers.clear();
            solidSeen.clear();
            if (mc.level != null) loadVisibleChunks();
            if (seedChanged) {
                seedInput.set(String.valueOf(current.seed));
                info("已与世界同步种子: " + current.seed);
            }
        } else if (mc.level != null) {
            // 种子没变，只把新加载、还没算过的区块补上。
            loadVisibleChunks();
        }

        // 维度/种子变了或补算了区块，都立刻把结果推给本体，不用等下一个刷新间隔。
        syncBridge();
    }

    @EventHandler
    public void onChunkData(ChunkDataEvent event) {
        doMathOnChunk(event.chunk());
    }

    private void doMathOnChunk(ChunkAccess chunk) {

        var chunkPos = chunk.getPos();
        long chunkKey = chunkPos.pack();

        ClientLevel world = mc.level;

        if (chunkRenderers.containsKey(chunkKey) || world == null) {
            return;
        }

        Set<ResourceKey<Biome>> biomes = new HashSet<>();
        ChunkPos.rangeClosed(chunkPos, 1).forEach(chunkPosx -> {
            ChunkAccess chunkxx = world.getChunk(chunkPosx.x(), chunkPosx.z(), ChunkStatus.BIOMES, false);
            if (chunkxx == null) return;

            for (LevelChunkSection chunkSection : chunkxx.getSections()) {
                chunkSection.getBiomes().getAll(entry -> biomes.add(entry.unwrapKey().get()));
            }
        });
        Set<Ore> oreSet = biomes.stream().flatMap(b -> getDefaultOres(b).stream()).collect(Collectors.toSet());

        int chunkX = chunkPos.x() << 4;
        int chunkZ = chunkPos.z() << 4;
        // 26.x：与上游 meteor-rejects 的 OreSim 一样，直接用本版游戏原生的
        // 世界生成算法算矿位，不经过种子库，所以“MC版本”里的选择不影响矿位，
        // 只作为种子元数据记录（26.2 / 26.1.2 / 26.1.1 / 26.1 都已被 SeedVersion 收录）。
        // 原版 ChunkGenerator#applyBiomeDecoration 用的是 XoroshiroRandomSource，
        // 别用 LegacyRandomSource：两者的 next(int) 序列不同，算出来的矿位会和真实世界错开。
        // 初始种子会在 setDecorationSeed 里被覆盖，传 0 即可。
        WorldgenRandom random = new WorldgenRandom(WorldgenRandom.Algorithm.XOROSHIRO.newInstance(0));

        long populationSeed = random.setDecorationSeed(worldSeed.seed, chunkX, chunkZ);
        HashMap<Ore, Set<Vec3>> h = new HashMap<>();

        for (Ore ore : oreSet) {

            HashSet<Vec3> ores = new HashSet<>();

            random.setFeatureSeed(populationSeed, ore.index, ore.step);

            int repeat = ore.count.sample(random);

            for (int i = 0; i < repeat; i++) {

                if (ore.rarity != 1F && random.nextFloat() >= 1 / ore.rarity) {
                    continue;
                }

                int x = random.nextInt(16) + chunkX;
                int z = random.nextInt(16) + chunkZ;
                int y = ore.heightProvider.sample(random, ore.heightContext);
                BlockPos origin = new BlockPos(x, y, z);

                ResourceKey<Biome> biome = world.getBiome(new BlockPos(x, y, z)).unwrapKey().get();

                if (!getDefaultOres(biome).contains(ore)) {
                    continue;
                }

                if (ore.scattered) {
                    ores.addAll(generateHidden(world, random, origin, ore.size));
                } else {
                    ores.addAll(generateNormal(world, random, origin, ore.size, ore.discardOnAirChance));
                }
            }
            if (!ores.isEmpty()) {
                h.put(ore, ores);
            }
        }
        chunkRenderers.put(chunkKey, h);
    }

    private List<Ore> getDefaultOres(ResourceKey<Biome> biomeRegistryKey) {
        if (oreConfig.containsKey(biomeRegistryKey)) {
            return oreConfig.get(biomeRegistryKey);
        } else {
            return this.oreConfig.values().stream().findAny().get();
        }
    }

    // ====================================
    // Mojang code
    // ====================================

    private ArrayList<Vec3> generateNormal(ClientLevel world, WorldgenRandom random, BlockPos blockPos, int veinSize, float discardOnAir) {
        float f = random.nextFloat() * 3.1415927F;
        float g = (float) veinSize / 8.0F;
        int i = Mth.ceil(((float) veinSize / 16.0F * 2.0F + 1.0F) / 2.0F);
        double d = (double) blockPos.getX() + Math.sin(f) * (double) g;
        double e = (double) blockPos.getX() - Math.sin(f) * (double) g;
        double h = (double) blockPos.getZ() + Math.cos(f) * (double) g;
        double j = (double) blockPos.getZ() - Math.cos(f) * (double) g;
        double l = (blockPos.getY() + random.nextInt(3) - 2);
        double m = (blockPos.getY() + random.nextInt(3) - 2);
        int n = blockPos.getX() - Mth.ceil(g) - i;
        int o = blockPos.getY() - 2 - i;
        int p = blockPos.getZ() - Mth.ceil(g) - i;
        int q = 2 * (Mth.ceil(g) + i);
        int r = 2 * (2 + i);

        for (int s = n; s <= n + q; ++s) {
            for (int t = p; t <= p + q; ++t) {
                if (o <= world.getHeight(Heightmap.Types.MOTION_BLOCKING, s, t)) {
                    return this.generateVeinPart(world, random, veinSize, d, e, h, j, l, m, n, o, p, q, r, discardOnAir);
                }
            }
        }

        return new ArrayList<>();
    }

    private ArrayList<Vec3> generateVeinPart(ClientLevel world, WorldgenRandom random, int veinSize, double startX, double endX, double startZ, double endZ, double startY, double endY, int x, int y, int z, int size, int i, float discardOnAir) {

        BitSet bitSet = new BitSet(size * i * size);
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        double[] ds = new double[veinSize * 4];

        ArrayList<Vec3> poses = new ArrayList<>();

        int n;
        double p;
        double q;
        double r;
        double s;
        for (n = 0; n < veinSize; ++n) {
            float f = (float) n / (float) veinSize;
            p = Mth.lerp(f, startX, endX);
            q = Mth.lerp(f, startY, endY);
            r = Mth.lerp(f, startZ, endZ);
            s = random.nextDouble() * (double) veinSize / 16.0D;
            double m = ((double) (Mth.sin(3.1415927F * f) + 1.0F) * s + 1.0D) / 2.0D;
            ds[n * 4] = p;
            ds[n * 4 + 1] = q;
            ds[n * 4 + 2] = r;
            ds[n * 4 + 3] = m;
        }

        for (n = 0; n < veinSize - 1; ++n) {
            if (!(ds[n * 4 + 3] <= 0.0D)) {
                for (int o = n + 1; o < veinSize; ++o) {
                    if (!(ds[o * 4 + 3] <= 0.0D)) {
                        p = ds[n * 4] - ds[o * 4];
                        q = ds[n * 4 + 1] - ds[o * 4 + 1];
                        r = ds[n * 4 + 2] - ds[o * 4 + 2];
                        s = ds[n * 4 + 3] - ds[o * 4 + 3];
                        if (s * s > p * p + q * q + r * r) {
                            if (s > 0.0D) {
                                ds[o * 4 + 3] = -1.0D;
                            } else {
                                ds[n * 4 + 3] = -1.0D;
                            }
                        }
                    }
                }
            }
        }

        for (n = 0; n < veinSize; ++n) {
            double u = ds[n * 4 + 3];
            if (!(u < 0.0D)) {
                double v = ds[n * 4];
                double w = ds[n * 4 + 1];
                double aa = ds[n * 4 + 2];
                int ab = Math.max(Mth.floor(v - u), x);
                int ac = Math.max(Mth.floor(w - u), y);
                int ad = Math.max(Mth.floor(aa - u), z);
                int ae = Math.max(Mth.floor(v + u), ab);
                int af = Math.max(Mth.floor(w + u), ac);
                int ag = Math.max(Mth.floor(aa + u), ad);

                for (int ah = ab; ah <= ae; ++ah) {
                    double ai = ((double) ah + 0.5D - v) / u;
                    if (ai * ai < 1.0D) {
                        for (int aj = ac; aj <= af; ++aj) {
                            double ak = ((double) aj + 0.5D - w) / u;
                            if (ai * ai + ak * ak < 1.0D) {
                                for (int al = ad; al <= ag; ++al) {
                                    double am = ((double) al + 0.5D - aa) / u;
                                    if (ai * ai + ak * ak + am * am < 1.0D) {
                                        int an = ah - x + (aj - y) * size + (al - z) * size * i;
                                        if (!bitSet.get(an)) {
                                            bitSet.set(an);
                                            mutable.set(ah, aj, al);
                                            if (aj >= -64 && aj < 320 && (airCheck.get() == AirCheck.OFF || world.getBlockState(mutable).canOcclude())) {
                                                if (shouldPlace(world, mutable, discardOnAir, random)) {
                                                    poses.add(new Vec3(ah, aj, al));
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        return poses;
    }

    private boolean shouldPlace(ClientLevel world, BlockPos orePos, float discardOnAir, WorldgenRandom random) {
        if (discardOnAir == 0F || (discardOnAir != 1F && random.nextFloat() >= discardOnAir)) {
            return true;
        }

        for (Direction direction : Direction.values()) {
            if (!world.getBlockState(orePos.offset(direction.getUnitVec3i())).canOcclude() && discardOnAir != 1F) {
                return false;
            }
        }
        return true;
    }

    private ArrayList<Vec3> generateHidden(ClientLevel world, WorldgenRandom random, BlockPos blockPos, int size) {

        ArrayList<Vec3> poses = new ArrayList<>();

        int i = random.nextInt(size + 1);

        for (int j = 0; j < i; ++j) {
            size = Math.min(j, 7);
            int x = this.randomCoord(random, size) + blockPos.getX();
            int y = this.randomCoord(random, size) + blockPos.getY();
            int z = this.randomCoord(random, size) + blockPos.getZ();
            if (airCheck.get() == AirCheck.OFF || world.getBlockState(new BlockPos(x, y, z)).canOcclude()) {
                if (shouldPlace(world, new BlockPos(x, y, z), 1F, random)) {
                    poses.add(new Vec3(x, y, z));
                }
            }
        }

        return poses;
    }

    private int randomCoord(WorldgenRandom random, int size) {
        return Math.round((random.nextFloat() - random.nextFloat()) * (float) size);
    }
}
