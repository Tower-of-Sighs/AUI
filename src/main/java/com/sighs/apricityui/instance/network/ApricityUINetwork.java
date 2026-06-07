package com.sighs.apricityui.instance.network;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.registry.annotation.NetworkRegister;
import com.sighs.apricityui.util.ReflectionUtils;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadHandler;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = ApricityUI.MOD_ID)
public class ApricityUINetwork {

    @SubscribeEvent
    public static void register(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar(ApricityUI.MOD_ID);

        ReflectionUtils.findAnnotationClasses(NetworkRegister.class, data -> true, clazz -> {
            NetworkRegister annotation = clazz.getAnnotation(NetworkRegister.class);
            try {
                CustomPacketPayload.Type<?> type = (CustomPacketPayload.Type<?>) clazz.getField("TYPE").get(null);
                StreamCodec<?, ?> codec = (StreamCodec<?, ?>) clazz.getField("STREAM_CODEC").get(null);
                IPayloadHandler<?> handler = (IPayloadHandler<?>) clazz.getField("HANDLER").get(null);

                registerNetworkHelper(registrar, type, codec, handler, annotation.type());
            } catch (Throwable throwable) {
                ApricityUI.LOGGER.error("Failed to load network {}", clazz.getName(), throwable);
            }
        }, () -> {
        });
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void registerNetworkHelper(PayloadRegistrar registrar,
                                              CustomPacketPayload.Type type,
                                              StreamCodec codec,
                                              IPayloadHandler handler,
                                              NetworkType netType) {
        switch (netType) {
            case COMMON -> registrar.playBidirectional(type, codec, handler);
            case S2C -> registrar.playToClient(type, codec, handler);
            case C2S -> registrar.playToServer(type, codec, handler);
        }
    }
}
