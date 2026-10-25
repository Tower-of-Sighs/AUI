package com.sighs.apricityui.network.packet;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.network.api.INetworkContext;
import com.sighs.apricityui.network.api.INetworkPacket;
import com.sighs.apricityui.network.api.NetworkPacket;
import com.sighs.apricityui.network.api.Side;
import com.sighs.apricityui.network.handler.ApricityScreenNetworkHandler;

import java.util.List;

/** 客户端完成 DOM selector 解析后回传的已验证本地槽位索引。 */
@NetworkPacket(modId = ApricityUI.MODID, id = "resolved_slot_filters", side = Side.SERVER)
public record ResolvedSlotFiltersPacket(int menuId, String containerId, String selector, List<Integer> localIndices)
        implements INetworkPacket<ResolvedSlotFiltersPacket> {
    public ResolvedSlotFiltersPacket {
        containerId = containerId == null ? "" : containerId.trim();
        selector = selector == null ? "" : selector.trim();
        localIndices = localIndices == null ? List.of() : List.copyOf(localIndices);
    }

    @Override
    public void handle(INetworkContext context) {
        ApricityScreenNetworkHandler.handleResolvedSlotFilters(this, context);
    }
}
