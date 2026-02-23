package com.sighs.apricityui.instance.network;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.registry.annotation.NetworkRegister;
import com.sighs.apricityui.util.ReflectionUtils;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = ApricityUI.MOD_ID)
public class ApricityUINetwork {

    @SubscribeEvent
    public static void register(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar(ApricityUI.MOD_ID);

        ReflectionUtils.findAnnotationClasses(NetworkRegister.class, data -> true, clazz -> {
            NetworkRegister annotation = clazz.getAnnotation(NetworkRegister.class);
            try {
                if (clazz.getConstructor().newInstance() instanceof INetwork<?> iNetwork) {
                    registerNetworkHelper(registrar, (INetwork<?>) iNetwork, annotation.type());
                }
            } catch (Throwable throwable) {
                ApricityUI.LOGGER.error("Failed to load network {}", clazz.getName(), throwable);
            }
        }, () -> {
        });
    }

    private static <T extends CustomPacketPayload> void registerNetworkHelper(PayloadRegistrar registrar, INetwork<T> network, NetworkType type) {
        switch (type) {
            case COMMON -> registrar.playBidirectional(network.type(), network.streamCodec(), network::execute);
            case S2C -> registrar.playToClient(network.type(), network.streamCodec(), network::execute);
            case C2S -> registrar.playToServer(network.type(), network.streamCodec(), network::execute);
        }
    }
}
