package com.sighs.apricityui.dev.debug;

final class DebugProtocolException extends RuntimeException {
    static final int INVALID_REQUEST = -32600;
    static final int METHOD_NOT_FOUND = -32601;
    static final int INVALID_PARAMS = -32602;
    static final int TARGET_CLOSED = -32001;
    static final int NODE_DETACHED = -32002;
    static final int NOT_ACTIONABLE = -32003;
    static final int LIMIT_EXCEEDED = -32004;

    private final int code;

    DebugProtocolException(int code, String message) {
        super(message);
        this.code = code;
    }

    int code() {
        return code;
    }
}
