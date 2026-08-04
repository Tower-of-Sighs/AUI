package com.sighs.apricityui.network.packet;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.container.bind.ContainerBindType;
import com.sighs.apricityui.element.ContainerDeclaration;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * 客户端→服务端：请求打开 Screen。
 * 携带模板路径和从模板中提取的容器声明列表。
 */
public record OpenScreenRequestPacket(String templatePath, List<ContainerDeclaration> containers)
        implements CustomPacketPayload {
    public static final Type<OpenScreenRequestPacket> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(ApricityUI.MODID, "open_screen_request"));

    public static final StreamCodec<ByteBuf, OpenScreenRequestPacket> STREAM_CODEC =
            StreamCodec.of(OpenScreenRequestPacket::encode, OpenScreenRequestPacket::decode);

    public OpenScreenRequestPacket(String templatePath, List<ContainerDeclaration> containers) {
        this.templatePath = templatePath == null ? "" : templatePath;
        this.containers = containers == null ? List.of() : List.copyOf(containers);
    }

    public OpenScreenRequestPacket(String templatePath) {
        this(templatePath, List.of());
    }

    public static void encode(ByteBuf buf, OpenScreenRequestPacket packet) {
        FriendlyByteBuf friendly = (FriendlyByteBuf) buf;
        friendly.writeUtf(packet.templatePath);
        friendly.writeVarInt(packet.containers.size());
        for (ContainerDeclaration decl : packet.containers) {
            friendly.writeUtf(decl.id());
            friendly.writeUtf(decl.bindType().id());
            friendly.writeVarInt(Math.max(0, decl.capacity()));
            friendly.writeBoolean(decl.primary());
        }
    }

    public static OpenScreenRequestPacket decode(ByteBuf buf) {
        FriendlyByteBuf friendly = (FriendlyByteBuf) buf;
        String templatePath = friendly.readUtf();
        int count = friendly.readVarInt();
        ArrayList<ContainerDeclaration> containers = new ArrayList<>(Math.max(0, count));
        for (int i = 0; i < count; i++) {
            String id = friendly.readUtf();
            String rawBindType = friendly.readUtf();
            int capacity = Math.max(0, friendly.readVarInt());
            boolean primary = friendly.readBoolean();
            ContainerBindType bindType = ContainerBindType.fromRaw(rawBindType);
            if (bindType == null) bindType = ContainerBindType.PLAYER;
            containers.add(new ContainerDeclaration(id, bindType, capacity, primary));
        }
        return new OpenScreenRequestPacket(templatePath, containers);
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
