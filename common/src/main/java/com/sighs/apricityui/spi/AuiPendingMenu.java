package com.sighs.apricityui.spi;

import java.util.function.Consumer;

/**
 * A server-side menu opened with container bindings, returned by
 * {@code ApricityUI.menu(player, templatePath)}.
 *
 * <p>The consumer receives the loader-specific binding builder (e.g. Forge's
 * {@code BindingBuilder}); the loader implementation casts the consumer argument.</p>
 */
public interface AuiPendingMenu {
    void bind(Consumer<Object> binder);
}
