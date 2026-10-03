package com.sighs.apricityui.container.datasource;

import com.sighs.apricityui.container.bind.ContainerBindType;
import com.sighs.apricityui.container.storage.GenericStorage;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;
import java.util.function.Predicate;

public final class FabricGenericDataSource implements ContainerDataSource {
    private final ContainerBindType bindType;
    private final GenericStorage storage;
    private final Predicate<ServerPlayer> validity;

    public FabricGenericDataSource(ContainerBindType bindType, GenericStorage storage, Predicate<ServerPlayer> validity) {
        this.bindType = Objects.requireNonNull(bindType);
        this.storage = Objects.requireNonNull(storage);
        this.validity = validity == null ? player -> true : validity;
    }

    @Override public ContainerBindType bindType() { return bindType; }
    @Override public int capacity() { return storage.size(); }
    @Override public GenericStorage genericStorage() { return storage; }
    @Override public boolean stillValid(ServerPlayer player) { return validity.test(player); }
}
