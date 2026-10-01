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
     * 26.1 的 vanilla {@link KeyMapping} 只暴露 {@code getDefaultKey()}，当前绑定在
     * protected 字段 {@code key} 里（{@code getKey()} 是 NeoForge 补丁）。因此这里
     * 必须经 accessor 读当前绑定，否则永远返回默认值（未绑定 = -1），用户重新绑定
     * 后快捷键依然不生效。
     */
    private static int keyCodeOrUnknown(KeyMapping mapping) {
        int value = ((KeyMappingAccessor) (Object) mapping).aui$key().getValue();
        return value == org.lwjgl.glfw.GLFW.GLFW_KEY_UNKNOWN ? -1 : value;
    }
}
