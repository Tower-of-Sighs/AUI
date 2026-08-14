package com.sighs.apricityui.network.packet;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.network.api.INetworkContext;
import com.sighs.apricityui.network.api.INetworkPacket;
import com.sighs.apricityui.network.api.NetworkPacket;
import com.sighs.apricityui.network.api.Side;
import com.sighs.apricityui.network.handler.ApricityScreenNetworkHandler;

import java.util.List;

/** 客户端回传服务端已声明 selector 在当前菜单 DOM 中解析出的本地槽位索引。 */
@NetworkPacket(modId = ApricityUI.MODID, id = "selector_filter_indices", side = Side.SERVER)
public record SelectorFilterIndicesPacket(int menuId, String containerId, String selector, List<Integer> localIndices)
        implements INetworkPacket<SelectorFilterIndicesPacket> {
    public SelectorFilterIndicesPacket {
        containerId = containerId == null ? "" : containerId;
        selector = selector == null ? "" : selector;
        localIndices = localIndices == null ? List.of() : List.copyOf(localIndices);
    }

    @Override
    public void handle(INetworkContext context) {
        ApricityScreenNetworkHandler.handleSelectorFilterIndices(this, context);
    }
}
