package com.sighs.apricityui.network.packet;

import com.sighs.apricityui.container.bind.ContainerBindType;
import com.sighs.apricityui.element.Container.ContainerDeclaration;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * 客户端→服务端：请求打开 Screen。
 * 携带模板路径和从模板中提取的容器声明列表。
 */
public record OpenScreenRequestPacket(String templatePath, List<ContainerDeclaration> containers) {
    public OpenScreenRequestPacket(String templatePath, List<ContainerDeclaration> containers) {
        this.templatePath = templatePath == null ? "" : templatePath;
        this.containers = containers == null ? List.of() : List.copyOf(containers);
    }

    public OpenScreenRequestPacket(String templatePath) {
        this(templatePath, List.of());
    }

    public static void encode(OpenScreenRequestPacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.templatePath);
        buf.writeVarInt(packet.containers.size());
        for (ContainerDeclaration decl : packet.containers) {
            buf.writeUtf(decl.id());
            buf.writeUtf(decl.bindType().id());
            buf.writeVarInt(Math.max(0, decl.capacity()));
            buf.writeBoolean(decl.primary());
        }
    }

    public static OpenScreenRequestPacket decode(FriendlyByteBuf buf) {
        String templatePath = buf.readUtf();
        int count = buf.readVarInt();
        ArrayList<ContainerDeclaration> containers = new ArrayList<>(Math.max(0, count));
        for (int i = 0; i < count; i++) {
            String id = buf.readUtf();
            String rawBindType = buf.readUtf();
            int capacity = Math.max(0, buf.readVarInt());
            boolean primary = buf.readBoolean();
            ContainerBindType bindType = ContainerBindType.fromRaw(rawBindType);
            if (bindType == null) bindType = ContainerBindType.PLAYER;
            containers.add(new ContainerDeclaration(id, bindType, capacity, primary));
        }
        return new OpenScreenRequestPacket(templatePath, containers);
    }
}
