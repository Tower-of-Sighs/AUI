package com.sighs.apricityui.instance.network;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.util.common.StrUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public interface INetwork<T extends CustomPacketPayload> extends CustomPacketPayload {
    @Override
    @NotNull
    default Type<T> type() {
        Class<?> clazz = this.getClass();
        return new Type<>(ApricityUI.id(StrUtil.toSnakeCase(clazz.getSimpleName())));
    }

    StreamCodec<? super RegistryFriendlyByteBuf, T> streamCodec();

    void execute(T payload, IPayloadContext context);
}
