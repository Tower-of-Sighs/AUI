package com.sighs.apricityui.instance.container.bind;

import com.sighs.apricityui.instance.network.handler.ApricityScreenNetworkHandler;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;
import java.util.UUID;

/**
 * 服务端开屏请求的链式封装：
 * <pre>
 * ApricityUI.screen("demo/index.html")
 *     .primaryBind("main").savedData("demo", "inv", 27)
 *     .bind("player").player()
 *     .open(player);
 * </pre>
 */
public final class ScreenOpenRequest {
    private final String templatePath;
    private final OpenBindPlan.Builder planBuilder;
    private OpenBindPlan appendPlan;

    private ScreenOpenRequest(String templatePath) {
        this.templatePath = templatePath;
        this.planBuilder = OpenBindPlan.builder().templatePath(templatePath);
    }

    public static ScreenOpenRequest of(String templatePath) {
        return new ScreenOpenRequest(templatePath);
    }

    /**
     * 追加一个已有计划；冲突字段以后者为准。
     */
    public ScreenOpenRequest withPlan(OpenBindPlan plan) {
        this.appendPlan = plan;
        return this;
    }

    public ScreenOpenRequest primaryContainer(String containerId) {
        planBuilder.primaryContainer(containerId);
        return this;
    }

    public ScreenOpenRequest defaultResizePolicy(OpenBindPlan.ResizePolicy resizePolicy) {
        planBuilder.defaultResizePolicy(resizePolicy);
        return this;
    }

    public ContainerChain bind(String containerId) {
        return new ContainerChain(planBuilder.bind(containerId));
    }

    public ContainerChain primaryBind(String containerId) {
        return new ContainerChain(planBuilder.primaryBind(containerId));
    }

    public OpenBindPlan build() {
        return OpenBindPlan.merge(planBuilder.build(), appendPlan);
    }

    public void open(ServerPlayer player) {
        ApricityScreenNetworkHandler.openScreen(player, templatePath, build());
    }

    public final class ContainerChain {
        private OpenBindPlan.Builder.ContainerBindBuilder delegate;

        private ContainerChain(OpenBindPlan.Builder.ContainerBindBuilder delegate) {
            this.delegate = Objects.requireNonNull(delegate, "delegate");
        }

        private ContainerChain swap(OpenBindPlan.Builder.ContainerBindBuilder nextDelegate) {
            this.delegate = Objects.requireNonNull(nextDelegate, "nextDelegate");
            return this;
        }

        public ContainerChain bindType(ContainerBindType bindType) {
            delegate.bindType(bindType);
            return this;
        }

        public ContainerChain player() {
            delegate.player();
            return this;
        }

        public ContainerChain savedData(String dataName, String inventoryKey) {
            delegate.savedData(dataName, inventoryKey);
            return this;
        }

        public ContainerChain savedData(String dataName, String inventoryKey, int slotCount) {
            delegate.savedData(dataName, inventoryKey, slotCount);
            return this;
        }

        public ContainerChain blockEntity(int x, int y, int z, String side) {
            delegate.blockEntity(x, y, z, side);
            return this;
        }

        public ContainerChain entity(String uuid) {
            delegate.entity(uuid);
            return this;
        }

        public ContainerChain entity(UUID uuid) {
            delegate.entity(uuid);
            return this;
        }

        public ContainerChain arg(String argKey, String argValue) {
            delegate.arg(argKey, argValue);
            return this;
        }

        public ContainerChain minCapacity(int minCapacity) {
            delegate.minCapacity(minCapacity);
            return this;
        }

        public ContainerChain exactCapacity(int exactCapacity) {
            delegate.exactCapacity(exactCapacity);
            return this;
        }

        public ContainerChain policy(OpenBindPlan.ResizePolicy resizePolicy) {
            delegate.policy(resizePolicy);
            return this;
        }

        public ContainerChain bind(String containerId) {
            return swap(delegate.bind(containerId));
        }

        public ContainerChain primaryBind(String containerId) {
            return swap(delegate.primaryBind(containerId));
        }

        public ScreenOpenRequest end() {
            delegate.bind();
            return ScreenOpenRequest.this;
        }

        public OpenBindPlan build() {
            return end().build();
        }

        public void open(ServerPlayer player) {
            end().open(player);
        }
    }
}
