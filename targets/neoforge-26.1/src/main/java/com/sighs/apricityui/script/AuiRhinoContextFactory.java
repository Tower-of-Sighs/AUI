package com.sighs.apricityui.script;

import dev.latvian.mods.rhino.Context;
import dev.latvian.mods.rhino.ContextFactory;
import dev.latvian.mods.rhino.Scriptable;
import dev.latvian.mods.rhino.type.TypeInfo;

/**
 * 26.1 页面脚本的 Rhino 上下文工厂：Java 的 {@code String}/{@code Number}/
 * {@code Boolean} 返回值原样交给脚本，不再包成 {@code NativeJavaObject}（issue #98）。
 *
 * <p>这个 Rhino fork 没有 {@code WrapFactory} / {@code javaPrimitiveWrap} 开关，
 * {@code Context.wrap} 只在 {@code TypeInfo.isPrimitive()}（真正的 Java 基本类型）时原样
 * 返回，所以 {@code el.getAttribute('12|34|56')} 到脚本里是 Java 对象：{@code .split('|')}
 * 派发到 {@code java.lang.String.split(String regex)}，按交替符切开；{@code replaceAll}
 * 这类 Java 方法反而看得见。脚本侧也修不了——{@code NativeJavaObject} 不是
 * {@code ScriptableObject}，既不能赋值方法名也不能 {@code Object.defineProperty}，
 * {@code global.js} 那层装饰在 Java 对象上从来没生效过。唯一能改包装行为的地方就是
 * {@code Context.wrap} 本身。</p>
 *
 * <p>26.1 没有 KubeJS，页面脚本的上下文本来就由 AUI 自己建，所以这里直接子类化
 * {@link Context}。Rhino 是可选依赖：本类引用它的类型，只能由
 * {@link ApricityScriptSupport#rhinoAvailable()} 放行之后的代码路径加载。</p>
 */
public final class AuiRhinoContextFactory extends ContextFactory {

    @Override
    protected Context createContext() {
        return new AuiContext(this);
    }

    private static final class AuiContext extends Context {
        AuiContext(ContextFactory factory) {
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
