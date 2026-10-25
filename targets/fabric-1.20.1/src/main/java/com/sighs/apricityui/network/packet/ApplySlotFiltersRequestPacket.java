package com.sighs.apricityui.network.packet;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.network.api.INetworkContext;
import com.sighs.apricityui.network.api.INetworkPacket;
import com.sighs.apricityui.network.api.NetworkPacket;
import com.sighs.apricityui.network.api.Side;
import com.sighs.apricityui.network.handler.ApricityScreenNetworkHandler;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** 客户端确认的 selector 命中槽位索引；过滤声明始终由服务端持有。 */
@NetworkPacket(modId = ApricityUI.MODID, id = "apply_slot_filters", side = Side.SERVER)
public record ApplySlotFiltersRequestPacket(int menuId, List<SelectorSlotMapping> mappings)
        implements INetworkPacket<ApplySlotFiltersRequestPacket> {
    public ApplySlotFiltersRequestPacket {
        ArrayList<SelectorSlotMapping> normalized = new ArrayList<>();
        if (mappings != null) {
            for (SelectorSlotMapping mapping : mappings) {
                if (mapping != null && mapping.isValid()) normalized.add(mapping);
            }
        }
        mappings = List.copyOf(normalized);
    }

    @Override
    public void handle(INetworkContext context) {
        ApricityScreenNetworkHandler.handleApplySlotFiltersRequest(this, context);
    }

    public record SelectorSlotMapping(String containerId, String selector, List<Integer> localIndices) {
        public SelectorSlotMapping {
            containerId = containerId == null ? "" : containerId;
            selector = selector == null ? "" : selector;
            LinkedHashSet<Integer> normalized = new LinkedHashSet<>();
            if (localIndices != null) {
                for (Integer index : localIndices) {
                    if (index != null && index >= 0) normalized.add(index);
                }
            }
            localIndices = List.copyOf(normalized);
        }

        private boolean isValid() {
            return !containerId.isBlank() && !selector.isBlank() && !localIndices.isEmpty();
        }
    }
}
