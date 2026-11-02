package com.sighs.apricityui.client;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.parser.HTML;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 阶段验收用的开发期 overlay：把指定的逻辑模板路径开成一个 reload-persistent 文档。
 *
 * <p>reload-persistent 的文档在标题界面也会被绘制（{@code Client.drawScreen} →
 * {@code drawPersistentScreenDocuments}），所以字体渲染的验收不依赖先进入世界，
 * 也就绕开了 {@code --quickPlaySingleplayer} 在开发环境里的不确定性。资源管理器
 * 自己也是这么打开的（{@code ResourceManager} 同样会 {@code setReloadPersistent(true)}），
 * 因此用同一个页面路径即可复现它的渲染负载。</p>
 *
 * <p>属性：{@code apricityui.devOverlay.path}（为空时本类完全不动作）、
 * {@code apricityui.devOverlay.holdSeconds}（&gt;0 时保持该秒数后自动退出客户端）。
 * 两个属性都由 {@code -PauiDevOverlayPath} / {@code -PauiDevOverlayHoldSeconds} 映射。</p>
 */
@Mod.EventBusSubscriber(modid = ApricityUI.MODID, value = Dist.CLIENT)
public final class DevOverlayVerificationRunner {
    private static final String PATH_PROPERTY = "apricityui.devOverlay.path";
    private static final String HOLD_SECONDS_PROPERTY = "apricityui.devOverlay.holdSeconds";
    private static final int OPEN_DELAY_TICKS = 10;

    private static int elapsedTicks;
    private static boolean opened;
    private static int ticksSinceOpen;
    private static int stopAtTick = -1;

    private DevOverlayVerificationRunner() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        String path = System.getProperty(PATH_PROPERTY, "").trim();
        boolean resourceManager = Boolean.getBoolean("apricityui.devOverlay.resourceManager");
        if (path.isEmpty() && !resourceManager) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getWindow() == null) return;

        if (!opened) {
            if (++elapsedTicks < OPEN_DELAY_TICKS) return;
            if (resourceManager) {
                // 资源管理器由 dev.ResourceManager 程序化构建、并自己标成 reload-persistent。
                // 直接 Document.create 它的 html 只会得到一个没被填充的空壳（实测只有 28 个元素），
                // 所以这里走它自己的打开入口。用全限定名是为了不再动这个文件的 import 段。
                opened = true;
                int holdSeconds = readHoldSeconds();
                stopAtTick = holdSeconds > 0 ? holdSeconds * 20 : -1;
                ticksSinceOpen = 0;
                com.sighs.apricityui.dev.ResourceManager.open();
                ApricityUI.LOGGER.info("[AUI DevOverlay] opened resourceManager holdSeconds={}", holdSeconds);
                return;
            }
            // 逻辑模板要等 AUI 的客户端资源扫描完成之后才会出现。
            if (HTML.getTemple(path) == null) return;
            opened = true;
            Document.remove(path);
            Document document = Document.create(path);
            if (document == null) {
                ApricityUI.LOGGER.error("[AUI DevOverlay] create failed path={}", path);
                return;
            }
            document.setReloadPersistent(true);
            int holdSeconds = readHoldSeconds();
            stopAtTick = holdSeconds > 0 ? holdSeconds * 20 : -1;
            ticksSinceOpen = 0;
            ApricityUI.LOGGER.info("[AUI DevOverlay] opened path={} holdSeconds={} elements={}",
                    path, holdSeconds, document.getElements().size());
            return;
        }

        if (stopAtTick <= 0) return;
        if (++ticksSinceOpen < stopAtTick) return;
        ApricityUI.LOGGER.info("[AUI DevOverlay] hold elapsed, stopping client");
        minecraft.stop();
    }

    private static int readHoldSeconds() {
        try {
            return Integer.parseInt(System.getProperty(HOLD_SECONDS_PROPERTY, "0").trim());
        } catch (RuntimeException ignored) {
            return 0;
        }
    }
}
