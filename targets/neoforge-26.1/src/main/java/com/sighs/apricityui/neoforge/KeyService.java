package com.sighs.apricityui.neoforge;

import com.sighs.apricityui.registry.Keybindings;
import com.sighs.apricityui.spi.AuiKeyService;
import com.sighs.apricityui.spi.PhysicalKeyState;

/**
 * Forge implementation of {@link AuiKeyService}, backed by the loader's
 * {@link Keybindings} {@code KeyMapping} registry.
 */
public final class KeyService implements AuiKeyService {
    public static final KeyService INSTANCE = new KeyService();

    private KeyService() {
    }

    // 按住释放鼠标直接读物理按键（GLFW），不走 KeyMapping.isDown() 的 Minecraft
    // 输入通道：默认绑在左 Alt 上的这个快捷键否则会和其它模组的 Alt+字母组合互抢。
    // 绑定本身仍是 KeyMapping，玩家照常在设置里重新绑定。
    @Override
    public boolean isReleaseMouseDown() {
        return PhysicalKeyState.isDown(Keybindings.RELEASE_MOUSE.getKey());
    }

    @Override
    public int devToolsKey() {
        return keyCodeOrUnknown(Keybindings.DEV_TOOLS);
    }

    @Override
    public int resourceManagerKey() {
        return keyCodeOrUnknown(Keybindings.RESOURCE_MANAGER);
    }

    @Override
    public int reloadKey() {
        return keyCodeOrUnknown(Keybindings.RELOAD);
    }

    private static int keyCodeOrUnknown(net.minecraft.client.KeyMapping mapping) {
        int value = mapping.getKey().getValue();
        return value == org.lwjgl.glfw.GLFW.GLFW_KEY_UNKNOWN ? -1 : value;
    }
}
