package cn.blockforge.meteorzhcn.mixin;

import java.nio.ByteBuffer;

import cn.blockforge.meteorzhcn.font_fix.FontFix;
import cn.blockforge.meteorzhcn.font_fix.FontFixOwner;
import meteordevelopment.meteorclient.renderer.MeshBuilder;
import meteordevelopment.meteorclient.renderer.Texture;
import meteordevelopment.meteorclient.renderer.text.Font;
import meteordevelopment.meteorclient.utils.render.color.Color;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 给 Meteor 的字体加上「缺字即时补进图集」的能力。
 *
 * <p>没有改任何渲染器的签名：Meteor 26.2 把 {@code CustomTextRenderer.begin}
 * 换成了带 {@code GuiGraphicsExtractor} 的新签名，凡是在 26.1 上编译、靠
 * {@code @Overwrite} 打进去的补丁都会在 26.2 找不到目标。而 {@code Font} 的
 * {@code <init>(ByteBuffer,int)}、{@code getWidth}、{@code getHeight}、{@code render}
 * 两个版本完全一致，改在这里一次编译就能同时跑 26.1 与 26.2。</p>
 *
 * <p>动态图集复用 Font 自己那张 2048×2048 纹理，所以不涉及 26.2 把
 * {@code TextureFormat} 换成 {@code GpuFormat} 的改动。</p>
 */
@Mixin(value = Font.class, remap = false)
public abstract class FontMixin implements FontFixOwner {
    @Shadow
    @Final
    public Texture texture;

    @Unique
    private FontFix meteorzhcn$fix;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void meteorzhcn$onInit(ByteBuffer buffer, int height, CallbackInfo ci) {
        try {
            this.meteorzhcn$fix = new FontFix(buffer, height, this.texture);
        } catch (Throwable t) {
            // 字体本身有问题/结构不符时放弃动态字形，保留 Meteor 原行为，别把游戏拖崩。
            this.meteorzhcn$fix = null;
            FontFix.LOG.warn("动态字形图集初始化失败，中文将回退为 Meteor 原行为", t);
        }
    }

    @Inject(method = "getWidth", at = @At("HEAD"), cancellable = true)
    private void meteorzhcn$getWidth(String string, int length, CallbackInfoReturnable<Double> cir) {
        if (this.meteorzhcn$fix != null) {
            cir.setReturnValue(this.meteorzhcn$fix.getWidth(string, length));
        }
    }

    @Inject(method = "getHeight", at = @At("HEAD"), cancellable = true)
    private void meteorzhcn$getHeight(CallbackInfoReturnable<Integer> cir) {
        if (this.meteorzhcn$fix != null) {
            cir.setReturnValue(this.meteorzhcn$fix.getHeight());
        }
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void meteorzhcn$render(MeshBuilder mesh, String string, double x, double y, Color color, double scale, CallbackInfoReturnable<Double> cir) {
        if (this.meteorzhcn$fix != null) {
            cir.setReturnValue(this.meteorzhcn$fix.render(mesh, string, x, y, color, scale));
        }
    }

    @Override
    public void meteorzhcn$release() {
        if (this.meteorzhcn$fix != null) {
            this.meteorzhcn$fix.close();
            this.meteorzhcn$fix = null;
        }
    }
}
