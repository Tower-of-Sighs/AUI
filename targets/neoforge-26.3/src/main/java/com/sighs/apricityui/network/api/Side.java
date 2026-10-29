package com.sighs.apricityui.network.api;

/**
 * Logical side on which a packet is handled.
 */
public enum Side {
    /**
     * Handled only on the logical client.
     */
    CLIENT,

    /**
     * Handled only on the logical server.
     */
    SERVER,

    /**
     * Handled on both client and server.
     */
    BOTH
}
