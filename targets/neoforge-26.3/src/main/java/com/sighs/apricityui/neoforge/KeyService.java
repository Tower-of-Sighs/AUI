package com.sighs.apricityui.neoforge;

import com.mojang.blaze3d.platform.InputConstants;
import com.sighs.apricityui.registry.Keybindings;
import com.sighs.apricityui.spi.AuiKeyService;
import com.sighs.apricityui.spi.PhysicalKeyState;

/**
 * NeoForge implementation of {@link AuiKeyService}, backed by the loader's
 * {@link Keybindings} {@code KeyMapping} registry.
 *
 * <p>26.3 reports key codes in the SDL scancode space ({@code KeyEvent#key()},
 * {@code KeyMapping} values, {@code InputConstants.KEY_*}), so the codes handed
 * to {@code common} are scancodes and an unbound shortcut reports {@code -1}
 * (AUI's own sentinel, see {@link AuiKeyService#matches}).</p>
 */
public final class KeyService implements AuiKeyService {
    public static final KeyService INSTANCE = new KeyService();

    private KeyService() {
    }

    private static int keyCodeOrUnknown(net.minecraft.client.KeyMapping mapping) {
        // 26.3: "unbound" is InputConstants.UNKNOWN (value 0), not GLFW's -1, and
        // KeyMapping.isUnbound() is the authoritative test.
        return mapping.isUnbound() ? -1 : mapping.getKey().getValue();
    }

    // 按住释放鼠标直接读物理按键（SDL），不走 KeyMapping.isDown() 的 Minecraft
    // 输入通道：默认绑在左 Alt 上的这个快捷键否则会和其它模组的 Alt+字母组合互抢。
    // 绑定本身仍是 KeyMapping，玩家照常在设置里重新绑定。
    @Override
    public boolean isReleaseMouseDown() {
        return PhysicalKeyState.isDown(Keybindings.RELEASE_MOUSE.getKey());
    }

    /**
     * SDL scancode of the DevTools binding, or {@code -1} when unbound.
     */
    @Override
    public int devToolsKey() {
        return keyCodeOrUnknown(Keybindings.DEV_TOOLS);
    }

    /**
     * SDL scancode of the resource-manager binding, or {@code -1} when unbound.
     */
    @Override
    public int resourceManagerKey() {
        return keyCodeOrUnknown(Keybindings.RESOURCE_MANAGER);
    }

    /**
     * SDL scancode of the reload binding, or {@code -1} when unbound.
     */
    @Override
    public int reloadKey() {
        return keyCodeOrUnknown(Keybindings.RELOAD);
    }

    /**
     * Physical (SDL keyboard state) test for a raw host key code, for callers
     * that already hold a scancode instead of a {@code KeyMapping}.
     */
    public static boolean isPhysicalKeyDown(int scancode) {
        return scancode != InputConstants.UNKNOWN.getValue() && InputConstants.isKeyDown(scancode);
    }
}
