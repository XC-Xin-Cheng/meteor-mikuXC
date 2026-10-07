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
import com.github.mikumiku.addon.util.ClickProfile;
import com.github.mikumiku.addon.util.ClickProfileListSetting;
import com.github.mikumiku.addon.util.Humanized;
import com.github.mikumiku.addon.util.HumanizedSettings;
import com.github.mikumiku.addon.util.MikuCompat;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.orbit.EventHandler;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.world.phys.HitResult;

import java.util.List;
import java.util.Random;

/**
 * 「Miku 连点器」——可自定义多套连点方案。
 *
 * <p>每套方案独立配置：鼠标键、速度区间（最低/最高 CPS）、按住时长、
 * 是否只在准星瞄准目标时点击。多套方案可以同时启用，互不干扰，
 * 例如“快速左键”配“慢速右键”一起用。</p>
 *
 * <p>点击的投递方式<b>每套方案各选各的</b>（{@link ClickProfile.Method}，在界面里就排在
 * “方案名”后面那个按钮）：</p>
 * <ul>
 *   <li><b>Miku</b>：走 {@link KeyMapping#click} 给对应鼠标键记一次点击，
 *       由游戏自己的按键处理流程去攻击/使用物品，节奏按最低/最高 CPS 区间随机；</li>
 *   <li><b>Meteor</b>：调 Meteor 自己的 {@link Utils#leftClick()} /
 *       {@link Utils#rightClick()}，也就是 Meteor AutoClicker 的 Press 模式同款做法，
 *       直接触发一次原版点击（左键还会顺手清掉 missTime），节奏按单个
 *       “间隔多少 tick”来，不需要 CPS 区间。</li>
 * </ul>
 *
 * <p>两种方式都保留「按住 ms」：需要按住时再用 {@link KeyMapping#set} 压住键，
 * 到点松开。</p>
 *
 * <p>安全约束：打开任何界面（背包/聊天/设置）或鼠标未锁定时立刻停止点击并
 * 松开我们按下的键，避免关闭界面后一次性补发一大串点击、也避免按键卡住。</p>
 */
public class MikuClicker extends BaseModule {

    /** 一个 tick 内最多补几次点击，防止卡顿后瞬间喷出一大串包。 */
    private static final int MAX_CLICKS_PER_TICK = 4;
    /** 两次点击的最小间隔，50 CPS 对应 20ms，这里留点余量。 */
    private static final long MIN_DELAY_MS = 12L;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<List<ClickProfile>> profiles = sgGeneral.add(new ClickProfileListSetting.Builder()
        .name("连点方案")
        .description("可添加多套方案：勾选启用、改名字、在方案名后面选 Miku 或 Meteor、切左/右键、填速度或间隔；多套可同时生效")
        .defaultValue(
            new ClickProfile("快速左键", true, ClickProfile.Button.LEFT, 12, 16, 0, false),
            new ClickProfile("右键连发", false, ClickProfile.Button.RIGHT, 8, 10, 0, false)
        )
        .build()
    );

    private final Setting<Boolean> requireFocus = sgGeneral.add(new BoolSetting.Builder()
        .name("需要窗口聚焦")
        .description("只在游戏窗口聚焦、鼠标被锁定时连点；关掉后切到别的窗口也会继续点")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> notify = sgGeneral.add(new BoolSetting.Builder()
        .name("启用提示")
        .description("启用时在聊天栏提示当前有几套方案在连点")
        .defaultValue(true)
        .build()
    );

    /**
     * 本模块自己的「类人化」设置组：非均匀间隔、可变延迟、错开松开。
     * 模式默认「跟随全局」，也就是和以前一样听「类人化输入」模块的。
     */
    private final HumanizedSettings humanized = HumanizedSettings.builder(this, "Miku 连点器").click().build();

    private final Random random = new Random();

    public MikuClicker() {
        super(BaseModule.CATEGORY_MIKU_PRO, "Miku 连点器", "可自定义多套连点方案：每套独立设置点击方式（Miku 或 Meteor）、按键、速度/间隔、按住时长与触发条件");
    }

    @Override
    public void onActivate() {
        super.onActivate();

        long now = System.currentTimeMillis();
        int enabled = 0;
        for (ClickProfile profile : profiles.get()) {
            profile.resetRuntime(now);
            if (profile.enabled) enabled++;
        }

        if (notify.get()) {
            info("Miku 连点器已启用，当前 %d 套方案在连点（类人化：%s）", enabled, humanized.modeName());
        }
    }

    @Override
    public void onDeactivate() {
        releaseAll();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.level == null) return;

        // 打开界面时绝不点击：否则 clickCount 会积压，关界面瞬间补发一大串
        if (MikuCompat.screen() != null || (requireFocus.get() && !mc.mouseHandler.isMouseGrabbed())) {
            releaseAll();
            return;
        }

        long now = System.currentTimeMillis();
        List<ClickProfile> list = profiles.get();

        for (ClickProfile profile : list) {
            // 在界面上刚勾上/取消：重新计时，避免一点开就立刻喷点击
            if (profile.enabled != profile.wasEnabled) {
                profile.wasEnabled = profile.enabled;
                profile.nextClickAt = now + delayMs(profile);
                if (!profile.enabled && profile.pressedByUs) release(profile, list);
            }

            if (!profile.enabled) continue;

            // 到点松开我们按住的键
            if (profile.releaseAt != 0L && now >= profile.releaseAt) release(profile, list);

            if (!canClick(profile)) continue;

            if (profile.nextClickAt == 0L) profile.nextClickAt = now + delayMs(profile);

            int guard = 0;
            while (now >= profile.nextClickAt && guard++ < MAX_CLICKS_PER_TICK) {
                fire(profile, now);
                profile.nextClickAt += delayMs(profile);
            }

            // 卡顿或长时间暂停之后不要补算，直接从当前时间重新排
            if (profile.nextClickAt < now) profile.nextClickAt = now;
        }
    }

