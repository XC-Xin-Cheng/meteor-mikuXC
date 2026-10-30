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


import com.github.mikumiku.addon.BaseModule;
import com.github.mikumiku.addon.util.BagUtil;
import com.github.mikumiku.addon.util.HumanizedSettings;
import com.github.mikumiku.addon.util.Rotation;
import com.github.mikumiku.addon.util.RotationManager;
import meteordevelopment.meteorclient.events.entity.player.PlayerMoveEvent;
import meteordevelopment.meteorclient.events.world.CollisionShapeEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.level.block.ScaffoldingBlock;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.Items;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.level.ClipContext;

public class Phase extends BaseModule {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgPearl = settings.createGroup("末影珍珠");
    private final SettingGroup sgClip = settings.createGroup("穿墙设置");

    /**
     * 本模块自己的「类人化」设置组：旋转注入手抖。模式默认「跟随全局」。
     * 只影响发给服务器的旋转包，末影珍珠的投掷角度保持精确。
     */
    private final HumanizedSettings humanized = HumanizedSettings.builder(this, "Phase").view().rotateNoise().build();

    // General
    private final Setting<PhaseMode> mode = sgGeneral.add(new EnumSetting.Builder<PhaseMode>()
        .name("模式")
        .description("穿墙模式选择")
        .defaultValue(PhaseMode.Normal)
        .build()
    );

    // Pearl settings
    private final Setting<Integer> pitch = sgPearl.add(new IntSetting.Builder()
        .name("俯仰角")
        .description("投掷末影珍珠的俯仰角度")
        .defaultValue(85)
        .min(70)
        .max(90)
        .visible(() -> mode.get() == PhaseMode.Pearl)
        .build()
    );

    private final Setting<Boolean> attack = sgPearl.add(new BoolSetting.Builder()
        .name("攻击实体")
        .description("攻击珍珠路径上的实体")
        .defaultValue(false)
        .visible(() -> mode.get() == PhaseMode.Pearl)
        .build()
    );

    private final Setting<Boolean> selfFill = sgPearl.add(new BoolSetting.Builder()
        .name("自动填充")
        .description("自动在穿墙位置填充方块")
        .defaultValue(false)
        .visible(() -> mode.get() == PhaseMode.Pearl)
        .build()
    );

    // Clip settings
    private final Setting<Double> blocks = sgClip.add(new DoubleSetting.Builder()
        .name("穿墙距离")
        .description("穿墙的方块距离")
        .defaultValue(0.003)
        .min(0.001)
        .max(10.0)
        .sliderMax(1.0)
        .visible(() -> mode.get() != PhaseMode.Pearl && mode.get() != PhaseMode.Clip)
        .build()
    );

    private final Setting<Double> distance = sgClip.add(new DoubleSetting.Builder()
        .name("偏移距离")
        .description("穿墙时的偏移距离")
        .defaultValue(0.2)
        .min(0.0)
        .max(10.0)
        .sliderMax(1.0)
        .visible(() -> mode.get() != PhaseMode.Pearl && mode.get() != PhaseMode.Clip)
        .build()
    );

    private final Setting<Boolean> autoClip = sgClip.add(new BoolSetting.Builder()
        .name("自动穿墙")
        .description("自动执行穿墙操作")
        .defaultValue(true)
        .visible(() -> mode.get() != PhaseMode.Pearl && mode.get() != PhaseMode.Clip)
        .build()
    );

    public Phase() {
        super(BaseModule.CATEGORY_MIKU_COMBAT, "珍珠穿墙", "允许玩家穿过固体方块");
    }

    @Override
    public void onActivate() {
        if (mc.player == null) return;

        if (mode.get() == PhaseMode.Pearl) {
            throwPearl();
            toggle();
        } else if (autoClip.get() && mode.get() != PhaseMode.Clip) {
            performAutoClip();
        }
    }

    @EventHandler
    private void onTick(PlayerMoveEvent event) {
        if (mode.get() == PhaseMode.Clip && mc.player.onGround() && !mc.player.isPassenger()) {
            Vec3 center = Vec3.atCenterOf(mc.player.blockPosition());
            boolean flagX = (center.x - mc.player.getX()) > 0;
            boolean flagZ = (center.z - mc.player.getZ()) > 0;
            double x = center.x + 0.2 * (flagX ? -1 : 1);
            double z = center.z + 0.2 * (flagZ ? -1 : 1);
            mc.player.setPos(x, mc.player.getY(), z);
            toggle();
        }
    }

    @EventHandler
    private void onCollisionShape(CollisionShapeEvent event) {
        if (mc.player == null) return;

        switch (mode.get()) {
            case Normal -> {
                if (event.shape != Shapes.empty() &&
                    event.shape.bounds().maxY > mc.player.getBoundingBox().minY &&
                    mc.player.isShiftKeyDown()) {
                    event.shape = Shapes.empty();
                }
            }
            case Sand -> {
                event.shape = Shapes.empty();
                mc.player.noPhysics = true;
            }
            case Climb -> {
                if (mc.player.horizontalCollision) {
                    event.shape = Shapes.empty();
                }
            }
        }
    }

