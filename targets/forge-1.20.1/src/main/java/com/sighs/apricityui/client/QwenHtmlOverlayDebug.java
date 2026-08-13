package com.sighs.apricityui.client;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.parser.HTML;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Opens the local Qwen editor page once as a persistent overlay in development runs. */
//@Mod.EventBusSubscriber(modid = ApricityUI.MODID, value = Dist.CLIENT)
public final class QwenHtmlOverlayDebug {
    private static final String SOURCE_URI =
            "file:/D:/work/AUI/targets/forge-1.20.1/run/apricity/overlays/Qwen_html.html";
    private static final String SCANNED_TEMPLATE_PATH = "overlays/Qwen_html.html";
    private static final int OPEN_DELAY_TICKS = 10;

    private static int elapsedTicks;
    private static boolean attempted;

    private QwenHtmlOverlayDebug() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || attempted || FMLEnvironment.production) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getWindow() == null) return;
        if (++elapsedTicks < OPEN_DELAY_TICKS) return;

        // The logical template appears only after AUI's client resource scan is ready.
        if (HTML.getTemple(SCANNED_TEMPLATE_PATH) == null) return;

        attempted = true;
        Path source = Path.of(URI.create(SOURCE_URI));
        if (!Files.isRegularFile(source)) {
            ApricityUI.LOGGER.warn("[QwenOverlayDebug] HTML file is missing: {}", source);
            return;
        }

        try {
            String markup = Files.readString(source, StandardCharsets.UTF_8);
            HTML.putTemple(SOURCE_URI, markup);
            Document.remove(SOURCE_URI);

            Document overlay = Document.create(SOURCE_URI);
            if (overlay == null) {
                ApricityUI.LOGGER.error("[QwenOverlayDebug] Failed to create overlay: {}", SOURCE_URI);
                return;
            }
            overlay.setReloadPersistent(true);
            ApricityUI.LOGGER.info("[QwenOverlayDebug] Opened persistent overlay: {}", SOURCE_URI);
        } catch (IOException | RuntimeException | LinkageError exception) {
            ApricityUI.LOGGER.error("[QwenOverlayDebug] Failed to open overlay: {}", SOURCE_URI, exception);
        }
    }
}
