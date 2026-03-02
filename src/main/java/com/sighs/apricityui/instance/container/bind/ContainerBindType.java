package com.sighs.apricityui.instance.container.bind;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 容器绑定类型（v1.4 主链路类型）。
 */
public enum ContainerBindType {
    PLAYER("player"),
    ENTITY("entity"),
    BLOCK_ENTITY("block_entity"),
    SAVED_DATA("saved_data"),
    VIRTUAL_UI("__virtual_ui");

    public static final int PLAYER_SLOT_COUNT = 36;

    private static final Map<String, ContainerBindType> BY_ID = new HashMap<>();

    static {
        for (ContainerBindType value : values()) {
            BY_ID.put(value.id, value);
        }
    }

    private final String id;

    ContainerBindType(String id) {
        this.id = id;
    }

    public static ContainerBindType fromRaw(String rawBindType) {
        if (rawBindType == null || rawBindType.isBlank()) return null;
        return BY_ID.get(rawBindType.trim().toLowerCase(Locale.ROOT));
    }

    public static boolean isPlayer(ContainerBindType bindType) {
        return bindType == PLAYER;
    }

    public static boolean isVirtualUi(ContainerBindType bindType) {
        return bindType == VIRTUAL_UI;
    }

    public String id() {
        return id;
    }
}