    private void throwPearl() {
        int slot = BagUtil.findItemInventorySlot(Items.ENDER_PEARL);
        if (slot == -1) {
            error("未找到末影珍珠或珍珠冷却中！");
            return;
        }

        float prevYaw = mc.player.getYRot();
        float prevPitch = mc.player.getXRot();
        Vec3 target = new Vec3(Math.floor(mc.player.getX()) + 0.5, 0.0, Math.floor(mc.player.getZ()) + 0.5);

        double deltaX = target.x - mc.player.getX();
        double deltaZ = target.z - mc.player.getZ();
        float yaw = (float) (Math.toDegrees(Math.atan2(deltaZ, deltaX)) - 90.0f) + 180.0f;

        if (attack.get()) {
            attackEntities(yaw);
            destroyScaffolding();
        }

        if (selfFill.get()) {
            placeBlockUnderFeet(yaw);
        }

        boolean registered = RotationManager.getInstance().register(new Rotation(yaw, pitch.get()), humanized.profile());
        if (registered) {

        }
        // Throw pearl
        Rotations.rotate(yaw, pitch.get(), () -> {
            BagUtil.doSwap(slot);
            mc.player.connection.send(new ServerboundUseItemPacket(InteractionHand.MAIN_HAND, 0, yaw, pitch.get()));

            mc.player.swing(InteractionHand.MAIN_HAND);
            BagUtil.doSwap(slot);
        });

        mc.player.setYRot(prevYaw);
        mc.player.setXRot(prevPitch);
    }

    private void attackEntities(float yaw) {
        Vec3 eyePos = mc.player.getEyePosition();
        BlockHitResult hit = mc.level.clip(new ClipContext(eyePos, eyePos.add(mc.player.getLookAngle().scale(3.0)),
            ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player));
        AABB box = new AABB(hit.getBlockPos()).inflate(0.2);

        for (var entity : mc.level.getEntities(null, box)) {
            if (entity instanceof ItemFrame) {
                mc.gameMode.attack(mc.player, entity);
                mc.player.connection.send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
            }
        }
    }

    private void destroyScaffolding() {
        if (mc.level.getBlockState(mc.player.blockPosition()).getBlock() instanceof ScaffoldingBlock) {
            mc.player.connection.send(new ServerboundPlayerActionPacket(
                ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,
                mc.player.blockPosition(),
                Direction.UP
            ));
            mc.player.connection.send(new ServerboundPlayerActionPacket(
                ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK,
                mc.player.blockPosition(),
                Direction.UP
            ));
        }
    }

    private void placeBlockUnderFeet(float yaw) {
        float normalizedYaw = yaw % 360.0f;
        if (normalizedYaw < 0.0f) normalizedYaw += 360.0f;

        BlockPos pos = mc.player.blockPosition();
        if (normalizedYaw >= 22.5 && normalizedYaw < 67.5) pos = pos.south().west();
        else if (normalizedYaw >= 67.5 && normalizedYaw < 112.5) pos = pos.west();
        else if (normalizedYaw >= 112.5 && normalizedYaw < 157.5) pos = pos.north().west();
        else if (normalizedYaw >= 157.5 && normalizedYaw < 202.5) pos = pos.north();
        else if (normalizedYaw >= 202.5 && normalizedYaw < 247.5) pos = pos.north().east();
        else if (normalizedYaw >= 247.5 && normalizedYaw < 292.5) pos = pos.east();
        else if (normalizedYaw >= 292.5 && normalizedYaw < 337.5) pos = pos.south().east();
        else pos = pos.south();

        FindItemResult block = InvUtils.findInHotbar(itemStack ->
            itemStack.getItem() == Items.OBSIDIAN ||
                itemStack.getItem() == Items.END_STONE ||
                itemStack.getItem() == Items.NETHERITE_BLOCK ||
                itemStack.getItem() == Items.DIAMOND_BLOCK ||
                itemStack.getItem() == Items.IRON_BLOCK ||
                itemStack.getItem() == Items.GOLD_BLOCK ||
                itemStack.getItem() == Items.COAL_BLOCK ||
                itemStack.getItem() == Items.REDSTONE_BLOCK
        );

        if (block.found() && !mc.level.getBlockState(pos.below()).canBeReplaced()) {
            BlockUtils.place(pos, block, true, 0);
        }
    }

    private void performAutoClip() {
        double cos = Math.cos(Math.toRadians(mc.player.getYRot() + 90.0f));
        double sin = Math.sin(Math.toRadians(mc.player.getYRot() + 90.0f));
        mc.player.setPos(
            mc.player.getX() + blocks.get() * cos,
            mc.player.getY(),
            mc.player.getZ() + blocks.get() * sin
        );
    }

    private boolean isPhasing() {
        AABB bb = mc.player.getBoundingBox();
        for (int x = Mth.floor(bb.minX); x < Mth.floor(bb.maxX) + 1; x++) {
            for (int y = Mth.floor(bb.minY); y < Mth.floor(bb.maxY) + 1; y++) {
                for (int z = Mth.floor(bb.minZ); z < Mth.floor(bb.maxZ) + 1; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (mc.level.getBlockState(pos).blocksMotion()) {
                        if (bb.intersects(new AABB(x, y, z, x + 1.0, y + 1.0, z + 1.0))) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    @Override
    public String getInfoString() {
        return mode.get().name();
    }

    public enum PhaseMode {
        Normal,
        Sand,
        Climb,
        Pearl,
        Clip
    }
}
