package com.sighs.apricityui.script;

import com.sighs.apricityui.ApricityUI;
import net.minecraftforge.fml.ModList;

/**
 * 页面脚本后端探测：KubeJS → Rhino → 不执行。
 *
 * <p>本类刻意不引用任何 KubeJS/Rhino 类型，两者都不存在时也能安全加载。
 * Rhino 用类存在性判断而不是只看模组 id：KubeJS 缺失时，Rhino 可能由其它模组
 * 以普通依赖或 jar-in-jar 形式提供，只要 {@code dev.latvian.mods.rhino.Context}
 * 能加载就可以跑页面脚本。</p>
 */
public final class ScriptRuntimeSupport {
    public enum Backend {
        /** KubeJS 在场：沿用 KubeJS 的客户端上下文与绑定（KubeJS 自带 Rhino）。 */
        KUBEJS,
        /** KubeJS 不在场但 Rhino 在场：使用 AUI 自己的独立 Rhino 运行时。 */
        RHINO,
        /** 两者都没有：页面照常渲染，脚本跳过。 */
        NONE
    }

    private static volatile Backend backend;

    private ScriptRuntimeSupport() {
    }

    public static Backend backend() {
        Backend value = backend;
        if (value == null) {
            value = probe();
            backend = value;
            log(value);
        }
        return value;
    }

    public static boolean kubeJsLoaded() {
        return backend() == Backend.KUBEJS;
    }

    public static boolean rhinoAvailable() {
        return backend() != Backend.NONE;
    }

    private static Backend probe() {
        if (modLoaded("kubejs")) return Backend.KUBEJS;
        if (rhinoPresent()) return Backend.RHINO;
        return Backend.NONE;
    }

    private static boolean modLoaded(String modId) {
        try {
            return ModList.get() != null && ModList.get().isLoaded(modId);
        } catch (LinkageError | RuntimeException unavailableForgeRuntime) {
            return false;
        }
    }

    private static boolean rhinoPresent() {
        try {
            ClassLoader loader = ScriptRuntimeSupport.class.getClassLoader();
            Class<?> context = Class.forName("dev.latvian.mods.rhino.Context", false, loader);
            Class<?> shared = Class.forName("dev.latvian.mods.rhino.SharedContextData", false, loader);
            Class<?> scriptable = Class.forName("dev.latvian.mods.rhino.Scriptable", false, loader);
            Class<?> wrapFactory = Class.forName("dev.latvian.mods.rhino.WrapFactory", false, loader);
            // 只认本 target 编译时使用的 API 代际；装了签名不匹配的 Rhino 时宁可跳过脚本，
            // 也不要在渲染中途抛 NoSuchMethodError。
            context.getMethod("javaToJS", shared, Object.class, scriptable);
            wrapFactory.getMethod("wrapJavaClass", shared, scriptable, Class.class);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void log(Backend value) {
        switch (value) {
            case KUBEJS -> ApricityUI.LOGGER.info("[AUI JS] page scripts run on the KubeJS runtime");
            case RHINO -> ApricityUI.LOGGER.info("[AUI JS] KubeJS not loaded; page scripts run on the standalone Rhino runtime");
            case NONE -> ApricityUI.LOGGER.warn("[AUI JS] neither KubeJS nor a compatible Rhino runtime is present; page scripts are skipped (pages still render)");
        }
    }
}
