package com.sighs.apricityui.stack;

import com.sighs.apricityui.spi.AuiItemRenderRequest;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Client-side renderer dispatch keyed by GenericStackType. */
public final class GenericStackRenderers {
    private static final Map<ResourceLocation, Entry<?>> RENDERERS = new ConcurrentHashMap<>();

    private GenericStackRenderers() {
    }

    public static synchronized <K extends GenericKey> void register(
            GenericStackType<K> type,
            Class<K> keyClass,
            Handler<K> handler
    ) {
        if (type == null || keyClass == null || handler == null) {
            throw new IllegalArgumentException("Generic renderer type, key class and handler are required");
        }
        if (RENDERERS.putIfAbsent(type.id(), new Entry<>(keyClass, handler)) != null) {
            throw new IllegalArgumentException("Duplicate generic renderer " + type.id());
        }
    }

    public static boolean render(AuiItemRenderRequest request, GenericStack stack) {
        if (request == null || stack == null) return false;
        Entry<?> entry = RENDERERS.get(stack.what().type().id());
        return entry != null && entry.render(request, stack);
    }

    @FunctionalInterface
    public interface Handler<K extends GenericKey> {
        void render(AuiItemRenderRequest request, GenericStack stack, K key);
    }

    private record Entry<K extends GenericKey>(Class<K> keyClass, Handler<K> handler) {
        boolean render(AuiItemRenderRequest request, GenericStack stack) {
            if (!keyClass.isInstance(stack.what())) return false;
            handler.render(request, stack, keyClass.cast(stack.what()));
            return true;
        }
    }
}
