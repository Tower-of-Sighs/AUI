package com.sighs.apricityui.instance.container.bind;

/**
 * 绑定失败原因分类。
 */
public enum BindingFailureReason {
    INVALID_REQUEST,
    UNSUPPORTED_BIND_TYPE,
    MISSING_ARGUMENT,
    INVALID_ARGUMENT,
    TARGET_NOT_FOUND,
    NO_ITEM_HANDLER,
    INSUFFICIENT_CAPACITY,
    RESIZE_NOT_SUPPORTED,
    INTERNAL_ERROR
}
