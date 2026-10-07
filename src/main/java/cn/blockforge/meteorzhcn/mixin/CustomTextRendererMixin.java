package cn.blockforge.meteorzhcn.mixin;

import cn.blockforge.meteorzhcn.font_fix.FontFixOwner;
import meteordevelopment.meteorclient.renderer.text.CustomTextRenderer;
import meteordevelopment.meteorclient.renderer.text.Font;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 渲染器销毁字体时，顺手把 {@link FontMixin} 挂在字体上的动态字形图集收掉。
 *
 * <p>只挂在 {@code destroy()} 上：这个签名 26.1 与 26.2 完全相同，不再像旧实现那样
 * {@code @Overwrite begin(double,boolean,boolean)}——那个签名 26.2 已经不存在了。</p>
 */
@Mixin(value = CustomTextRenderer.class, remap = false)
public abstract class CustomTextRendererMixin {
    @Shadow
    @Final
    private Font[] fonts;

    @Inject(method = "destroy", at = @At("TAIL"), require = 0)
    private void meteorzhcn$releaseFonts(CallbackInfo ci) {
        if (this.fonts == null) {
            return;
        }

        for (Font font : this.fonts) {
            if (font instanceof FontFixOwner owner) {
                owner.meteorzhcn$release();
            }
        }
    }
}
