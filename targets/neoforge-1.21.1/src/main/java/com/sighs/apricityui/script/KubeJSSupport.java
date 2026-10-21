package com.sighs.apricityui.script;

/**
 * KubeJS availability probe kept for binary compatibility with the previous
 * 1.21.1 target. New code should use {@link ScriptRuntimeSupport}.
 *
 * @deprecated use {@link ScriptRuntimeSupport#kubeJsLoaded()} instead.
 */
@Deprecated
public final class KubeJSSupport {
    private KubeJSSupport() {
    }

    /** Same semantics as before: true only when the KubeJS mod is loaded. */
    public static boolean loaded() {
        return ScriptRuntimeSupport.kubeJsLoaded();
    }
}
