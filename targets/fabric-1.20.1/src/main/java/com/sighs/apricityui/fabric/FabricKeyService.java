package com.sighs.apricityui.fabric;

import com.sighs.apricityui.mixin.accessor.KeyMappingAccessor;
import com.sighs.apricityui.registry.Keybindings;
import com.sighs.apricityui.spi.AuiKeyService;
import net.minecraft.client.KeyMapping;

public final class FabricKeyService implements AuiKeyService {
    public static final FabricKeyService INSTANCE = new FabricKeyService();
    private FabricKeyService() { }
    public boolean isReleaseMouseDown() { return Keybindings.RELEASE_MOUSE.isDown(); }
    public int devToolsKey() { return keyCodeOrUnknown(Keybindings.DEV_TOOLS); }
    public int resourceManagerKey() { return keyCodeOrUnknown(Keybindings.RESOURCE_MANAGER); }
    public int reloadKey() { return keyCodeOrUnknown(Keybindings.RELOAD); }

    /**
     * vanilla 的 {@link KeyMapping} 只暴露 {@code getDefaultKey()}，当前绑定在私有字段
     * {@code key} 里（{@code getKey()} 是 Forge/NeoForge 的补丁，vanilla 没有）。所以必须
     * 经 accessor 读当前绑定：用 {@code getDefaultKey()} 会永远返回默认值（未绑定 = -1），
     * 用户在设置里重新绑定后快捷键依然不生效。
     */
    private static int keyCodeOrUnknown(KeyMapping mapping) {
        int value = ((KeyMappingAccessor) (Object) mapping).aui$key().getValue();
        return value == org.lwjgl.glfw.GLFW.GLFW_KEY_UNKNOWN ? -1 : value;
    }
}
