package cn.blockforge.meteorzhcn.util.trans_engine;

import java.util.function.Function;

import cn.blockforge.meteorzhcn.util.TransUtil;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;

/**
 * 自动引擎：同一份词库要服务 1.21.1 到 26.2 的三套键写法。
 * 对每个模块/设置，按新式 → 旧式 → 早期键依次去当前语言里试，命中哪个就用哪个；
 * 三套都没有时退回新式键，让上层照常显示英文原名，不会整轮中断。
 */
public class TransEngineAuto extends AbstractTransEngine {
    /** 候选顺序即优先级：新式键最具体，早期键最宽松。 */
    private static final IKeyGenerate[] CHAIN = {
            new TransEngineNew(),
            new TransEngineOld(),
            new TransEngineLegacy()
    };

    private static String firstHit(Function<IKeyGenerate, String> keyOf) {
        String fallback = null;

        for (IKeyGenerate engine : CHAIN) {
            String key = keyOf.apply(engine);
            if (fallback == null) {
                fallback = key;
            }
            if (TransUtil.hasKey(key)) {
                return key;
            }
        }

        return fallback;
    }

    @Override
    public String getModuleNameKey(Module module) {
        return firstHit(engine -> engine.getModuleNameKey(module));
    }

    @Override
    public String getModuleDescriptionKey(Module module) {
        return firstHit(engine -> engine.getModuleDescriptionKey(module));
    }

    @Override
    public String getSettingNameKey(Module module, SettingGroup group, Setting<?> setting) {
        return firstHit(engine -> engine.getSettingNameKey(module, group, setting));
    }

    @Override
    public String getSettingDesKey(Module module, SettingGroup group, Setting<?> setting) {
        return firstHit(engine -> engine.getSettingDesKey(module, group, setting));
    }
}
