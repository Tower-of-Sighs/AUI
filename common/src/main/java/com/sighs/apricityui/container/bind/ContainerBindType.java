package com.sighs.apricityui.container.bind;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public enum ContainerBindType {
    PLAYER("player"),
    ENTITY("entity"),
    BLOCK_ENTITY("block_entity"),
    SAVED_DATA("saved_data");

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

    /**
     * 判断指定绑定类型是否有对应的数据源。
     */
    public static boolean hasDataSource(ContainerBindType bindType) {
        return bindType != null;
    }

    public String id() {
        return id;
    }
}
