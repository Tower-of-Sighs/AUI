package com.sighs.apricityui.spi;

/**
 * Version-neutral identity for a texture.
 *
 * <p>{@code common} holds a {@code TextureKey} (instead of the loader-specific
 * {@code ResourceLocation}/{@code Identifier}) for every texture it registers or
 * draws. The loader maps the key to its own location type via
 * {@link AuiResourceService#textureLocation}.</p>
 */
public record TextureKey(String value) {
    public TextureKey {
        value = value == null ? "" : value;
    }

    public static TextureKey of(String value) {
        return new TextureKey(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
