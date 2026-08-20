package com.sighs.apricityui.container.datasource;

import com.sighs.apricityui.container.filter.FilterUtil;

/** 当前打开菜单中可安装本地槽位过滤器的服务端槽位。 */
public interface FilterableSlot {
    void installFilter(FilterUtil filter);
}
