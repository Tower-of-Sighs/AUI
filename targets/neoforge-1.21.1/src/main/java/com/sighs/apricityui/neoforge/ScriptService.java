package com.sighs.apricityui.neoforge;

import com.sighs.apricityui.event.Event;
import com.sighs.apricityui.script.ApricityJS;
import com.sighs.apricityui.script.KubeJS;
import com.sighs.apricityui.script.RhinoScriptEngine;
import com.sighs.apricityui.script.ScriptRuntimeSupport;
import com.sighs.apricityui.spi.AuiScriptService;
import com.sighs.apricityui.startup.StartupContext;

/**
 * NeoForge implementation of {@link AuiScriptService}.
 *
 * <p>后端按 {@link ScriptRuntimeSupport} 的选择执行：KubeJS 在场时走
 * {@link ApricityJS}（KubeJS 的客户端上下文与绑定），KubeJS 缺失但 Rhino 在场时走
 * {@link RhinoScriptEngine}，两者都没有时脚本静默跳过，页面照常渲染。</p>
 */
public final class ScriptService implements AuiScriptService {
    public static final ScriptService INSTANCE = new ScriptService();

    private ScriptService() {
    }

    @Override
    public void eval(String code, Event event, String source) {
        switch (ScriptRuntimeSupport.backend()) {
            case KUBEJS -> ApricityJS.eval(code, event, source);
            case RHINO -> RhinoScriptEngine.eval(code, event, source);
            case NONE -> {
            }
        }
    }

    @Override
    public void evalGlobal(String code, String documentUuid) {
        switch (ScriptRuntimeSupport.backend()) {
            case KUBEJS -> ApricityJS.evalGlobal(code, documentUuid);
            case RHINO -> RhinoScriptEngine.evalGlobal(code, documentUuid);
            case NONE -> {
            }
        }
    }

    @Override
    public void startupPhase(StartupContext context) {
        if (ScriptRuntimeSupport.kubeJsLoaded()) {
            KubeJS.fireStartup(context);
        }
    }

    @Override
    public void reload() {
        switch (ScriptRuntimeSupport.backend()) {
            case KUBEJS -> ApricityJS.reload();
            case RHINO -> RhinoScriptEngine.reload();
            case NONE -> {
            }
        }
    }

    @Override
    public void warmUp() {
        switch (ScriptRuntimeSupport.backend()) {
            case KUBEJS -> ApricityJS.warmUp();
            case RHINO -> RhinoScriptEngine.warmUp();
            case NONE -> {
            }
        }
    }
}
