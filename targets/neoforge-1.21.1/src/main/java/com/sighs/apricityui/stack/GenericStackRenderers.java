package com.sighs.apricityui.stack;

import com.sighs.apricityui.spi.AuiItemRenderRequest;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Client-side renderer dispatch keyed by GenericStackType. */
public final class GenericStackRenderers {
    private static final java.util.Set<String> MISSING = ConcurrentHashMap.newKeySet();
    private static final Map<String, Entry<?>> RENDERERS = new ConcurrentHashMap<>();

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
        Entry<?> entry = RENDERERS.get(stack.key().type().id());
        if (entry == null) {
            if (MISSING.add(stack.key().type().id())) {
                com.sighs.apricityui.ApricityUI.LOGGER.warn("No renderer for generic stack type {}", stack.key().type().id());
            }
            return false;
        }
        return entry.render(request, stack);
    }

    @FunctionalInterface
    public interface Handler<K extends GenericKey> {
        void render(AuiItemRenderRequest request, GenericStack stack, K key);
    }

    private record Entry<K extends GenericKey>(Class<K> keyClass, Handler<K> handler) {
        boolean render(AuiItemRenderRequest request, GenericStack stack) {
            if (!keyClass.isInstance(stack.key())) return false;
            handler.render(request, stack, keyClass.cast(stack.key()));
            return true;
        }
    }
}

