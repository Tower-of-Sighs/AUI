package com.sighs.apricityui.container;

/**
 * 服务端声明的容器槽位 CSS 选择器。
 * <p>
 * selector 仅在客户端已完成 DOM 扩展后求值；服务端只使用此键恢复已声明的过滤规则。
 * </p>
 */
public record SlotFilterSelector(String containerId, String selector) {
    public SlotFilterSelector {
        containerId = containerId == null ? "" : containerId.trim();
        selector = selector == null ? "" : selector.trim();
    }

    public boolean isValid() {
        return !containerId.isEmpty() && !selector.isEmpty();
    }
}
