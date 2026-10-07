package cn.blockforge.meteorzhcn.mixin;

import java.util.List;

import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 设置组内部的设置列表是包私有的，遍历时需要一个访问器。 */
@Mixin(value = SettingGroup.class, remap = false)
public interface SettingGroupAccessor {
    @Accessor("settings")
    List<Setting<?>> getSettings();
}
