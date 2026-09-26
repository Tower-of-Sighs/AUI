package com.sighs.apricityui.spi;

import java.util.function.Consumer;

/**
 * A server-side menu opened with container bindings, returned by
 * {@code ApricityUI.menu(player, templatePath)}.
 */
public interface AuiPendingMenu {
    void bind(Consumer<AuiBindingBuilder> configurator);
}
