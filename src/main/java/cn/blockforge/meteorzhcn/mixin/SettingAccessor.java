package cn.blockforge.meteorzhcn.mixin;

import meteordevelopment.meteorclient.settings.Setting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 设置项的标题/描述同样是 final 字段。 */
@Mixin(value = Setting.class, remap = false)
public interface SettingAccessor {
    @Mutable
    @Accessor("title")
    void setTitle(String title);

    @Mutable
    @Accessor("description")
    void setDescription(String description);
}
