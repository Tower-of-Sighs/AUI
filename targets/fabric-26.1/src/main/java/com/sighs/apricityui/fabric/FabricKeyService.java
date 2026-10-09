package com.sighs.apricityui.fabric;

import com.mojang.blaze3d.platform.InputConstants;
import com.sighs.apricityui.mixin.accessor.KeyMappingAccessor;
import com.sighs.apricityui.registry.Keybindings;
import com.sighs.apricityui.spi.AuiKeyService;
import com.sighs.apricityui.spi.PhysicalKeyState;
import net.minecraft.client.KeyMapping;

public final class FabricKeyService implements AuiKeyService {
    public static final FabricKeyService INSTANCE = new FabricKeyService();
    private FabricKeyService() { }
    // 按住释放鼠标直接读物理按键（GLFW），不走 KeyMapping.isDown() 的 Minecraft
    // 输入通道：默认绑在左 Alt 上的这个快捷键否则会和其它模组的 Alt+字母组合互抢。
    // 绑定本身仍是 KeyMapping，玩家照常在设置里重新绑定。
    public boolean isReleaseMouseDown() { return PhysicalKeyState.isDown(boundKey(Keybindings.RELEASE_MOUSE)); }
    public int devToolsKey() { return keyCodeOrUnknown(Keybindings.DEV_TOOLS); }
    public int resourceManagerKey() { return keyCodeOrUnknown(Keybindings.RESOURCE_MANAGER); }
    public int reloadKey() { return keyCodeOrUnknown(Keybindings.RELOAD); }

    private static InputConstants.Key boundKey(KeyMapping mapping) {
        return ((KeyMappingAccessor) (Object) mapping).aui$key();
    }

    /**
     * 26.1 的 vanilla {@link KeyMapping} 只暴露 {@code getDefaultKey()}，当前绑定在
     * protected 字段 {@code key} 里（{@code getKey()} 是 NeoForge 补丁）。因此这里
     * 必须经 accessor 读当前绑定，否则永远返回默认值（未绑定 = -1），用户重新绑定
     * 后快捷键依然不生效。
     */
    private static int keyCodeOrUnknown(KeyMapping mapping) {
        int value = boundKey(mapping).getValue();
        return value == org.lwjgl.glfw.GLFW.GLFW_KEY_UNKNOWN ? -1 : value;
    }
}
