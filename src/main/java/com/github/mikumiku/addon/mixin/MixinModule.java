package com.github.mikumiku.addon.mixin;

import com.github.mikumiku.addon.modules.MikuMessageSettings;
import meteordevelopment.meteorclient.systems.modules.Module;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 挂进 Meteor 的 {@link Module#toggle()}：任意模块（Meteor 自带、本插件、其它 addon）
 * 被开关时通知「Miku 消息设置」，由它按设置决定是否在聊天栏提示。
 */
@Mixin(Module.class)
public abstract class MixinModule {
    @Inject(method = "toggle", at = @At("TAIL"))
    private void onToggleTail(CallbackInfo ci) {
        MikuMessageSettings.onModuleToggled((Module) (Object) this);
    }
}
