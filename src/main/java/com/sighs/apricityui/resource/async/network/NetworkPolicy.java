package com.sighs.apricityui.resource.async.network;

/**
 * 网络资源下载策略（一期：仅允许 HTTPS）。
 */
public final class NetworkPolicy {
    public static final int CONNECT_TIMEOUT_MS = 3000;
    public static final int READ_TIMEOUT_MS = 3000;
    public static final int MAX_RETRY_COUNT = 1;
    public static final int MAX_IN_FLIGHT_REQUESTS = 4;
    public static final int MAX_REDIRECTS = 3;
    public static final int MAX_CONTENT_LENGTH_BYTES = 8 * 1024 * 1024;
    public static final long SUCCESS_CACHE_TTL_MS = 60_000L;
    public static final long RETRY_DELAY_429_MS = 20_000L;
    public static final long RETRY_DELAY_5XX_OR_TIMEOUT_MS = 2_000L;

    private NetworkPolicy() {
    }
}