    /**
     * 补一次点击：按这套方案自己的「点击方式」把这次点击投递出去——Miku 是记进游戏
     * 按键队列，Meteor 是直接触发一次点击；需要按住时再把键压下去，并安排到点松开。
     */
    private void fire(ClickProfile profile, long now) {
        if (profile.method == ClickProfile.Method.VANILLA) {
            // Meteor：和 Meteor AutoClicker 的 Press 模式一样，
            // 调 Utils.leftClick()/rightClick() 直接执行一次点击
            if (profile.button == ClickProfile.Button.RIGHT) {
                Utils.rightClick();
            } else {
                Utils.leftClick();
            }
        } else {
            // Miku：给游戏按键队列补一次点击，由 handleKeybinds 去消费
            KeyMapping.click(keyOf(profile.button));
        }

        if (profile.holdMs > 0) {
            // 真实玩家自己按着这个键时，不要替他松开
            if (!mappingOf(profile.button).isDown()) {
                KeyMapping.set(keyOf(profile.button), true);
                profile.pressedByUs = true;
            }
            // 「类人化输入」开启时错开松开时间，不做整齐的一次性抬手
            profile.releaseAt = now + profile.holdMs + Humanized.releaseStaggerMs(humanProfile(profile));
        }
    }

    /** 松开这套方案按住的键；若同一按键还有别的启用方案按着，就先不松。 */
    private void release(ClickProfile profile, List<ClickProfile> all) {
        if (profile.pressedByUs && !anotherHolds(profile, all)) {
            KeyMapping.set(keyOf(profile.button), false);
        }
        profile.pressedByUs = false;
        profile.releaseAt = 0L;
    }

    private boolean anotherHolds(ClickProfile self, List<ClickProfile> all) {
        for (ClickProfile other : all) {
            if (other != self && other.enabled && other.pressedByUs && other.button == self.button) return true;
        }
        return false;
    }

    /** 松开全部由本模块按下的键，模块关闭、切界面、丢焦点时都走这里。 */
    private void releaseAll() {
        for (ClickProfile profile : profiles.get()) {
            if (profile.pressedByUs) KeyMapping.set(keyOf(profile.button), false);
            profile.pressedByUs = false;
            profile.releaseAt = 0L;
        }
    }

    private boolean canClick(ClickProfile profile) {
        if (!profile.requireTarget) return true;
        return mc.hitResult != null && mc.hitResult.getType() != HitResult.Type.MISS;
    }

    /**
     * 取下一次点击要等多久（毫秒）：Miku 按速度区间随机，Meteor 按 tick 间隔。
     *
     * <p>「类人化输入」开启时，先按上面的规则算出基准间隔，再叠加钟形抖动
     * （打破均匀随机）与一段偏向低端的神经/硬件延迟，让点击节奏不是完美等距。</p>
     */
    private long delayMs(ClickProfile profile) {
        long base;

        if (profile.method == ClickProfile.Method.VANILLA) {
            // 1 tick = 50 ms；onTick 本身就是每 tick 跑一次，间隔 1 就是每 tick 点一下
            base = profile.clampedIntervalTicks() * 50L;
        } else {
            int min = profile.lowerCps();
            int max = profile.upperCps();
            int cps = min + (max > min ? random.nextInt(max - min + 1) : 0);
            base = Math.max(MIN_DELAY_MS, Math.round(1000.0 / cps));
        }

        Humanized.Profile prof = humanProfile(profile);
        return Math.max(MIN_DELAY_MS, Humanized.clickInterval(prof, base) + Humanized.latencyMs(prof));
    }

    /**
     * 这套方案当前该用哪份类人化档位。
     *
     * <p>方案自己取消勾选「类人化」时返回不生效的档位，这套方案就保持等距机械节奏；
     * 否则取本模块「类人化」设置组解析出来的档位（关闭/跟随全局/自定义）。</p>
     */
    private Humanized.Profile humanProfile(ClickProfile profile) {
        return profile.humanized ? humanized.profile() : Humanized.OFF;
    }

    private static InputConstants.Key keyOf(ClickProfile.Button button) {
        return InputConstants.Type.MOUSE.getOrCreate(button == ClickProfile.Button.RIGHT ? 1 : 0);
    }

    private KeyMapping mappingOf(ClickProfile.Button button) {
        return button == ClickProfile.Button.RIGHT ? mc.options.keyUse : mc.options.keyAttack;
    }
}
