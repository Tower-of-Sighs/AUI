package com.sighs.apricityui.script;

import dev.latvian.mods.kubejs.script.KubeJSContext;
import dev.latvian.mods.kubejs.script.KubeJSContextFactory;
import dev.latvian.mods.kubejs.script.ScriptManager;
import dev.latvian.mods.rhino.Scriptable;
import dev.latvian.mods.rhino.type.TypeInfo;

/**
 * AUI 页面脚本专用的 KubeJS 上下文工厂：Java 的 {@code String}/{@code Number}/
 * {@code Boolean} 返回值原样交给脚本，不再包成 {@code NativeJavaObject}（issue #98）。
 *
 * <p>KubeJS 用的这个 Rhino fork 没有 {@code WrapFactory} / {@code javaPrimitiveWrap}
 * 开关，{@code Context.wrap} 只在 {@code TypeInfo.isPrimitive()}（真正的 Java 基本类型）
 * 时原样返回，所以 {@code el.getAttribute('12|34|56')} 到脚本里是 Java 对象：
 * {@code .split('|')} 会派发到 {@code java.lang.String.split(String regex)}，按交替符切开。</p>
 *
 * <p>脚本侧也修不了：{@code NativeJavaObject} 只 implements {@code Scriptable} 而不是
 * {@code ScriptableObject}，既不能给方法名赋值（{@code InternalError: Java method
 * "getAttribute" cannot be assigned to}），也不能 {@code Object.defineProperty}
 * （{@code InternalError: Invalid JavaScript value of type ...}）。所以 {@code global.js}
 * 那层 {@code __auiInstallValueBridge} 装饰在 Java 对象上从来没有生效过，唯一能改包装
 * 行为的地方就是 {@code Context.wrap} 本身，这里用 AUI 自己的上下文子类覆盖它。</p>
 *
 * <p>作用域只影响 AUI 页面脚本：KubeJS 自己的脚本仍在 {@code ScriptManager} 的上下文里跑。
 * 页面脚本用的作用域是这个上下文自己的 {@code topLevelScope}（{@code KubeJSContext}
 * 构造时就装好了 KubeJS 与 AUI 的绑定），因此不再与 {@code kubejs/client_scripts} 的
 * 顶层变量共享；需要跨脚本共享的数据走 Java / KubeJS API。</p>
 *
 * <p>Rhino / KubeJS 是可选依赖：本类引用它们的类型，只能由
 * {@link KubeJSSupport#loaded()} 放行之后的代码路径加载。</p>
 */
public final class AuiScriptContextFactory extends KubeJSContextFactory {

    public AuiScriptContextFactory(ScriptManager manager) {
        super(manager);
    }

    @Override
    protected KubeJSContext createContext() {
        return new AuiScriptContext(this);
    }

    private static final class AuiScriptContext extends KubeJSContext {
        AuiScriptContext(KubeJSContextFactory factory) {
            super(factory);
        }

        @Override
        public Object wrap(Scriptable scope, Object value, TypeInfo typeInfo) {
            return isJsPrimitive(value) ? value : super.wrap(scope, value, typeInfo);
        }

        @Override
        public Object wrap(Scriptable scope, Object value) {
            return isJsPrimitive(value) ? value : super.wrap(scope, value);
        }

        @Override
        public Object wrapAny(Scriptable scope, Object value) {
            return isJsPrimitive(value) ? value : super.wrapAny(scope, value);
        }
    }

    /**
     * {@code String}/{@code Number}/{@code Boolean} 就是 JS 的基本类型，原样返回才是浏览器
     * 语义（等同于老 Rhino 的 {@code WrapFactory.setJavaPrimitiveWrap(false)}）。{@code char}
     * 不在这里处理：Rhino 自己把它转成数字，跟着它的既有行为走。
     */
    private static boolean isJsPrimitive(Object value) {
        return value instanceof String || value instanceof Number || value instanceof Boolean;
    }
}
