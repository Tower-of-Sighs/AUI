package com.sighs.apricityui.network.serialization;

import com.sighs.apricityui.network.util.ReflectionUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Type;

/**
 * Legacy record codec builder used as a fallback and for benchmarks.
 *
 * <p>This implementation relies on reflection for record accessors and construction, and is kept
 * so {@link NetworkRecordCodecBuilder} can fall back when code generation is disabled or fails.</p>
 */
final class NetworkRecordCodecBuilderLegacy {

    private NetworkRecordCodecBuilderLegacy() {
    }

    @SuppressWarnings("unchecked")
    static <T> StreamCodec<RegistryFriendlyByteBuf, T> build(Class<T> recordClass) {
        var components = recordClass.getRecordComponents();
        Class<?>[] parameterTypes = new Class<?>[components.length];
        Method[] accessors = new Method[components.length];
        StreamCodec<RegistryFriendlyByteBuf, Object>[] customCodecs =
                (StreamCodec<RegistryFriendlyByteBuf, Object>[]) new StreamCodec<?, ?>[components.length];
        Type[] genericTypes = new Type[components.length];
        ComponentIO.ComponentPlan[] plans = new ComponentIO.ComponentPlan[components.length];

        for (int i = 0; i < components.length; i++) {
            var component = components[i];
            parameterTypes[i] = component.getType();
            accessors[i] = component.getAccessor();
            accessors[i].setAccessible(true);
            customCodecs[i] = CustomCodecResolver.resolve(recordClass, component);
            genericTypes[i] = component.getGenericType();
            if (customCodecs[i] == null) {
                plans[i] = ComponentIO.planOf(parameterTypes[i], genericTypes[i]);
            }
        }

        Constructor<T> constructor = ReflectionUtil.findCanonicalConstructor(recordClass, parameterTypes);

        return new StreamCodec<>() {
            @Override
            public T decode(RegistryFriendlyByteBuf buf) {
                Object[] values = new Object[components.length];
                for (int i = 0; i < components.length; i++) {
                    var codec = customCodecs[i];
                    if (codec != null) {
                        values[i] = codec.decode(buf);
                    } else {
                        values[i] = ComponentIO.decodeWithPlan(buf, plans[i], 0, components[i].getName(), recordClass);
                    }
                }
                return ReflectionUtil.newInstance(constructor, values);
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buf, T value) {
                for (int i = 0; i < components.length; i++) {
                    var fieldValue = ReflectionUtil.invoke(accessors[i], value);
                    var codec = customCodecs[i];
                    if (codec != null) {
                        codec.encode(buf, fieldValue);
                    } else {
                        ComponentIO.encodeWithPlan(buf, plans[i], fieldValue, 0, components[i].getName(), recordClass);
                    }
                }
            }
        };
    }
}
