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
import com.github.mikumiku.addon.util.Via;
import meteordevelopment.meteorclient.events.world.TickEvent.Post;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.DoubleSetting.Builder;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;

public class AutoMinecart extends BaseModule {
   private final SettingGroup sg = this.settings.getDefaultGroup();
   private final Setting<Double> range = this.sg
      .add(
         ((Builder)((Builder)new Builder().name("检测范围")).description("矿车中心与玩家的距离 ≤ 此值时自动上车（格）。服务端实体交互可达约 3 格，超过可能上不去。范围 1–6。"))
            .defaultValue(3.0)
            .min(1.0)
            .max(6.0)
            .sliderMin(1.0)
            .sliderMax(6.0)
            .build()
      );
   private final Setting<Boolean> reboardOnDismount = this.sg
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("下车立即重上"))
                  .description("玩家从矿车脱离（含自己主动潜行下车）后立即转向刚下的矿车再次坐上。关闭则只在矿车靠近时上车，不强制重上。"))
               .defaultValue(true))
            .build()
      );
   private final Setting<Integer> reboardDelay = this.sg
      .add(
         ((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                        .name("重上延迟(tick)"))
                     .description("下车到尝试重上的最小间隔（游戏刻）。留一点时间等服务器处理脱离，避免包顺序冲突被忽略。范围 0–20。"))
                  .defaultValue(2))
               .min(0)
               .max(20)
               .sliderMin(0)
               .sliderMax(20)
               .visible(() -> (Boolean)this.reboardOnDismount.get()))
            .build()
      );
   private final Setting<Integer> cooldown = this.sg
      .add(
         ((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                     .name("尝试冷却(tick)"))
                  .description("两次上车尝试之间的最小间隔（游戏刻）。防止上车未成功时狂发交互包。范围 2–60。"))
               .defaultValue(10))
            .min(2)
            .max(60)
            .sliderMin(2)
            .sliderMax(60)
            .build()
      );
   private final Setting<Boolean> notify = this.sg
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("提示"))
                  .description("每次尝试上车时在聊天栏提示一次（仅本地，不影响功能）。"))
               .defaultValue(true))
            .build()
      );
   private long nextMountTick = 0L;
   private boolean wasRiding = false;
   private int lastCartId = -1;

   public AutoMinecart() {
      super("自动上矿车", "矿车到达玩家附近自动上车；下车（含自己下车）立即转向重上。Grim 安全。from：fuyi");
   }

   public void onDeactivate() {
      this.nextMountTick = 0L;
      this.wasRiding = false;
      this.lastCartId = -1;
   }

   @EventHandler
   private void onTick(Post event) {
      if (this.isActive()) {
         net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
         net.minecraft.client.player.LocalPlayer player = client.player;
         if (player != null && client.level != null) {
            net.minecraft.world.entity.Entity veh = player.getVehicle();
            boolean ridingCart = veh instanceof net.minecraft.world.entity.vehicle.minecart.Minecart;
            if (ridingCart) {
               this.lastCartId = veh.getId();
               this.wasRiding = true;
            } else {
               if (this.wasRiding) {
                  this.wasRiding = false;
                  if ((Boolean)this.reboardOnDismount.get() && this.lastCartId >= 0) {
                     this.nextMountTick = client.level.getGameTime() + ((Integer)this.reboardDelay.get()).intValue();
                  }
               }

               if (client.level.getGameTime() >= this.nextMountTick) {
                  net.minecraft.world.entity.Entity best = this.findTarget(client, player);
                  if (best != null) {
                     net.minecraft.world.phys.Vec3 eye = player.getEyePosition();
                     net.minecraft.world.phys.Vec3 center = best.getBoundingBox().getCenter();
                     double dx = center.x - eye.x;
                     double dy = center.y - eye.y;
                     double dz = center.z - eye.z;
                     double horiz = Math.sqrt(dx * dx + dz * dz);
                     float yaw = net.minecraft.util.Mth.wrapDegrees((float)Math.toDegrees(Math.atan2(-dx, dz)));
                     float pitch = net.minecraft.util.Mth.clamp((float)Math.toDegrees(Math.atan2(-dy, horiz)), -90.0F, 90.0F);
                     ServerboundMovePlayerPacket packet = Via.getFull(player.getX(), player.getY(), player.getZ(), yaw, pitch, player.onGround());
                     this.mc.getConnection().send(packet);
                     client.gameMode.interact(player, best, new net.minecraft.world.phys.EntityHitResult(best), net.minecraft.world.InteractionHand.MAIN_HAND);
                     this.nextMountTick = client.level.getGameTime() + ((Integer)this.cooldown.get()).intValue();
                     if ((Boolean)this.notify.get()) {
                        double d = player.distanceTo(best);
                        String why = this.lastCartId >= 0 && best.getId() == this.lastCartId ? "重上刚下的矿车" : "附近矿车";
                        this.info("§b[自动上矿车] " + why + "，已发送上车交互（距离 " + String.format("%.1f", d) + " 格）", new Object[0]);
                     }

                     this.lastCartId = -1;
                  }
               }
            }
         }
      }
   }

   private net.minecraft.world.entity.Entity findTarget(net.minecraft.client.Minecraft client, net.minecraft.client.player.LocalPlayer player) {
      net.minecraft.world.entity.Entity best = null;
      double bestDist = Double.MAX_VALUE;
      if (this.lastCartId >= 0) {
         net.minecraft.world.entity.Entity last = client.level.getEntity(this.lastCartId);
         if (last instanceof net.minecraft.world.entity.vehicle.minecart.Minecart && !last.isRemoved() && last.isAlive()) {
            double d = player.distanceTo(last);
            if (d <= (Double)this.range.get()) {
               best = last;
               bestDist = d;
            }
         }
      }

      for (net.minecraft.world.entity.Entity e : client.level.entitiesForRendering()) {
         if (e instanceof net.minecraft.world.entity.vehicle.minecart.Minecart && e != player && e != best && !e.isRemoved() && e.isAlive()) {
            double d = player.distanceTo(e);
            if (d <= (Double)this.range.get() && d < bestDist) {
               bestDist = d;
               best = e;
            }
         }
      }

      return best;
   }
}
