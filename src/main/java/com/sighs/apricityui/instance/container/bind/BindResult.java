package com.sighs.apricityui.instance.container.bind;

import com.sighs.apricityui.instance.container.datasource.ContainerDataSource;

/**
 * 绑定解析结果。
 */
public record BindResult(
        ContainerDataSource dataSource,
        int capacity,
        boolean supportsResize,
        BindingFailureReason failureReason,
        String failureDetail
) {
    public static BindResult success(ContainerDataSource dataSource) {
        if (dataSource == null) {
            return failure(BindingFailureReason.INTERNAL_ERROR, "dataSource is null");
        }
        return new BindResult(
                dataSource,
                Math.max(0, dataSource.capacity()),
                dataSource.supportsResize(),
                null,
                ""
        );
    }

    public static BindResult failure(BindingFailureReason reason, String detail) {
        return new BindResult(null, 0, false, reason, detail == null ? "" : detail);
    }

    public boolean isSuccess() {
        return dataSource != null && failureReason == null;
    }
}
