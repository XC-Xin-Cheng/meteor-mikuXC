package cn.blockforge.meteorzhcn.util.trans_engine;

import java.util.LinkedHashMap;
import java.util.Set;

/**
 * 键规则注册表。默认使用 AUTO：它把三套写法都试一遍，自动匹配 1.21.1 到 26.2 的键；
 * 找不到名字时也回退到 AUTO，保证不会因为拼错名字而拿不到引擎。
 */
public class EngineManager {
    private static final EngineManager INSTANCE = new EngineManager();
    LinkedHashMap<String, AbstractTransEngine> engines = new LinkedHashMap<>();

    public static EngineManager getInstance() {
        return INSTANCE;
    }

    public Set<String> getEngineNames() {
        return this.engines.keySet();
    }

    public AbstractTransEngine getEngine(String name) {
        AbstractTransEngine engine = this.engines.get(name);
        return engine == null ? this.engines.get("AUTO") : engine;
    }

    private EngineManager() {
        this.engines.put("AUTO", new TransEngineAuto());
        this.engines.put("NEW", new TransEngineNew());
        this.engines.put("OLD", new TransEngineOld());
        this.engines.put("LEGACY", new TransEngineLegacy());
    }
}
