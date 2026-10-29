package com.sighs.apricityui.registry;

import com.mojang.blaze3d.platform.InputConstants;
import com.sighs.apricityui.ApricityUI;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * AUI's keybindings.
 *
 * <p>26.3 replaced GLFW with SDL3: the keyboard key type is
 * {@link InputConstants.Type#KEYBOARD} (26.1's {@code KEYSYM}) and its values are
 * SDL <em>scancodes</em> ({@code KEY_LEFT_ALT == 226}, not GLFW's 342), which is
 * also what {@code KeyMapping} matches and what {@code KeyEvent#key()} carries.
 * "Unbound" is {@link InputConstants#UNKNOWN} (value 0), no longer -1.</p>
 */
@EventBusSubscriber(modid = ApricityUI.MODID, value = Dist.CLIENT)
public class Keybindings {
    private static final KeyMapping.Category CATEGORY =
            new KeyMapping.Category(Identifier.fromNamespaceAndPath(ApricityUI.MODID, "apricityui"));

    public static final KeyMapping RELEASE_MOUSE = new KeyMapping("key.apricityui.release_mouse",
            KeyConflictContext.IN_GAME,
            KeyModifier.NONE,
            InputConstants.Type.KEYBOARD,
            InputConstants.KEY_LALT,
            CATEGORY
    );

    public static final KeyMapping RELOAD = new KeyMapping("key.apricityui.reload",
            KeyConflictContext.GUI,
            KeyModifier.NONE,
            InputConstants.Type.KEYBOARD,
            InputConstants.UNKNOWN.getValue(),
            CATEGORY
    );

    public static final KeyMapping DEV_TOOLS = new KeyMapping("key.apricityui.dev_tools",
            KeyConflictContext.GUI,
            KeyModifier.NONE,
            InputConstants.Type.KEYBOARD,
            InputConstants.UNKNOWN.getValue(),
            CATEGORY
    );

    public static final KeyMapping RESOURCE_MANAGER = new KeyMapping("key.apricityui.resource_manager",
            KeyConflictContext.GUI,
            KeyModifier.NONE,
            InputConstants.Type.KEYBOARD,
            InputConstants.UNKNOWN.getValue(),
            CATEGORY
    );

    @SubscribeEvent
    public static void registerKeyMapping(RegisterKeyMappingsEvent event) {
        event.register(RELEASE_MOUSE);
        event.register(RELOAD);
        event.register(DEV_TOOLS);
        event.register(RESOURCE_MANAGER);
    }
}
