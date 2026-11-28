package com.sighs.apricityui.network.forge;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.network.chunk.GenericChunkPacket;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.ServerStatusPing.ChannelData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForgeChannelCompatibilityTest {
    @Test
    void letsClientsJoinWithoutApricityChannelsAndChecksTheProtocolWhenPresent() {
        // The standalone test JVM does not load the Forge event transformer.
        new NetworkEvent(() -> null).getListenerList();
        new NetworkEvent.GatherLoginPayloadsEvent(List.of(), false).getListenerList();
        new NetworkManagerImpl().registerAnnotated(GenericChunkPacket.class);

        var serverbound = new ResourceLocation(ApricityUI.MODID, "oelib_c2s");
        var clientbound = new ResourceLocation(ApricityUI.MODID, "oelib_s2c");

        assertTrue(NetworkRegistry.checkListPingCompatibilityForClient(Map.of()));
        assertTrue(NetworkRegistry.checkListPingCompatibilityForClient(Map.of(
                serverbound, new ChannelData(serverbound, "1", true),
                clientbound, new ChannelData(clientbound, "1", true))));
        assertFalse(NetworkRegistry.checkListPingCompatibilityForClient(Map.of(
                serverbound, new ChannelData(serverbound, "2", true),
                clientbound, new ChannelData(clientbound, "1", true))));
        var requiredByServer = NetworkRegistry.getServerNonVanillaNetworkMods();
        assertTrue(requiredByServer.contains(serverbound.toString()));
        assertTrue(requiredByServer.contains(clientbound.toString()));
    }
}
