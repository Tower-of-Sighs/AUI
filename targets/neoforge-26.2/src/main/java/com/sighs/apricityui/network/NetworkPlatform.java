package com.sighs.apricityui.network;

import net.minecraft.server.MinecraftServer;

import java.util.function.Supplier;

/** Small platform seam used by common codecs; each target installs its supplier. */
public final class NetworkPlatform {
    private static volatile Supplier<MinecraftServer> currentServer = () -> null;

    private NetworkPlatform() { }

    public static MinecraftServer currentServer() { return currentServer.get(); }

    public static void setCurrentServerSupplier(Supplier<MinecraftServer> supplier) {
        currentServer = supplier == null ? () -> null : supplier;
    }
}
