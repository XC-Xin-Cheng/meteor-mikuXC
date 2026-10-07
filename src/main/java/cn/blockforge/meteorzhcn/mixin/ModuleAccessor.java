package cn.blockforge.meteorzhcn.mixin;

import meteordevelopment.meteorclient.systems.modules.Module;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Meteor 的模块标题/描述是 final 字段，只能通过访问器改写。 */
@Mixin(value = Module.class, remap = false)
public interface ModuleAccessor {
    @Mutable
    @Accessor("title")
    void setTitle(String title);

    @Mutable
    @Accessor("description")
    void setDescription(String description);
}
