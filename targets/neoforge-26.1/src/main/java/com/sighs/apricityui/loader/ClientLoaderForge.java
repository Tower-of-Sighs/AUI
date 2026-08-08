package com.sighs.apricityui.loader;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.dev.debug.ExternalDebugServer;
import com.sighs.apricityui.parser.HTML;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * NeoForge client-setup hook for {@link ClientLoader}.
 *
 * <p>The shared client-resource reload logic lives in {@code common}; only the
 * event wiring (registration, enqueue work) is loader-specific and lives here.
 * {@code @EventBusSubscriber(value = Dist.CLIENT)} scopes registration to the
 * client; the redundant {@code @OnlyIn} is intentionally omitted because 26.1
 * no longer strips client members at runtime.</p>
 *
 * <p>26.1 runs client setup while {@code Minecraft} is still constructing, so
 * the startup scan in {@link #setup} usually sees an empty client resource
 * manager in production (dev is saved by the filesystem fallbacks in
 * {@link Loader}). Two compensating mechanisms ensure templates still load:
 * {@link #tickTemplateScanRetry} rescans on tick until the pack-provided templates
 * become visible, and {@link #registerResourceReloadListener} rescans on every
 * subsequent client resource reload (F3+T, pack toggles).</p>
 */
@EventBusSubscriber(modid = ApricityUI.MODID, value = Dist.CLIENT)
public final class ClientLoaderForge {
    /** Probe template: once visible, the scan has reached the client resource manager. */
    private static final String TEMPLATE_PROBE = "devtools/resource.html";
    private static final int TEMPLATE_RETRY_INTERVAL_TICKS = 10;
    private static final int TEMPLATE_RETRY_LIMIT = 30;

    private static int templateRetryCountdown = TEMPLATE_RETRY_INTERVAL_TICKS;
    private static int templateRetryAttempts;
    private static boolean initialPackScanPending = true;
    private static boolean reloadListenerRegistered;

    private ClientLoaderForge() {
    }

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        // 初始加载时不调用 ApricityJS.reload()，因为此时其他模组的客户端资源
        // （如模型层）可能尚未注册完毕，强制重载 KubeJS 客户端脚本会导致崩溃。
        event.enqueueWork(() -> {
            ExternalDebugServer.startIfEnabled();
            ClientLoader.reloadResources();
        });
    }

    /**
     * Rescans on tick until the pack-provided templates become visible (see
     * class doc). Driven from {@code Client#tick}: this class is registered on
     * the mod bus for the lifecycle/reload events above, and the mod bus does
     * not accept game-bus events like {@code ClientTickEvent}.
     */
    public static void tickTemplateScanRetry() {
        if (HTML.getTemple(TEMPLATE_PROBE) != null) return;
        if (++templateRetryAttempts > TEMPLATE_RETRY_LIMIT) return;
        if (--templateRetryCountdown > 0) return;
        templateRetryCountdown = TEMPLATE_RETRY_INTERVAL_TICKS;
        HTML.scan();
    }

    /** Hooks template scanning into client resource reloads (F3+T, pack toggles). */
    @SubscribeEvent
    public static void registerResourceReloadListener(AddClientReloadListenersEvent event) {
        // 26.1 may fire this event more than once for the same resource manager
        // during startup; the registry rejects duplicate keys.
        if (reloadListenerRegistered) return;
        reloadListenerRegistered = true;
        event.addListener(
                Identifier.fromNamespaceAndPath(ApricityUI.MODID, "apricity_templates"),
                new PreparableReloadListener() {
                    @Override
                    public CompletableFuture<Void> reload(SharedState sharedState, Executor backgroundExecutor,
                                                          PreparationBarrier barrier, Executor gameExecutor) {
                        return CompletableFuture.<Void>completedFuture(null)
                                .thenCompose(barrier::wait)
                                .thenAcceptAsync(ignored -> rescanAfterReload(), gameExecutor);
                    }

                    @Override
                    public String getName() {
                        return "apricityui_templates";
                    }
                }
        );
    }

    private static void rescanAfterReload() {
        // The first invocation is the initial startup reload: documents cannot
        // exist yet, so scan quietly without the refresh pass and toast.
        if (initialPackScanPending) {
            initialPackScanPending = false;
            HTML.scan();
            return;
        }
        ClientLoader.reloadResources();
    }
}
