package com.sighs.apricityui.instance.container.bind;

import net.minecraft.server.level.ServerPlayer;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 绑定解析请求。
 */
public record BindRequest(
        ServerPlayer player,
        String containerId,
        ContainerBindType bindType,
        Map<String, String> args,
        int requiredCapacity,
        OpenBindPlan.ResizePolicy resizePolicy
) {
    public BindRequest {
        containerId = containerId == null ? "" : containerId.trim();
        requiredCapacity = Math.max(0, requiredCapacity);
        resizePolicy = resizePolicy == null ? OpenBindPlan.ResizePolicy.KEEP_OVERFLOW : resizePolicy;

        LinkedHashMap<String, String> normalizedArgs = new LinkedHashMap<>();
        if (args != null) {
            args.forEach((key, value) -> {
                if (key == null) return;
                String normalizedKey = key.trim();
                if (normalizedKey.isEmpty()) return;
                normalizedArgs.put(normalizedKey, value == null ? "" : value);
            });
        }
        args = Map.copyOf(normalizedArgs);
    }
}
