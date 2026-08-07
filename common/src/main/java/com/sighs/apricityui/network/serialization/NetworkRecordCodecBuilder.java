package com.sighs.apricityui.network.serialization;

import com.sighs.apricityui.network.codec.StreamCodec;
import com.sighs.apricityui.network.util.ReflectionUtil;
import net.minecraft.network.FriendlyByteBuf;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Type;

/**
 * Internal utility for building {@link StreamCodec}s for Java records via reflection.
 * <p>
 * This class encapsulates the logic to analyze record components, resolve custom codecs,
 * and generate efficient encode/decode routines with minimal runtime overhead after initial setup.
 * </p>
 */
public final class NetworkRecordCodecBuilder {

    private NetworkRecordCodecBuilder() {
    }

    /**
     * Builds a {@link StreamCodec} for the given record class by inspecting its components.
     *
     * @param recordClass the record class to analyze
     * @param <T>         the type of the record
     * @return a new StreamCodec for the record
     * @throws IllegalStateException if the canonical constructor or custom codecs cannot be resolved
     */
    @SuppressWarnings("unchecked")
    public static <T> StreamCodec<FriendlyByteBuf, T> build(Class<T> recordClass) {
        var components = recordClass.getRecordComponents();
        Class<?>[] parameterTypes = new Class<?>[components.length];
        Method[] accessors = new Method[components.length];
        StreamCodec<FriendlyByteBuf, Object>[] customCodecs =
                (StreamCodec<FriendlyByteBuf, Object>[]) new StreamCodec<?, ?>[components.length];
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
            public T decode(FriendlyByteBuf buf) {
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
            public void encode(FriendlyByteBuf buf, T value) {
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