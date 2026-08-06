package com.sighs.apricityui.network.util;

import com.sighs.apricityui.network.codec.StreamCodec;
import net.minecraft.network.FriendlyByteBuf;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class ReflectionUtil {

    private ReflectionUtil() {
    }

    public static <T> Constructor<T> findCanonicalConstructor(Class<T> clazz, Class<?>[] paramTypes) {
        try {
            var ctor = clazz.getDeclaredConstructor(paramTypes);
            ctor.setAccessible(true);
            return ctor;
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("Canonical constructor not found for record: " + clazz.getName(), e);
        }
    }

    public static <T> T newInstance(Constructor<T> ctor, Object... args) {
        try {
            return ctor.newInstance(args);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to instantiate record: " + ctor.getDeclaringClass().getName(), e);
        }
    }

    public static Object invoke(Method method, Object instance) {
        try {
            return method.invoke(instance);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to invoke method: " + method.getName(), e);
        }
    }

    @SuppressWarnings("unchecked")
    public static StreamCodec<FriendlyByteBuf, Object> getStaticFieldAsCodec(
            Class<?> recordClass, Class<?> holder, Field field
    ) {
        try {
            field.setAccessible(true);
            String errorInfo = "Field " + holder.getName() + "#" + field.getName() + " is not a StreamCodec";
            try {
                var getter = MethodHandles.lookup().unreflectGetter(field);
                var codecObj = getter.invokeWithArguments();
                if (!(codecObj instanceof StreamCodec<?, ?> codec)) {
                    throw new IllegalStateException(errorInfo);
                }
                return (StreamCodec<FriendlyByteBuf, Object>) codec;
            } catch (IllegalAccessException e) {
                var codecObj = field.get(null);
                if (!(codecObj instanceof StreamCodec<?, ?> codec)) {
                    throw new IllegalStateException(errorInfo);
                }
                return (StreamCodec<FriendlyByteBuf, Object>) codec;
            }
        } catch (Throwable t) {
            throw new IllegalStateException("Failed to resolve codec field " + holder.getName() + "#" + field.getName()
                    + " for record " + recordClass.getName(), t);
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T getStaticField(Class<?> recordClass, Class<?> holder, Field field, Class<T> expectedType) {
        try {
            field.setAccessible(true);
            String errorInfo = "Field " + holder.getName() + "#" + field.getName() + " is not a " + expectedType.getSimpleName();
            try {
                var getter = MethodHandles.lookup().unreflectGetter(field);
                var obj = getter.invokeWithArguments();
                if (!expectedType.isInstance(obj)) {
                    throw new IllegalStateException(errorInfo);
                }
                return (T) obj;
            } catch (IllegalAccessException e) {
                var obj = field.get(null);
                if (!expectedType.isInstance(obj)) {
                    throw new IllegalStateException(errorInfo);
                }
                return (T) obj;
            }
        } catch (Throwable t) {
            throw new IllegalStateException("Failed to resolve field " + holder.getName() + "#" + field.getName()
                    + " for record " + recordClass.getName(), t);
        }
    }
}