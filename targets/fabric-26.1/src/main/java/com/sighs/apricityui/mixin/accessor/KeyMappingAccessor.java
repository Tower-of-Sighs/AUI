package com.sighs.apricityui.mixin.accessor;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Reads a key mapping's <em>current</em> binding.
 *
 * <p>Vanilla only exposes {@code getDefaultKey()} — the bound key lives in the
 * private {@code key} field, and the {@code getKey()} accessor the
 * Forge/NeoForge targets use is a loader patch. Without this accessor a Fabric
 * target can only ever see the default value, so rebinding a shortcut never
 * makes it fire.</p>
 */
@Mixin(KeyMapping.class)
public interface KeyMappingAccessor {
    @Accessor("key")
    InputConstants.Key aui$key();
}
