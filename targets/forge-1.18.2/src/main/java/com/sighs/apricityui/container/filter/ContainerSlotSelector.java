package com.sighs.apricityui.container.filter;

import com.sighs.apricityui.util.common.NormalizeUtil;

/**
 * 标识作用域限定到一个非玩家容器绑定的 CSS 槽位选择器。
 */
public record ContainerSlotSelector(String containerId, String selector) {
    public ContainerSlotSelector {
        containerId = NormalizeUtil.normalizeContainerId(containerId);
        selector = selector == null ? null : selector.trim();
    }

    public boolean isValid() {
        return containerId != null && selector != null && !selector.isBlank();
    }
}
